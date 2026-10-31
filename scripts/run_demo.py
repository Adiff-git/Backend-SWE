#!/usr/bin/env python3
"""Isolated catalog bootstrap with explicit, stage-bound operator authorization."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import time
import uuid
from urllib.request import urlopen
from urllib.parse import urlsplit,unquote

BACKEND=Path(__file__).resolve().parents[1]

def stages_needed(count):
    if count<0 or count>1500:raise RuntimeError('Unexpected Product count')
    return tuple(stage for stage in (100,1000,1500) if count<stage)

def volume_owned(inspected,project):
    labels=json.loads(inspected)[0].get('Labels') or {}
    return labels.get('com.docker.compose.project')==project and labels.get('com.docker.compose.volume')=='postgres-data'

def env_values(path):
    result={}
    for line in path.read_text(encoding='utf-8').splitlines():
        line=line.strip()
        if not line or line.startswith('#'):continue
        if '=' not in line:raise ValueError('Invalid private environment file')
        key,value=line.split('=',1)
        if not key.isidentifier() or any(x in value for x in ('\n','\r')):raise ValueError('Invalid private environment entry')
        result[key]=value.strip().strip('"').strip("'")
    return result

def command(args,environment,step,stdin=None,stdout=None,allow_failure=False):
    process=subprocess.run(args,cwd=BACKEND,env=environment,stdin=stdin,stdout=stdout if stdout else subprocess.PIPE,
                           stderr=subprocess.PIPE,check=False)
    if process.returncode and not allow_failure:raise RuntimeError(step+' failed; inspect local Docker service logs without sharing secrets')
    return process

def run(catalog,env_file,data_only=False,prepare_only=False,authorization_dir=None):
    if sys.platform!='linux':raise RuntimeError('Demo host-network topology currently requires Linux')
    from demo_preflight import run as preflight
    gate=preflight(catalog,env_file,data_only=data_only)
    if not gate['ready']:raise RuntimeError('Preflight blocked: '+', '.join(gate['blockers']))
    values=env_values(env_file)
    project=values.get('COMPOSE_PROJECT_NAME','')
    if not project.startswith('demo-') or not project.replace('-','').isalnum():
        raise RuntimeError('Use an isolated COMPOSE_PROJECT_NAME beginning demo-')
    if values.get('LOCAL_DB_NAME')!='backend' or values.get('LOCAL_DB_HOST') not in ('127.0.0.1','localhost'):
        raise RuntimeError('Demo target must be loopback backend database')
    if not values.get('LOCAL_DB_PORT','').isdigit() or not values.get('SERVER_PORT','').isdigit() or not values.get('LOCAL_STORAGE_PORT','').isdigit():
        raise RuntimeError('Demo ports must be explicit integers')
    if not values.get('CATALOG_IMPORT_DSN') or not values.get('LOCAL_DB_PASSWORD') or not values.get('JWT_SECRET'):
        raise RuntimeError('Private database/JWT configuration is incomplete')
    dsn=urlsplit(values['CATALOG_IMPORT_DSN'])
    if (dsn.scheme not in ('postgres','postgresql') or dsn.hostname!='127.0.0.1'
            or dsn.port!=int(values['LOCAL_DB_PORT']) or unquote(dsn.path.lstrip('/'))!='backend'
            or unquote(dsn.username or '')!=values.get('LOCAL_DB_USER','backend')
            or unquote(dsn.password or '')!=values['LOCAL_DB_PASSWORD']):
        raise RuntimeError('Private DSN does not match the isolated Compose PostgreSQL target')
    admin=Path(values.get('DEMO_ADMIN_CREDENTIALS',''))
    if not admin.is_absolute():admin=BACKEND/admin
    environment=os.environ.copy();environment.update(values)
    environment['DEMO_UID']=str(os.getuid());environment['DEMO_GID']=str(os.getgid())
    checkpoints=BACKEND/'tools/catalog-import/output/demo-product-checkpoints'
    checkpoints.mkdir(parents=True,exist_ok=True)
    environment['CATALOG_IMAGES_CHECKPOINT_DIRECTORY']=str(checkpoints)
    environment['DEMO_ENV_FILE']=str(env_file.resolve())
    compose=['docker','compose','--env-file',str(env_file.resolve()),'-p',project,
             '-f','compose.yaml','-f','compose.catalog-images.yaml','-f','compose.demo.yaml']
    command(['docker','compose','version'],environment,'Docker Compose availability')
    volume=project+'_postgres-data'
    inspected=command(['docker','volume','inspect',volume],environment,'Volume inspection',allow_failure=True)
    existing=inspected.returncode==0
    if existing:
        if not volume_owned(inspected.stdout,project):
            raise RuntimeError('Existing volume is not owned by the selected Compose project')
    target='backend@127.0.0.1:'+values['LOCAL_DB_PORT']
    verify=[sys.executable,str(BACKEND/'scripts/verify_demo.py'),'--catalog',str(catalog),
            '--target-kind','local-development','--expect-database','backend','--expect-host','127.0.0.1',
            '--expect-port',values['LOCAL_DB_PORT'],'--confirm-target',target]
    if data_only:verify+=['--data-only','--check-api','--api-base','http://127.0.0.1:'+values['SERVER_PORT']]
    else:verify+=['--check-s3','--s3-endpoint','http://127.0.0.1:'+values['LOCAL_STORAGE_PORT'],
                 '--check-api','--api-base','http://127.0.0.1:'+values['SERVER_PORT']]
    if existing and not data_only:
        result=command(verify,environment,'Read-only existing catalog verification',allow_failure=True)
        if result.returncode:raise RuntimeError('Existing volume is incomplete or incompatible; no import or reset attempted')
        image_verify=[sys.executable,str(BACKEND/'tools/catalog-import/amazon_image_import.py'),
                '--catalog',str(catalog),'--mode','verify-only',
                '--permission',str(catalog/'data/approvals/image_permission.json'),
                '--target-kind','local-development','--expect-database','backend',
                '--expect-host','127.0.0.1','--expect-port',values['LOCAL_DB_PORT'],
                '--confirm-target',target]
        command(image_verify,environment,'Read-only real image S3 checksum verification')
        checked=json.loads(result.stdout)
        return {'state':'VERIFIED_EXISTING' if checked.get('published_products') else 'VERIFIED_EXISTING_DRAFT',
                'products':1500,'image_objects':1500,'writes':0,'public_products':checked.get('published_products',0)}
    if not data_only:
        command(compose+['pull','postgres','localstack','image-storage'],environment,'Docker image pull')
    else:
        command(compose+['pull','postgres'],environment,'PostgreSQL image pull')
    command(compose+(['up','-d','--wait','postgres'] if data_only else ['up','-d','--wait','postgres','localstack','image-storage']),environment,'Storage startup')
    command(compose+['build','backend'],environment,'Backend image build')
    command(compose+['up','-d','backend'],environment,'Backend startup and Flyway')
    url='http://127.0.0.1:'+values['SERVER_PORT']+'/api/products?page=0&size=1'
    for _ in range(60):
        try:
            with urlopen(url,timeout=2) as response:
                if response.status==200:break
        except Exception:time.sleep(2)
    else:raise RuntimeError('Backend did not become healthy; no catalog import attempted')
    sys.path.insert(0,str(BACKEND/'tools/catalog-import'))
    import amazon_import
    import import_postgres
    from types import SimpleNamespace
    approvals=catalog/'data/approvals/amazon_import_approved_1500.csv'
    categories_file=catalog/'data/category_mapping.json'
    reports={stage:amazon_import.plan(catalog,approvals,categories_file,stage) for stage in (100,1000,1500)}
    if any(report['pending'] or report['eligible']!=stage for stage,(report,_,_) in reports.items()):
        raise RuntimeError('Approved data import plans are incomplete')
    args=SimpleNamespace(target_kind='local-development',expect_database='backend',expect_host='127.0.0.1',
                         expect_port=int(values['LOCAL_DB_PORT']),confirm_target=target,dsn_env='CATALOG_IMPORT_DSN')
    def inventory():
        conn=import_postgres.target_connection(args,read_only=True)
        try:
            full=reports[1500][1]
            wanted_products={p['product_id'] for p in full};wanted_variants={p['variant_id'] for p in full}
            actual_products={row[0] for row in conn.execute('SELECT id FROM public.products')}
            actual_variants={row[0] for row in conn.execute('SELECT id FROM public.product_variants')}
            actual_categories={row[0] for row in conn.execute('SELECT id FROM public.categories')}
            wanted_categories={uuid.UUID(row['category_uuid']) for row in reports[1500][2].values()}
            if not actual_products<=wanted_products or not actual_variants<=wanted_variants or not actual_categories<=wanted_categories:
                raise RuntimeError('Unexpected catalog identities; no import attempted')
            if any(conn.execute('SELECT count(*) FROM public.'+table).fetchone()[0] for table in
                   ('product_images','product_image_uploads','catalog_source_links')):
                raise RuntimeError('Existing media or source links require separate review')
            if len(actual_products)!=len(actual_variants):raise RuntimeError('Partial Product/Variant count mismatch')
            amazon_import.check_or_apply(conn,full,reports[1500][2],False)
            if actual_products and actual_categories!=wanted_categories:
                raise RuntimeError('Partial catalog has unexpected category set')
            return len(actual_products)
        finally:
            conn.rollback();conn.close()
    count=inventory()
    if count==1500:
        return json.loads(command(verify,environment,'Final read-only verification').stdout)
    dbuser=values.get('LOCAL_DB_USER','backend');database='backend'
    work=BACKEND/'tools/catalog-import/output/demo-setup'/project
    work.mkdir(parents=True,exist_ok=True,mode=0o700)
    work.chmod(0o700)
    backup=work/'preimport.dump'
    if not authorization_dir or prepare_only or not backup.is_file():
        with os.fdopen(os.open(backup,os.O_WRONLY|os.O_CREAT|os.O_TRUNC,0o600),'wb') as output:
            command(compose+['exec','-T','postgres','pg_dump','-U',dbuser,'-d',database,'-Fc'],environment,'Pre-import backup',stdout=output)
    os.chmod(backup,0o600)
    if backup.read_bytes()[:5]!=b'PGDMP':raise RuntimeError('Pre-import backup format invalid')
    restore_db='backend_restore_'+uuid.uuid4().hex[:12]
    command(compose+['exec','-T','postgres','createdb','-U',dbuser,restore_db],environment,'Disposable restore database creation')
    try:
        with backup.open('rb') as source:
            command(compose+['exec','-T','postgres','pg_restore','-U',dbuser,'-d',restore_db],environment,'Disposable backup restore',stdin=source)
        restored=command(compose+['exec','-T','postgres','psql','-U',dbuser,'-d',restore_db,'-Atc',
                 'SELECT count(*) FROM flyway_schema_history'],environment,'Restored Flyway history verification')
        if restored.stdout.strip()!=b'10':raise RuntimeError('Restored database lacks exact V1-V10 history count')
    finally:
        # This database was created by this invocation solely for the restore test.
        command(compose+['exec','-T','postgres','dropdb','-U',dbuser,restore_db],environment,'Disposable restore database cleanup')
    evidence={'target':target,'backup':str(backup),'backup_sha256':hashlib.sha256(backup.read_bytes()).hexdigest(),
              'restore_tested':True,'stages':{str(stage):report['manifest_sha256'] for stage,(report,_,_) in reports.items()}}
    evidence_path=work/'review-evidence.json'
    with os.fdopen(os.open(evidence_path,os.O_WRONLY|os.O_CREAT|os.O_TRUNC,0o600),'w',encoding='utf-8') as stream:
        stream.write(json.dumps(evidence,indent=2)+'\n')
    evidence_path.chmod(0o600)
    if prepare_only or not authorization_dir:
        return {'state':'AUTHORIZATION_REQUIRED','current_products':count,'review_evidence':str(work/'review-evidence.json')}
    for stage in stages_needed(count):
        auth=Path(authorization_dir)/('stage'+str(stage)+'.json')
        amazon_import.authorization(auth,target,reports[stage][0]['manifest_sha256'],backup,stage)
    common=[sys.executable,str(BACKEND/'tools/catalog-import/amazon_import.py'),'--seed',str(catalog),
            '--approvals',str(catalog/'data/approvals/amazon_import_approved_1500.csv'),
            '--categories',str(catalog/'data/category_mapping.json'),
            '--target-kind','local-development','--expect-database','backend','--expect-host','127.0.0.1',
            '--expect-port',values['LOCAL_DB_PORT'],'--confirm-target',target]
    command(common+['--check-db','--stage','1500'],environment,'Read-only seed preview')
    for stage in stages_needed(count):
        auth=Path(authorization_dir)/('stage'+str(stage)+'.json')
        command(common+['--execute','--stage',str(stage),'--authorization-record',str(auth),'--backup',str(backup)],
                environment,'Approved stage '+str(stage)+' import')
        count=inventory()
        if count!=stage:raise RuntimeError('Imported stage count differs from approved scope')
    if data_only:
        return json.loads(command(verify,environment,'Final data-only verification').stdout)
    media_mode=json.loads((catalog/'manifests/catalog_manifest.json').read_text(encoding='utf-8')).get('image_rights_status')
    if media_mode != 'LICENSED_PRODUCT_IMAGES':
        raise RuntimeError('REAL_PRODUCT_IMAGES_REQUIRED_NO_FIXTURE_FALLBACK')
    admin.parent.mkdir(parents=True,exist_ok=True,mode=0o700)
    admin_command=[sys.executable,str(BACKEND/'tools/catalog-import/catalog_admin.py'),
                   '--api-base','http://127.0.0.1:'+values['SERVER_PORT'],
                   '--credentials',str(admin),'--target-kind','local-development',
                   '--expect-database','backend','--expect-host','127.0.0.1',
                   '--expect-port',values['LOCAL_DB_PORT'],'--confirm-target',target]
    if not admin.is_file():admin_command.append('--provision')
    command(admin_command,environment,'Authenticated Admin login after Backend startup')
    image=[sys.executable,str(BACKEND/'tools/catalog-import/amazon_image_import.py'),
           '--catalog',str(catalog),'--mode','execute',
           '--admin-credentials',str(admin),'--checkpoint',str(work/'image-checkpoint.json'),
           '--api-base','http://127.0.0.1:'+values['SERVER_PORT'],'--target-kind','local-development',
           '--expect-database','backend','--expect-host','127.0.0.1','--expect-port',values['LOCAL_DB_PORT'],'--confirm-target',target]
    image.extend(['--permission',str(catalog/'data/approvals/image_permission.json')])
    command(image,environment,'Authorized ProductImage API import')
    checked=json.loads(command(verify,environment,'Final database, S3, and API verification').stdout)
    return {'state':'VERIFIED_FRESH' if checked.get('published_products') else 'VERIFIED_FRESH_DRAFT',
            'products':1500,'image_objects':1500,'public_products':checked.get('published_products',0),'project':project}

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('catalog',type=Path);parser.add_argument('env_file',type=Path)
    parser.add_argument('--data-only',action='store_true');parser.add_argument('--prepare-only',action='store_true')
    parser.add_argument('--authorization-dir',type=Path)
    args=parser.parse_args()
    try:print(json.dumps(run(args.catalog.resolve(),args.env_file.resolve(),args.data_only,args.prepare_only,args.authorization_dir),indent=2))
    except (OSError,ValueError,KeyError,RuntimeError) as failure:
        print(json.dumps({'error':str(failure),'secrets_printed':False}),file=sys.stderr);sys.exit(2)
