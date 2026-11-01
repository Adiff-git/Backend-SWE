import importlib.util,unittest
from pathlib import Path
from datetime import datetime,timedelta,timezone
from urllib.parse import urlsplit
import boto3
from botocore.config import Config
from botocore.auth import S3SigV4Auth
from botocore.awsrequest import AWSRequest
from botocore.credentials import Credentials
import import_postgres as imp
from unittest.mock import Mock,patch
spec=importlib.util.spec_from_file_location('gateway',imp.ROOT.parents[1]/'docker/localstack/gateway/signed_s3_gateway.py')
gateway=importlib.util.module_from_spec(spec);spec.loader.exec_module(gateway)
class SignedGatewayTest(unittest.TestCase):
 def test_readiness_requires_completed_successful_restore(self):
  import json
  for complete,state,expected in [(False,'RUNNING',False),(True,'ERROR',False),(True,'SUCCESSFUL',True)]:
   replies=[Mock(status=200,read=Mock(return_value=json.dumps({'services':{'s3':'running'}}).encode())),Mock(status=200,read=Mock(return_value=json.dumps({'completed':complete,'scripts':[{'state':state}]}).encode()))]
   with patch.object(gateway.http.client,'HTTPConnection') as connection:
    connection.return_value.getresponse.side_effect=replies
    self.assertEqual(gateway.upstream_ready('127.0.0.1',4566),expected)
 def signed(self):
  req=AWSRequest(method='PUT',url='http://127.0.0.1:14566/backend-images/test',data=b'authorized fixture',headers={'Host':'127.0.0.1:14566'})
  S3SigV4Auth(Credentials('test','test'),'s3','us-east-1').add_auth(req);return req
 def test_real_header_signature(self):
  req=self.signed();self.assertTrue(gateway.authorized('PUT','/backend-images/test',req.headers,req.data))
 def test_modified_payload(self):
  req=self.signed();self.assertFalse(gateway.authorized('PUT','/backend-images/test',req.headers,b'modified'))
 def test_modified_signature(self):
  req=self.signed();req.headers['Authorization']=req.headers['Authorization'][:-1]+'0'
  # Force a definitely different signature without relying on the last hex digit.
  del req.headers['Authorization'];req.headers['Authorization']='AWS4-HMAC-SHA256 Credential=test/20261029/us-east-1/s3/aws4_request, SignedHeaders=host, Signature='+'0'*64
  self.assertFalse(gateway.authorized('PUT','/backend-images/test',req.headers,req.data))
 def test_presign_real_300_second_expiry(self):
  client=boto3.client('s3',endpoint_url='http://127.0.0.1:14566',region_name='us-east-1',aws_access_key_id='test',aws_secret_access_key='test',config=Config(signature_version='s3v4'))
  url=client.generate_presigned_url('get_object',Params={'Bucket':'backend-images','Key':'test'},ExpiresIn=300);parts=urlsplit(url)
  from botocore.compat import HTTPHeaders
  headers=HTTPHeaders();headers['Host']=parts.netloc
  self.assertTrue(gateway.authorized('GET',parts.path+'?'+parts.query,headers,b''))
  self.assertFalse(gateway.authorized('GET',parts.path+'?'+parts.query,headers,b'',now=datetime.now(timezone.utc)+timedelta(seconds=301)))
 def test_unsigned_and_partial_signature(self):
  from botocore.compat import HTTPHeaders
  headers=HTTPHeaders();headers['Host']='127.0.0.1:14566'
  self.assertFalse(gateway.authorized('GET','/backend-images/test',headers,b''))
  self.assertFalse(gateway.authorized('GET','/backend-images/test?X-Amz-Signature=abc',headers,b''))
if __name__=='__main__':unittest.main(verbosity=2)
