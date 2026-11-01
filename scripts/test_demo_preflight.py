import importlib.util
from pathlib import Path
import tempfile
import unittest

SCRIPT=Path(__file__).resolve().parent/'demo_preflight.py'
spec=importlib.util.spec_from_file_location('demo_preflight',SCRIPT)
module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)

class DemoPreflightTests(unittest.TestCase):
    def test_missing_catalog_fails_before_docker(self):
        with tempfile.TemporaryDirectory() as temp:
            report=module.run(Path(temp)/'missing',Path(temp)/'.env')
            self.assertFalse(report['ready'])
            self.assertIn('CATALOG_DATA_DIRECTORY_MISSING',report['blockers'])

    def test_current_package_requires_approval_and_images(self):
        catalog=Path(__file__).resolve().parents[2]/'Catalog-Data'
        if not catalog.exists():self.skipTest('Sibling Catalog-Data unavailable')
        report=module.run(catalog,catalog/'.env.demo.local')
        self.assertFalse(report['ready'])
        self.assertIn('AUTHORIZED_IMAGE_ASSETS_INCOMPLETE',report['blockers'])
        self.assertIn('DATA_APPROVAL_MISSING',report['blockers'])

if __name__=='__main__':unittest.main()
