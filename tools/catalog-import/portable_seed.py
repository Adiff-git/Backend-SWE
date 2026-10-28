#!/usr/bin/env python3
"""Load a reviewed portable local Catalog snapshot through the existing importer."""
import argparse
import hashlib
import json
import uuid
from decimal import Decimal
from pathlib import Path
import import_postgres as importer

VERSION = 'catalog-dev-v1'

def load_seed(directory):
    manifest = json.loads((directory / 'manifest.json').read_text())
    if manifest['counts'] != dict(products=1000, variants=1000, categories=31, published=5, draft=995):
        raise ValueError('Seed manifest counts differ')
    raw = (directory / 'catalog.json').read_bytes()
    if manifest['version'] != VERSION or hashlib.sha256(raw).hexdigest() != manifest['sha256']:
        raise ValueError('Seed version or checksum mismatch')
    expected = {name: importer.flyway_checksum(importer.MIGRATIONS / name) for name in importer.MIGRATION_FILES}
    if manifest['migrations'] != expected:
        raise ValueError('Seed migration checksums differ from checkout')
    data = json.loads(raw)
    if set(data) != {'categories', 'products'} or len(data['products']) != 1000 or len(data['categories']) != 31:
        raise ValueError('Unexpected seed shape/counts')
    categories = {}
    for row in data['categories']:
        if set(row) != {'id','name','parent_id'} or not isinstance(row['name'],str) or not row['name'].strip():
            raise ValueError('Invalid category')
        cid = uuid.UUID(row['id'])
        if cid in categories: raise ValueError('Duplicate category')
        categories[cid] = (row['name'], uuid.UUID(row['parent_id']) if row['parent_id'] else None)
    ordered = []
    remaining = dict(categories)
    while remaining:
        ready = [cid for cid, (_, parent) in remaining.items() if parent is None or parent in {x[0] for x in ordered}]
        if not ready: raise ValueError('Missing category parent or category cycle')
        for cid in sorted(ready):
            name,parent = remaining.pop(cid); ordered.append((cid,name,parent))
    plans=[]; identities=set(); skus=set(); ids=set()
    fields={'identity','product_id','variant_id','category_id','name','description','status','sku','price','options'}
    for row in data['products']:
        if set(row)!=fields: raise ValueError('Unexpected product fields')
        item=dict(row)
        source,url=item['identity'].split('|',1)
        if source not in ('cellphones','tgdd') or not url.startswith('https://'): raise ValueError('Invalid source identity')
        for field in ('product_id','variant_id','category_id'): item[field]=uuid.UUID(item[field])
        if str(item['product_id']) != importer.normalize.proposed_uuid(source,url) or item['variant_id'] != importer.variant_id(item['product_id']):
            raise ValueError('Deterministic UUID mismatch')
        if item['identity'] in identities or item['product_id'] in ids or item['sku'].casefold() in skus: raise ValueError('Duplicate identity, UUID or SKU')
        if not item['name'].strip() or not item['description'] or not item['sku'].strip() or item['sku']!=item['sku'].strip() or item['sku'].casefold() in {'0','na','n/a','none','null','unknown'}: raise ValueError('Invalid text/SKU')
        if item['status'] not in ('DRAFT','PUBLISHED') or item['options']!={} or item['category_id'] not in categories: raise ValueError('Invalid status/options/category')
        if not isinstance(item['price'],str) or importer.valid_price(item['price']) is None: raise ValueError('Invalid whole VND price')
        item['price']=Decimal(item['price']);item['create_category_path']=False
        identities.add(item['identity']);ids.add(item['product_id']);skus.add(item['sku'].casefold());plans.append(item)
    if sum(p['status']=='PUBLISHED' for p in plans)!=5: raise ValueError('Publication split differs')
    return ordered,plans

def apply_seed(connection,categories,plans,execute):
    product_ids = {p['product_id'] for p in plans}
    category_ids = {c[0] for c in categories}
    if not {r[0] for r in connection.execute('SELECT id FROM products')} <= product_ids:
        raise importer.ImportErrorDetail('Target contains products outside this seed')
    if not {r[0] for r in connection.execute('SELECT id FROM categories')} <= category_ids:
        raise importer.ImportErrorDetail('Target contains categories outside this seed')
    for cid,name,parent in categories:
        found=connection.execute('SELECT name,parent_category_id FROM categories WHERE id=%s',(cid,)).fetchone()
        if found is not None and found != (name,parent): raise importer.ImportErrorDetail('Seed category conflicts with target')
    # Inspect every existing product before any insert. Missing categories are valid on a fresh target.
    unchanged=sum(importer.inspect_existing(connection,p,allow_missing_category=True) for p in plans)
    if not execute: return {'unchanged_products':unchanged,'products_to_create':len(plans)-unchanged}
    created=sum(importer.ensure_category(connection,*category) for category in categories)
    result=importer.apply_plans(connection,plans,True)
    result['categories_created']=created
    return result

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--seed',type=Path,required=True)
    p.add_argument('--execute',action='store_true',help='Default is read-only compatibility preview')
    p.add_argument('--target-kind',choices=('local-development','disposable'),required=True)
    p.add_argument('--expect-database',required=True);p.add_argument('--expect-host',required=True)
    p.add_argument('--expect-port',type=int,required=True);p.add_argument('--confirm-target',required=True)
    p.add_argument('--dsn-env',default='CATALOG_IMPORT_DSN')
    a=p.parse_args()
    try:
        categories,plans=load_seed(a.seed)
        db=importer.target_connection(a,read_only=not a.execute)
        try:
            result=apply_seed(db,categories,plans,a.execute)
            if a.execute: db.commit()
            else: db.rollback()
        except BaseException:
            db.rollback();raise
        finally: db.close()
        print(json.dumps(result))
    except Exception:
        p.exit(2,'Seed validation or target operation failed; transaction rolled back. Check reviewed seed, target and schema.\n')

if __name__=='__main__': main()
