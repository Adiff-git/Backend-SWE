import json
import sqlite3
import tempfile
import unittest
from pathlib import Path
import enhance_normalize as n

class EnhancementNormalizationTest(unittest.TestCase):
    def setUp(self):
        self.registry=n.load_registries()
    def record(self,**updates):
        raw={'id':'id','source':'tgdd','source_url':'https://www.thegioididong.com/item','name':'Laptop','brand':'ASUS','sku':'sku','category':'Laptop','breadcrumbs':[], 'specifications':{},'offers':[{'type':'Offer','price':'100','currency':'VND'}]}
        raw.update(updates)
        return n.normalize_record(('id','tgdd','https://www.thegioididong.com/item',json.dumps(raw)),self.registry)
    def test_approved_brand_and_unknown(self):
        self.assertEqual(self.record(brand=' Asus ')['brand']['code'],'asus')
        self.assertEqual(self.record(brand='asus')['brand']['code'],'asus')
        self.assertIsNone(self.record(brand='Apple Chính hãng')['brand'])
        self.assertIsNone(self.record(brand=None)['brand'])
        self.assertEqual(self.record(brand=' ZAGG ')['brand']['code'],'zagg')
        self.assertEqual(self.record(brand='tomtoc')['brand']['code'],'tomtoc')
        self.assertIsNone(self.record(brand='Samsung Chính hãng')['brand'])
        self.assertIsNone(self.record(brand='Hãng khác')['brand'])
    def test_typed_values(self):
        r=self.record(specifications={'RAM':'8 GB','Kích thước màn hình':'15.6 inches','Hệ điều hành':' Windows   11 ','unused':'kept'})
        self.assertEqual(r['technical_specifications']['values'],{'physical_ram':{'value':8,'unit':'GB'},'display_diagonal':{'value':15.6,'unit':'inch'},'installed_os':'Windows 11'})
        self.assertEqual(r['unmapped_attributes'],{'unused':'kept'})
        self.assertIn('unused',r['raw_payload']['specifications'])
    def test_mass_unit_conversion(self):
        self.assertEqual(self.record(specifications={'Khối lượng máy':'1.5 kg'})['technical_specifications']['values']['weight'],{'value':1500,'unit':'g'})
    def test_pilot_display_storage_and_battery_units(self):
        values=self.record(specifications={'Độ phân giải màn hình':'1920 x 1080 pixels (FullHD)',
            'Ổ cứng':'512GB PCIe NVMe SSD (Nâng cấp tối đa 2TB)',
            'Pin':'3 Cell Int (41.04Wh)'})['technical_specifications']['values']
        self.assertEqual(values['display_resolution'],'1920x1080')
        self.assertEqual(values['installed_storage_gb'],{'value':512,'unit':'GB'})
        self.assertEqual(values['battery_capacity_wh'],{'value':41.04,'unit':'Wh'})
        self.assertEqual(self.record(specifications={'Pin':'42WHrs, 3S1P, 3-cell Li-ion'})['technical_specifications']['values']['battery_capacity_wh'],{'value':42,'unit':'Wh'})
        self.assertEqual(self.record(specifications={'Pin':'Pin Li-Po 53,8 watt‑giờ tích hợp'})['technical_specifications']['values']['battery_capacity_wh'],{'value':53.8,'unit':'Wh'})
        self.assertEqual(self.record(category='Phụ kiện',specifications={'Dung lượng pin':'10.000 mAh'})['technical_specifications']['values']['battery_capacity_mah'],{'value':10000,'unit':'mAh'})
    def test_ambiguous_storage_and_battery_remain_raw(self):
        for value in ['1TB','512GB + 1TB HDD','512GB/1TB']:
            self.assertIn('Ổ cứng',self.record(specifications={'Ổ cứng':value})['invalid_attributes'])
        self.assertIn('Dung lượng pin',self.record(category='Phụ kiện',specifications={'Dung lượng pin':'TX 114 mAh, RX 170 mAh'})['invalid_attributes'])
        self.assertIn('Pin',self.record(specifications={'Pin':'42Wh + 50Wh'})['invalid_attributes'])
        self.assertIn('Độ phân giải màn hình',self.record(specifications={'Độ phân giải màn hình':'FullHD'})['invalid_attributes'])
        self.assertIn('Dung lượng pin',self.record(category='Laptop',specifications={'Dung lượng pin':'10000mAh'})['unmapped_attributes'])
    def test_boolean_and_category_scope(self):
        self.assertEqual(self.record(category='Điện thoại',specifications={'Công nghệ NFC':'Không'})['technical_specifications']['values']['nfc_supported'],False)
        self.assertEqual(self.record(category='Laptop',specifications={'Công nghệ NFC':'Có'})['technical_specifications']['values'],{})
    def test_no_guessed_units_or_compound_ram(self):
        for v in ['6GB + Mở rộng 6GB','8192 MB','-1GB','NaN GB','0GB']:
            self.assertIn('RAM',self.record(specifications={'RAM':v})['invalid_attributes'])
    def test_conflicting_aliases_not_last_wins(self):
        self.assertNotIn('physical_ram',self.record(specifications={'RAM':'8GB','Dung lượng RAM':'16GB'})['technical_specifications']['values'])
    def test_price_creation_and_enrichment_separate(self):
        for offers in [[{'type':'Offer','price':'0','currency':'VND'}],[{'type':'AggregateOffer','low_price':'100','currency':'VND'}],[]]:
            r=self.record(offers=offers);self.assertFalse(r['creation_eligible']);self.assertTrue(r['enrichment_candidate'])
    def test_sku_placeholder_and_unreviewed_category(self):
        r=self.record(sku='0');self.assertIn('UNRELIABLE_SKU',r['issues']);self.assertIsNone(r['category_id'])
    def test_identity_preserved_and_hash_bound(self):
        r=self.record();self.assertEqual(r['product_id'],n.normalize.proposed_uuid(r['source'],r['source_key']))
        self.assertEqual(n.normalized_hash(r),r['normalized_payload_sha256'])
        r['variant_id']='changed';self.assertNotEqual(n.normalized_hash(r),r['normalized_payload_sha256'])
    def test_malformed_fields_and_untrusted_host(self):
        self.assertIn('INVALID_BRAND_TYPE',self.record(brand={})['issues'])
        self.assertIn('INVALID_SPECIFICATIONS',self.record(specifications='bad')['issues'])
        raw=self.record()['raw_payload'];raw['source_url']='https://evil.test/item'
        r=n.normalize_record(('id','tgdd',raw['source_url'],json.dumps(raw)),self.registry)
        self.assertIn('INVALID_SOURCE_IDENTITY',r['issues'])
        self.assertFalse(r['creation_eligible'])
        self.assertIn('RAM',self.record(specifications={'RAM':'9'*400+'GB'})['invalid_attributes'])
    def test_registry_version_rejected(self):
        with tempfile.TemporaryDirectory() as d:
            for f in n.REGISTRIES.glob('*.json'):
                x=json.loads(f.read_text());x['version']=2;(Path(d)/f.name).write_text(json.dumps(x))
            with self.assertRaises(ValueError):n.load_registries(d)
    def test_duplicate_canonical_brand_identity_rejected(self):
        with tempfile.TemporaryDirectory() as d:
            for f in n.REGISTRIES.glob('*.json'):
                (Path(d)/f.name).write_bytes(f.read_bytes())
            brand_file=Path(d)/'brands-v1.json';brands=json.loads(brand_file.read_text())
            duplicate=dict(brands['brands'][0],aliases=['Unrelated duplicate label'])
            brands['brands'].append(duplicate);brand_file.write_text(json.dumps(brands))
            with self.assertRaisesRegex(ValueError,'Duplicate canonical Brand identity'):n.load_registries(d)
    def test_full_run_readonly_deterministic_duplicate_detection(self):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d);db=p/'source.sqlite';c=sqlite3.connect(db);c.execute('CREATE TABLE products(id,site,url,payload)')
            raw=self.record()['raw_payload'];payload=json.dumps(raw)
            c.executemany('INSERT INTO products VALUES(?,?,?,?)',[('id','tgdd',raw['source_url'],payload)]*2);c.commit();c.close()
            before=db.read_bytes();a=n.run(db,p/'a');b=n.run(db,p/'b')
            self.assertEqual(before,db.read_bytes());self.assertEqual(a,b);self.assertEqual(a['counts']['total_source_records'],2);self.assertEqual(a['counts']['duplicate_identities'],1)
            self.assertEqual((p/'a/records.jsonl').read_bytes(),(p/'b/records.jsonl').read_bytes())
    def test_equal_aliases_coalesce(self):
        r=self.record(specifications={'RAM':'8GB','Ram':'8 GB'})
        self.assertEqual(r['technical_specifications']['values']['physical_ram']['value'],8)

if __name__=='__main__':unittest.main()
