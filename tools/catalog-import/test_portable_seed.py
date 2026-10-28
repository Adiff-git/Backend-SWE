"""Run with reviewed seed at CATALOG_SEED_TEST_DIR (default local output)."""
import copy,json,os,secrets,socket,subprocess,tempfile,time,unittest,urllib.request,urllib.error
from pathlib import Path
import psycopg
from testcontainers.postgres import PostgresContainer
import portable_seed as seed
import import_postgres as imp

DIRECTORY=Path(os.environ.get('CATALOG_SEED_TEST_DIR',str(imp.ROOT/'output/catalog-dev-v1')))
class SeedValidation(unittest.TestCase):
 def mutate(self,change):
  data=json.loads((DIRECTORY/'catalog.json').read_text());change(data)
  raw=json.dumps(data).encode();manifest=json.loads((DIRECTORY/'manifest.json').read_text());manifest['sha256']=seed.hashlib.sha256(raw).hexdigest()
  with tempfile.TemporaryDirectory() as d:
   p=Path(d);(p/'catalog.json').write_bytes(raw);(p/'manifest.json').write_text(json.dumps(manifest))
   with self.assertRaises((ValueError,KeyError)):seed.load_seed(p)
 def test_checksum(self):
  with tempfile.TemporaryDirectory() as d:
   p=Path(d);(p/'catalog.json').write_bytes((DIRECTORY/'catalog.json').read_bytes()+b' ');(p/'manifest.json').write_bytes((DIRECTORY/'manifest.json').read_bytes())
   with self.assertRaises(ValueError):seed.load_seed(p)
 def test_manifest_counts(self):
  with tempfile.TemporaryDirectory() as d:
   p=Path(d);m=json.loads((DIRECTORY/'manifest.json').read_text());m['counts']['products']=999
   (p/'manifest.json').write_text(json.dumps(m));(p/'catalog.json').write_bytes((DIRECTORY/'catalog.json').read_bytes())
   with self.assertRaises(ValueError):seed.load_seed(p)
 def test_uuid(self):self.mutate(lambda d:d['products'][0].update(product_id='00000000-0000-0000-0000-000000000000'))
 def test_duplicate_sku(self):self.mutate(lambda d:d['products'][0].update(sku=d['products'][1]['sku']))
 def test_negative_price(self):self.mutate(lambda d:d['products'][0].update(price='-1'))
 def test_publication(self):self.mutate(lambda d:d['products'][0].update(status='ARCHIVED'))
 def test_category_cycle(self):self.mutate(lambda d:d['categories'][0].update(parent_id=d['categories'][0]['id']))

class SeedPostgres(unittest.TestCase):
 def test_fresh_seed_repeat_rollback_and_api(self):
  categories,plans=seed.load_seed(DIRECTORY)
  with PostgresContainer('postgres:17-alpine',username='test',password='test',dbname='portable_seed_test') as c:
   host=c.get_container_host_ip();port=int(c.get_exposed_port(5432));dsn=f'postgresql://test:test@{host}:{port}/portable_seed_test'
   env=dict(os.environ,IMPORT_TEST_JDBC_URL=f'jdbc:postgresql://{host}:{port}/portable_seed_test',IMPORT_TEST_DB_USER='test',IMPORT_TEST_DB_PASSWORD='test')
   cp=Path('/tmp/backend_catalog_import_classpath.txt').read_text().strip()
   subprocess.run(['java','--class-path',cp,str(imp.ROOT/'test_support/FlywayBootstrap.java'),str(imp.MIGRATIONS)],env=env,check=True,capture_output=True)
   with psycopg.connect(dsn) as db:
    imp.validate_history(db)
    result=seed.apply_seed(db,categories,plans,True);db.commit()
    self.assertEqual((result['products_created'],result['variants_created'],result['categories_created']),(1000,1000,31))
    result=seed.apply_seed(db,categories,plans,True);db.commit();self.assertEqual(result['unchanged_products'],1000);self.assertEqual(result['products_created'],0)
    for item in plans:self.assertTrue(imp.inspect_existing(db,item))
    self.assertEqual(db.execute('select count(*) from product_images').fetchone()[0],0)
    self.assertEqual(db.execute('select count(*) from users').fetchone()[0],0)
    # Changed seed fields must fail and leave the target unchanged.
    changed=copy.deepcopy(plans);changed[-1]['name']='conflicting seed name'
    with self.assertRaises(imp.ImportErrorDetail):seed.apply_seed(db,categories,changed,True)
    db.rollback();self.assertTrue(imp.inspect_existing(db,plans[-1]))
   with socket.socket() as sock:sock.bind(('127.0.0.1',0));apiport=sock.getsockname()[1]
   env.update(LOCAL_DB_HOST=host,LOCAL_DB_PORT=str(port),LOCAL_DB_NAME='portable_seed_test',LOCAL_DB_USER='test',LOCAL_DB_PASSWORD='test',JWT_SECRET=secrets.token_urlsafe(48),JWT_ISSUER='urn:portable-seed:test',SERVER_PORT=str(apiport),SERVER_ADDRESS='127.0.0.1')
   log=open('/tmp/catalog-seed-api.log','w')
   app=subprocess.Popen(['java','-cp',str(imp.ROOT.parents[1]/'demo/target/classes')+os.pathsep+cp,'com.example.demo.DemoApplication','--spring.profiles.active=local'],env=env,stdout=log,stderr=log)
   base=f'http://127.0.0.1:{apiport}'
   try:
    for _ in range(120):
     try:
      page=json.load(urllib.request.urlopen(base+'/api/products?page=0&size=20',timeout=2));break
     except (OSError,ValueError):
      if app.poll() is not None:raise RuntimeError('Disposable backend startup failed; see /tmp/catalog-seed-api.log')
      time.sleep(.5)
    else:raise RuntimeError('Disposable backend startup timeout')
    public={str(p['product_id']):p for p in plans if p['status']=='PUBLISHED'}
    self.assertEqual(set(page),{'items','page','size','totalItems','totalPages'})
    self.assertEqual((page['page'],page['size'],page['totalItems'],page['totalPages']),(0,20,5,1))
    self.assertEqual({x['id'] for x in page['items']},set(public))
    for pid,p in public.items():
     detail=json.load(urllib.request.urlopen(base+'/api/products/'+pid))
     self.assertEqual(detail['name'],p['name']);self.assertEqual(detail['category']['id'],str(p['category_id']));self.assertEqual(detail['images'],[])
     self.assertEqual(detail['variants'],[dict(id=str(p['variant_id']),sku=p['sku'],options={},sellingPrice=dict(amount=int(p['price']),currency='VND'))])
    for p in [p for p in plans if p['status']=='DRAFT'][:10]:
     with self.assertRaises(urllib.error.HTTPError) as error:urllib.request.urlopen(base+'/api/products/'+str(p['product_id']))
     self.assertEqual(error.exception.code,404)
   finally:
    app.terminate();app.wait(timeout=30);log.close()
if __name__=='__main__':unittest.main(verbosity=2)
