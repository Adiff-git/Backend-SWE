"""Status-only publication and rollback on a fresh disposable PostgreSQL 17."""
import json,os,subprocess,tempfile,unittest
from pathlib import Path
import psycopg
from testcontainers.postgres import PostgresContainer
import portable_seed,portable_seed_v2 as v2,import_postgres as imp,publish_local_catalog as pub
from test_portable_seed_v2 import generated_seed
class LocalPublication(unittest.TestCase):
 def test_transactional_publish_repeat_rollback_and_drift_rejection(self):
  with tempfile.TemporaryDirectory() as directory,PostgresContainer('postgres:17-alpine',username='test',password='test',dbname='publication_test') as pg:
   root=Path(directory);port=pg.get_exposed_port(5432);host=pg.get_container_host_ip();dsn=f'postgresql://test:test@{host}:{port}/publication_test';seed=imp.ROOT/'output/catalog-dev-v1'
   env=dict(os.environ,IMPORT_TEST_JDBC_URL=f'jdbc:postgresql://{host}:{port}/publication_test',IMPORT_TEST_DB_USER='test',IMPORT_TEST_DB_PASSWORD='test');cp=Path('/tmp/backend_catalog_import_classpath.txt').read_text().strip()
   subprocess.run(['java','--class-path',cp,str(imp.ROOT/'test_support/FlywayBootstrap.java'),str(imp.MIGRATIONS)],env=env,check=True,capture_output=True)
   rows=generated_seed(root/'fixture');cats,plans=portable_seed.load_seed(seed);manifest=root/'manifest.json';manifest.write_text(json.dumps({'products':[{'product_id':str(p['product_id']),'source_identity':p['identity']} for p in plans]}))
   with psycopg.connect(dsn) as db:
    portable_seed.apply_seed(db,cats,plans,True)
    for row in rows:db.execute('INSERT INTO product_images ('+','.join(v2.COLUMNS)+') VALUES ('+','.join(['%s']*len(v2.COLUMNS))+')',tuple(row[c] for c in v2.COLUMNS))
    db.commit();statuses,baseline,_=pub.validate(db,seed,manifest);hashes,immutable=pub.snapshot(db);db.rollback();receipt=dict(baseline_statuses=baseline,catalog_hashes=hashes,immutable_products=immutable)
    first=pub.transition(db,seed,manifest,'publish',receipt);db.commit();self.assertEqual((first['changed'],first['published'],first['draft']),(995,1000,0));self.assertTrue(first['only_publication_status_changed'])
    again=pub.transition(db,seed,manifest,'publish',receipt);db.commit();self.assertEqual(again['changed'],0)
    db.execute("UPDATE products SET name=name || ' changed' WHERE id=%s",(plans[0]['product_id'],))
    with self.assertRaises(imp.ImportErrorDetail):pub.transition(db,seed,manifest,'rollback',receipt)
    db.rollback()
    reverse=pub.transition(db,seed,manifest,'rollback',receipt);db.commit();self.assertEqual((reverse['changed'],reverse['published'],reverse['draft']),(995,5,995))
    self.assertEqual(pub.transition(db,seed,manifest,'rollback',receipt)['changed'],0);db.commit()
    end,after_immutable=pub.snapshot(db);self.assertEqual(hashes,end);self.assertEqual(immutable,after_immutable)
    manifest.write_text(json.dumps({'products':[]}))
    with self.assertRaisesRegex(ValueError,'MANIFEST_UUID_IDENTITY_MISMATCH'):pub.validate(db,seed,manifest)
    db.rollback()
if __name__=='__main__':unittest.main(verbosity=2)
