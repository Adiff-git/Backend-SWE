import json
from pathlib import Path
import tempfile
import unittest
from types import SimpleNamespace
import amazon_image_import as module

class AmazonImageAdapterTests(unittest.TestCase):
    def test_generated_fixture_package_is_ready_offline(self):
        catalog=Path(__file__).resolve().parents[3]/'Catalog-Data'
        if not catalog.exists():self.skipTest('Sibling Catalog-Data unavailable')
        seed,digest,ready,blocked=module.prepare(catalog)
        self.assertEqual(len(ready),1500)
        self.assertEqual(len(blocked),0)
        self.assertEqual(seed['image_rights_status'],'GENERATED_FIXTURES_ONLY')
        module.permission(None,seed,digest,'backend@127.0.0.1:55432',ready,fixture_mode=True)

    def test_permission_requires_actual_approval(self):
        with tempfile.TemporaryDirectory() as tmp:
            path=Path(tmp)/'permission.json'
            path.write_text(json.dumps({'decision':'PENDING_APPROVAL'}))
            with self.assertRaisesRegex(ValueError,'PERMISSION'):
                module.permission(path,{'image_rights_status':'PENDING_APPROVAL'},'0'*64,'backend@127.0.0.1:55432',[])

if __name__=='__main__':unittest.main()
