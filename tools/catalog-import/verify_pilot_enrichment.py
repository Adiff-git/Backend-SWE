#!/usr/bin/env python3
"""Exercise the exact 1,000-row pilot seed and enrichment in disposable PostgreSQL."""
import argparse
import json
import os
from pathlib import Path
import subprocess
from types import SimpleNamespace

import psycopg
from testcontainers.postgres import PostgresContainer

import enhance_import as enhancement
import import_postgres as importer
import portable_seed


def snapshot(db):
    products = db.execute('SELECT id,category_id,name,description,status FROM products ORDER BY id').fetchall()
    variants = db.execute('SELECT id,product_id,sku,selling_price,option_combination FROM product_variants ORDER BY id').fetchall()
    images = db.execute('SELECT id,product_id,object_key FROM product_images ORDER BY id').fetchall()
    return dict(products=products, variants=variants, images=images)


def verify(seed_dir, rows_file, classpath_file):
    categories, plans = portable_seed.load_seed(seed_dir)
    rows = list(enhancement.rows(rows_file))
    if len(plans) != 1000 or len(rows) != 1000:
        raise ValueError('This verification requires exactly 1,000 seed products and matching source rows')
    with PostgresContainer('postgres:17-alpine', username='test', password='test', dbname='catalog_enhancement_test') as container:
        host = container.get_container_host_ip(); port = int(container.get_exposed_port(5432))
        dsn = f'postgresql://test:test@{host}:{port}/catalog_enhancement_test'
        env = dict(os.environ, IMPORT_TEST_JDBC_URL=f'jdbc:postgresql://{host}:{port}/catalog_enhancement_test',
                   IMPORT_TEST_DB_USER='test', IMPORT_TEST_DB_PASSWORD='test')
        classpath = Path(classpath_file).read_text().strip()
        subprocess.run(['java','--class-path',classpath,str(importer.ROOT/'test_support/FlywayBootstrap.java'),str(importer.MIGRATIONS)],
                       env=env,check=True,capture_output=True)
        with psycopg.connect(dsn) as db:
            importer.validate_history(db)
            result = portable_seed.apply_seed(db,categories,plans,True); db.commit()
            if (result['products_created'],result['variants_created']) != (1000,1000):
                raise AssertionError('Pilot seed did not create exactly 1,000 Product/Variant pairs')
            before = snapshot(db)
            os.environ['CATALOG_ENHANCEMENT_VERIFY_DSN'] = dsn
            try:
                preview = enhancement.run(SimpleNamespace(records=Path(rows_file), registry_dir=None,
                    approvals=None, apply=False, batch_size=100, report=None,
                    dsn_env='CATALOG_ENHANCEMENT_VERIFY_DSN', target_kind='disposable',
                    expect_database='catalog_enhancement_test', expect_host=host,
                    expect_port=port, confirm_target=f'catalog_enhancement_test@{host}:{port}'))
            finally:
                os.environ.pop('CATALOG_ENHANCEMENT_VERIFY_DSN', None)
            if preview['counts'] != {'ENRICH': 1000} or snapshot(db) != before:
                raise AssertionError('Read-only 1,000-product preview changed the target or lost matches')
            counts = {'ENRICH':0,'UNCHANGED':0,'UNMATCHED':0}
            for row in rows:
                plan = enhancement.proposal(db,row)
                if plan['operation'] == 'UNMATCHED':
                    counts['UNMATCHED'] += 1
                    continue
                approval = dict(row,operation=plan['operation'],expected_before_sha256=plan['expected_before_sha256'])
                outcome = enhancement.execute_item(db,row,approval,'a'*64)
                counts[outcome] += 1
            db.commit()
            after = snapshot(db)
            if before != after:
                raise AssertionError('Enrichment changed protected Product/Variant/image fields')
            replay = {'ENRICH':0,'UNCHANGED':0}
            for row in rows:
                plan = enhancement.proposal(db,row)
                approval = dict(row,operation=plan['operation'],expected_before_sha256=plan['expected_before_sha256'])
                outcome = enhancement.execute_item(db,row,approval,'a'*64)
                replay[outcome] += 1
            db.commit()
            if snapshot(db) != before:
                raise AssertionError('Replay changed protected fields')
            return dict(seed_products=len(before['products']),seed_variants=len(before['variants']),
                        products_after=len(after['products']),variants_after=len(after['variants']),
                        source_links=db.execute('SELECT count(*) FROM catalog_source_links').fetchone()[0],
                        read_only_preview=preview['counts'],first_pass=counts,replay=replay,
                        brands_assigned=db.execute('SELECT count(*) FROM products WHERE brand_id IS NOT NULL').fetchone()[0],
                        specifications_assigned=db.execute("SELECT count(*) FROM product_variants WHERE technical_specifications->'values' != '{}'::jsonb").fetchone()[0],
                        protected_fields_unchanged=True,real_database_touched=False)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--seed-dir',type=Path,required=True)
    parser.add_argument('--records',type=Path,required=True)
    parser.add_argument('--classpath',type=Path,default=Path('/tmp/backend_catalog_import_classpath.txt'))
    parser.add_argument('--report',type=Path,required=True)
    args=parser.parse_args()
    outcome=verify(args.seed_dir,args.records,args.classpath)
    args.report.parent.mkdir(parents=True,exist_ok=True)
    args.report.write_text(json.dumps(outcome,indent=2,sort_keys=True)+'\n')
    print(json.dumps(outcome,sort_keys=True))
