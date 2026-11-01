"""Offline compatibility tests. No Docker, network or database writes."""
import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import types
import unittest
from types import SimpleNamespace
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]

# All imported production service dependencies are stubbed, so these tests
# operate exclusively on deterministic, synthetic in-memory data.
pg = types.ModuleType('import_postgres')
pg.ImportErrorDetail = type('ImportErrorDetail', (Exception,), {})
pg.target_connection = lambda *args, **kwargs: None
media = types.ModuleType('import_product_images')
media.MAX_BYTES = 10_000_000
sys.modules['import_postgres'] = pg
sys.modules['import_product_images'] = media


def load(name, relative):
    spec = importlib.util.spec_from_file_location(name, ROOT / relative)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


images = load('amazon_image_import', 'tools/catalog-import/amazon_image_import.py')
preflight = load('demo_preflight', 'scripts/demo_preflight.py')
bootstrap = load('run_demo', 'scripts/run_demo.py')


def mock_ready():
    result = []
    for number in range(1500):
        asin = 'B' + str(number).zfill(9)
        row = {'parent_asin': asin, 'asset_path': f'images/products/{asin}.webp',
               'rights_status': 'LICENSED_FOR_REDISTRIBUTION',
               'rights_evidence_reference': 'manifests/licenses/vendor_license.txt',
               'status': 'VALIDATED_AUTHORIZED_ASSET'}
        result.append((row, None, None, None))
    return result


class PermissionTests(unittest.TestCase):
    def test_fixture_scope_is_rejected_without_exception(self):
        with self.assertRaisesRegex(ValueError, 'REAL_PRODUCT_IMAGES_REQUIRED_NO_FIXTURE_FALLBACK'):
            images.permission(None, {'image_rights_status': 'GENERATED_FIXTURES_ONLY',
                                     'data_approval_status': 'APPROVED'},
                              'digest', None, mock_ready())

    def test_licensed_media_requires_target_and_manifest_bound_approval(self):
        with tempfile.TemporaryDirectory() as temp:
            approval = Path(temp) / 'approval.json'
            records = mock_ready()
            approval.write_text(json.dumps({
                'decision': 'APPROVED', 'target': 'backend@127.0.0.1:55432',
                'image_manifest_sha256': 'expected', 'approved_by': 'owner',
                'evidence_reference': 'manifests/licenses/vendor_license.txt',
                'parent_asins': [item[0]['parent_asin'] for item in records],
            }), encoding='utf-8')
            seed = {'image_rights_status': images.LICENSED_MODE,
                    'data_approval_status': 'APPROVED'}
            images.permission(approval, seed, 'expected', 'backend@127.0.0.1:55432', records)
            with self.assertRaisesRegex(ValueError, 'PERMISSION_MISSING_OR_MISMATCHED'):
                images.permission(approval, seed, 'changed-hash', 'backend@127.0.0.1:55432', records)
            records[0][0]['rights_status'] = 'UNKNOWN'
            with self.assertRaisesRegex(ValueError, 'IMAGE_RIGHTS_UNVERIFIED'):
                images.permission(approval, seed, 'expected', 'backend@127.0.0.1:55432', records)

    def test_prepare_requires_consistent_source_product_count(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / 'manifests').mkdir()
            (root / 'data').mkdir()
            (root / 'manifests/catalog_manifest.json').write_text('{"image_rights_status": "LICENSED_PRODUCT_IMAGES"}')
            (root / 'manifests/image_manifest.json').write_text('{"images": []}')
            (root / 'data/products.jsonl').write_text('{}\n')
            with self.assertRaisesRegex(ValueError, 'IMAGE_MANIFEST_PRODUCT_ACCOUNTING'):
                images.prepare(root)

    def test_fixture_manifest_is_rejected_before_reading_assets(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            (root / 'manifests').mkdir()
            (root / 'manifests/catalog_manifest.json').write_text(
                '{"image_rights_status":"GENERATED_FIXTURES_ONLY"}',
                encoding='utf-8',
            )
            with self.assertRaisesRegex(ValueError, 'REAL_PRODUCT_IMAGES_REQUIRED_NO_FIXTURE_FALLBACK'):
                images.prepare(root)

    def test_license_record_missing_is_rejected(self):
        with self.assertRaisesRegex(ValueError, 'IMAGE_REDISTRIBUTION_PERMISSION_REQUIRED'):
            images.permission(None, {
                'image_rights_status': images.LICENSED_MODE,
                'data_approval_status': 'APPROVED',
            }, 'sha', 'backend@127.0.0.1:55432', mock_ready())

    def test_dry_run_is_offline(self):
        with patch.object(images, 'prepare', return_value=(
                {'image_rights_status': images.LICENSED_MODE}, 'hash',
                mock_ready(), [])):
            report = images.run(SimpleNamespace(mode='dry-run', catalog=Path('unused')))
        self.assertEqual(report['validated_local_assets'], 1500)
        self.assertFalse(report['database_checked'])

    def test_existing_volume_rejects_fixture_bytes_during_verify_only(self):
        class FakeConnection:
            def rollback(self):
                pass

            def close(self):
                pass

        ready = [(row, {'product': {'id_proposal': 'demo-uuid'}}, None,
                  {'sha256': 'real-hash'}) for row, _, _, _ in mock_ready()]
        args = SimpleNamespace(
            catalog=Path('unused'), mode='verify-only',
            confirm_target='backend@127.0.0.1:55432',
            permission=Path('some-approval-record'),
        )
        with patch.object(images, 'prepare', return_value=(
                {'image_rights_status': images.LICENSED_MODE,
                 'data_approval_status': 'APPROVED'}, 'digest', ready, [])), \
             patch.object(images, 'package_validation', return_value={'ready_for_bootstrap': True}), \
             patch.object(images, 'permission', return_value=None), \
             patch.object(images, 'storage_client', return_value=(object(), 'bucket')), \
             patch.object(images, 'existing_images', return_value=[('img-id', 'opaque-s3-key')]), \
             patch.object(images, 'stored_sha', return_value='fixture-hash'), \
             patch.object(images.importer, 'target_connection', return_value=FakeConnection()):
            with self.assertRaisesRegex(ValueError, 'STORED_REAL_IMAGE_HASH_MISMATCH'):
                images.run(args)

    def test_existing_image_identity_does_not_assume_object_key_is_sha(self):
        class FakeConnection:
            def execute(self, query, args):
                self.args = args
                return self

            def fetchall(self):
                return [('some-uuid', 'uploads/path/opaque-uuid.webp')]
        rows = images.existing_images(FakeConnection(), 'some-product')
        self.assertEqual(rows, [('some-uuid', 'uploads/path/opaque-uuid.webp')])

    def test_existing_different_media_blocks_before_any_upload(self):
        ready = mock_ready()
        # Production preflight checks all existing S3 hashes before first write.
        ready = [(entry, {'product': {'id_proposal': 'product-uuid'}}, None,
                  {'sha256': 'newsha'}) for entry, _, _, _ in ready]
        connection = SimpleNamespace(close=lambda: None, rollback=lambda: None)

        class Cursor:
            def execute(self, sql, values):
                self.sql = sql
                return self

            def fetchone(self):
                return ('DRAFT',)

            def fetchall(self):
                return [('old-image-uuid', 'images/old-fixture.webp')]

            def rollback(self):
                pass

            def close(self):
                pass

        args = SimpleNamespace(
            catalog=Path('unused'), mode='execute', api_base='http://127.0.0.1:8080',
            admin_credentials=Path('fake'), confirm_target='backend@127.0.0.1:55432',
            permission=Path('approved'), checkpoint=Path('unused'),
        )
        with patch.object(images, 'prepare', return_value=(
                {'image_rights_status': images.LICENSED_MODE,
                 'data_approval_status': 'APPROVED'}, 'digest', ready, [])), \
             patch.object(images, 'package_validation', return_value={'ready_for_bootstrap': True}), \
             patch.object(images, 'permission', return_value=None), \
             patch.object(images, 'storage_client', return_value=(object(), 'test-bucket')), \
             patch.object(images, 'stored_sha', return_value='oldsha'), \
             patch.object(images, 'existing_images', return_value=[('old-image-uuid', 'opaque')]), \
             patch.object(images.importer, 'target_connection', return_value=Cursor()), \
             patch.object(Path, 'read_text', return_value='{"token":"test"}'):
            with self.assertRaisesRegex(ValueError, 'EXISTING_IMAGE_REPLACEMENT_REQUIRES_REVIEWED_BACKEND_API'):
                images.run(args)


class BootstrapTests(unittest.TestCase):
    def test_orchestrator_uses_reviewed_1500_approval_file(self):
        source = (ROOT / 'scripts/run_demo.py').read_text()
        self.assertIn('amazon_import_approved_1500.csv', source)
        self.assertNotIn('amazon_import_template.csv', source)
        self.assertNotIn('--fixture-mode', source)
        self.assertIn('REAL_PRODUCT_IMAGES_REQUIRED_NO_FIXTURE_FALLBACK', source)
        self.assertIn('image_permission.json', source)

    def test_preflight_requires_explicit_bootstrap_policy(self):
        source = (ROOT / 'scripts/demo_preflight.py').read_text()
        self.assertIn('FRESH_BOOTSTRAP_POLICY_MISSING', source)
        self.assertIn('LICENSED_PRODUCT_IMAGES', source)
        self.assertIn('REAL_PRODUCT_IMAGES_REQUIRED_NO_FIXTURE_FALLBACK', source)
        self.assertNotIn('FIXTURE_MODE', source)


if __name__ == '__main__':
    unittest.main()
