"""Disposable PostgreSQL preservation, rollback, dry-run and replay checks."""
import json
from pathlib import Path
import tempfile
import unittest
import uuid
import argparse
from unittest.mock import patch
import psycopg
from psycopg.types.json import Jsonb
import test_import_postgres as legacy_tests
import enhance_import as enhance
import enhance_normalize as normalizer


class EnhancementImportIT(unittest.TestCase):
    setUpClass = classmethod(legacy_tests.ImportPostgresIT.setUpClass.__func__)
    tearDownClass = classmethod(legacy_tests.ImportPostgresIT.tearDownClass.__func__)
    # Reuse disposable PostgreSQL/Flyway lifecycle, not legacy test methods.
    def setUp(self):
        legacy_tests.ImportPostgresIT.setUp(self)
        self.pid=uuid.UUID(enhance.legacy.normalize.proposed_uuid('cellphones','https://cellphones.com.vn/enrichment-fixture.html'))
        self.vid=enhance.legacy.variant_id(self.pid)
        self.cid=uuid.uuid4();self.iid=uuid.uuid4()
        with psycopg.connect(self.dsn) as db:
            db.execute('INSERT INTO categories(id,name) VALUES (%s,%s)',(self.cid,'Laptop'))
            db.execute('INSERT INTO products(id,category_id,name,status) VALUES (%s,%s,%s,%s)',(self.pid,self.cid,'Preserved name','PUBLISHED'))
            db.execute("INSERT INTO product_variants(id,product_id,sku,option_combination,selling_price) VALUES (%s,%s,'KEEP-SKU','{}',120000)",(self.vid,self.pid))
            db.execute("INSERT INTO product_images(id,product_id,object_key,alt_text,content_type,byte_size,width,height,display_order,is_primary,created_at) VALUES (%s,%s,'keep/private.jpg','Keep','image/jpeg',100,10,10,0,true,now())",(self.iid,self.pid))
        raw={'id':'fixture','source':'cellphones','source_url':'https://cellphones.com.vn/enrichment-fixture.html','name':'Other name','sku':'0','brand':'Lenovo','offers':[],'breadcrumbs':['CELLPHONES','Laptop','Other name'],'specifications':{'Dung lượng RAM':'8GB'}}
        self.row=normalizer.normalize_record(('fixture','cellphones',raw['source_url'],json.dumps(raw)),normalizer.load_registries())

    def tearDown(self):
        with psycopg.connect(self.dsn) as db:
            db.execute('DELETE FROM catalog_source_links');db.execute('DELETE FROM product_images')
        legacy_tests.ImportPostgresIT.tearDown(self)
        with psycopg.connect(self.dsn) as db:db.execute('DELETE FROM brands')

    def approval(self,db,row=None):
        row=row or self.row;p=enhance.proposal(db,row)
        return dict(row,operation=p['operation'],expected_before_sha256=p['expected_before_sha256'])

    def test_enrichment_preserves_core_and_image_and_replays(self):
        with psycopg.connect(self.dsn) as db:
            approval=self.approval(db)
            self.assertEqual(enhance.execute_item(db,self.row,approval,'c'*64),'ENRICH')
        with psycopg.connect(self.dsn) as db:
            self.assertEqual(enhance.execute_item(db,self.row,approval,'c'*64),'UNCHANGED')
            self.assertEqual(db.execute('SELECT name,status,category_id FROM products WHERE id=%s',(self.pid,)).fetchone(),('Preserved name','PUBLISHED',self.cid))
            self.assertEqual(db.execute('SELECT id,sku,selling_price,option_combination FROM product_variants').fetchone(),(self.vid,'KEEP-SKU',120000,{}))
            self.assertEqual(db.execute('SELECT id,object_key FROM product_images').fetchone(),(self.iid,'keep/private.jpg'))
            self.assertEqual(db.execute('SELECT count(*) FROM catalog_source_links').fetchone()[0],1)

    def test_conflict_and_batch_failure_rollback(self):
        with psycopg.connect(self.dsn) as db:approval=self.approval(db)
        with self.assertRaises(ValueError):
            with psycopg.connect(self.dsn) as db:
                enhance.execute_item(db,self.row,approval,'c'*64)
                changed=dict(self.row,technical_specifications={'schema_version':1,'values':{'physical_ram':{'value':16,'unit':'GB'}}})
                enhance.execute_item(db,changed,approval,'c'*64)
        with psycopg.connect(self.dsn) as db:
            self.assertEqual(db.execute('SELECT technical_specifications FROM product_variants').fetchone()[0],enhance.EMPTY)
            self.assertEqual(db.execute('SELECT count(*) FROM catalog_source_links').fetchone()[0],0)

    def test_missing_source_does_not_clear_and_dry_run_read_only(self):
        with psycopg.connect(self.dsn) as db:
            enhance.execute_item(db,self.row,self.approval(db),'c'*64)
        row=dict(self.row,brand=None,technical_specifications=enhance.EMPTY)
        row['normalized_payload_sha256']=normalizer.normalized_hash(row)
        with psycopg.connect(self.dsn) as db:
            plan=enhance.proposal(db,row)
            self.assertFalse(plan['brand_update']);self.assertFalse(plan['specification_update'])
        path=self.directory/'normalized.jsonl';path.write_text(json.dumps(self.row)+'\n')
        args=argparse.Namespace(records=path,approvals=None,apply=False,batch_size=10,dsn_env='CATALOG_IMPORT_DSN',target_kind='disposable',expect_database='catalog_import_test',expect_host=self.host,expect_port=self.port,confirm_target=f'catalog_import_test@{self.host}:{self.port}')
        report=enhance.run(args)
        self.assertFalse(report['applied'])
        with psycopg.connect(self.dsn) as db:self.assertEqual(db.execute('SELECT count(*) FROM catalog_source_links').fetchone()[0],1)

    def test_duplicate_and_modified_normalized_rows_rejected(self):
        path=self.directory/'normalized.jsonl';path.write_text((json.dumps(self.row)+'\n')*2)
        with self.assertRaises(ValueError):list(enhance.rows(path))
        row=dict(self.row,product_id=str(uuid.uuid4()));path.write_text(json.dumps(row)+'\n')
        with self.assertRaises(ValueError):list(enhance.rows(path))

    def test_controlled_apply_reconciles_uncertain_commit(self):
        path=self.directory/'records.jsonl';path.write_text(json.dumps(self.row)+'\n')
        with psycopg.connect(self.dsn) as db:approval=self.approval(db)
        approvals=self.directory/'approvals.json';approvals.write_text(json.dumps([approval]))
        backup=self.directory/'recovery-fixture';backup.write_text('Synthetic disposable recovery evidence only')
        args=argparse.Namespace(records=path,approvals=approvals,apply=True,batch_size=1,dsn_env='CATALOG_IMPORT_DSN',target_kind='disposable',expect_database='catalog_import_test',expect_host=self.host,expect_port=self.port,confirm_target=f'catalog_import_test@{self.host}:{self.port}',confirm_apply=f'catalog_import_test@{self.host}:{self.port}',backup=backup,backup_sha256=enhance.hashlib.sha256(backup.read_bytes()).hexdigest(),report=self.directory/'report.json')
        self.assertEqual(enhance.run(args)['counts'],{'ENRICH':1})
        self.assertEqual(enhance.run(args)['counts'],{'UNCHANGED':1})

    def test_unmatched_source_never_creates_product_or_variant(self):
        raw=dict(self.row['raw_payload'],id='new-fixture',source_url='https://cellphones.com.vn/new-fixture.html',sku='VALID-NEW',offers=[{'type':'Offer','price':'1000','currency':'VND'}])
        registries=normalizer.load_registries();registries['category_mappings'][('cellphones',('Laptop',))]=str(self.cid)
        row=normalizer.normalize_record(('new-fixture','cellphones',raw['source_url'],json.dumps(raw)),registries)
        with psycopg.connect(self.dsn) as db:
            before=db.execute('SELECT (SELECT count(*) FROM products),(SELECT count(*) FROM product_variants)').fetchone()
            plan=enhance.proposal(db,row);self.assertEqual(plan['operation'],'UNMATCHED')
            with self.assertRaises(ValueError):
                enhance.execute_item(db,row,self.approval(db,row),'d'*64)
        with psycopg.connect(self.dsn) as db:
            self.assertEqual(db.execute('SELECT (SELECT count(*) FROM products),(SELECT count(*) FROM product_variants)').fetchone(),before)

    def test_failure_report_retains_committed_batch_and_retry_reconciles(self):
        raw=dict(self.row['raw_payload'],id='second',source_url='https://cellphones.com.vn/second-fixture.html')
        second=normalizer.normalize_record(('second','cellphones',raw['source_url'],json.dumps(raw)),normalizer.load_registries())
        with psycopg.connect(self.dsn) as db:
            db.execute("INSERT INTO products(id,category_id,name,status) VALUES (%s,%s,'Second','DRAFT')",(second['product_id'],self.cid))
            db.execute("INSERT INTO product_variants(id,product_id,sku,option_combination,selling_price) VALUES (%s,%s,'SECOND','{}',100)",(second['variant_id'],second['product_id']))
            approvals=[self.approval(db),self.approval(db,second)]
        path=self.directory/'records.jsonl';path.write_text(json.dumps(self.row)+'\n'+json.dumps(second)+'\n')
        ap=self.directory/'approved.json';ap.write_text(json.dumps(approvals))
        backup=self.directory/'backup';backup.write_text('synthetic recovery fixture')
        args=argparse.Namespace(records=path,approvals=ap,apply=True,batch_size=1,dsn_env='CATALOG_IMPORT_DSN',target_kind='disposable',expect_database='catalog_import_test',expect_host=self.host,expect_port=self.port,confirm_target=f'catalog_import_test@{self.host}:{self.port}',confirm_apply=f'catalog_import_test@{self.host}:{self.port}',backup=backup,backup_sha256=enhance.file_sha256(backup),report=self.directory/'report.json')
        real=enhance.execute_item
        def interrupt(db,row,approval,manifest):
            if row['source_record_id']=='second':raise ValueError('Synthetic process interruption')
            return real(db,row,approval,manifest)
        with patch.object(enhance,'execute_item',side_effect=interrupt):
            with self.assertRaises(ValueError):enhance.run(args)
        self.assertEqual(json.loads(args.report.read_text())['counts'],{'ENRICH':1})
        self.assertEqual(enhance.run(args)['counts'],{'UNCHANGED':1,'ENRICH':1})
