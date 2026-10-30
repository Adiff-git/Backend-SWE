import json,unittest
import prepare_product_images as images
import import_postgres as imp

class ImageInventoryTest(unittest.TestCase):
 def fixture(self,urls):
  source='cellphones';url='https://cellphones.com.vn/example.html'
  product=dict(identity=source+'|'+url,product_id=imp.normalize.proposed_uuid(source,url))
  row=('source-id',source,url,json.dumps(dict(id='source-id',source=source,source_url=url,images=urls)))
  return product,row
 def test_exact_identity_and_order(self):
  p,r=self.fixture(['https://cdn2.cellphones.com.vn/b.png','https://cdn2.cellphones.com.vn/a.png'])
  result=images.image_record(p,r)
  self.assertEqual(result['product_id'],p['product_id']);self.assertEqual(result['images'][0]['source_index'],0)
  self.assertTrue(result['images'][0]['source_url'].endswith('b.png'));self.assertEqual(result['permission'],'NOT_ESTABLISHED')
 def test_missing(self):
  p,r=self.fixture([]);self.assertEqual(images.image_record(p,r)['images'],[])
 def test_duplicates(self):
  p,r=self.fixture(['https://cdn2.cellphones.com.vn/a.png']*2)
  self.assertEqual(images.image_record(p,r)['images'][1]['issue'],'DUPLICATE_SOURCE_URL')
 def test_identity_mismatch(self):
  p,r=self.fixture([])
  with self.assertRaises(ValueError):images.image_record(p,('wrong',*r[1:]))
 def test_uuid_mismatch(self):
  p,r=self.fixture([]);p['product_id']='00000000-0000-0000-0000-000000000000'
  with self.assertRaises(ValueError):images.image_record(p,r)
 def test_unsafe_urls(self):
  for url in ['http://cdn2.cellphones.com.vn/a','https://127.0.0.1/a','https://cdn2.cellphones.com.vn.evil.test/a','https://user:pass@cdn2.cellphones.com.vn/a','https://cdn2.cellphones.com.vn:8443/a']:
   self.assertIsNotNone(images.url_issue(url,'cellphones'))
 def test_invalid_source_image_value(self):
  p,r=self.fixture([None]);self.assertEqual(images.image_record(p,r)['images'][0]['issue'],'INVALID_URL')
if __name__=='__main__':unittest.main(verbosity=2)
