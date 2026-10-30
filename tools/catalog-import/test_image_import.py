import io,json,os,tempfile,unittest
from datetime import datetime,timedelta,timezone
from pathlib import Path
from unittest.mock import patch
from PIL import Image
import import_product_images as images

class ImageImporterTest(unittest.TestCase):
 def png(self):
  b=io.BytesIO();Image.new('RGB',(16,12),'blue').save(b,format='PNG');return b.getvalue()
 def test_actual_bytes(self):
  m=images.validate_bytes(self.png());self.assertEqual((m['content_type'],m['width'],m['height']),('image/png',16,12));self.assertEqual(len(m['sha256']),64)
 def test_invalid_bytes(self):
  for data in [b'',b'not an image',b'x'*(images.MAX_BYTES+1)]:
   with self.assertRaises((ValueError,OSError)):images.validate_bytes(data)
 def test_permission_required_before_network(self):
  with self.assertRaises(ValueError):images.authorize(dict(source_identity='cellphones|https://cellphones.com.vn/a'),{})
 def test_permission_scope_and_expiry(self):
  r=dict(source_identity='cellphones|https://cellphones.com.vn/a')
  p=dict(decision='APPROVED',scope='local-development',approved_by='owner',evidence_reference='permission record',usage=['download','store-private-local'],source_identities=[r['source_identity']],expires_at=(datetime.now(timezone.utc)+timedelta(days=1)).isoformat())
  images.authorize(r,p)
  p['source_identities']=[]
  with self.assertRaises(ValueError):images.authorize(r,p)
 def test_ssrf_private_dns(self):
  with patch('socket.getaddrinfo',return_value=[(2,1,6,'',('127.0.0.1',443))]):
   with self.assertRaisesRegex(ValueError,'NON_PUBLIC'):images.safe_download('https://cdn2.cellphones.com.vn/a',{'cdn2.cellphones.com.vn'})
 def test_ssrf_url(self):
  for url in ['http://cdn2.cellphones.com.vn/a','https://user:pass@cdn2.cellphones.com.vn/a','https://evil.example/a']:
   with self.assertRaisesRegex(ValueError,'UNSAFE'):images.safe_download(url,{'cdn2.cellphones.com.vn'})
 def test_redirect_cannot_escape_authorized_hosts(self):
  class Response:
   status=302
   def getheader(self,name,default=''):return 'https://127.0.0.1/private'
  class Connection:
   def __init__(self,*a,**k):pass
   def request(self,*a,**k):pass
   def getresponse(self):return Response()
   def close(self):pass
  with patch('socket.getaddrinfo',return_value=[(2,1,6,'',('93.184.216.34',443))]),patch('http.client.HTTPSConnection',Connection):
   with self.assertRaisesRegex(ValueError,'UNSAFE'):images.safe_download('https://cdn2.cellphones.com.vn/a',{'cdn2.cellphones.com.vn'})
 def test_checkpoint_atomic_and_private(self):
  with tempfile.TemporaryDirectory() as d:
   p=Path(d)/'checkpoint.json';images.save(p,{'target':'test'});self.assertEqual(json.loads(p.read_text()),{'target':'test'});self.assertEqual(p.stat().st_mode&0o777,0o600)
if __name__=='__main__':unittest.main(verbosity=2)
