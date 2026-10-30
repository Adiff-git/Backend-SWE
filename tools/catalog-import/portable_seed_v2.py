#!/usr/bin/env python3
"""Checksum-verified Catalog + private image seed. No crawler or auth data dependency."""
import argparse,base64,hashlib,json,os,re,uuid
from collections import defaultdict
from datetime import datetime,timezone
from pathlib import Path
from urllib.parse import urlsplit
import boto3
from botocore.config import Config
from botocore.exceptions import ClientError
import portable_seed as v1
import import_postgres as importer
from import_product_images import validate_bytes,MAX_BYTES
VERSION='catalog-dev-v2'
COUNTS=dict(products=1000,variants=1000,categories=31,images=1096,published=5,draft=995)
COLUMNS=('id','product_id','object_key','alt_text','content_type','byte_size','width','height','display_order','is_primary','created_at')

def digest(data):return hashlib.sha256(data).hexdigest()
def canonical(value):return (json.dumps(value,sort_keys=True,ensure_ascii=False,default=str,indent=2)+'\n').encode()
def write(path,data):
 path.parent.mkdir(parents=True,exist_ok=True)
 fd=os.open(path,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
 with os.fdopen(fd,'wb') as f:f.write(data)
def inventory(db):
 rows=[]
 for row in db.execute('SELECT '+','.join(COLUMNS)+' FROM product_images ORDER BY product_id,display_order'):
  r=dict(zip(COLUMNS,row))
  for k in ('id','product_id'):r[k]=str(r[k])
  r['created_at']=r['created_at'].isoformat();rows.append(r)
 return rows
def media_permission(record,rows,target_kind):
 if not record or record.get('decision')!='APPROVED' or record.get('scope')!='portable-seed-v2' or not {'package','redistribute'}<=set(record.get('usage',[])) or not record.get('approved_by') or not record.get('evidence_reference'):raise ValueError('MEDIA_REDISTRIBUTION_PERMISSION_REQUIRED')
 deadline=datetime.fromisoformat(record['expires_at'])
 if deadline.tzinfo is None or deadline<=datetime.now(timezone.utc):raise ValueError('MEDIA_PERMISSION_EXPIRED')
 if record.get('image_inventory_sha256')!=digest(canonical(rows)):raise ValueError('MEDIA_PERMISSION_INVENTORY_MISMATCH')
 if record.get('content_class') not in ('authorized-fixtures','retailer-media'):raise ValueError('MEDIA_CONTENT_CLASS')
 if record['content_class']=='authorized-fixtures' and target_kind!='disposable':raise ValueError('FIXTURES_REQUIRE_DISPOSABLE_DATABASE')

def seal(directory,content_class):
 files={str(p.relative_to(directory)):digest(p.read_bytes()) for p in sorted(directory.rglob('*')) if p.is_file()}
 manifest=dict(version=VERSION,counts=COUNTS,files=files,content_class=content_class)
 write(directory/'manifest.json',canonical(manifest))
 write(directory/'SHA256SUMS',''.join(h+'  '+name+'\n' for name,h in sorted({**files,'manifest.json':digest((directory/'manifest.json').read_bytes())}.items())).encode())

def load(directory):
 directory=Path(directory)
 if directory.is_symlink():raise ValueError('SYMLINK_SEED')
 manifest=json.loads((directory/'manifest.json').read_text())
 if set(manifest)!={'version','counts','files','content_class'} or manifest['version']!=VERSION or manifest['counts']!=COUNTS or manifest['content_class'] not in ('authorized-fixtures','retailer-media'):raise ValueError('INVALID_V2_MANIFEST')
 files=manifest['files'];required={'catalog.json','catalog-manifest.json','images.json'}
 if not required<=set(files) or any(n not in required and not re.fullmatch(r'objects/[a-f0-9]{64}',n) for n in files):raise ValueError('INVALID_SEED_PATH')
 actual=set()
 for path in directory.rglob('*'):
  if path.is_symlink():raise ValueError('SYMLINK_SEED')
  if path.is_file():actual.add(str(path.relative_to(directory)))
 if actual!=set(files)|{'manifest.json','SHA256SUMS'}:raise ValueError('UNEXPECTED_SEED_FILES')
 sums={}
 for line in (directory/'SHA256SUMS').read_text().splitlines():
  h,name=line.split('  ',1)
  if name in sums:raise ValueError('DUPLICATE_CHECKSUM')
  sums[name]=h
 if sums!={**files,'manifest.json':digest((directory/'manifest.json').read_bytes())}:raise ValueError('CHECKSUM_MANIFEST_MISMATCH')
 for name,h in files.items():
  if digest((directory/name).read_bytes())!=h:raise ValueError('FILE_CHECKSUM_MISMATCH:'+name)
 categories,plans=v1.load_seed(directory,'catalog-manifest.json')
 rows=json.loads((directory/'images.json').read_text())
 if len(rows)!=1096:raise ValueError('IMAGE_COUNT')
 products={str(p['product_id']) for p in plans};ids=set();keys=set();groups=defaultdict(list);content={}
 for r in rows:
  if set(r)!=set(COLUMNS):raise ValueError('INVALID_IMAGE_FIELDS')
  uuid.UUID(r['id']);uuid.UUID(r['product_id'])
  if r['product_id'] not in products or r['id'] in ids or r['object_key'] in keys:raise ValueError('IMAGE_IDENTITY_CONFLICT')
  parts=r['object_key'].split('/')
  if len(parts)!=5 or parts[:2]!=['images','products'] or parts[2]!=r['product_id'] or not re.fullmatch('[a-f0-9]{64}',parts[4]):raise ValueError('INVALID_OBJECT_MAPPING')
  uuid.UUID(parts[3]);sha=parts[4]
  if 'objects/'+sha not in files:raise ValueError('MISSING_OBJECT_PAYLOAD')
  if sha not in content:
   data=(directory/'objects'/sha).read_bytes();content[sha]=validate_bytes(data)
   if digest(data)!=sha:raise ValueError('OBJECT_HASH')
  m=content[sha]
  if (r['byte_size'],r['content_type'],r['width'],r['height'])!=(m['byte_size'],m['content_type'],m['width'],m['height']):raise ValueError('IMAGE_METADATA_MISMATCH')
  if not isinstance(r['alt_text'],str) or not r['alt_text'].strip() or type(r['is_primary']) is not bool or type(r['display_order']) is not int:raise ValueError('INVALID_IMAGE_METADATA')
  if datetime.fromisoformat(r['created_at']).tzinfo is None:raise ValueError('IMAGE_TIMESTAMP')
  ids.add(r['id']);keys.add(r['object_key']);groups[r['product_id']].append(r)
 if set(groups)!=products:raise ValueError('PRODUCT_WITHOUT_IMAGES')
 for gallery in groups.values():
  if len(gallery)>10 or sum(r['is_primary'] for r in gallery)!=1 or sorted(r['display_order'] for r in gallery)!=list(range(len(gallery))):raise ValueError('INVALID_GALLERY')
  if len({r['object_key'].rsplit('/',1)[-1] for r in gallery})!=len(gallery):raise ValueError('DUPLICATE_GALLERY_BYTES')
 if {n for n in files if n.startswith('objects/')}!={'objects/'+sha for sha in content}:raise ValueError('UNREFERENCED_SEED_PAYLOAD')
 return categories,plans,rows

def export(db,s3,bucket,catalog_seed,directory,permission,target_kind):
 # Authorization is bound to the exact image inventory before any media is read.
 rows=inventory(db);media_permission(permission,rows,target_kind)
 categories,plans=v1.load_seed(catalog_seed)
 result=v1.apply_seed(db,categories,plans,False)
 if result['unchanged_products']!=1000:raise ValueError('SOURCE_CATALOG_INCOMPLETE')
 if len(rows)!=1096:raise ValueError('SOURCE_IMAGE_COUNT')
 directory=Path(directory);directory.mkdir(mode=0o700,parents=True,exist_ok=False)
 write(directory/'catalog.json',(catalog_seed/'catalog.json').read_bytes())
 write(directory/'catalog-manifest.json',(catalog_seed/'manifest.json').read_bytes())
 write(directory/'images.json',canonical(rows))
 for row in rows:
  obj=s3.get_object(Bucket=bucket,Key=row['object_key']);data=obj['Body'].read(MAX_BYTES+1);m=validate_bytes(data);sha=digest(data)
  if row['object_key'].rsplit('/',1)[-1]!=sha or obj['ContentType']!=row['content_type'] or (row['byte_size'],row['content_type'],row['width'],row['height'])!=(len(data),m['content_type'],m['width'],m['height']):raise ValueError('SOURCE_OBJECT_INVALID')
  path=directory/'objects'/sha
  if not path.exists():write(path,data)
 seal(directory,permission['content_class']);load(directory)
 return dict(COUNTS,unique_payloads=len(list((directory/'objects').iterdir())))

def read_existing(s3,bucket,key):
 try:
  obj=s3.get_object(Bucket=bucket,Key=key);return obj,obj['Body'].read(MAX_BYTES+1)
 except ClientError as e:
  if e.response['Error']['Code'] in ('NoSuchKey','404','NotFound'):return None,None
  raise

def restore(db,s3,bucket,directory,checkpoint_directory):
 categories,plans,rows=load(directory)
 # Lock and preflight the whole DB before object writes; conflicts never overwrite data.
 db.execute('SELECT pg_advisory_xact_lock(683127402)')
 v1.apply_seed(db,categories,plans,False)
 expected={r['id']:r for r in rows}
 for r in inventory(db):
  if r['id'] not in expected or r!=expected[r['id']]:raise ValueError('EXISTING_IMAGE_METADATA_CONFLICT')
 # Preflight existing objects before restoring any missing object.
 existing={}
 for r in rows:
  obj,data=read_existing(s3,bucket,r['object_key'])
  if obj is not None and (digest(data)!=r['object_key'].rsplit('/',1)[-1] or obj['ContentType']!=r['content_type']):raise ValueError('EXISTING_OBJECT_CONFLICT')
  existing[r['object_key']]=obj is not None
 result=v1.apply_seed(db,categories,plans,True);created=0;uploaded=0
 checkpoint_directory=Path(checkpoint_directory);checkpoint_directory.mkdir(parents=True,exist_ok=True,mode=0o700)
 for r in rows:
  sha=r['object_key'].rsplit('/',1)[-1];data=(Path(directory)/'objects'/sha).read_bytes()
  if not existing[r['object_key']]:
   s3.put_object(Bucket=bucket,Key=r['object_key'],Body=data,ContentType=r['content_type'],ACL='private',CacheControl='no-store');uploaded+=1
  stored,actual=read_existing(s3,bucket,r['object_key'])
  if actual!=data or stored['ContentType']!=r['content_type']:raise ValueError('RESTORED_OBJECT_MISMATCH')
  # Same durable checkpoint format used by the local ready-hook restoration.
  cp=checkpoint_directory/(digest(r['object_key'].encode())+'.json')
  raw=canonical(dict(key=r['object_key'],content_type=r['content_type'],sha256=sha,bytes_base64=base64.b64encode(data).decode()))
  if cp.exists() and json.loads(cp.read_text())!=json.loads(raw):raise ValueError('CHECKPOINT_CONFLICT')
  if not cp.exists():
   temporary=cp.with_suffix('.'+str(uuid.uuid4())+'.tmp');write(temporary,raw)
   with temporary.open('rb') as f:os.fsync(f.fileno())
   temporary.replace(cp)
  found=db.execute('SELECT id FROM product_images WHERE id=%s',(r['id'],)).fetchone()
  if not found:
   db.execute('INSERT INTO product_images ('+','.join(COLUMNS)+') VALUES ('+','.join(['%s']*len(COLUMNS))+')',tuple(r[c] for c in COLUMNS));created+=1
 directory_fd=os.open(checkpoint_directory,os.O_RDONLY)
 try:os.fsync(directory_fd)
 finally:os.close(directory_fd)
 db.execute('SET CONSTRAINTS ALL IMMEDIATE')
 return dict(products_created=result['products_created'],variants_created=result['variants_created'],images_created=created,objects_uploaded=uploaded,images_verified=len(rows))

def client(endpoint):
 p=urlsplit(endpoint)
 if p.scheme!='http' or p.hostname not in ('127.0.0.1','localhost') or not p.port or p.username or p.password or p.path or p.query:raise ValueError('EXPLICIT_LOCAL_S3_ENDPOINT_REQUIRED')
 return boto3.client('s3',endpoint_url=endpoint,region_name='us-east-1',aws_access_key_id='test',aws_secret_access_key='test',config=Config(signature_version='s3v4',connect_timeout=5,read_timeout=30))
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('mode',choices=['export','restore','verify','inventory']);p.add_argument('--seed',type=Path)
 p.add_argument('--catalog-seed',type=Path);p.add_argument('--permission',type=Path);p.add_argument('--checkpoint-directory',type=Path)
 p.add_argument('--execute',action='store_true');p.add_argument('--s3-endpoint');p.add_argument('--bucket',default='backend-images')
 p.add_argument('--target-kind',choices=['local-development','disposable']);p.add_argument('--dsn-env',default='CATALOG_IMPORT_DSN')
 for name in ['expect-database','expect-host','confirm-target']:p.add_argument('--'+name)
 p.add_argument('--expect-port',type=int);a=p.parse_args()
 if a.mode!='inventory' and not a.seed:p.error('--seed is required')
 if a.mode=='verify':load(a.seed);print(json.dumps(dict(version=VERSION,**COUNTS)));return
 if a.mode=='restore' and (not a.execute or not a.checkpoint_directory):p.error('restore requires --execute and --checkpoint-directory; verify is offline/read-only')
 if a.mode=='export' and (not a.permission or not a.catalog_seed):p.error('export requires separate redistribution --permission and --catalog-seed')
 db=importer.target_connection(a,a.mode!='restore')
 try:
  if a.mode=='inventory':
   rows=inventory(db);print(json.dumps(dict(images=len(rows),image_inventory_sha256=digest(canonical(rows)))));db.rollback();return
  s3=client(a.s3_endpoint)
  if a.mode=='export':result=export(db,s3,a.bucket,a.catalog_seed,a.seed,json.loads(a.permission.read_text()),a.target_kind);db.rollback()
  else:result=restore(db,s3,a.bucket,a.seed,a.checkpoint_directory);db.commit()
  print(json.dumps(result))
 except BaseException:db.rollback();raise
 finally:db.close()
if __name__=='__main__':main()
