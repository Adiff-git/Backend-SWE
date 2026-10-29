#!/usr/bin/env python3
"""Explicit local-only publication, backup receipt and status-only rollback. No import."""
import argparse,hashlib,json,os,subprocess,uuid
from datetime import datetime,timezone
from pathlib import Path
from urllib.parse import urlsplit
from psycopg import sql
import import_postgres as imp,portable_seed
from portable_seed_v2 import client as s3client
from import_product_images import validate_bytes,MAX_BYTES
CATALOG=('products','product_variants','categories','product_images')
def canonical(x):return json.dumps(x,sort_keys=True,ensure_ascii=False,default=str,separators=(',',':')).encode()
def digest(x):return hashlib.sha256(x).hexdigest()
def save(path,data):
 path.parent.mkdir(parents=True,exist_ok=True);tmp=path.with_suffix('.tmp')
 fd=os.open(tmp,os.O_WRONLY|os.O_CREAT|os.O_TRUNC,0o600)
 with os.fdopen(fd,'wb') as f:f.write(canonical(data));f.flush();os.fsync(f.fileno())
 tmp.replace(path)
def snapshot(db):
 tables=[r[0] for r in db.execute("SELECT tablename FROM pg_tables WHERE schemaname='public' ORDER BY tablename")]
 rows={t:[r[0] for r in db.execute(sql.SQL('SELECT to_jsonb(t) FROM public.{} t').format(sql.Identifier(t)))] for t in tables}
 def hashrows(values):return digest(canonical(sorted(values,key=lambda x:canonical(x))))
 return {t:hashrows(v) for t,v in rows.items()},hashrows([{k:v for k,v in row.items() if k!='status'} for row in rows['products']])
def lock_tables(db):
 db.execute("SET LOCAL lock_timeout='10s'");db.execute("SET LOCAL statement_timeout='60s'")
 names=[sql.Identifier(r[0]) for r in db.execute("SELECT tablename FROM pg_tables WHERE schemaname='public' ORDER BY tablename")]
 db.execute(sql.SQL('LOCK TABLE {} IN SHARE ROW EXCLUSIVE MODE').format(sql.SQL(',').join(names)))
def validate(db,seed,manifest):
 cats,plans=portable_seed.load_seed(seed)
 inventory=json.loads(manifest.read_text())['products'];expected={str(p['product_id']):p['identity'] for p in plans}
 if len(inventory)!=1000 or {r['product_id']:r['source_identity'] for r in inventory}!=expected:raise ValueError('MANIFEST_UUID_IDENTITY_MISMATCH')
 statuses={str(pid):status for pid,status in db.execute('SELECT id,status FROM products')}
 baseline={str(p['product_id']):p['status'] for p in plans}
 if set(statuses)!=set(expected) or any(s not in ('DRAFT','PUBLISHED') for s in statuses.values()):raise ValueError('UNEXPECTED_PRODUCTS_OR_STATUSES')
 if statuses!=baseline and any(s!='PUBLISHED' for s in statuses.values()):raise ValueError('UNEXPECTED_PARTIAL_PUBLICATION')
 checked=[dict(p,status=statuses[str(p['product_id'])]) for p in plans]
 if portable_seed.apply_seed(db,cats,checked,False)['unchanged_products']!=1000:raise ValueError('CATALOG_DIFFERS_FROM_REVIEWED_SEED')
 counts={t:db.execute(sql.SQL('SELECT count(*) FROM {}').format(sql.Identifier(t))).fetchone()[0] for t in CATALOG}
 if counts!=dict(products=1000,product_variants=1000,categories=31,product_images=1096):raise ValueError('UNEXPECTED_COUNTS')
 bad=db.execute('SELECT product_id FROM product_images GROUP BY product_id HAVING count(*)>10 OR count(*) FILTER (WHERE is_primary)<>1 OR min(display_order)<>0 OR max(display_order)<>count(*)-1').fetchall()
 if bad or db.execute('SELECT count(DISTINCT product_id) FROM product_images').fetchone()[0]!=1000:raise ValueError('INVALID_PRIMARY_OR_GALLERY')
 return statuses,baseline,counts

def verify_images(db,endpoint):
 s3=s3client(endpoint);bucket=os.environ.get('LOCAL_STORAGE_BUCKET','backend-images');n=0
 for pid,key,mime,size,width,height in db.execute('SELECT product_id,object_key,content_type,byte_size,width,height FROM product_images ORDER BY id'):
  o=s3.get_object(Bucket=bucket,Key=key);b=o['Body'].read(MAX_BYTES+1);m=validate_bytes(b)
  if not key.startswith('images/products/'+str(pid)+'/') or key.rsplit('/',1)[-1]!=m['sha256'] or (mime,size,width,height)!=(m['content_type'],len(b),m['width'],m['height']) or o['ContentType']!=mime:raise ValueError('INVALID_STORED_IMAGE')
  n+=1
 if n!=1096:raise ValueError('IMAGE_COUNT_MISMATCH')
 return n

def transition(db,seed,manifest,action,receipt):
 """Caller owns commit. All catalog fields and unrelated tables are checked in-tx."""
 lock_tables(db);statuses,baseline,counts=validate(db,seed,manifest);before,immutable=snapshot(db)
 if receipt['baseline_statuses']!=baseline or receipt['immutable_products']!=immutable or any(receipt['catalog_hashes'][t]!=before[t] for t in CATALOG if t!='products'):raise ValueError('ROLLBACK_RECEIPT_OR_CATALOG_DRIFT')
 desired={pid:'PUBLISHED' for pid in statuses} if action=='publish' else baseline
 ids=[uuid.UUID(pid) for pid in statuses if statuses[pid]!=desired[pid]]
 if action=='publish':
  changed=[str(r[0]) for r in db.execute("UPDATE products SET status='PUBLISHED' WHERE id=ANY(%s::uuid[]) AND status='DRAFT' RETURNING id",(ids,))] if ids else []
 else:
  changed=[str(r[0]) for r in db.execute("UPDATE products SET status='DRAFT' WHERE id=ANY(%s::uuid[]) AND status='PUBLISHED' RETURNING id",(ids,))] if ids else []
 if set(changed)!={str(i) for i in ids}:raise ValueError('STATUS_UPDATE_COUNT_MISMATCH')
 db.execute('SET CONSTRAINTS ALL IMMEDIATE');after,immutable_after=snapshot(db)
 if immutable!=immutable_after or any(before[t]!=after[t] for t in before if t!='products'):raise ValueError('UNEXPECTED_NONSTATUS_DATABASE_CHANGE')
 final={str(pid):status for pid,status in db.execute('SELECT id,status FROM products')}
 if final!=desired:raise ValueError('FINAL_STATUS_MISMATCH')
 return dict(changed=len(changed),changed_ids=sorted(changed),counts=counts,published=sum(s=='PUBLISHED' for s in final.values()),draft=sum(s=='DRAFT' for s in final.values()),only_publication_status_changed=True,before_hashes=before,after_hashes=after)

def target_container(a):
 items=json.loads(subprocess.check_output(['docker','inspect',a.compose_project+'-postgres-1'],text=True));c=items[0]
 labels=c['Config']['Labels'];ports=c['NetworkSettings']['Ports']['5432/tcp']
 if labels.get('com.docker.compose.project')!=a.compose_project or labels.get('com.docker.compose.service')!='postgres' or c['State']['Status']!='running' or ports!=[{'HostIp':a.expect_host,'HostPort':str(a.expect_port)}]:raise ValueError('DOCKER_TARGET_MISMATCH')
 uri=urlsplit(os.environ[a.dsn_env]);values=dict(x.split('=',1) for x in c['Config']['Env'] if '=' in x)
 if values.get('POSTGRES_DB')!=a.expect_database or values.get('POSTGRES_USER')!=uri.username:raise ValueError('POSTGRES_CONTAINER_IDENTITY_MISMATCH')
 return c['Name'].lstrip('/'),uri.username

def backup(a,container,user):
 if a.backup.exists():raise ValueError('BACKUP_EXISTS_USE_NEW_PATH')
 a.backup.parent.mkdir(parents=True,exist_ok=True)
 fd=os.open(a.backup,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
 with os.fdopen(fd,'wb') as f:
  subprocess.run(['docker','exec',container,'pg_dump','-U',user,'-d',a.expect_database,'-Fc'],stdout=f,check=True);f.flush();os.fsync(f.fileno())
 with a.backup.open('rb') as f:p=subprocess.run(['docker','exec','-i',container,'pg_restore','--list'],stdin=f,capture_output=True,check=True)
 return dict(path=str(a.backup.resolve()),sha256=digest(a.backup.read_bytes()),archive_entries=len([x for x in p.stdout.splitlines() if x and not x.startswith(b';')]))

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--mode',choices=['preview','publish','rollback'],default='preview');p.add_argument('--seed',type=Path,required=True);p.add_argument('--image-manifest',type=Path,required=True);p.add_argument('--receipt',type=Path,required=True);p.add_argument('--backup',type=Path)
 p.add_argument('--compose-project',required=True);p.add_argument('--target-kind',choices=['local-development'],required=True);p.add_argument('--dsn-env',default='CATALOG_IMPORT_DSN')
 for n in ['expect-database','expect-host','confirm-target']:p.add_argument('--'+n,required=True)
 p.add_argument('--expect-port',type=int,required=True);a=p.parse_args()
 if a.expect_host!='127.0.0.1':p.error('Only explicit IPv4 loopback is supported')
 container,user=target_container(a);db=imp.target_connection(a,True)
 target=dict(database=a.expect_database,host=a.expect_host,port=a.expect_port,compose_project=a.compose_project)
 try:
  statuses,baseline,counts=validate(db,a.seed,a.image_manifest);hashes,immutable=snapshot(db);db.rollback()
  if a.mode=='preview':print(json.dumps(dict(counts=counts,published=sum(s=='PUBLISHED' for s in statuses.values()),draft=sum(s=='DRAFT' for s in statuses.values()),target=target)));return
  if a.receipt.exists():
   receipt=json.loads(a.receipt.read_text())
   if receipt['target']!=target or receipt['seed_sha256']!=digest((a.seed/'catalog.json').read_bytes()) or receipt['image_manifest_sha256']!=digest(a.image_manifest.read_bytes()):raise ValueError('RECEIPT_TARGET_OR_MANIFEST_MISMATCH')
   b=Path(receipt['backup']['path'])
   if not b.exists() or digest(b.read_bytes())!=receipt['backup']['sha256']:raise ValueError('BACKUP_MISSING_OR_CHANGED')
  else:
   if a.mode!='publish' or statuses!=baseline or not a.backup:raise ValueError('FIRST_PUBLICATION_REQUIRES_ORIGINAL_STATE_AND_NEW_BACKUP')
   db.execute('SET TRANSACTION READ ONLY');verified=verify_images(db,os.environ.get('LOCAL_STORAGE_ENDPOINT',''));db.rollback()
   receipt=dict(target=target,seed_sha256=digest((a.seed/'catalog.json').read_bytes()),image_manifest_sha256=digest(a.image_manifest.read_bytes()),baseline_statuses=baseline,catalog_hashes=hashes,immutable_products=immutable,images_verified=verified,backup=backup(a,container,user),phase='PREPARED',created_at=datetime.now(timezone.utc).isoformat())
   save(a.receipt,receipt)
  # A committed action is reconciled safely even if its final receipt write was interrupted.
  result=transition(db,a.seed,a.image_manifest,a.mode,receipt);db.commit()
  receipt.update(phase='PUBLISHED' if a.mode=='publish' else 'ROLLED_BACK',last_result=result,last_action_at=datetime.now(timezone.utc).isoformat());save(a.receipt,receipt)
  print(json.dumps(result))
 except BaseException:db.rollback();raise
 finally:db.close()
if __name__=='__main__':main()
