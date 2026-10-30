"""Authorized generated fixtures only; fresh PostgreSQL/LocalStack and real JWT login."""
import io,json,os,secrets,socket,subprocess,tempfile,time,unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch
import boto3,psycopg,requests
from PIL import Image
from testcontainers.postgres import PostgresContainer
from testcontainers.localstack import LocalStackContainer
from testcontainers.core.container import DockerContainer
from testcontainers.core.network import Network
import import_postgres as imp,portable_seed,import_product_images as bulk,catalog_admin

class ProductImageFlow(unittest.TestCase):
 def test_five_seed_products_real_auth_private_objects_resume(self):
  root=imp.ROOT.parents[1];seed=imp.ROOT/'output/catalog-dev-v1'
  categories,plans=portable_seed.load_seed(seed);public=[p for p in plans if p['status']=='PUBLISHED']
  cp=Path('/tmp/backend_catalog_import_classpath.txt').read_text().strip()
  with tempfile.TemporaryDirectory() as directory,Network() as network,PostgresContainer('postgres:17-alpine',username='test',password='test',dbname='product_images_test') as pg,LocalStackContainer('localstack/localstack:4.14.0').with_services('s3').with_env('S3_SKIP_SIGNATURE_VALIDATION','0').with_network(network).with_network_aliases('localstack') as localstack:
   with DockerContainer('localstack/localstack:4.14.0').with_kwargs(entrypoint=['python3','/opt/catalog/signed_s3_gateway.py']).with_network(network).with_exposed_ports(4566).with_volume_mapping(str(root/'docker/localstack/gateway/signed_s3_gateway.py'),'/opt/catalog/signed_s3_gateway.py','ro') as gateway:
    endpoint='http://127.0.0.1:'+str(gateway.get_exposed_port(4566));port=int(pg.get_exposed_port(5432));dsn=f'postgresql://test:test@127.0.0.1:{port}/product_images_test'
    s3=boto3.client('s3',endpoint_url=endpoint,region_name='us-east-1',aws_access_key_id='test',aws_secret_access_key='test',config=__import__('botocore.config',fromlist=['Config']).Config(signature_version='s3v4'))
    for _ in range(30):
     try:s3.create_bucket(Bucket='backend-images');break
     except Exception:time.sleep(.5)
    s3.put_public_access_block(Bucket='backend-images',PublicAccessBlockConfiguration=dict(BlockPublicAcls=True,IgnorePublicAcls=True,BlockPublicPolicy=True,RestrictPublicBuckets=True))
    env=dict(os.environ,IMPORT_TEST_JDBC_URL=f'jdbc:postgresql://127.0.0.1:{port}/product_images_test',IMPORT_TEST_DB_USER='test',IMPORT_TEST_DB_PASSWORD='test')
    subprocess.run(['java','--class-path',cp,str(imp.ROOT/'test_support/FlywayBootstrap.java'),str(imp.MIGRATIONS)],env=env,check=True,capture_output=True)
    with psycopg.connect(dsn) as db:portable_seed.apply_seed(db,categories,plans,True);db.commit()
    with socket.socket() as sock:sock.bind(('127.0.0.1',0));apiport=sock.getsockname()[1]
    base=f'http://127.0.0.1:{apiport}'
    tmp=Path(directory)
    env.update(CATALOG_IMAGES_CHECKPOINT_DIRECTORY=str(tmp/'object-checkpoints'),LOCAL_DB_HOST='127.0.0.1',LOCAL_DB_PORT=str(port),LOCAL_DB_NAME='product_images_test',LOCAL_DB_USER='test',LOCAL_DB_PASSWORD='test',JWT_SECRET=secrets.token_urlsafe(48),JWT_ISSUER='urn:product-image:fixtures',SERVER_PORT=str(apiport),SERVER_ADDRESS='127.0.0.1',LOCAL_STORAGE_ENDPOINT=endpoint,LOCAL_STORAGE_SIGNING_ENDPOINT=endpoint)
    log=open('/tmp/product-image-fixture-api.log','w')
    app=subprocess.Popen(['java','-cp',str(root/'demo/target/classes')+os.pathsep+cp,'com.example.demo.DemoApplication','--spring.profiles.active=local','--catalog.upload-cleanup-delay-ms=1000'],env=env,stdout=log,stderr=log)
    try:
     for _ in range(120):
      try:
       if requests.get(base+'/api/products',timeout=1).status_code==200:break
      except requests.RequestException:pass
      if app.poll() is not None:raise RuntimeError('Fixture backend exited; see log')
      time.sleep(.5)
     else:raise RuntimeError('Fixture backend timeout')
     tmp=Path(directory);credentials=tmp/'admin.json'
     target=dict(target_kind='disposable',expect_database='product_images_test',expect_host='127.0.0.1',expect_port=port,confirm_target=f'product_images_test@127.0.0.1:{port}',dsn_env='CATALOG_IMPORT_DSN')
     with patch.dict(os.environ,{'CATALOG_IMPORT_DSN':dsn,'LOCAL_STORAGE_ENDPOINT':endpoint}):
      catalog_admin.provision(SimpleNamespace(**target,provision=True,username='fixture-admin',credentials=credentials,api_base=base))
      token=json.loads(credentials.read_text())['token'];session=requests.Session();session.headers['Authorization']='Bearer '+token
      manifest=[]
      for n,p in enumerate(public):
       name=str(n)+'.png';Image.new('RGB',(32,24),(n*40,90,120)).save(tmp/name)
       manifest.append(dict(product_id=str(p['product_id']),source_identity=p['identity'],name=p['name'],images=[dict(source_url='fixture://'+name,issue=None)]))
      Image.new('RGB',(24,32),'yellow').save(tmp/'extra.png');manifest[0]['images'].append(dict(source_url='fixture://extra.png',issue=None))
      mf=tmp/'images.json';mf.write_text(json.dumps({'products':manifest}))
      args=SimpleNamespace(**target,manifest=mf,seed=seed,checkpoint=tmp/'checkpoint.json',mode='execute',permission=None,fixture_root=tmp,api_base=base,token_env='CATALOG_ADMIN_TOKEN',admin_credentials=credentials,download_interval=.25)
      # Interrupt one PUT: other products continue; resume must complete the exact same upload session.
      original=bulk.put_object;failed=[False]
      def flaky(*a,**kw):
       if not failed[0]:failed[0]=True;raise requests.ConnectionError('synthetic PUT interruption')
       return original(*a,**kw)
      with patch('import_product_images.put_object',side_effect=flaky):first=bulk.run(args)
      self.assertTrue(first['errors']);args.mode='resume';result=bulk.run(args);self.assertFalse(result['errors']);self.assertEqual(result['verified'],6)
      again=bulk.run(args);self.assertFalse(again['errors']);self.assertEqual(again['verified'],6)
      args.mode='verify';self.assertEqual(bulk.run(args)['verified'],6)
      with psycopg.connect(dsn) as db:
       self.assertEqual(db.execute('select count(*) from product_images').fetchone()[0],6)
       self.assertEqual(db.execute('select count(*) from products').fetchone()[0],1000)
       for p in public:
        self.assertEqual(db.execute('select count(*) from product_images where product_id=%s and is_primary',(p['product_id'],)).fetchone()[0],1)
       self.assertEqual(db.execute('select display_order from product_images where product_id=%s order by display_order',(public[0]['product_id'],)).fetchall(),[(0,),(1,)])
      # Prove LocalStack object restoration from synchronous verified checkpoints.
      self.assertEqual(len(list((tmp/'object-checkpoints').glob('*.json'))),6)
      localstack.get_wrapped_container().restart()
      for _ in range(60):
       try:s3.create_bucket(Bucket='backend-images');break
       except Exception:time.sleep(.5)
      import importlib.util
      spec=importlib.util.spec_from_file_location('restore_images',root/'docker/localstack/init/restore_product_images.py');restore=importlib.util.module_from_spec(spec);spec.loader.exec_module(restore)
      self.assertEqual(restore.restore(tmp/'object-checkpoints',s3,'backend-images'),6)
      self.assertEqual(restore.restore(tmp/'object-checkpoints',s3,'backend-images'),6)
      self.assertEqual(bulk.run(args)['verified'],6)
      listing=requests.get(base+'/api/products?page=0&size=20').json();self.assertEqual(listing['totalItems'],5);self.assertTrue(all(p['primaryImageUrl'] for p in listing['items']))
      for p in public:
       detail=requests.get(base+'/api/products/'+str(p['product_id'])).json()
       for image in detail['images']:
        url=image['url'];self.assertIn('X-Amz-Expires=300',url)
        response=requests.get(url);self.assertEqual(response.status_code,200);bulk.validate_bytes(response.content)
        self.assertEqual(requests.get(url.split('?')[0]).status_code,403)
        self.assertEqual(requests.get(url.split('?')[0]+'?X-Amz-Signature=abc').status_code,403)
        self.assertEqual(requests.get(url.split('?')[0],headers={'Authorization':'AWS4-HMAC-SHA256 Credential=test/20261029/us-east-1/s3/aws4_request, SignedHeaders=host, Signature='+'0'*64}).status_code,403)
        tampered=url[:-1]+('1' if url[-1]!='1' else '2');self.assertEqual(requests.get(tampered).status_code,403)
      # Prove actual expiration with an otherwise valid old 300-second signature, no five-minute sleep.
      from datetime import datetime,timedelta,timezone
      import botocore.auth
      with patch('botocore.auth.get_current_datetime',return_value=datetime.now(timezone.utc)-timedelta(seconds=301)):
       key=s3.list_objects_v2(Bucket='backend-images',Prefix='images/products/')['Contents'][0]['Key'];expired=s3.generate_presigned_url('get_object',Params=dict(Bucket='backend-images',Key=key),ExpiresIn=300)
      self.assertIn('X-Amz-Expires=300',expired);self.assertEqual(requests.get(expired).status_code,403)
      pid=str(public[0]['product_id']);path=base+'/api/products/'+pid+'/images/uploads'
      self.assertEqual(requests.post(path,json={}).status_code,401)
      # Real customer token is authenticated but not ADMIN.
      reg=requests.post(base+'/api/auth/register',json=dict(username='fixture-customer',password=secrets.token_urlsafe(24)))
      self.assertEqual(requests.post(path,json={},headers={'Authorization':reg.headers['Authorization']}).status_code,403)
      # The existing JWT immediately loses admin access when its database role is revoked.
      with psycopg.connect(dsn) as db:
       uid=db.execute("select id from users where username='fixture-admin'").fetchone()[0]
       db.execute("delete from user_roles where user_id=%s and role_id=(select id from roles where name='ADMIN')",(uid,));db.commit()
      self.assertEqual(session.post(path,json={}).status_code,403)
      with psycopg.connect(dsn) as db:
       db.execute("insert into user_roles(user_id,role_id) select %s,id from roles where name='ADMIN'",(uid,));db.commit()
      # A conflict after verified S3 copy rolls back gallery changes; same session can recover.
      buf=io.BytesIO();Image.new('RGB',(20,20),'red').save(buf,format='PNG');data=buf.getvalue()
      prepared=session.post(path,json=dict(contentType='image/png',byteSize=len(data),altText='recoverable fixture')).json()
      requests.put(prepared['uploadUrl'],data=data,headers={'Content-Type':'image/png'})
      self.assertEqual(session.post(path+'/'+prepared['uploadId']+'/complete',json={'displayOrder':0}).status_code,409)
      with psycopg.connect(dsn) as db:self.assertEqual(db.execute('select count(*) from product_images').fetchone()[0],6)
      recovered=session.post(path+'/'+prepared['uploadId']+'/complete',json={'displayOrder':2,'isPrimary':True})
      self.assertEqual(recovered.status_code,201)
      with psycopg.connect(dsn) as db:self.assertEqual(db.execute('select count(*) from product_images where product_id=%s and is_primary',(public[0]['product_id'],)).fetchone()[0],1)
      # Same bytes in another session cannot create a duplicate gallery row.
      duplicate=session.post(path,json=dict(contentType='image/png',byteSize=len(data),altText='duplicate fixture')).json()
      requests.put(duplicate['uploadUrl'],data=data,headers={'Content-Type':'image/png'})
      self.assertEqual(session.post(path+'/'+duplicate['uploadId']+'/complete',json={}).status_code,409)
      with psycopg.connect(dsn) as db:
       key=db.execute('select durable_key from product_image_uploads where id=%s',(duplicate['uploadId'],)).fetchone()[0]
       db.execute("update product_image_uploads set prepared_at=clock_timestamp()-interval '900 seconds',expires_at=clock_timestamp()-interval '1 second' where id=%s",(duplicate['uploadId'],));db.commit()
      for _ in range(20):
       time.sleep(.25)
       try:s3.head_object(Bucket='backend-images',Key=key)
       except s3.exceptions.ClientError:break
      else:raise AssertionError('Expired unreferenced durable object not cleaned')
      # Invalid bytes never create gallery metadata.
      prepared=session.post(path,json=dict(contentType='image/png',byteSize=4,altText='invalid fixture')).json()
      other=tmp/'other-admin.json'
      catalog_admin.provision(SimpleNamespace(**target,provision=True,username='fixture-other-admin',credentials=other,api_base=base))
      other_token=json.loads(other.read_text())['token']
      self.assertEqual(requests.post(path+'/'+prepared['uploadId']+'/complete',json={},headers={'Authorization':'Bearer '+other_token}).status_code,403)
      requests.put(prepared['uploadUrl'],data=b'nope',headers={'Content-Type':'image/png'})
      self.assertEqual(session.post(path+'/'+prepared['uploadId']+'/complete',json={}).status_code,422)
      with psycopg.connect(dsn) as db:self.assertEqual(db.execute('select count(*) from product_images').fetchone()[0],7)
      args.mode='execute';args.checkpoint=tmp/'recovered-new-checkpoint.json'
      self.assertFalse(bulk.run(args)['errors'])
      with psycopg.connect(dsn) as db:self.assertEqual(db.execute('select count(*) from product_images').fetchone()[0],7)
      print('Fixture evidence: 5 seed products, 6 bulk images + 1 recovered gallery image; checkpoint restore and cleanup verified; interrupted PUT resumed; repeated import unchanged; unsigned/tampered/expired GET=403; anonymous=401; customer=403')
    finally:app.terminate();app.wait(timeout=30);log.close()
if __name__=='__main__':unittest.main(verbosity=2)
