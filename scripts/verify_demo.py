#!/usr/bin/env python3
"""Read-only PostgreSQL/S3/API reconciliation for an approved demo environment."""
import argparse
import hashlib
import json
from pathlib import Path
import sys
from urllib.parse import urlsplit

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools/catalog-import'))
import import_postgres as importer

def run(args):
    catalog=Path(args.catalog)
    manifest=json.loads((catalog/'manifests/catalog_manifest.json').read_text(encoding='utf-8'))
    with (catalog/'data/products.jsonl').open(encoding='utf-8') as stream:products=[json.loads(x) for x in stream]
    with (catalog/'data/specifications.jsonl').open(encoding='utf-8') as stream:specs=[json.loads(x) for x in stream]
    with (catalog/'data/variants.jsonl').open(encoding='utf-8') as stream:variant_seed=[json.loads(x) for x in stream]
    category_mapping=json.loads((catalog/'data/category_mapping.json').read_text(encoding='utf-8'))
    mapped={x['source_category']:x for x in category_mapping['categories']}
    image_manifest=(json.loads((catalog/'manifests/image_manifest.json').read_text(encoding='utf-8'))
                    if not args.data_only else None)
    expected={x['product']['id_proposal']:x for x in products}
    issues=[];report={'expected_products':len(products),'database_checked':False,'s3_checked':False,'api_checked':False,'issues':issues}
    connection=importer.target_connection(args,read_only=True)
    try:
        rows=connection.execute('SELECT id,category_id,name,status FROM public.products').fetchall()
        variants=connection.execute('SELECT id,product_id,sku,option_combination,selling_price,technical_specifications FROM public.product_variants').fetchall()
        images=connection.execute('SELECT id,product_id,object_key,is_primary FROM public.product_images').fetchall()
        categories={str(i):(name,str(parent) if parent else None) for i,name,parent in connection.execute('SELECT id,name,parent_category_id FROM public.categories')}
        if args.data_only:
            for table in ('product_image_uploads','catalog_source_links'):
                if connection.execute('SELECT count(*) FROM public.'+table).fetchone()[0]:
                    issues.append('UNEXPECTED_'+table.upper())
        report.update(database_checked=True,actual_products=len(rows),actual_variants=len(variants),actual_image_records=len(images),
                      published_products=sum(status=='PUBLISHED' for _,_,_,status in rows))
        report['actual_categories']=len(categories)
        report['draft_products']=sum(status=='DRAFT' for _,_,_,status in rows)
        report['variants_with_specifications']=sum(bool(doc.get('values')) for *_,doc in variants if isinstance(doc,dict))
        if len(categories)!=10 or set(categories)!={x['category_uuid'] for x in category_mapping['categories']}:
            issues.append('CATEGORY_COUNT_OR_ID_MISMATCH')
        for item in category_mapping['categories']:
            if categories.get(item['category_uuid'])!=(item['path'][-1],item.get('parent_uuid')):
                issues.append('CATEGORY_HIERARCHY_MISMATCH')
        if args.data_only and (report['draft_products']!=1500 or report['variants_with_specifications']!=428):
            issues.append('DRAFT_OR_SPECIFICATION_COUNT_MISMATCH')
        if len(rows)!=1500 or {str(i) for i,_,_,_ in rows}!=set(expected):issues.append('PRODUCT_COUNT_OR_ID_MISMATCH')
        for product_id,category_id,name,status in rows:
            source=expected.get(str(product_id))
            if not source:continue
            mapping=mapped.get(source['product']['category_label'])
            if not mapping or str(category_id)!=mapping.get('category_uuid') or name!=source['product']['name']:
                issues.append('PRODUCT_CATEGORY_OR_NAME_MISMATCH')
            if str(category_id) not in categories or categories[str(category_id)][0]!=mapping['path'][-1]:
                issues.append('CATEGORY_REFERENCE_MISMATCH')
        if len(variants)!=1500 or len({str(p) for _,p,_,_,_,_ in variants})!=1500:issues.append('VARIANT_COUNT_OR_OWNERSHIP_MISMATCH')
        if len({sku.casefold() for _,_,sku,_,_,_ in variants})!=len(variants):issues.append('DUPLICATE_SKU')
        expected_variants={x['variant']['id_proposal']:(x['parent_asin'],x['variant']) for x in variant_seed}
        expected_specs={x['parent_asin']:x['technical_specifications'] for x in specs}
        for variant_id,product_id,sku,options,price,doc in variants:
            if str(product_id) not in expected:issues.append('ORPHAN_VARIANT');continue
            source_variant=expected_variants.get(str(variant_id))
            if (not source_variant or sku!=source_variant[1]['sku_proposal']
                    or str(product_id)!=source_variant[1]['product_id_proposal']
                    or doc!=expected_specs.get(source_variant[0])):
                issues.append('VARIANT_SEED_MISMATCH')
            if options!={} or price<=0 or price!=price.to_integral_value():issues.append('INVALID_VARIANT_OPTIONS_OR_PRICE')
            if not isinstance(doc,dict) or set(doc)!={'schema_version','values'} or doc.get('schema_version')!=1 or not isinstance(doc.get('values'),dict):issues.append('INVALID_SPEC_DOCUMENT')
        image_by_product={}
        for image_id,product_id,key,primary in images:
            image_by_product.setdefault(str(product_id),[]).append((str(image_id),key,primary))
        if not args.data_only and len(images)!=manifest.get('image_count'):issues.append('IMAGE_METADATA_COUNT_MISMATCH')
        if args.data_only and images:issues.append('UNEXPECTED_PRODUCT_IMAGES')
        for product_id,items in image_by_product.items():
            if product_id not in expected or sum(bool(x[2]) for x in items)!=1:issues.append('IMAGE_GALLERY_INVARIANT')
        connection.rollback()
    finally:connection.close()
    if args.check_s3:
        import boto3
        from botocore.config import Config
        endpoint=urlsplit(args.s3_endpoint or '')
        if endpoint.scheme!='http' or endpoint.hostname not in ('127.0.0.1','localhost') or not endpoint.port:raise ValueError('LOCAL_S3_ENDPOINT_REQUIRED')
        client=boto3.client('s3',endpoint_url=args.s3_endpoint,region_name='us-east-1',aws_access_key_id='test',aws_secret_access_key='test',
                            config=Config(connect_timeout=5,read_timeout=15))
        object_checks=0
        for entry in image_manifest['images']:
            if not entry.get('asset_path'):issues.append('IMAGE_ASSET_MISSING:'+entry['parent_asin']);continue
            owned=image_by_product.get(entry['product_id_proposal'],[])
            if len(owned)!=1:issues.append('PRODUCT_IMAGE_MAPPING_MISSING:'+entry['parent_asin']);continue
            body=client.get_object(Bucket=args.bucket,Key=owned[0][1])['Body'].read(10485761)
            if hashlib.sha256(body).hexdigest()!=entry['validation']['sha256']:
                issues.append('S3_IMAGE_HASH_MISMATCH:'+entry['parent_asin'])
            object_checks+=1
        report.update(s3_checked=True,s3_objects_checked=object_checks)
    if args.check_api:
        import requests
        base=urlsplit(args.api_base or '')
        if base.scheme!='http' or base.hostname not in ('127.0.0.1','localhost') or not base.port:raise ValueError('LOCAL_API_BASE_REQUIRED')
        response=requests.get(args.api_base+'/api/products',params={'page':0,'size':1},timeout=10)
        if response.status_code!=200:issues.append('PUBLIC_PRODUCT_LIST_FAILED')
        else:
            page=response.json()
            if page.get('totalItems')!=report['published_products']:issues.append('PUBLIC_PRODUCT_COUNT_MISMATCH')
        sampled=next((x for x in rows if x[3]=='PUBLISHED'),None),next((x for x in rows if x[3]!='PUBLISHED'),None)
        for row in sampled:
            if row is None:continue
            product_id,_,_,status=row
            detail=requests.get(args.api_base+'/api/products/'+str(product_id),timeout=10)
            if status=='PUBLISHED' and detail.status_code!=200:issues.append('PUBLISHED_DETAIL_UNAVAILABLE')
            if status!='PUBLISHED' and detail.status_code!=404:issues.append('DRAFT_PUBLICLY_VISIBLE')
        report['api_checked']=True
        report['public_visibility']='PUBLISHED_ONLY' if report['published_products'] else 'DRAFT_NOT_PUBLIC'
    report['issues']=sorted(set(issues));report['verified']=not issues and report['database_checked'] and (args.data_only or report['s3_checked']) and (not args.check_api or report['api_checked'])
    report['state']='DATA_READY_MEDIA_PENDING' if args.data_only and report['verified'] else ('FULLY_READY' if report['verified'] else 'FAILED_VERIFICATION')
    report['state_digest']=hashlib.sha256(json.dumps({'products':sorted(str(x[0]) for x in rows),
       'variants':sorted(str(x[0]) for x in variants),'images':sorted(str(x[0]) for x in images)},sort_keys=True).encode()).hexdigest()
    if args.compare_snapshot:
        previous=json.loads(args.compare_snapshot.read_text(encoding='utf-8'))
        if previous.get('state_digest')!=report['state_digest']:report['issues'].append('PERSISTENCE_STATE_CHANGED');report['verified']=False
    if args.snapshot:args.snapshot.write_text(json.dumps({'state_digest':report['state_digest']},indent=2)+'\n',encoding='utf-8')
    return report

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--catalog',type=Path,required=True)
    p.add_argument('--target-kind',choices=('disposable','local-development'),required=True)
    for name in ('expect-database','expect-host','confirm-target'):p.add_argument('--'+name,required=True)
    p.add_argument('--expect-port',type=int,required=True);p.add_argument('--dsn-env',default='CATALOG_IMPORT_DSN')
    p.add_argument('--check-s3',action='store_true');p.add_argument('--s3-endpoint');p.add_argument('--bucket',default='backend-images')
    p.add_argument('--data-only',action='store_true')
    p.add_argument('--check-api',action='store_true');p.add_argument('--api-base')
    p.add_argument('--snapshot',type=Path);p.add_argument('--compare-snapshot',type=Path)
    args=p.parse_args()
    try:
        report=run(args);print(json.dumps(report,ensure_ascii=False,indent=2));sys.exit(0 if report['verified'] else 2)
    except (OSError,ValueError,KeyError,importer.ImportErrorDetail) as failure:
        print(json.dumps({'error':str(failure),'secrets_printed':False}),file=sys.stderr);sys.exit(2)
