"""Private stages invoked by the supervised two-mode Bash orchestrator."""
from __future__ import annotations
import argparse
from collections import Counter
import os
from pathlib import Path
import sys
from uuid import uuid4

from bootstrap import (BootstrapError, SOURCE_REVISION, atomic_json, canonical_bytes,digest_bytes,digest_file,
                       load_artifact,member_path,read_json,require,validate_environment,operational_report,private_fault)
import database
from storage import (OwnershipLedger,local_s3,inspect_checkpoints,compare_owned_objects,ensure_asset,preflight_checkpoints,
                     begin_reset,cleanup_reset_assets,reconcile_restore_sources)


def importer_digest() -> str:
    root=Path(__file__).parent
    files={p.relative_to(root).as_posix():digest_file(p) for p in sorted(root.rglob('*'))
           if p.is_file() and (p.suffix in {'.py','.sql'} or p.name=='requirements.lock') and '__pycache__' not in p.parts}
    return digest_bytes(canonical_bytes(files))


def source_check(source: Path, manifest: dict) -> None:
    files=manifest.get('source',{}).get('files',{})
    require(len(files)==1512,'SOURCE_INVENTORY','Artifact lacks the complete source inventory')
    for name,expected in files.items():
        path=member_path(source,name)
        require(path.is_file(),'SOURCE_FILE_MISSING','A tracked source file is missing')
        actual=path.read_bytes()
        require(digest_bytes(actual)==expected or
                (b'\x00' not in actual and digest_bytes(actual.replace(b'\r\n',b'\n'))==expected),
                'SOURCE_WORKTREE_DRIFT','Tracked source bytes changed beyond approved checkout newlines')


def check_migration_files(directory: Path, manifest: dict) -> None:
    expected=manifest['importer_compatibility']['migration_fingerprint']['files']
    actual={p.name:digest_file(p) for p in sorted(directory.glob('V*__*.sql'))}
    require(actual==expected,'MIGRATION_INCOMPATIBLE','Migration files differ from the reviewed compatibility fingerprint')


def check_history(connection,manifest,*,prefix=False):
    actual=database.migration_history(connection)
    expected=manifest['importer_compatibility']['migration_history']
    database.compare_history(actual,expected,allow_prefix=prefix)
    if len(actual)==len(expected):
        require(database.schema_fingerprint(connection)==manifest['importer_compatibility'].get('schema_fingerprint'),
                'SCHEMA_INCOMPATIBLE','Live schema/protection fingerprint differs from the reviewed migrated baseline')


def surviving_references(connection,root: Path,ledger: OwnershipLedger) -> set[str]:
    reviewed={r['object_key']:r for r in read_json(root/'assets/images.json')}
    required=set()
    for row in connection.execute('SELECT object_key,product_id,content_type,byte_size,width,height FROM product_images').fetchall():
        key=row['object_key'];required.add(key)
        if key in reviewed:
            expected=reviewed[key]
            require(key in ledger.value['assets'] and ledger.value['assets'][key]['cleanup_state']=='RETAIN'
                    and str(row['product_id'])==expected['product_id'] and row['content_type']==expected['content_type']
                    and row['byte_size']==expected['bytes'] and row['width']==expected['width'] and row['height']==expected['height'],
                    'REFERENCE_CONFLICT','Surviving owned image metadata differs from trusted ownership/content; no rewrite is permitted')
    for row in connection.execute('SELECT final_key AS key FROM image_uploads WHERE final_key IS NOT NULL '
                                  "UNION SELECT durable_key AS key FROM product_image_uploads WHERE durable_key IS NOT NULL AND state IN ('VERIFIED','PUBLISHED')").fetchall():
        required.add(row['key'])
        if row['key'] in reviewed:
            require(row['key'] in ledger.value['assets'] and ledger.value['assets'][row['key']]['cleanup_state']=='RETAIN',
                    'OWNERSHIP_MISSING','A surviving owned reference has no retained ownership evidence')
    return required


def demo_scenario_map(connection,root: Path) -> list[dict]:
    result=[]
    for account in read_json(root/'scenarios/demo-accounts.json'):
        uid=account['id']
        current=connection.execute('SELECT id,username,account_status FROM users WHERE id=%s',(uid,)).fetchone()
        if current is None:
            result.append({'id':uid,'journey':account['journey'],'state':'ACCOUNT_REMOVED'});continue
        entry={**dict(current),'journey':account['journey']}
        pending=connection.execute("SELECT id FROM orders WHERE customer_id=%s AND status='PENDING' ORDER BY id LIMIT 1",(uid,)).fetchone()
        eligible=connection.execute("SELECT i.id FROM order_items i JOIN orders o ON o.id=i.order_id WHERE o.customer_id=%s AND o.status='COMPLETED' "
                                    'AND NOT EXISTS(SELECT 1 FROM product_reviews r WHERE r.order_item_id=i.id) ORDER BY i.id LIMIT 1',(uid,)).fetchone()
        entry.update(pending_order_id=pending['id'] if pending else None,
                     review_eligible_order_item_id=eligible['id'] if eligible else None,
                     initial_customer_quota_fixture=(uid==1),initial_selected_stock_failure_fixture=(uid==5))
        result.append(entry)
    return result


def main():
    parser=argparse.ArgumentParser(description='Private startup stage; use scripts/start-dev.sh')
    parser.add_argument('stage',choices=['artifact','checkpoint-preflight','live-preflight','restore-plan','classify','migrated','import','reset','report-ready'])
    parser.add_argument('--artifact',type=Path,default=Path('/artifact'))
    parser.add_argument('--source',type=Path,default=Path('/source'))
    parser.add_argument('--migrations',type=Path,default=Path('/migrations'))
    parser.add_argument('--checkpoints',type=Path,default=Path('/catalog-checkpoints'))
    parser.add_argument('--report',type=Path,default=Path('/reports/latest.json'))
    args=parser.parse_args()
    manifest=load_artifact(args.artifact)
    require(manifest['importer_compatibility'].get('importer_digest')==importer_digest(),
            'IMPORTER_INCOMPATIBLE','Importer code differs from the approved artifact')
    check_migration_files(args.migrations,manifest)
    compatibility=manifest['importer_compatibility']
    approved_startups=compatibility.get('startup_sha256s',[compatibility.get('startup_sha256')])
    require(isinstance(approved_startups,list) and bool(approved_startups)
            and digest_file(Path('/startup-script')) in approved_startups,
            'STARTUP_INCOMPATIBLE','Startup script differs from the reviewed release')
    if args.stage=='artifact':
        source_check(args.source,manifest)
        print('VERIFIED');return
    validate_environment(dict(os.environ))
    checkpoint_inventory=inspect_checkpoints(args.checkpoints)
    ledger=OwnershipLedger(args.checkpoints,allow_new_empty=not checkpoint_inventory)
    daemon=os.environ.get('BOOTSTRAP_DAEMON_ID','')
    require(bool(daemon) and all(c.isalnum() or c in ':-' for c in daemon),
            'DAEMON_IDENTITY','Orchestrator must provide the verified local Docker daemon identity')
    if ledger.path.exists():
        require(ledger.value.get('daemon_id')==daemon,'OWNERSHIP_CONFLICT','Ledger belongs to another or unverified daemon')
    else:
        ledger.value['daemon_id']=daemon
    if args.stage=='checkpoint-preflight':
        inspect_checkpoints(args.checkpoints)
        print('VERIFIED');return
    if args.stage=='restore-plan':
        if ledger.value.get('reset',{}).get('state') in {'INTENDED','CLEANING'}:
            require(ledger.value['reset']['artifact']==manifest['artifact_digest'],
                    'RESET_INCOMPATIBLE','Saved reset belongs to another artifact')
            # A confirmed reset may intentionally replace an incompatible old
            # dataset. Do not classify/adopt that old DB before reset resumes.
            print('VERIFIED');return
        with database.connect() as connection:
            database.capability_probe(connection)
            state=database.classify(connection,manifest,importer_digest())
            check_history(connection,manifest,prefix=state['state']!='COMPLETED')
            if state['state']=='COMPLETED':
                require(ledger.path.is_file(),'OWNERSHIP_MISSING','Completed dataset lost its durable ownership ledger')
                reconcile_restore_sources(ledger,surviving_references(connection,args.artifact,ledger))
        print('VERIFIED');return
    client=local_s3()
    if not ledger.path.exists():
        try:
            empty=client.list_objects_v2(Bucket='backend-demo-images',MaxKeys=1).get('KeyCount')==0
        except Exception:
            raise BootstrapError('OWNERSHIP_UNPROVEN','Cannot prove a new/empty object resource set; no adoption or reset') from None
        require(empty,'OWNERSHIP_MISSING','Missing ledger with existing object data; no adoption or reset')
    if args.stage=='live-preflight':
        preflight_checkpoints(client,ledger)
        print('VERIFIED');return
    if args.stage=='reset':
        pending=ledger.value.get('reset',{}).get('state') in {'INTENDED','CLEANING'}
        require(os.environ.get('BOOTSTRAP_FRESH_CONFIRMED')=='1' or pending,'CONFIRMATION_REQUIRED','Only interactive confirmation or its saved unfinished intention permits reset',2)
        # This is internal routing, not a public confirmation bypass. Bash never
        # consumes an environment confirmation flag; it always prompts itself.
        if not ledger.path.exists():
            with database.connect() as baseline:
                require(database.metadata(baseline) is None and not database.business_population(baseline),
                        'OWNERSHIP_MISSING','Missing ownership ledger with existing database state; fresh reset refused')
        reset=begin_reset(client,ledger,reset_id=str(uuid4()),artifact_digest=manifest['artifact_digest'])
        if reset['database_state']!='RECREATED':
            with database.connect(maintenance=True) as c:
                database.reset_database(c,target_owner=os.environ['LOCAL_DB_USER'])
            reset['database_state']='RECREATED';ledger.save()
        cleanup_reset_assets(client,ledger)
        print('UNSEEDED');return
    if args.stage=='classify' and ledger.value.get('reset',{}).get('state') in {'INTENDED','CLEANING'}:
        require(ledger.value['reset']['artifact']==manifest['artifact_digest'],'RESET_INCOMPATIBLE','Saved reset belongs to another artifact')
        print('RESET_PENDING');return
    with database.connect() as connection:
        database.capability_probe(connection,migrated=args.stage in {'migrated','import','report-ready'})
        state=database.classify(connection,manifest,importer_digest())
        check_history(connection,manifest,prefix=state['state']!='COMPLETED' and args.stage=='classify')
        if args.stage=='classify':
            print(state['state']);return
        if args.stage=='migrated':
            require(state['state']!='COMPLETED','MIGRATOR_FORBIDDEN','Completed datasets must skip migration')
            print('VERIFIED');return
        if args.stage=='report-ready':
            require(state['state']=='COMPLETED','NOT_INITIALIZED','Backend readiness cannot establish import completion')
            marker=state['marker']
            previous=read_json(args.report) if args.report.is_file() else {}
            previous_initialization=previous.get('initialization',{}) if previous.get('artifact_digest')==manifest['artifact_digest'] else {}
            report={'result':'PASS','stage':'ready','initialization':{'state':'COMPLETED','initialized':True},
                    'backend_ready':True,'artifact_digest':manifest['artifact_digest'],'source_revision':SOURCE_REVISION,
                    'anchor':marker['anchor_at'].isoformat(timespec='microseconds'),
                    'demo_accounts':demo_scenario_map(connection,args.artifact),
                    'unexecuted_checks':['Native Linux/WSL2 host lifecycle','Frontend journeys unless separately evidenced']}
            report['initialization'].update(previous_initialization)
            report['counts']={'initial_import':marker['verification_summary']['counts']}
            report['counts']['actual_current']=database.business_population(connection)
            report['counts']['imported_this_run']=marker['verification_summary']['counts'] if previous_initialization.get('action')=='imported' else 0
            def port(name,default):
                value=os.environ.get(name,str(default))
                require(value.isdigit() and 1<=int(value)<=65535,'INVALID_PORT','Configured local port is invalid',2)
                return str(int(value))
            report['connection_info']={'backend':'http://127.0.0.1:'+port('SERVER_PORT',18081),
                                       'mailpit':'http://127.0.0.1:'+port('LOCAL_MAILPIT_PORT',8025)}
            report['assets']=previous.get('assets',{})
            operational_report(Path('/reports')/str(marker['attempt_id'])/'ready.json',report)
            operational_report(args.report,report)
            print(canonical_bytes(report).decode());return
        images=read_json(args.artifact/'assets/images.json')
        required=None
        if state['state']=='COMPLETED':
            required=surviving_references(connection,args.artifact,ledger)
            require(ledger.path.is_file(),'OWNERSHIP_MISSING','Completed dataset lost its durable asset ledger')
            attempt=None
        else:
            validate_environment(dict(os.environ),need_password=True)
            attempt=database.prepare_attempt(connection,manifest,importer_digest(),ledger)
        outcomes=Counter()
        for image in images:
            key=image['object_key']
            if required is not None and key not in required:
                continue
            data=member_path(args.source,image['source_path']).read_bytes()
            outcomes[ensure_asset(client,ledger,key,image,data,repair=required is not None)['object']]+=1
        if state['state']=='COMPLETED':
            result={'initialized':True,'action':'assets_repaired' if outcomes['repaired'] else 'skipped'}
        else:
            result=database.atomic_import(connection,args.artifact,manifest,attempt,os.environ['DEMO_BOOTSTRAP_PASSWORD'],
                                          ownership_digest=digest_file(ledger.path))
        result['assets']=dict(outcomes)
        result['assets']['verified_bytes']=sum(r['bytes'] for r in images if required is None or r['object_key'] in required)
        result['assets']['ledger_sha256']=digest_file(ledger.path)
        saved=database.metadata(connection) if not connection.closed else None
        identity=str(saved['attempt_id']) if saved else attempt['attempt_id']
        evidence={'result':'PASS','stage':'initialized','artifact_digest':manifest['artifact_digest'],
                  'backend_ready':False,'attempt_id':identity,'initialization':{k:v for k,v in result.items() if k not in {'summary','assets'}},
                  'assets':result['assets'],'counts':result.get('summary',{}).get('counts',{})}
        try:
            private_fault('REPORT_WRITE')
            operational_report(Path('/reports')/identity/'initialization.json',evidence)
            operational_report(args.report,evidence)
        except Exception:
            raise BootstrapError('REPORT_WRITE_FAILED','Dataset is committed (initialized=true); report persistence failed; do not reset',6) from None
        if result['action']=='imported':
            try:
                late=compare_owned_objects(client,ledger)
                require(late['missing']==0,'POST_COMMIT_ASSETS','A required object disappeared after commit',5)
            except Exception:
                evidence.update(result='FAIL',stage='post_commit_assets',message='Dataset committed; object verification failed; retry normal after storage recovery')
                try:
                    operational_report(args.report,evidence)
                    operational_report(Path('/reports')/identity/'post-commit-assets.json',evidence)
                except Exception:pass
                raise BootstrapError('POST_COMMIT_ASSETS','Dataset committed (initialized=true); object verification failed; backend remains stopped; recover storage and retry normal',5) from None
        print(canonical_bytes({k:v for k,v in result.items() if k!='summary'}).decode())


if __name__=='__main__':
    try: main()
    except BootstrapError as e:
        print(canonical_bytes({'result':'FAIL','code':e.code,'message':e.public_message}).decode(),file=sys.stderr)
        raise SystemExit(e.exit_code)
    except Exception:
        print('{"result":"FAIL","code":"RUNTIME_FAILED","message":"Private stage failed; backend remains stopped; inspect sanitized evidence"}',file=sys.stderr)
        raise SystemExit(5)
