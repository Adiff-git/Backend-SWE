"""Host stdlib-only maintainer adapter for the existing unpublished migrator.

This is offline preparation, not a third user startup mode. Database work always
runs in the approved non-root helper. It also permits Windows preparation while
native Bash lifecycle verification is explicitly deferred.
"""
from __future__ import annotations
import json
import os
from pathlib import Path
import subprocess
import time
from uuid import uuid4
from bootstrap import BootstrapError,PROJECT,SOURCE_REVISION,atomic_json,require,validate_compose,canonical_bytes


def migrate(candidate: Path, env_file: Path, compose_overlay: Path | None = None) -> dict:
    repo=Path(__file__).resolve().parents[2]
    candidate=candidate.resolve(strict=True)
    require(candidate.is_relative_to(repo/'.private/demo-bootstrap/preparation'), 'UNSAFE_PATH','Preparation candidate must remain in its private workspace')
    require(env_file.resolve(strict=True).is_relative_to(repo),'UNSAFE_PATH','Maintainer configuration must be local to this repository')
    image='backend-swe-demo-bootstrap:local'
    require(not any(os.environ.get(k) for k in ('AWS_PROFILE','AWS_DEFAULT_PROFILE','AWS_ENDPOINT_URL','AWS_ENDPOINT_URL_S3','DB_URL','DATABASE_URL'))
            and not any(k.startswith('PG') and v for k,v in os.environ.items()),
            'UNSAFE_OVERRIDE','Ambient cloud/database discovery and session overrides are forbidden',2)
    token=str(uuid4())
    lock='backend-swe-demo-bootstrap-lock'
    migrator=f'{PROJECT}-prepare-migrator-{token}'
    helper=f'{PROJECT}-prepare-helper-{token}'
    reserved=False
    def run(args,*,allow_failure=False,timeout=600,input_bytes=None):
        try:
            result=subprocess.run(args,cwd=repo,capture_output=True,timeout=timeout,input=input_bytes)
        except (OSError,subprocess.TimeoutExpired):
            raise BootstrapError('PREPARATION_RUNTIME','Local preparation command failed or exceeded its deadline',5) from None
        require(allow_failure or result.returncode==0,'PREPARATION_RUNTIME','Local preparation command failed; no import/reset was attempted',5)
        return result
    def inspect(name):
        result=run(['docker','inspect',name],allow_failure=True)
        return json.loads(result.stdout)[0] if result.returncode==0 else None
    compose=['docker','compose','--project-name',PROJECT,'--env-file',str(env_file),'-f',str(repo/'compose.dev.yaml')]
    if compose_overlay is not None:
        require(compose_overlay.resolve(strict=True).is_relative_to(repo/'.private'),
                'UNSAFE_PATH','Verification overlay must remain in the ignored private workspace')
        compose+=['-f',str(compose_overlay)]
    def helper_state():
        result=run(['docker','run','--rm','--name',helper,'--label',f'demo-bootstrap.invocation={token}',
                    '--network',PROJECT+'_default','--env-file',str(env_file),
                    '--mount',f'type=bind,source={repo.as_posix()}/tools/demo-bootstrap,target=/app,readonly',
                    '--mount',f'type=bind,source={candidate.as_posix()},target=/candidate,readonly',
                    '--mount',f'type=bind,source={repo.as_posix()}/demo/src/main/resources/db/migration,target=/migrations,readonly',
                    '--entrypoint','python',image,'/app/prepare.py','migration-state','--workspace','/candidate','--migrations','/migrations'])
        return json.loads(result.stdout)
    context=json.loads(run(['docker','context','inspect']).stdout)[0]
    endpoint=os.environ.get('DOCKER_HOST') or context['Endpoints']['docker']['Host']
    require(endpoint.startswith('unix:///') or endpoint=='npipe:////./pipe/dockerDesktopLinuxEngine',
            'EXTERNAL_DAEMON','Only the verified local Docker daemon is allowed')
    run(['docker','image','inspect',image])
    require(run(['git','-C',str(repo/'data/Catalog-Data'),'rev-parse','HEAD']).stdout.decode().strip()==SOURCE_REVISION,
            'SOURCE_REVISION_MISMATCH','Source Git revision changed')
    run(['git','-C',str(repo/'data/Catalog-Data'),'diff','--quiet',SOURCE_REVISION,'--'])
    configuration=json.loads(run(compose+['config','--format','json']).stdout)
    validate_compose(configuration)
    try:
        claim=run(['docker','create','--name',lock,'--network','none','--label',f'demo-bootstrap.invocation={token}',
                   '--label',f'demo-bootstrap.project={PROJECT}','--label','demo-bootstrap.database=backend_demo',
                   '--label','demo-bootstrap.bucket=backend-demo-images','--entrypoint','python',image,'-c','pass'],allow_failure=True)
        require(claim.returncode==0,'LOCK_HELD','Bootstrap lock is occupied; no competing preparation is permitted',3)
        reserved=True
        networks=run(['docker','network','ls','--format','{{.Name}}']).stdout.decode().splitlines()
        for name in (PROJECT+'_default',PROJECT+'_storage-private'):
            if name in networks:
                info=json.loads(run(['docker','network','inspect',name]).stdout)[0]
                require(info.get('Labels',{}).get('com.docker.compose.project')==PROJECT and info['Driver']=='bridge',
                        'NETWORK_OWNERSHIP','Existing network is not an owned local project bridge')
        for service in ('postgres','localstack','image-storage','mailpit','backend'):
            ids=run(compose+['ps','--all','--quiet',service]).stdout.decode().split()
            for identity in ids:
                info=inspect(identity)
                require(info is not None and info['Config']['Labels'].get('com.docker.compose.project')==PROJECT,
                        'RESOURCE_OWNERSHIP','Existing service ownership differs')
                if service!='backend':
                    if service=='image-storage':
                        parent_ids=run(compose+['ps','--all','--quiet','localstack']).stdout.decode().split()
                        require(len(parent_ids)==1,'RESOURCE_OWNERSHIP','Storage gateway lost its owned namespace parent')
                        parent=inspect(parent_ids[0])
                        namespace='container:'+parent['Id']
                        require(info['HostConfig']['NetworkMode']==namespace,'RESOURCE_OWNERSHIP','Storage gateway shares an unexpected namespace')
                        overlay={'services':{'image-storage':{'network_mode':namespace}}}
                        expected=run(compose+['-f','-','config','--hash',service],input_bytes=canonical_bytes(overlay)).stdout.decode().split()[-1]
                    else:
                        expected=run(compose+['config','--hash',service]).stdout.decode().split()[-1]
                    require(info['Config']['Labels'].get('com.docker.compose.config-hash')==expected,
                            'SERVICE_DRIFT','Preparation refuses unintended infrastructure recreation')
            if service=='backend' and ids:run(compose+['stop','backend'])
        volumes=run(['docker','volume','ls','--format','{{.Name}}']).stdout.decode().splitlines()
        for name in ('postgres-data','product-checkpoints'):
            full=PROJECT+'_'+name
            if full in volumes:
                info=json.loads(run(['docker','volume','inspect',full]).stdout)[0]
                require(info.get('Labels',{}).get('com.docker.compose.project')==PROJECT,'VOLUME_OWNERSHIP','Existing volume ownership differs')
        checkpoints=PROJECT+'_product-checkpoints'
        if checkpoints not in volumes:
            run(['docker','volume','create','--label',f'com.docker.compose.project={PROJECT}',
                 '--label','com.docker.compose.volume=product-checkpoints',checkpoints])
            run(['docker','run','--rm','--network','none','--mount',f'type=volume,source={checkpoints},target=/catalog-checkpoints',
                 '--entrypoint','python',image,'-c','import os; assert os.access("/catalog-checkpoints",os.W_OK)'])
        run(['docker','run','--rm','--network','none','--mount',f'type=volume,source={checkpoints},target=/catalog-checkpoints,readonly',
             '--entrypoint','python',image,'-c','from pathlib import Path; assert not list(Path("/catalog-checkpoints").glob("*.json")), "Preparation requires empty checkpoint inputs"'])
        run(compose+['up','-d','--no-deps','--no-recreate','--wait','--wait-timeout','180','postgres'])
        state=helper_state()
        if state['state']=='READY':
            return {'result':'PASS','state':'READY','action':'existing_migrations_verified','business_imported':False}
        require(state['state']=='NEEDS_MIGRATION','FOREIGN_DATA','Preparation target is not an empty compatible baseline')
        for service in ('localstack','image-storage','mailpit'):
            run(compose+['up','-d','--no-deps','--no-recreate','--wait','--wait-timeout','180',service])
        run(compose+['build','backend'],timeout=1800)
        run(compose+['run','-d','--no-deps','--name',migrator,'--label',f'demo-bootstrap.invocation={token}',
                     '-e','SERVER_ADDRESS=127.0.0.1','-e','SECURITY_IDENTITY_ENABLED=false','-e','SECURITY_VERIFICATION_ENABLED=false',
                     '-e','SECURITY_VERIFICATION_MAIL_WORKER_ENABLED=false','-e','SECURITY_AUTH_CLEANUP_ENABLED=false','-e','BEHAVIOR_RETENTION_ENABLED=false','backend'])
        deadline=time.monotonic()+600
        while time.monotonic()<deadline:
            info=inspect(migrator)
            require(info is not None and info['State']['Running'],'MIGRATION_FAILED','Temporary backend exited before readiness',5)
            require(not any(info['NetworkSettings'].get('Ports',{}).values()),'EXTERNAL_BIND','Temporary backend unexpectedly published a port')
            probe=run(['docker','exec',migrator,'wget','-q','-O','/dev/null',
                       'http://127.0.0.1:8080/api/products?q=catalog-readiness-probe&page=0&size=1'],allow_failure=True,timeout=10)
            if probe.returncode==0:break
            time.sleep(1)
        else:raise BootstrapError('MIGRATION_TIMEOUT','Temporary backend readiness deadline exceeded',5)
        run(['docker','stop','--time','30',migrator])
        require(helper_state()['state']=='READY','MIGRATION_INCOMPATIBLE','Actual migration history differs after backend readiness')
        return {'result':'PASS','state':'READY','action':'unpublished_existing_backend_migrated','business_imported':False}
    finally:
        safe=True
        if reserved:
            for name in (helper,migrator):
                info=inspect(name)
                if info is None:
                    # Verify daemon availability before absence can release the lock.
                    if run(['docker','info'],allow_failure=True).returncode:safe=False
                    continue
                if info['Config']['Labels'].get('demo-bootstrap.invocation')!=token:safe=False;continue
                run(['docker','stop','--time','30',name],allow_failure=True)
                stopped=inspect(name)
                if stopped and stopped['State']['Running']:safe=False
                elif stopped:run(['docker','rm',name],allow_failure=True)
            info=inspect(lock)
            if safe and info and info['Config']['Labels'].get('demo-bootstrap.invocation')==token:
                run(['docker','rm',lock])
