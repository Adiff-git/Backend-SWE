"""Real Spring API on the caller's disposable Compose PostgreSQL/S3."""
import contextlib,os,secrets,socket,subprocess,time
from pathlib import Path
import requests
from .isolated_compose import ROOT
@contextlib.contextmanager
def fixture_api(stack):
 with socket.socket() as sock:sock.bind(('127.0.0.1',0));port=sock.getsockname()[1]
 cp=Path('/tmp/backend_catalog_import_classpath.txt').read_text().strip()
 env=dict(stack.env,IMPORT_TEST_JDBC_URL=f'jdbc:postgresql://127.0.0.1:{stack.pgport}/image_seed_test',IMPORT_TEST_DB_USER='test',IMPORT_TEST_DB_PASSWORD='test')
 subprocess.run(['java','--class-path',cp,str(ROOT/'tools/catalog-import/test_support/FlywayBootstrap.java'),str(ROOT/'demo/src/main/resources/db/migration')],env=env,check=True,capture_output=True)
 env.update(LOCAL_DB_HOST='127.0.0.1',LOCAL_DB_PORT=str(stack.pgport),LOCAL_DB_NAME='image_seed_test',LOCAL_DB_USER='test',LOCAL_DB_PASSWORD='test',LOCAL_STORAGE_ENDPOINT=stack.endpoint,LOCAL_STORAGE_SIGNING_ENDPOINT=stack.endpoint,JWT_SECRET=secrets.token_urlsafe(48),JWT_ISSUER='urn:isolated-api:fixtures',SERVER_PORT=str(port),SERVER_ADDRESS='127.0.0.1')
 with open('/tmp/catalog-isolated-upload-api.log','w') as log:
  app=subprocess.Popen(['java','-cp',str(ROOT/'demo/target/classes')+os.pathsep+cp,'com.example.demo.DemoApplication','--spring.profiles.active=local'],env=env,stdout=log,stderr=log);base=f'http://127.0.0.1:{port}'
  try:
   for _ in range(120):
    try:
     if requests.get(base+'/api/products',timeout=2).status_code==200:break
    except requests.RequestException:pass
    if app.poll() is not None:raise RuntimeError('Isolated upload API exited; see log')
    time.sleep(.5)
   else:raise RuntimeError('Isolated upload API startup timed out')
   yield base
  finally:app.terminate();app.wait(timeout=30)
