import csv
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
import uuid

import amazon_import as module

class AmazonAdapterTests(unittest.TestCase):
    def fixture(self,root):
        for name in ('data','data/approvals','manifests'):(root/name).mkdir(parents=True,exist_ok=True)
        product_id=str(uuid.uuid5(uuid.NAMESPACE_DNS,'amazon-demo'))
        variant_id=str(uuid.uuid5(uuid.UUID(product_id),'variant|single'))
        asin='B012345678';sku='DEMO-AMZ-'+asin
        product={'parent_asin':asin,'disposition':'BLOCKED','product':{'id_proposal':product_id,'name':'Test Laptop','description':None,
                 'category_label':'Laptop','category_path_proposal':['Electronics','Laptop'],'status':'DRAFT'}}
        spec={'parent_asin':asin,'technical_specifications':{'schema_version':1,'values':{'physical_ram':{'value':8,'unit':'GB'}}}}
        variant={'parent_asin':asin,'variant':{'id_proposal':variant_id,'product_id_proposal':product_id,
                 'sku_proposal':sku,'approved_selling_price_vnd':None,'option_combination_proposal':{}}}
        payload={'data/products.jsonl':product,'data/specifications.jsonl':spec,'data/variants.jsonl':variant}
        hashes={}
        for name,value in payload.items():
            path=root/name;path.write_text(module.canonical(value)+'\n',encoding='utf-8');hashes[name]=hashlib.sha256(path.read_bytes()).hexdigest()
        (root/'manifests/catalog_manifest.json').write_text(json.dumps({'version':'amazon-mvp1500-seed-1','product_count':1,
             'file_sha256':hashes,'data_approval_status':'PENDING_APPROVAL'}),encoding='utf-8')
        category_id=str(uuid.uuid5(uuid.NAMESPACE_DNS,'Laptop category'))
        mapping={'version':1,'categories':[{'source_category':'Laptop','path':['Electronics','Laptop'],
                 'category_uuid':category_id,'parent_uuid':None,'approval':'APPROVED'}]}
        (root/'data/category_mapping.json').write_text(json.dumps(mapping),encoding='utf-8')
        row={key:'' for key in module.FIELDS};row.update(parent_asin=asin,approved_category_uuid=category_id,approved_sku=sku,
             approved_selling_price_vnd='5000000',category_approval='APPROVED',sku_policy_approval='APPROVED',
             variant_approval='APPROVED',price_approval='APPROVED',import_approval='APPROVED',publication_approval='PENDING_APPROVAL')
        approvals=root/'data/approvals/template.csv'
        with approvals.open('w',newline='') as f:
            writer=csv.DictWriter(f,fieldnames=module.FIELDS);writer.writeheader();writer.writerow(row)
        return approvals,root/'data/category_mapping.json'

    def test_pending_manifest_blocks_even_with_approved_rows(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);approvals,categories=self.fixture(root)
            report,plans,_=module.plan(root,approvals,categories)
            self.assertEqual(report['eligible'],0)
            self.assertIn('DATA_MANIFEST_NOT_APPROVED',report['pending'][0]['reasons'])
            manifest=root/'manifests/catalog_manifest.json';data=json.loads(manifest.read_text());data['data_approval_status']='APPROVED';manifest.write_text(json.dumps(data))
            report,plans,_=module.plan(root,approvals,categories)
            self.assertEqual(report['eligible'],1)
            self.assertEqual(plans[0]['status'],'DRAFT')

    def test_checksum_and_specification_guard(self):
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp);self.fixture(root)
            with (root/'data/specifications.jsonl').open('a') as f:f.write('tampered')
            with self.assertRaisesRegex(module.AmazonImportError,'checksum'):
                module.load_seed(root)
        with self.assertRaisesRegex(module.AmazonImportError,'specification'):
            module.validate_specs({'schema_version':1,'values':{'invented':'x'}},'Laptop')

    def test_price_and_approval_contract(self):
        self.assertIsNone(module.legacy.valid_price('12.5'))
        self.assertIsNotNone(module.legacy.valid_price('5000000'))
        self.assertEqual(len(module.FIELDS),12)

if __name__=='__main__':unittest.main()
