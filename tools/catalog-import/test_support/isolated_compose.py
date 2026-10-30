"""Disposable Compose topology identical to the pilot; no pilot environment or volumes."""
import contextlib,json,os,secrets,socket,subprocess,tempfile,time
from pathlib import Path
ROOT=Path(__file__).resolve().parents[3]
IMAGE='localstack/localstack:4.14.0@sha256:3ebc37595918b8accb852f8048fef2aff047d465167edd655528065b07bc364a'
def port():
 with socket.socket() as s:s.bind(('127.0.0.1',0));return s.getsockname()[1]
class Stack:
 def __init__(self):
  self.directory=Path(tempfile.mkdtemp(prefix='catalog-isolation-'));self.project='catalog-isolation-'+secrets.token_hex(5)
  self.pgport=port();self.s3port=port();self.endpoint=f'http://127.0.0.1:{self.s3port}'
  self.env={k:v for k,v in os.environ.items() if not k.startswith(('LOCAL_','CATALOG_','COMPOSE_'))}
  self.env.update(LOCAL_DB_NAME='image_seed_test',LOCAL_DB_USER='test',LOCAL_DB_PASSWORD='test',LOCAL_DB_PORT=str(self.pgport),LOCAL_STORAGE_PORT=str(self.s3port),CATALOG_IMAGES_CHECKPOINT_DIRECTORY=str(self.directory/'checkpoints'))
  for name in ['checkpoints','postgres','localstack']:(self.directory/name).mkdir()
  override=self.directory/'compose.yaml'
  override.write_text('''services:
  postgres:
    volumes: !override
      - '''+str(self.directory/'postgres')+''':/var/lib/postgresql/data
  localstack:
    volumes: !override
      - '''+str(self.directory/'localstack')+''':/var/lib/localstack
      - '''+str(ROOT/'docker/localstack/init/10-init-s3.sh')+''':/etc/localstack/init/ready.d/10-init-s3.sh:ro
      - '''+str(ROOT/'docker/localstack/init/restore_product_images.py')+''':/opt/catalog/restore_product_images.py:ro
      - '''+str(ROOT/'docker/localstack/gateway/signed_s3_gateway.py')+''':/opt/catalog/signed_s3_gateway.py:ro
      - '''+str(self.directory/'checkpoints')+''':/catalog-product-checkpoints:ro
volumes: !reset {}
''')
  self.command=['docker','compose','-p',self.project,'-f',str(ROOT/'compose.yaml'),'-f',str(ROOT/'compose.catalog-images.yaml'),'-f',str(override)]
  self.dsn=f'postgresql://test:test@127.0.0.1:{self.pgport}/image_seed_test'
 def compose(self,*args):return subprocess.run(self.command+list(args),env=self.env,check=True,capture_output=True,text=True).stdout
 def inspect(self,service):return json.loads(subprocess.check_output(['docker','inspect',self.project+'-'+service+'-1']))[0]
 def attack(self,network,host,port,path):
  code='''import http.client,json,socket
try:
 c=http.client.HTTPConnection(HOST,PORT,timeout=3);c.request('GET',PATH);r=c.getresponse();print(json.dumps({'status':r.status,'bytes':len(r.read())}))
except (OSError,TimeoutError) as e:print(json.dumps({'blocked':True,'error':type(e).__name__}))
'''.replace('HOST',repr(host)).replace('PORT',str(port)).replace('PATH',repr(path))
  data=subprocess.check_output(['docker','run','--rm','--read-only','--cap-drop=ALL','--security-opt=no-new-privileges','--network',network,'--entrypoint','python3',IMAGE,'-c',code],text=True)
  return json.loads(data)
 def wait(self):
  deadline=time.monotonic()+180
  while time.monotonic()<deadline:
   if all(self.inspect(s)['State'].get('Health',{}).get('Status')=='healthy' for s in ['postgres','localstack','image-storage']):return
   time.sleep(1)
  raise RuntimeError('Compose health timeout: '+self.compose('ps'))
@contextlib.contextmanager
def isolated_stack():
 stack=Stack()
 try:stack.compose('up','-d','--wait','--wait-timeout','180');yield stack
 finally:stack.compose('down','--timeout','10') # Bind-mounted test artifacts retained; never delete Docker volumes.
