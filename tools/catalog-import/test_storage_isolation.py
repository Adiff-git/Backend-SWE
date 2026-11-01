import unittest,json,io,os
from datetime import datetime,timedelta,timezone
from unittest.mock import patch
import boto3,requests
import psycopg
from PIL import Image
from types import SimpleNamespace
import portable_seed,catalog_admin,import_postgres
from botocore.config import Config
from test_support.isolated_compose import isolated_stack
from test_support.fixture_api import fixture_api
class StorageIsolation(unittest.TestCase):
 def test_compose_isolation_health_signed_operations_and_restart(self):
  with isolated_stack() as stack:
   s3=boto3.client('s3',endpoint_url=stack.endpoint,region_name='us-east-1',aws_access_key_id='test',aws_secret_access_key='test',config=Config(signature_version='s3v4'))
   s3.put_object(Bucket='backend-images',Key='fixture.txt',Body=b'authorized storage operation',ContentType='text/plain')
   url=s3.generate_presigned_url('get_object',Params={'Bucket':'backend-images','Key':'fixture.txt'},ExpiresIn=300)
   self.assertEqual(requests.get(url).content,b'authorized storage operation')
   self.assertEqual(requests.get(url.split('?')[0]).status_code,403)
   self.assertEqual(requests.get(url+'&tamper=yes').status_code,403)
   with patch('botocore.auth.get_current_datetime',return_value=datetime.now(timezone.utc)-timedelta(seconds=301)):
    expired=s3.generate_presigned_url('get_object',Params={'Bucket':'backend-images','Key':'fixture.txt'},ExpiresIn=300)
   self.assertEqual(requests.get(expired).status_code,403)
   attrs=stack.inspect('localstack');ip=next(iter(attrs['NetworkSettings']['Networks'].values()))['IPAddress']
   self.assertEqual(set(attrs['NetworkSettings']['Ports']['4567/tcp'][0].values()),{'127.0.0.1',str(stack.s3port)})
   self.assertIsNone(attrs['NetworkSettings']['Ports']['4566/tcp'])
   for network in [stack.project+'_default',stack.project+'_storage-private']:
    raw=stack.attack(network,ip,4566,'/backend-images/fixture.txt');self.assertTrue(raw.get('blocked'),raw)
   gateway=stack.attack(stack.project+'_storage-private',ip,4567,'/backend-images/fixture.txt');self.assertEqual(gateway['status'],403)
   stack.compose('restart','image-storage');stack.wait();self.assertEqual(requests.get(url).status_code,200)
   # Restarting the gateway does not restart or erase raw storage.
   self.assertEqual(s3.get_object(Bucket='backend-images',Key='fixture.txt')['Body'].read(),b'authorized storage operation')
   with fixture_api(stack) as base:
    with psycopg.connect(stack.dsn) as db:
     portable_seed.apply_seed(db,*portable_seed.load_seed(import_postgres.ROOT/'output/catalog-dev-v1'),True);db.commit()
    credentials=stack.directory/'admin.json'
    args=SimpleNamespace(target_kind='disposable',expect_database='image_seed_test',expect_host='127.0.0.1',expect_port=stack.pgport,confirm_target=f'image_seed_test@127.0.0.1:{stack.pgport}',dsn_env='CATALOG_IMPORT_DSN',provision=True,username='isolated-fixture-admin',credentials=credentials,api_base=base)
    with patch.dict(os.environ,{'CATALOG_IMPORT_DSN':stack.dsn}):catalog_admin.provision(args)
    admin=requests.Session();admin.headers['Authorization']='Bearer '+json.loads(credentials.read_text())['token']
    pid=requests.get(base+'/api/products').json()['items'][0]['id'];route=base+'/api/products/'+pid+'/images/uploads'
    self.assertEqual(requests.post(route,json={}).status_code,401)
    data=io.BytesIO();Image.new('RGB',(12,10),'blue').save(data,format='PNG');data=data.getvalue()
    response=admin.post(route,json={'contentType':'image/png','byteSize':len(data),'altText':'Authorized isolated upload fixture'});self.assertEqual(response.status_code,201)
    prepared=response.json();self.assertEqual(requests.put(prepared['uploadUrl'],data=data,headers={'Content-Type':'image/png'}).status_code,200)
    completed=admin.post(route+'/'+prepared['uploadId']+'/complete',json={});self.assertIn(completed.status_code,(200,201))
    detail=requests.get(base+'/api/products/'+pid).json();self.assertEqual(len(detail['images']),1);self.assertTrue(detail['images'][0]['isPrimary']);self.assertEqual(requests.get(detail['primaryImageUrl']).content,data)
   print('Isolation evidence:',json.dumps({'health':'all healthy','raw_from_application_and_storage_network':'blocked','unsigned_gateway':403,'signed_GET':200,'signed_PUT':'passed','gateway_restart':'passed'}))
if __name__=='__main__':unittest.main(verbosity=2)
