#!/usr/bin/env python3
"""Permission-gated API-only Product image ingestion; no direct SQL writer."""
import argparse,collections,hashlib,http.client,io,ipaddress,json,os,socket,ssl,time,uuid,warnings
from datetime import datetime,timezone
from pathlib import Path
from urllib.parse import urlsplit,urljoin
import requests
from PIL import Image
import portable_seed,import_postgres as importer
from prepare_product_images import ALLOWED_HOSTS
MAX_BYTES=10*1024*1024
Image.MAX_IMAGE_PIXELS=40000000

def validate_bytes(data):
 if not data or len(data)>MAX_BYTES:raise ValueError('IMAGE_SIZE')
 with warnings.catch_warnings():
  warnings.simplefilter('error')
  with Image.open(io.BytesIO(data)) as image:
   if image.format not in ('JPEG','PNG','WEBP') or image.width*image.height>40000000 or getattr(image,'n_frames',1)!=1:raise ValueError('IMAGE_FORMAT_OR_DIMENSIONS')
   width,height=image.size;mime=Image.MIME[image.format];image.verify()
  with Image.open(io.BytesIO(data)) as image:image.load()
 return dict(content_type=mime,width=width,height=height,byte_size=len(data),sha256=hashlib.sha256(data).hexdigest())

def authorize(record,permission):
 required={'download','store-private-local'}
 if permission.get('decision')!='APPROVED' or permission.get('scope')!='local-development' or not permission.get('evidence_reference') or not permission.get('approved_by') or not required <= set(permission.get('usage',[])):raise ValueError('MEDIA_PERMISSION_MISSING')
 deadline=datetime.fromisoformat(permission['expires_at'])
 if deadline.tzinfo is None or deadline<=datetime.now(timezone.utc):raise ValueError('MEDIA_PERMISSION_EXPIRED')
 if record['source_identity'] not in permission.get('source_identities',[]):raise ValueError('SOURCE_NOT_AUTHORIZED')

def safe_download(url,hosts):
 for redirect in range(4):
  parsed=urlsplit(url);host=parsed.hostname
  if parsed.scheme!='https' or host not in hosts or parsed.username or parsed.password or parsed.port not in (None,443) or parsed.fragment:raise ValueError('UNSAFE_SOURCE_URL')
  addresses={r[4][0] for r in socket.getaddrinfo(host,443,type=socket.SOCK_STREAM)}
  if not addresses or any(not ipaddress.ip_address(a).is_global for a in addresses):raise ValueError('NON_PUBLIC_SOURCE_ADDRESS')
  # Pin the checked address. TLS still authenticates the original hostname; no DNS rebinding window.
  class PinnedHTTPS(http.client.HTTPSConnection):
   def connect(self):
    self.sock=ssl.create_default_context().wrap_socket(socket.create_connection((sorted(addresses)[0],443),timeout=15),server_hostname=host)
  connection=PinnedHTTPS(host,timeout=15)
  try:
   connection.request('GET',parsed.path+('?' +parsed.query if parsed.query else ''),headers={'User-Agent':'Backend-SWE-authorized-local-image-import/1'})
   response=connection.getresponse()
   if response.status in (301,302,303,307,308):
    if redirect==3:raise ValueError('REDIRECT_LIMIT')
    url=urljoin(url,response.getheader('Location',''));continue
   if response.status!=200:raise ValueError('SOURCE_HTTP_'+str(response.status))
   if int(response.getheader('Content-Length','0'))>MAX_BYTES:raise ValueError('IMAGE_SIZE')
   chunks=[];length=0;started=time.monotonic()
   while True:
    chunk=response.read(65536)
    if not chunk:break
    length+=len(chunk)
    if length>MAX_BYTES:raise ValueError('IMAGE_SIZE')
    if time.monotonic()-started>60:raise ValueError('DOWNLOAD_DEADLINE')
    chunks.append(chunk)
   data=b''.join(chunks)
   return data
  finally:connection.close()
 raise ValueError('REDIRECT_LIMIT')

def save(path,data):
 path.parent.mkdir(parents=True,exist_ok=True);temporary=path.with_suffix('.tmp')
 fd=os.open(temporary,os.O_WRONLY|os.O_CREAT|os.O_TRUNC,0o600)
 with os.fdopen(fd,'w') as f:json.dump(data,f,ensure_ascii=False,indent=2)
 temporary.replace(path)

def put_object(url,data,content_type):
 with requests.Session() as client:
  client.trust_env=False
  return client.put(url,data=data,headers={'Content-Type':content_type},timeout=(5,90),allow_redirects=False)

def api_call(session,method,url,**kwargs):
 response=session.request(method,url,timeout=(5,90),**kwargs)
 if response.status_code>=400:raise ValueError('API_HTTP_'+str(response.status_code))
 return response

def run(a):
 base=urlsplit(a.api_base)
 if base.scheme!='http' or base.hostname not in ('127.0.0.1','localhost') or not base.port or base.username or base.password or base.path:raise ValueError('API_MUST_BE_EXPLICIT_LOOPBACK')
 inventory=json.loads(a.manifest.read_text())['products'];_,plans=portable_seed.load_seed(a.seed)
 identities={str(p['product_id']):p['identity'] for p in plans}
 names={str(p['product_id']):p['name'] for p in plans}
 if len({r['product_id'] for r in inventory})!=len(inventory):raise ValueError('DUPLICATE_PRODUCT_MAPPING')
 for record in inventory:
  if identities.get(record['product_id'])!=record['source_identity']:raise ValueError('SOURCE_UUID_MAPPING_MISMATCH')
  record['name']=names[record['product_id']]
 if getattr(a,'published_only',False) and a.mode!='verify':
  public_ids={str(p['product_id']) for p in plans if p['status']=='PUBLISHED'}
  inventory=[r for r in inventory if r['product_id'] in public_ids]
 if a.mode=='dry-run':return dict(products=len(inventory),source_entries=sum(len(r['images']) for r in inventory),planned_gallery_entries=sum(min(10,len({i['source_url'] for i in r['images'] if not i.get('issue')})) for r in inventory),capped_products=sum(len(r['images'])>10 for r in inventory),gallery_limit=10,external_downloads=0,permission_required=True,image_manifest_sha256=hashlib.sha256(a.manifest.read_bytes()).hexdigest())
 full_manifest_hash=hashlib.sha256(a.manifest.read_bytes()).hexdigest()
 total_products=len(inventory)
 if a.mode!='verify':inventory=inventory[getattr(a,'offset',0):getattr(a,'offset',0)+getattr(a,'batch_size',25)]
 permission=json.loads(a.permission.read_text()) if a.permission else {}
 if not a.fixture_root and a.mode!='verify':
  if not permission:raise ValueError('MEDIA_PERMISSION_MISSING')
  if permission.get('image_manifest_sha256')!=full_manifest_hash:raise ValueError('PERMISSION_MANIFEST_MISMATCH')
  if permission.get('target')!=a.confirm_target:raise ValueError('PERMISSION_TARGET_MISMATCH')
  for record in inventory:authorize(record,permission)
 elif a.fixture_root and a.target_kind!='disposable':raise ValueError('FIXTURES_REQUIRE_DISPOSABLE_TARGET')
 db=importer.target_connection(a,True)
 try:
  portable_seed.apply_seed(db,*portable_seed.load_seed(a.seed),False)
  existing_images={}
  for image_id,product_id,object_key in db.execute('SELECT id,product_id,object_key FROM product_images'):
   suffix=object_key.rsplit('/',1)[-1]
   if len(suffix)==64 and all(c in '0123456789abcdef' for c in suffix):existing_images[(str(product_id),suffix)]=str(image_id)
  db.rollback()
 finally:db.close()
 state=json.loads(a.checkpoint.read_text()) if a.checkpoint.exists() else {'target':a.confirm_target,'images':{},'errors':{}}
 if state['target']!=a.confirm_target:raise ValueError('CHECKPOINT_TARGET_MISMATCH')
 if state.get('manifest_sha256',full_manifest_hash)!=full_manifest_hash:raise ValueError('CHECKPOINT_MANIFEST_MISMATCH')
 state['manifest_sha256']=full_manifest_hash
 if a.permission and a.mode!='verify':
  permission_hash=hashlib.sha256(a.permission.read_bytes()).hexdigest()
  if state.get('permission_sha256',permission_hash)!=permission_hash:raise ValueError('CHECKPOINT_PERMISSION_MISMATCH')
  state['permission_sha256']=permission_hash
 session=requests.Session();session.trust_env=False
 token=json.loads(a.admin_credentials.read_text())['token'] if a.admin_credentials else os.environ.get(a.token_env)
 if not token:raise ValueError('ADMIN_TOKEN_MISSING')
 session.headers['Authorization']='Bearer '+token
 errors={};completed=0
 for record in inventory:
  seen=set();gallery=[]
  for image in record['images']:
   if image.get('issue') or image['source_url'] in seen:continue
   seen.add(image['source_url']);gallery.append(image)
  for image in gallery[:10]:
   url=image['source_url'];key=str(uuid.uuid5(uuid.UUID(record['product_id']),url));entry=state['images'].get(key,{})
   try:
    if a.mode=='verify':
     if not entry.get('completed'):raise ValueError('NOT_COMPLETED')
     completed+=1;continue
    if entry.get('completed'):
     completed+=1;continue
    for attempt in range(3):
     try:
      if a.fixture_root:
       parsed=urlsplit(url)
       if parsed.scheme!='fixture':raise ValueError('FIXTURE_ONLY')
       path=(a.fixture_root/(parsed.netloc+parsed.path)).resolve()
       if not path.is_relative_to(a.fixture_root.resolve()):raise ValueError('FIXTURE_PATH_ESCAPE')
       data=path.read_bytes()
      else:
       time.sleep(a.download_interval)
       allowed=ALLOWED_HOSTS[record['source_identity'].split('|')[0]] & set(permission.get('image_hosts',[]))
       data=safe_download(url,allowed)
      metadata=validate_bytes(data);break
     except (OSError,ValueError,http.client.HTTPException):
      if attempt==2:raise
      time.sleep(2**attempt)
    known=existing_images.get((record['product_id'],metadata['sha256']))
    if not entry and known:
     entry=dict(image_id=known,completed=True,sha256=metadata['sha256'],product_id=record['product_id'],source_url=url);state['images'][key]=entry;save(a.checkpoint,state)
    duplicate=next((e for e in state['images'].values() if e.get('completed') and e.get('sha256')==metadata['sha256'] and e.get('product_id')==record['product_id']),None)
    if not entry and duplicate:
     entry=dict(duplicate);state['images'][key]=entry;save(a.checkpoint,state)
    if entry.get('sha256') and entry['sha256']!=metadata['sha256']:raise ValueError('SOURCE_BYTES_CHANGED')
    prefix=a.api_base+'/api/products/'+record['product_id']+'/images/uploads'
    if not entry:
     prepared=api_call(session,'POST',prefix,json=dict(contentType=metadata['content_type'],byteSize=len(data),altText=record.get('name','Product image'))).json()
     put=urlsplit(prepared['uploadUrl'])
     if put.scheme!='http' or put.hostname not in ('127.0.0.1','localhost'):raise ValueError('NONLOCAL_STORAGE')
     entry=dict(upload_id=prepared['uploadId'],put_url=prepared['uploadUrl'],expires_at=prepared['expiresAt'],sha256=metadata['sha256'],product_id=record['product_id'],source_url=url)
     state['images'][key]=entry;save(a.checkpoint,state)
    if not entry.get('completed'):
     # Replay completion first: a previous response could have been lost after commit.
     response=session.post(prefix+'/'+entry['upload_id']+'/complete',json={},timeout=(5,90))
     if response.status_code not in (200,201):
      if response.status_code!=409:raise ValueError('COMPLETE_HTTP_'+str(response.status_code))
      if datetime.fromisoformat(entry['expires_at'].replace('Z','+00:00'))<=datetime.now(timezone.utc):
       previous=entry['upload_id']
       prepared=api_call(session,'POST',prefix,json=dict(contentType=metadata['content_type'],byteSize=len(data),altText=record.get('name','Product image'))).json()
       entry.update(upload_id=prepared['uploadId'],put_url=prepared['uploadUrl'],expires_at=prepared['expiresAt'],previous_upload_id=previous);save(a.checkpoint,state)
      # Never send the ADMIN token to an object-storage URL.
      put_address=urlsplit(entry['put_url']);expected=urlsplit(os.environ.get('LOCAL_STORAGE_ENDPOINT',''))
      if put_address.username or put_address.password or (put_address.scheme,put_address.hostname,put_address.port)!=(expected.scheme,expected.hostname,expected.port):raise ValueError('STORAGE_ENDPOINT_MISMATCH')
      put=put_object(entry['put_url'],data,metadata['content_type'])
      if put.status_code not in (200,204):raise ValueError('PUT_HTTP_'+str(put.status_code))
      response=api_call(session,'POST',prefix+'/'+entry['upload_id']+'/complete',json={})
     entry['image_id']=response.json()['imageId'];entry['completed']=True;entry.pop('put_url',None);save(a.checkpoint,state)
    completed+=1
   except Exception as failure:
    errors[key]=dict(product_id=record['product_id'],source_identity=record['source_identity'],source_url=url,reason=str(failure) if isinstance(failure,ValueError) else type(failure).__name__)
    break
  if a.mode!='verify':
   state['errors']=errors;save(a.checkpoint,state)
 # Verify metadata AND actual stored bytes in a read-only transaction using the S3 SDK.
 import boto3
 from botocore.config import Config
 endpoint=os.environ.get('LOCAL_STORAGE_ENDPOINT')
 if not endpoint or urlsplit(endpoint).hostname not in ('127.0.0.1','localhost'):raise ValueError('LOCAL_STORAGE_ENDPOINT_REQUIRED')
 s3=boto3.client('s3',endpoint_url=endpoint,region_name='us-east-1',aws_access_key_id='test',aws_secret_access_key='test',config=Config(connect_timeout=5,read_timeout=15,retries={'max_attempts':2}))
 db=importer.target_connection(a,True)
 try:
  verified=0;unique_verified=set()
  for key,entry in state['images'].items():
   if not entry.get('completed'):continue
   try:
    row=db.execute('SELECT product_id,object_key FROM product_images WHERE id=%s',(entry['image_id'],)).fetchone()
    if not row or str(row[0])!=entry['product_id']:raise ValueError('MISSING_IMAGE_METADATA')
    stored=s3.get_object(Bucket=os.environ.get('LOCAL_STORAGE_BUCKET','backend-images'),Key=row[1])['Body'].read(MAX_BYTES+1)
    if validate_bytes(stored)['sha256']!=entry['sha256']:raise ValueError('STORED_HASH_MISMATCH')
    verified+=1;unique_verified.add(entry["image_id"])
   except Exception as failure:
    errors[key]=dict(product_id=entry["product_id"],reason=str(failure) if isinstance(failure,ValueError) else type(failure).__name__)
  for record in inventory:
   primary,count=db.execute('SELECT count(*) FILTER (WHERE is_primary),count(*) FROM product_images WHERE product_id=%s',(record['product_id'],)).fetchone()
   if count and (primary!=1 or count>10):raise ValueError('INVALID_GALLERY')
  db.rollback()
 finally:db.close()
 return dict(total_products=total_products,batch_products=len(inventory),completed=completed,verified=verified,unique_verified_images=len(unique_verified),errors=errors)

def main():
 p=argparse.ArgumentParser(description=__doc__)
 for name in ['manifest','seed','checkpoint']:p.add_argument('--'+name,type=Path,required=True)
 p.add_argument('--mode',choices=['dry-run','execute','resume','verify'],default='dry-run')
 p.add_argument('--admin-credentials',type=Path);p.add_argument('--permission',type=Path);p.add_argument('--fixture-root',type=Path)
 p.add_argument('--api-base',required=True);p.add_argument('--token-env',default='CATALOG_ADMIN_TOKEN')
 p.add_argument('--published-only',action='store_true')
 p.add_argument('--batch-size',type=int,default=25);p.add_argument('--offset',type=int,default=0)
 p.add_argument('--download-interval',type=float,default=1.0)
 p.add_argument('--target-kind',choices=['local-development','disposable']);p.add_argument('--dsn-env',default='CATALOG_IMPORT_DSN')
 p.add_argument('--expect-database');p.add_argument('--expect-host');p.add_argument('--expect-port',type=int);p.add_argument('--confirm-target')
 a=p.parse_args()
 if a.batch_size<1 or a.batch_size>1000 or a.offset<0:p.error('Invalid batch bounds')
 if a.download_interval<.25:p.error('Download interval must be at least 0.25 seconds')
 try:
  result=run(a);print(json.dumps(result,ensure_ascii=False));return 1 if result.get('errors') else 0
 except Exception as e:
  print(json.dumps({'error':str(e) if isinstance(e,ValueError) else type(e).__name__}));return 2
if __name__=='__main__':raise SystemExit(main())
