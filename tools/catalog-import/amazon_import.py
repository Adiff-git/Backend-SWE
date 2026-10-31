#!/usr/bin/env python3
"""Approval-gated Amazon demo seed adapter. Defaults to offline validation."""
import argparse
import csv
from decimal import Decimal
import hashlib
import json
from pathlib import Path
import re
import sys
import uuid

import import_postgres as legacy

FIELDS=('parent_asin','approved_category_uuid','approved_sku','approved_selling_price_vnd',
        'approved_brand_uuid','category_approval','sku_policy_approval','variant_approval',
        'price_approval','import_approval','publication_approval','review_notes')
APPROVALS=('category_approval','sku_policy_approval','variant_approval','price_approval','import_approval')
HEX64=re.compile(r'^[0-9a-f]{64}$')
ASIN=re.compile(r'^[A-Z0-9]{10}$')
SKU=re.compile(r'^DEMO-AMZ-[A-Z0-9]{10}$')
SPEC_CATEGORY={'Smartphone':'Điện thoại','Laptop':'Laptop','Tablet':'Máy tính bảng',
               'Headphone & Earbuds':'Âm thanh','Speaker':'Âm thanh','Charger & Power Bank':'Phụ kiện',
               'Computer Accessories':'Phụ kiện','Phone Accessories':'Phụ kiện','Smartwatch':'Điện thoại'}

class AmazonImportError(ValueError): pass

def canonical(value): return json.dumps(value,ensure_ascii=False,sort_keys=True,separators=(',',':'))
def load_jsonl(path):
    with path.open(encoding='utf-8') as stream:return [json.loads(line) for line in stream if line.strip()]

def validate_specs(document,category):
    if not isinstance(document,dict) or set(document)!={'schema_version','values'} or type(document.get('schema_version')) is not int or document['schema_version']!=1 or not isinstance(document.get('values'),dict):
        raise AmazonImportError('Invalid specification document')
    registry=json.loads((Path(__file__).resolve().parent/'registries/attributes-v1.json').read_text(encoding='utf-8'))
    if registry.get('version')!=1:raise AmazonImportError('Unsupported specification registry')
    definitions={x['key']:x for x in registry['attributes']}
    for key,value in document['values'].items():
        rule=definitions.get(key)
        if rule is None or SPEC_CATEGORY.get(category) not in rule['categories']:
            raise AmazonImportError('Unsupported or category-inapplicable specification key')
        kind=rule['type']
        if kind=='boolean':valid=type(value) is bool
        elif kind in ('quantity','storage_gb','battery_wh','battery_mah'):
            valid=False
            if isinstance(value,dict) and set(value)=={'value','unit'} and value['unit']==rule['unit'] and type(value['value']) in (int,float):
                amount=Decimal(str(value['value']));valid=amount.is_finite() and 0<amount<=Decimal(10)**12 and amount.as_tuple().exponent>=-6
        elif kind=='resolution':valid=isinstance(value,str) and bool(re.fullmatch(r'[0-9]{3,5}x[0-9]{3,5}',value))
        else:valid=isinstance(value,str) and bool(value.strip())
        if not valid:raise AmazonImportError('Invalid specification value')

def load_seed(directory):
    directory=Path(directory)
    manifest=json.loads((directory/'manifests/catalog_manifest.json').read_text(encoding='utf-8'))
    if manifest.get('version')!='amazon-mvp1500-seed-1':raise AmazonImportError('Unsupported catalog seed version')
    for name,expected in manifest.get('file_sha256',{}).items():
        path=directory/name
        if not path.is_file() or hashlib.sha256(path.read_bytes()).hexdigest()!=expected:
            raise AmazonImportError('Missing or checksum-mismatched seed file: '+name)
    products=load_jsonl(directory/'data/products.jsonl')
    specs=load_jsonl(directory/'data/specifications.jsonl')
    variants=load_jsonl(directory/'data/variants.jsonl')
    if len(products)!=len(specs) or len(products)!=len(variants) or len(products)!=manifest.get('product_count'):
        raise AmazonImportError('Seed row counts do not match manifest')
    identities=set();skus=set()
    for p,s,v in zip(products,specs,variants):
        asin=p.get('parent_asin')
        if not isinstance(asin,str) or not ASIN.fullmatch(asin) or asin in identities:
            raise AmazonImportError('Invalid or duplicate ASIN')
        identities.add(asin)
        if s.get('parent_asin')!=asin or v.get('parent_asin')!=asin:
            raise AmazonImportError('Product, specification, and variant identities differ')
        product=p.get('product',{});variant=v.get('variant',{})
        if variant.get('product_id_proposal')!=product.get('id_proposal') or not variant.get('id_proposal'):
            raise AmazonImportError('Variant relationship invalid')
        sku=variant.get('sku_proposal')
        if not isinstance(sku,str) or not SKU.fullmatch(sku) or sku.casefold() in skus:
            raise AmazonImportError('Invalid or duplicate proposed SKU')
        skus.add(sku.casefold())
        if product.get('status')!='DRAFT' or variant.get('approved_selling_price_vnd') is not None:
            raise AmazonImportError('Seed contains an unexpected approved price or publication state')
        validate_specs(s.get('technical_specifications'),product.get('category_label'))
    return manifest,products,specs,variants

def read_approvals(path):
    with path.open(encoding='utf-8-sig',newline='') as stream:
        reader=csv.DictReader(stream)
        if tuple(reader.fieldnames or ())!=FIELDS:raise AmazonImportError('Approval manifest columns differ from Amazon contract')
        rows=list(reader)
    if any(None in row or any(value is None for value in row.values()) for row in rows):
        raise AmazonImportError('Incomplete approval row')
    if len({r['parent_asin'] for r in rows})!=len(rows):raise AmazonImportError('Duplicate approval ASIN')
    return {r['parent_asin']:r for r in rows}

def load_categories(path):
    payload=json.loads(path.read_text(encoding='utf-8'))
    if payload.get('version')!=1 or not isinstance(payload.get('categories'),list):
        raise AmazonImportError('Invalid category mapping version')
    result={}
    for row in payload['categories']:
        label=row.get('source_category')
        if label in result:raise AmazonImportError('Duplicate category mapping')
        result[label]=row
    return result

def plan(directory,approvals_path,categories_path,stage=1500,replacement=False):
    manifest,products,specs,variants=load_seed(directory)
    approvals=read_approvals(approvals_path)
    categories=load_categories(categories_path)
    if set(approvals)!={p['parent_asin'] for p in products}:
        raise AmazonImportError('Approval manifest must account for every seed ASIN')
    if stage not in (100,1000,1500):raise AmazonImportError('Unsupported import stage')
    if stage==1500:stage_asins={p['parent_asin'] for p in products}
    else:
        payload=json.loads((Path(directory)/f'data/stages/stage{stage}_asins.json').read_text(encoding='utf-8'))
        stage_list=payload.get('asins')
        if payload.get('stage')!=stage or not isinstance(stage_list,list) or len(stage_list)!=stage or len(set(stage_list))!=stage:
            raise AmazonImportError('Invalid stage identity manifest')
        stage_asins=set(stage_list)
        if not stage_asins<={p['parent_asin'] for p in products}:raise AmazonImportError('Stage ASIN outside seed')
    pending=[];plans=[]
    for p,s,v in zip(products,specs,variants):
        asin=p['parent_asin'];a=approvals[asin];reasons=[]
        if asin not in stage_asins:continue
        if manifest.get('data_approval_status')!='APPROVED':reasons.append('DATA_MANIFEST_NOT_APPROVED')
        for field in APPROVALS:
            if a[field] not in ('APPROVED','PENDING_APPROVAL'):
                reasons.append('INVALID_APPROVAL_VALUE:'+field)
            elif a[field]!='APPROVED':reasons.append('APPROVAL_REQUIRED:'+field)
        if a['publication_approval'] not in ('APPROVED','PENDING_APPROVAL'):
            reasons.append('INVALID_PUBLICATION_APPROVAL')
        category=p['product']['category_label'];mapping=categories.get(category)
        if not mapping or mapping.get('approval')!='APPROVED':reasons.append('CATEGORY_MAPPING_NOT_APPROVED')
        if mapping and mapping.get('path')!=p['product'].get('category_path_proposal'):
            reasons.append('CATEGORY_PATH_MISMATCH')
        try:
            cid=uuid.UUID(a['approved_category_uuid'] or (mapping['category_uuid'] if mapping and mapping.get('approval')=='APPROVED' else ''))
            if not mapping or str(cid)!=mapping.get('category_uuid'):reasons.append('CATEGORY_REFERENCE_MISMATCH')
        except (ValueError,TypeError):cid=None;reasons.append('APPROVED_CATEGORY_UUID_REQUIRED')
        sku=a['approved_sku']
        if sku!=v['variant']['sku_proposal'] or not SKU.fullmatch(sku):reasons.append('APPROVED_SKU_MISMATCH')
        price=legacy.valid_price(a['approved_selling_price_vnd'])
        if price is None:reasons.append('POSITIVE_WHOLE_VND_PRICE_REQUIRED')
        brand_id=None
        if a['approved_brand_uuid']:
            try:brand_id=uuid.UUID(a['approved_brand_uuid'])
            except ValueError:reasons.append('INVALID_BRAND_UUID')
        if reasons:
            pending.append({'parent_asin':asin,'reasons':sorted(reasons)});continue
        plans.append({'asin':asin,'product_id':uuid.UUID(p['product']['id_proposal']),
                      'variant_id':uuid.UUID(v['variant']['id_proposal']),
                      'category_id':cid,'brand_id':brand_id,'name':p['product']['name'],
                      'description':p['product'].get('description'),'status':'PUBLISHED' if a['publication_approval']=='APPROVED' else 'DRAFT',
                      'sku':sku,'price':price,'options':v['variant']['option_combination_proposal'],
                      'specifications':s['technical_specifications']})
    if len({x['sku'].casefold() for x in plans})!=len(plans):raise AmazonImportError('Duplicate approved SKU')
    plan_digest=hashlib.sha256(canonical({'stage':stage,'plans':[
        {key:(str(value) if isinstance(value,(uuid.UUID,Decimal)) else value) for key,value in item.items()}
        for item in plans],'categories':categories}).encode('utf-8')).hexdigest()
    return {'seed_count':len(products),'stage':stage,'stage_count':len(stage_asins),'eligible':len(plans),'pending':pending,
            'source_links_created':0,'source_link_reason':'V10 source_url and original payload hash unavailable',
            'manifest_sha256':plan_digest},plans,categories

def check_or_apply(connection,plans,categories,execute):
    if execute:
        from psycopg.types.json import Jsonb
    counts={'categories_created':0,'products_created':0,'variants_created':0,'unchanged_products':0}
    seen_ids=set();seen_paths={}
    for category in sorted(categories.values(),key=lambda row:len(row.get('path',[]))):
        if category.get('approval')!='APPROVED':raise AmazonImportError('Category mapping is not approved')
        if not isinstance(category.get('path'),list) or not category['path'] or any(not isinstance(x,str) or not x.strip() for x in category['path']):
            raise AmazonImportError('Invalid category path')
        cid=uuid.UUID(category['category_uuid']);parent=uuid.UUID(category['parent_uuid']) if category.get('parent_uuid') else None
        expected_parent=seen_paths.get(tuple(category['path'][:-1])) if len(category['path'])>1 else None
        if cid in seen_ids or parent!=expected_parent or tuple(category['path']) in seen_paths:
            raise AmazonImportError('Category hierarchy is duplicated or not topological')
        seen_ids.add(cid);seen_paths[tuple(category['path'])]=cid
        existing=connection.execute('SELECT name,parent_category_id FROM public.categories WHERE id=%s',(cid,)).fetchone()
        if existing and existing!=(category['path'][-1],parent):raise AmazonImportError('Existing category ID differs from approved mapping')
        if not existing and execute:
            connection.execute('INSERT INTO public.categories(id,name,parent_category_id) VALUES (%s,%s,%s)',(cid,category['path'][-1],parent))
            counts['categories_created']+=1
    for item in plans:
        if item['brand_id']:
            if connection.execute('SELECT id FROM public.brands WHERE id=%s',(item['brand_id'],)).fetchone() is None:
                raise AmazonImportError('Approved Brand UUID does not exist')
        owned=connection.execute('SELECT id,product_id FROM public.product_variants WHERE lower(sku)=lower(%s)',(item['sku'],)).fetchone()
        if owned and owned!=(item['variant_id'],item['product_id']):raise AmazonImportError('SKU belongs to another Variant')
        existing=connection.execute('SELECT category_id,brand_id,name,description,status FROM public.products WHERE id=%s',(item['product_id'],)).fetchone()
        expected=(item['category_id'],item['brand_id'],item['name'],item['description'],item['status'])
        if existing:
            if existing!=expected:raise AmazonImportError('Existing Product differs from approved seed')
            variant=connection.execute('SELECT id,sku,option_combination,selling_price,technical_specifications FROM public.product_variants WHERE product_id=%s',(item['product_id'],)).fetchall()
            if len(variant)!=1 or variant[0]!=(item['variant_id'],item['sku'],item['options'],item['price'],item['specifications']):
                raise AmazonImportError('Existing Variant differs from approved seed')
            counts['unchanged_products']+=1
        elif execute:
            if owned:raise AmazonImportError('Orphaned approved SKU collision')
            connection.execute('INSERT INTO public.products(id,category_id,brand_id,name,description,status) VALUES (%s,%s,%s,%s,%s,%s)',
                               (item['product_id'],*expected))
            connection.execute('INSERT INTO public.product_variants(id,product_id,sku,option_combination,selling_price,technical_specifications) VALUES (%s,%s,%s,%s,%s,%s)',
                               (item['variant_id'],item['product_id'],item['sku'],Jsonb(item['options']),item['price'],Jsonb(item['specifications'])))
            counts['products_created']+=1;counts['variants_created']+=1
    return counts

def authorization(record_path,target,manifest_sha,backup_path,stage=None):
    record=json.loads(record_path.read_text(encoding='utf-8'))
    required={'decision':'APPROVED','target':target,'manifest_sha256':manifest_sha,'restore_tested':True}
    if stage is not None:required['stage']=stage
    if any(record.get(k)!=v for k,v in required.items()) or not record.get('approved_by') or not record.get('evidence_reference'):
        raise AmazonImportError('Explicit target/import authorization record is missing or mismatched')
    if not backup_path or not backup_path.is_file():
        raise AmazonImportError('Verified custom-format PostgreSQL backup required')
    with backup_path.open('rb') as stream:header=stream.read(5)
    if header!=b'PGDMP':
        raise AmazonImportError('Verified custom-format PostgreSQL backup required')
    if hashlib.sha256(backup_path.read_bytes()).hexdigest()!=record.get('backup_sha256'):
        raise AmazonImportError('Backup checksum differs from authorization record')

def validate_existing_scope(connection,full_plans,categories):
    """Reject unrelated catalog rows before a staged insert in the same transaction."""
    product_ids={p['product_id'] for p in full_plans}
    variant_ids={p['variant_id'] for p in full_plans}
    category_ids={uuid.UUID(row['category_uuid']) for row in categories.values()}
    products={row[0] for row in connection.execute('SELECT id FROM public.products')}
    variants={row[0] for row in connection.execute('SELECT id FROM public.product_variants')}
    existing_categories={row[0] for row in connection.execute('SELECT id FROM public.categories')}
    if not products<=product_ids or not variants<=variant_ids or not existing_categories<=category_ids:
        raise AmazonImportError('Unexpected existing catalog identity')
    if len(products)!=len(variants):raise AmazonImportError('Partial Product/Variant count mismatch')
    for table in ('product_images','product_image_uploads','catalog_source_links'):
        if connection.execute('SELECT count(*) FROM public.'+table).fetchone()[0]:
            raise AmazonImportError('Existing media or source links require separate review')
    checked=check_or_apply(connection,full_plans,categories,False)
    if checked['unchanged_products']!=len(products):
        raise AmazonImportError('Existing Product set differs from approved seed')
    if products and existing_categories!=category_ids:
        raise AmazonImportError('Existing category set differs from approved seed')
    return len(products)

def replace_legacy(connection,plans,categories,inventory,cleanup):
    """Replace only the inspected legacy identity set in the current transaction."""
    if len(plans)!=1500 or len({p['product_id'] for p in plans})!=1500 or len({p['variant_id'] for p in plans})!=1500:
        raise AmazonImportError('Approved replacement must contain 1500 unique Products and Variants')
    connection.execute('SELECT pg_advisory_xact_lock(%s)',(2026103102,))
    for table in ('categories','brands','products','product_variants','product_images','product_image_uploads','catalog_source_links'):
        connection.execute('LOCK TABLE public.'+table+' IN SHARE ROW EXCLUSIVE MODE')
    expected_fks=set(inventory['actual_target_fks'])
    fk_rows=connection.execute("""SELECT conrelid::regclass::text, confrelid::regclass::text,
        pg_get_constraintdef(oid) FROM pg_constraint WHERE contype='f' AND
        conrelid::regclass::text IN ('categories','products','product_variants','product_images','product_image_uploads','catalog_source_links')""").fetchall()
    actual_fks={(table,parent,definition.replace(' ','')) for table,parent,definition in fk_rows}
    required={('categories','categories','FOREIGNKEY(parent_category_id)REFERENCEScategories(id)'),
        ('products','categories','FOREIGNKEY(category_id)REFERENCEScategories(id)'),
        ('products','brands','FOREIGNKEY(brand_id)REFERENCESbrands(id)'),
        ('product_variants','products','FOREIGNKEY(product_id)REFERENCESproducts(id)'),
        ('product_images','products','FOREIGNKEY(product_id)REFERENCESproducts(id)'),
        ('product_image_uploads','products','FOREIGNKEY(product_id)REFERENCESproducts(id)'),
        ('product_image_uploads','product_images','FOREIGNKEY(product_id,product_image_id)REFERENCESproduct_images(product_id,id)'),
        ('catalog_source_links','product_variants','FOREIGNKEY(product_variant_id)REFERENCESproduct_variants(id)')}
    if len(expected_fks)!=7 or actual_fks!=required:raise AmazonImportError('Foreign-key dependency set changed')
    inbound=connection.execute("""SELECT conrelid::regclass::text, confrelid::regclass::text
        FROM pg_constraint WHERE contype='f' AND confrelid IN
        ('public.products'::regclass,'public.product_variants'::regclass,
         'public.product_images'::regclass,'public.product_image_uploads'::regclass,
         'public.catalog_source_links'::regclass)""").fetchall()
    allowed={('product_variants','products'),('product_images','products'),
        ('product_image_uploads','products'),('product_image_uploads','product_images'),
        ('catalog_source_links','product_variants')}
    if set(inbound)!=allowed or len(inbound)!=5:
        raise AmazonImportError('Unexpected inbound catalog foreign-key dependency')
    def rows(table,columns):
        result=connection.execute('SELECT '+','.join(columns)+' FROM public.'+table).fetchall()
        return [{key:str(value) if isinstance(value,uuid.UUID) else value for key,value in zip(columns,row)} for row in result]
    checks=(('products','confirmed_legacy_product_rows',('id','category_id','brand_id','status')),
            ('product_variants','confirmed_legacy_variant_rows',('id','product_id','sku')),
            ('product_images','confirmed_legacy_image_rows',('id','product_id','object_key')),
            ('product_image_uploads','confirmed_legacy_image_upload_rows',('id','product_id','product_image_id','state')),
            ('catalog_source_links','confirmed_legacy_source_link_rows',('id','product_variant_id','source','source_key')))
    current_products=rows('products',checks[0][2]);new_ids={str(p['product_id']) for p in plans}
    if {r['id'] for r in current_products}==new_ids:
        if set(map(canonical,rows('categories',('id','name','parent_category_id')))) != set(map(canonical,[{'id':r['id'],'name':r['name'],'parent_category_id':r['parent_id']} for r in inventory['existing_category_rows']]+[{'id':r['category_uuid'],'name':r['path'][-1],'parent_category_id':r['parent_uuid']} for r in categories.values()])):
            raise AmazonImportError('Existing MVP category hierarchy differs')
        result=check_or_apply(connection,plans,categories,False)
        if result['unchanged_products']!=1500 or len(rows('product_variants',('id',)))!=1500:
            raise AmazonImportError('Existing MVP seed differs')
        return {'result':'NO_OP','deletions':{},'reconciliation':result}
    if {r['id'] for r in current_products}!={r['id'] for r in inventory['confirmed_legacy_product_rows']}:
        raise AmazonImportError('Live Product identity set differs from reviewed legacy inventory')
    for table,key,columns in checks:
        if set(map(canonical,rows(table,columns)))!=set(map(canonical,inventory[key])):
            raise AmazonImportError('Live '+table+' rows differ from reviewed legacy inventory')
    if set(map(canonical,rows('categories',('id','name','parent_category_id'))))!=set(map(canonical,[{'id':r['id'],'name':r['name'],'parent_category_id':r['parent_id']} for r in inventory['existing_category_rows']])):
        raise AmazonImportError('Original Category set changed')
    brand_ids={r['id'] for r in rows('brands',('id',))}
    if len(brand_ids)!=inventory['database_counts']['brands']:raise AmazonImportError('Brand set changed')
    if new_ids & {r['id'] for r in current_products}:raise AmazonImportError('MVP Product UUID collides with legacy')
    for table,foreign in (('product_variants','product_id'),('product_images','product_id'),('product_image_uploads','product_id')):
        if any(r[foreign] not in {p['id'] for p in current_products} for r in rows(table,('id',foreign))):
            raise AmazonImportError('Unexpected dependent row in '+table)
    if any(r['product_variant_id'] not in {v['id'] for v in inventory['confirmed_legacy_variant_rows']} for r in rows('catalog_source_links',('id','product_variant_id'))):
        raise AmazonImportError('Unexpected source link')
    deletion={}
    for table,key in (('product_image_uploads','image_upload_ids'),('product_images','image_ids'),('catalog_source_links','source_link_ids'),('product_variants','variant_ids'),('products','product_ids')):
        ids=cleanup[key]
        if len(ids)!=len(set(ids)):raise AmazonImportError('Duplicate reviewed deletion UUID')
        deleted=connection.execute('DELETE FROM public.'+table+' WHERE id = ANY(%s::uuid[])',(ids,)).rowcount
        if deleted!=len(ids):raise AmazonImportError('Deletion count mismatch for '+table)
        deletion[table]=deleted
    result=check_or_apply(connection,plans,categories,True)
    if result['products_created']!=1500 or result['variants_created']!=1500 or result['categories_created']!=10:
        raise AmazonImportError('Insertion count mismatch')
    if {r['id'] for r in rows('products',('id',))}!=new_ids or {r['id'] for r in rows('product_variants',('id',))}!={str(p['variant_id']) for p in plans}:
        raise AmazonImportError('Post-insert identity set mismatch')
    if any(rows(table,('id',)) for table in ('product_images','product_image_uploads','catalog_source_links')):
        raise AmazonImportError('Legacy dependent records remain')
    if {r['id'] for r in rows('brands',('id',))}!=brand_ids:raise AmazonImportError('Brand set changed during replacement')
    if not all(r['status']=='DRAFT' for r in rows('products',('status',))):raise AmazonImportError('Product publication state mismatch')
    return {'result':'COMMIT_READY','deletions':deletion,'reconciliation':result}

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--seed',type=Path,required=True);parser.add_argument('--approvals',type=Path,required=True)
    parser.add_argument('--categories',type=Path,required=True);parser.add_argument('--report',type=Path)
    parser.add_argument('--stage',type=int,choices=(100,1000,1500),default=1500)
    action=parser.add_mutually_exclusive_group();action.add_argument('--check-db',action='store_true');action.add_argument('--execute',action='store_true')
    for name in ('target-kind','expect-database','expect-host','confirm-target'):parser.add_argument('--'+name)
    parser.add_argument('--expect-port',type=int);parser.add_argument('--dsn-env',default='CATALOG_IMPORT_DSN')
    parser.add_argument('--authorization-record',type=Path);parser.add_argument('--backup',type=Path)
    parser.add_argument('--replace-legacy',action='store_true')
    parser.add_argument('--legacy-inventory',type=Path)
    parser.add_argument('--cleanup-preview',type=Path)
    args=parser.parse_args()
    try:
        report,plans,categories=plan(args.seed,args.approvals,args.categories,args.stage,args.replace_legacy)
        report['mode']='execute' if args.execute else 'check-db' if args.check_db else 'dry-run'
        report['database_checked']=False
        if args.execute and report['pending']:raise AmazonImportError('Pending or invalid approvals; no database write attempted')
        if args.execute and not args.replace_legacy:
            if not args.authorization_record:raise AmazonImportError('Explicit import authorization record required')
            authorization(args.authorization_record,args.confirm_target,report['manifest_sha256'],args.backup,args.stage)
        if args.check_db or args.execute:
            conn=legacy.target_connection(args,read_only=not args.execute)
            try:
                report['database_checked']=True
                if args.replace_legacy:
                    if args.stage!=1500 or args.confirm_target!='backend@127.0.0.1:55432' or not args.legacy_inventory or not args.cleanup_preview:
                        raise AmazonImportError('Full replacement requires exact approved target and legacy reports')
                    inventory=json.loads(args.legacy_inventory.read_text(encoding='utf-8'))
                    cleanup=json.loads(args.cleanup_preview.read_text(encoding='utf-8'))
                    if inventory['target']['compose_project']!='catalog-pilot-20261029-01' or cleanup['target']['compose_project']!='catalog-pilot-20261029-01':
                        raise AmazonImportError('Legacy report target mismatch')
                    report['replacement']=replace_legacy(conn,plans,categories,inventory,cleanup) if args.execute else {'check':'execute required for locked revalidation'}
                    report['backup_restore']='WAIVED_BY_PROJECT_OWNER_IN_CURRENT_TASK'
                else:
                    full_report,full_plans,full_categories=plan(args.seed,args.approvals,args.categories,1500)
                    if full_report['pending']:raise AmazonImportError('Full approved import plan required')
                    if args.execute:
                        for table in ('categories','products','product_variants','product_images','product_image_uploads','catalog_source_links'):
                            conn.execute('LOCK TABLE public.'+table+' IN SHARE ROW EXCLUSIVE MODE')
                    validate_existing_scope(conn,full_plans,full_categories)
                    report['reconciliation']=check_or_apply(conn,plans,categories,args.execute) if plans else {'eligible':0}
                if args.execute:conn.commit()
                else:conn.rollback()
            except BaseException:
                conn.rollback();raise
            finally:conn.close()
        if args.report:args.report.write_text(json.dumps(report,ensure_ascii=False,indent=2,default=str)+'\n',encoding='utf-8')
        print(json.dumps({'mode':report['mode'],'seed_count':report['seed_count'],'eligible':report['eligible'],
                          'pending':len(report['pending']),'database_checked':report['database_checked']},ensure_ascii=False))
        return 0
    except (AmazonImportError,legacy.ImportErrorDetail,OSError,ValueError,KeyError,TypeError) as failure:
        print(json.dumps({'error':str(failure),'database_password_printed':False}),file=sys.stderr);return 2

if __name__=='__main__':sys.exit(main())
