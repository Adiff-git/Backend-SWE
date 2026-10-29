"""Local-only SigV4 gateway for Community LocalStack, which is not an IAM boundary.
Checks real request signatures and expiry before forwarding; never logs signed URLs.
"""
import hashlib,hmac,http.client,os,re,json,sys
from datetime import datetime,timezone
from http.server import BaseHTTPRequestHandler,ThreadingHTTPServer
from urllib.parse import urlsplit,parse_qs
from botocore.auth import S3SigV4Auth
from botocore.awsrequest import AWSRequest
from botocore.credentials import Credentials

MAX=10485760

def upstream_ready(host=None,port=None):
 host=host or os.environ.get('S3_UPSTREAM_HOST','localstack')
 port=port or int(os.environ.get('S3_UPSTREAM_PORT','4566'))
 states=[]
 for path in ('/_localstack/health','/_localstack/init/ready'):
  connection=http.client.HTTPConnection(host,port,timeout=2)
  try:
   connection.request('GET',path);response=connection.getresponse()
   if response.status!=200:return False
   states.append(json.loads(response.read(65536)))
  finally:connection.close()
 health,initialization=states
 return health.get('services',{}).get('s3') in ('running','available') and initialization.get('completed') is True and all(s.get('state')=='SUCCESSFUL' for s in initialization.get('scripts',[]))

def authorized(method,path,headers,body,now=None):
 try:
  now=now or datetime.now(timezone.utc);parts=urlsplit(path);query=parse_qs(parts.query,keep_blank_values=True)
  if any(len(v)!=1 for v in query.values()):return False
  if 'X-Amz-Signature' in query:
   if headers.get('Authorization') or query.get('X-Amz-Algorithm')!=['AWS4-HMAC-SHA256']:return False
   credential=query['X-Amz-Credential'][0];signed=query['X-Amz-SignedHeaders'][0];signature=query['X-Amz-Signature'][0];stamp=query['X-Amz-Date'][0]
   expiry=int(query['X-Amz-Expires'][0]);age=(now-datetime.strptime(stamp,'%Y%m%dT%H%M%SZ').replace(tzinfo=timezone.utc)).total_seconds()
   if not 0<expiry<=900 or not -60<=age<expiry:return False
   raw_query='&'.join(x for x in parts.query.split('&') if x.split('=',1)[0]!='X-Amz-Signature')
   payload='UNSIGNED-PAYLOAD'
  else:
   match=re.fullmatch(r'AWS4-HMAC-SHA256 Credential=([^, ]+),\s*SignedHeaders=([^, ]+),\s*Signature=([a-f0-9]{64})',headers.get('Authorization',''))
   if not match:return False
   credential,signed,signature=match.groups();stamp=headers['X-Amz-Date'];raw_query=parts.query
   if abs((now-datetime.strptime(stamp,'%Y%m%dT%H%M%SZ').replace(tzinfo=timezone.utc)).total_seconds())>900:return False
   payload=headers.get('X-Amz-Content-SHA256',hashlib.sha256(body).hexdigest())
   if payload!='UNSIGNED-PAYLOAD' and payload!=hashlib.sha256(body).hexdigest():return False
  key,date,region,service,terminator=credential.split('/')
  if (key,date,region,service,terminator)!=(os.environ.get('AWS_ACCESS_KEY_ID','test'),stamp[:8],'us-east-1','s3','aws4_request'):return False
  names=signed.split(';')
  if names!=sorted(set(names)) or 'host' not in names or any(name.lower()!=name or headers.get(name) is None for name in names):return False
  selected={name:headers[name] for name in names}
  request=AWSRequest(method=method,url='http://'+headers['Host']+(parts.path or '/')+('?' +raw_query if raw_query else ''),headers=selected,data=body)
  request.context['timestamp']=stamp
  signer=S3SigV4Auth(Credentials(key,os.environ.get('AWS_SECRET_ACCESS_KEY','test')),'s3',region)
  canonical='\n'.join([method.upper(),parts.path or '/',signer.canonical_query_string(request),signer.canonical_headers(request.headers)+'\n',signed,payload])
  expected=signer.signature(signer.string_to_sign(request,canonical),request)
  return hmac.compare_digest(expected,signature)
 except (KeyError,ValueError,TypeError,AttributeError):return False

class Handler(BaseHTTPRequestHandler):
 protocol_version='HTTP/1.1'
 def log_message(self,*args):pass
 def handle_request(self):
  try:
   if self.path=='/_gateway/health' and self.command=='GET':
    ready=upstream_ready()
    data=json.dumps({'status':'ready' if ready else 'unavailable'}).encode()
    self.send_response(200 if ready else 503);self.send_header('Content-Type','application/json');self.send_header('Content-Length',str(len(data)));self.end_headers();self.wfile.write(data);return
   if self.headers.get('Transfer-Encoding'):self.send_error(400);return
   length=int(self.headers.get('Content-Length','0'))
   if not 0<=length<=MAX:self.send_error(413);return
   self.connection.settimeout(30);body=self.rfile.read(length)
   if len(body)!=length:self.send_error(400);return
   if self.command!='OPTIONS' and not authorized(self.command,self.path,self.headers,body):self.send_error(403);return
   connection=http.client.HTTPConnection(os.environ.get('S3_UPSTREAM_HOST','localstack'),int(os.environ.get('S3_UPSTREAM_PORT','4566')),timeout=90)
   try:
    headers={k:v for k,v in self.headers.items() if k.lower() not in ('connection','transfer-encoding')}
    headers['Connection']='close'
    connection.request(self.command,self.path,body=body,headers=headers)
    response=connection.getresponse();data=response.read(MAX+1)
    if len(data)>MAX:self.send_error(502);return
    self.send_response(response.status)
    for key,value in response.getheaders():
     if key.lower() not in ('server','date','connection','transfer-encoding','content-length'):self.send_header(key,value)
    self.send_header('Content-Length',response.getheader('Content-Length','0') if self.command=='HEAD' else str(len(data)))
    self.send_header('Connection','close');self.end_headers()
    if self.command!='HEAD':self.wfile.write(data)
   finally:connection.close()
  except Exception:self.send_error(502)
 do_GET=do_HEAD=do_PUT=do_POST=do_DELETE=do_OPTIONS=handle_request
if __name__=='__main__':
 port=int(os.environ.get('GATEWAY_PORT','4566'))
 if sys.argv[1:]==['--upstream-healthcheck']:
  try:sys.exit(0 if upstream_ready('127.0.0.1',4566) else 1)
  except Exception:sys.exit(1)
 if sys.argv[1:]==['--healthcheck']:
  connection=http.client.HTTPConnection('127.0.0.1',port,timeout=3)
  try:
   connection.request('GET','/_gateway/health');response=connection.getresponse();body=response.read()
   sys.exit(0 if response.status==200 and json.loads(body).get('status')=='ready' else 1)
  except Exception:sys.exit(1)
  finally:connection.close()
 ThreadingHTTPServer(('0.0.0.0',port),Handler).serve_forever()
