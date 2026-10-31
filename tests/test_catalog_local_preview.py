"""Offline checks for local-preview validation. No real Amazon images required."""
import hashlib
import json
import tempfile
import sys
import unittest
from pathlib import Path
from PIL import Image
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from catalog_local_preview import validate_local_preview


class LocalPreviewTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        for folder in ('manifests', 'data', 'images/products'):
            (self.root / folder).mkdir(parents=True)
        self.asins = ['A000000001', 'A000000002']
        products = []
        variants = []
        specs = []
        images = []
        for i, asin in enumerate(self.asins):
            product_id = f'00000000-0000-0000-0000-00000000000{i+1}'
            products.append({'parent_asin': asin, 'product': {'id_proposal': product_id}})
            variants.append({'parent_asin': asin, 'variant': {'product_id_proposal': product_id}})
            specs.append({'parent_asin': asin})
            rel = f'images/products/{asin}.webp'
            path = self.root / rel
            Image.new('RGB', (4, 4), (i * 90, 30, 70)).save(path, 'WEBP')
            data = path.read_bytes()
            images.append({'parent_asin': asin, 'product_id_proposal': product_id,
                           'asset_path': rel, 'validation': {'sha256': hashlib.sha256(data).hexdigest()},
                           'file_size_bytes': len(data), 'width': 4, 'height': 4,
                           'rights_status': 'UNKNOWN'})
        for name, rows in [('products', products), ('variants', variants), ('specifications', specs)]:
            (self.root / f'data/{name}.jsonl').write_text(''.join(json.dumps(row)+'\n' for row in rows))
        (self.root / 'manifests/image_manifest.json').write_text(json.dumps({'images': images}))
        file_sha = {f'data/{name}.jsonl': hashlib.sha256((self.root / f'data/{name}.jsonl').read_bytes()).hexdigest()
                    for name in ('products', 'variants', 'specifications')}
        file_sha['manifests/image_manifest.json'] = hashlib.sha256((self.root / 'manifests/image_manifest.json').read_bytes()).hexdigest()
        (self.root / 'manifests/catalog_manifest.json').write_text(json.dumps({
            'image_rights_status': 'AUTHENTIC_MEDIA_LOCAL_PREVIEW',
            'data_approval_status': 'APPROVED', 'product_count': 2, 'image_count': 2,
            'file_sha256': file_sha,
        }))
        (self.root / 'manifests/checksums.sha256').write_text(
            ''.join(f'{file_sha[key]}  {key}\n' for key in sorted(file_sha)))

    def run_validation(self):
        import catalog_local_preview as check
        original = check.EXPECTED
        check.EXPECTED = 2
        try:
            return validate_local_preview(self.root)
        finally:
            check.EXPECTED = original

    def test_valid_images_but_never_authorized(self):
        report = self.run_validation()
        self.assertEqual(report['state'], 'LOCAL_PREVIEW_VALIDATED', report)
        self.assertEqual(report['validated_image_count'], 2)
        self.assertEqual(report['authorized_image_count'], 0)
        self.assertFalse(report['upload_allowed'])
        self.assertFalse(report['release_ready'])

    def test_two_asins_with_identical_image_bytes_are_flagged_for_review(self):
        first = self.root / f'images/products/{self.asins[0]}.webp'
        second = self.root / f'images/products/{self.asins[1]}.webp'
        second.write_bytes(first.read_bytes())
        manifest_path = self.root / 'manifests/image_manifest.json'
        image_manifest = json.loads(manifest_path.read_text())
        source = image_manifest['images'][0]
        target = image_manifest['images'][1]
        target['validation']['sha256'] = source['validation']['sha256']
        target['file_size_bytes'] = source['file_size_bytes']
        manifest_path.write_text(json.dumps(image_manifest))
        catalog_path = self.root / 'manifests/catalog_manifest.json'
        catalog = json.loads(catalog_path.read_text())
        catalog['file_sha256']['manifests/image_manifest.json'] = hashlib.sha256(
            manifest_path.read_bytes()).hexdigest()
        catalog_path.write_text(json.dumps(catalog))
        (self.root / 'manifests/checksums.sha256').write_text(
            ''.join(f'{catalog["file_sha256"][key]}  {key}\n'
                    for key in sorted(catalog['file_sha256'])))

        report = self.run_validation()
        self.assertEqual(report['state'], 'LOCAL_PREVIEW_VALIDATED', report)
        self.assertEqual(report['technical_integrity'], 'PASS')
        self.assertEqual(report['duplicate_content_image_count'], 1)
        self.assertEqual(report['duplicate_content_group_count'], 1)
        self.assertEqual(report['distinct_image_content_count'], 1)
        self.assertTrue(report['manual_image_quality_review_required'])
        self.assertEqual(report['warnings'][0]['code'], 'DUPLICATE_IMAGE_CONTENT')
        self.assertFalse(report['upload_allowed'])
        self.assertFalse(report['release_ready'])
        self.assertEqual(report['authorized_image_count'], 0)

    def test_corrupted_image_fails_closed(self):
        path = self.root / f'images/products/{self.asins[0]}.webp'
        path.write_bytes(b'not a webp')
        report = self.run_validation()
        self.assertEqual(report['state'], 'LOCAL_PREVIEW_INVALID')

    def test_fixture_not_allowed(self):
        fixture = self.root / 'images/fixtures'
        fixture.mkdir()
        (fixture / 'bad.webp').write_bytes(b'xx')
        report = self.run_validation()
        self.assertEqual(report['state'], 'LOCAL_PREVIEW_INVALID')

    def test_unknown_rights_not_permission(self):
        path = self.root / 'manifests/image_manifest.json'
        data = json.loads(path.read_text())
        data['images'][0]['rights_status'] = 'APPROVED'
        path.write_text(json.dumps(data))
        self.assertEqual(self.run_validation()['state'], 'LOCAL_PREVIEW_INVALID')


if __name__ == '__main__':
    unittest.main()
