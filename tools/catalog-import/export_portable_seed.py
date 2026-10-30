#!/usr/bin/env python3
"""Export only Catalog fields from a verified read-only local target."""
import argparse,hashlib,json
from pathlib import Path
import import_postgres as importer
from portable_seed import VERSION,load_seed

def main():
 p=argparse.ArgumentParser(description=__doc__)
 p.add_argument('--output',type=Path,required=True);p.add_argument('--approvals',type=Path,nargs='+',required=True)
 p.add_argument('--target-kind',choices=('local-development',),required=True)
 for name in ('expect-database','expect-host','confirm-target'): p.add_argument('--'+name,required=True)
 p.add_argument('--expect-port',type=int,required=True);p.add_argument('--dsn-env',default='CATALOG_IMPORT_DSN')
 a=p.parse_args(); provenance={}
 for path in a.approvals:
  for row in importer.read_manifest(path):
   if row['import_approval']=='APPROVED':
    pid=row['proposed_product_uuid']
    if pid in provenance: raise ValueError('Duplicate approved product')
    provenance[pid]=row
 db=importer.target_connection(a,True)
 try:
  categories=[dict(zip(('id','name','parent_id'),r)) for r in db.execute('SELECT id,name,parent_category_id FROM categories ORDER BY id')]
  products=[]
  for r in db.execute('SELECT p.id,v.id,p.category_id,p.name,p.description,p.status,v.sku,v.selling_price,v.option_combination FROM products p JOIN product_variants v ON v.product_id=p.id ORDER BY p.id'):
   pid,vid,cid,name,description,status,sku,price,options=r;approved=provenance[str(pid)]
   if (name,description,sku,str(price),status)!=(approved['product_name'],approved['description'],approved['proposed_catalog_sku'],approved['approved_selling_price_vnd'],'PUBLISHED' if approved['publication_approval']=='APPROVED' else 'DRAFT'): raise ValueError('Approval differs from database')
   products.append(dict(identity=approved['source_retailer']+'|'+approved['source_url'],product_id=pid,variant_id=vid,category_id=cid,name=name,description=description,status=status,sku=sku,price=str(price),options=options))
  if db.execute('SELECT count(*) FROM product_images').fetchone()[0]!=0: raise ValueError('Unexpected images')
  db.rollback()
 finally: db.close()
 a.output.mkdir(parents=True,exist_ok=False)
 raw=(json.dumps(dict(categories=categories,products=products),ensure_ascii=False,sort_keys=True,indent=2,default=str)+'\n').encode()
 (a.output/'catalog.json').write_bytes(raw)
 manifest=dict(version=VERSION,sha256=hashlib.sha256(raw).hexdigest(),counts=dict(products=1000,variants=1000,categories=31,published=5,draft=995),migrations={n:importer.flyway_checksum(importer.MIGRATIONS/n) for n in importer.MIGRATION_FILES},policy='LOCAL DEVELOPMENT DEMO ONLY; historical prices; no images')
 (a.output/'manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
 load_seed(a.output)
 (a.output/'SHA256SUMS').write_text(''.join(hashlib.sha256((a.output/name).read_bytes()).hexdigest()+'  '+name+'\n' for name in ('catalog.json','manifest.json')))
 print(json.dumps(manifest['counts']))
if __name__=='__main__':main()
