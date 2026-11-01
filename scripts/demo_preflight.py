#!/usr/bin/env python3
"""Read-only, fail-closed preflight for the sibling Catalog-Data demo package."""
import argparse
import csv
import hashlib
import json
from pathlib import Path
import shutil
import sys

LICENSED_MODE = 'LICENSED_PRODUCT_IMAGES'
EXPECTED = 1500


def run(catalog, env_file, data_only=False, local_preview=False):
    catalog = Path(catalog).resolve()
    env_file = Path(env_file).resolve()
    blockers = []
    if local_preview:
        if data_only:
            return {'ready': False, 'blockers': ['PREVIEW_AND_DATA_ONLY_CONFLICT'],
                    'state': 'INVALID_MODE_COMBINATION'}
        from catalog_local_preview import validate_local_preview
        return validate_local_preview(catalog, env_file)
    if not catalog.is_dir():
        return {'ready': False, 'blockers': ['CATALOG_DATA_DIRECTORY_MISSING']}

    sys.path.insert(0, str(catalog / 'scripts'))
    if data_only:
        package={'package_valid':True,'ready_for_bootstrap':False,'product_count':EXPECTED,'image_count':0}
    else:
        import validate_catalog
        package = validate_catalog.validate(catalog)
    if not data_only and not package.get('package_valid'):
        blockers.append('PACKAGE_VALIDATION_FAILED')
    if not data_only and not package.get('ready_for_bootstrap'):
        blockers.append('CATALOG_PACKAGE_NOT_RELEASE_READY')
    manifest_file = catalog / 'manifests/catalog_manifest.json'
    manifest = json.loads(manifest_file.read_text(encoding='utf-8'))
    mode = manifest.get('image_rights_status')
    if manifest.get('data_approval_status') != 'APPROVED':
        blockers.append('DATA_APPROVAL_MISSING')
    if not data_only and mode != LICENSED_MODE:
        blockers.append('REAL_PRODUCT_IMAGES_REQUIRED_NO_FIXTURE_FALLBACK')
    if not data_only and package['image_count'] != EXPECTED:
        blockers.append('AUTHORIZED_IMAGE_ASSETS_INCOMPLETE')
    if not env_file.is_file():
        blockers.append('PRIVATE_ENV_FILE_MISSING')

    mapping = json.loads((catalog / 'data/category_mapping.json').read_text(encoding='utf-8'))
    categories = mapping.get('categories', [])
    if (len(categories) != 10
            or any(item.get('approval') != 'APPROVED' or not item.get('category_uuid')
                   for item in categories)):
        blockers.append('CATEGORY_MAPPING_APPROVAL_MISSING')

    # The template is a review artefact, not the approved 1,500-row import file.
    approvals_file = catalog / 'data/approvals/amazon_import_approved_1500.csv'
    if not approvals_file.is_file():
        blockers.append('APPROVED_IMPORT_FILE_MISSING')
    else:
        with approvals_file.open(encoding='utf-8', newline='') as stream:
            approvals = list(csv.DictReader(stream))
        fields = ('category_approval', 'sku_policy_approval', 'variant_approval',
                  'price_approval', 'import_approval')
        if (len(approvals) != EXPECTED
                or len({row.get('parent_asin') for row in approvals}) != EXPECTED
                or any(any(row.get(field) != 'APPROVED' for field in fields)
                       for row in approvals)):
            blockers.append('IMPORT_RECORD_APPROVALS_MISSING')

    sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'tools/catalog-import'))
    import amazon_import
    if not data_only:
        import amazon_image_import
    if approvals_file.is_file():
        try:
            plan, _, _ = amazon_import.plan(
                catalog, approvals_file, catalog / 'data/category_mapping.json',
                stage=EXPECTED,
            )
            if plan.get('eligible') != EXPECTED:
                blockers.append('IMPORT_PLAN_NOT_FULLY_APPROVED')
        except (OSError, ValueError, KeyError, TypeError):
            blockers.append('IMPORT_PLAN_INVALID')

    policy = catalog / 'data/approvals/bootstrap_policy.json'
    if not data_only and not policy.is_file():
        blockers.append('FRESH_BOOTSTRAP_POLICY_MISSING')
    elif not data_only:
        approved = json.loads(policy.read_text(encoding='utf-8'))
        digest = hashlib.sha256(manifest_file.read_bytes()).hexdigest()
        if (approved.get('decision') != 'APPROVED'
                or approved.get('manifest_sha256') != digest
                or not approved.get('approved_by')
                or not approved.get('evidence_reference')
                or approved.get('scope') != 'fresh-isolated-demo'
                or approved.get('project_prefix') != 'demo-'):
            blockers.append('FRESH_BOOTSTRAP_POLICY_INVALID')

    try:
        if data_only:
            raise StopIteration
        seed, image_digest, ready, pending = amazon_image_import.prepare(catalog)
        if pending or len(ready) != EXPECTED:
            blockers.append('IMAGE_IMPORT_INVENTORY_INCOMPLETE')
        target = None
        if env_file.is_file():
            for line in env_file.read_text(encoding='utf-8').splitlines():
                if line.startswith('LOCAL_DB_PORT='):
                    port = line.split('=', 1)[1].strip().strip('"').strip("'")
                    if port.isdigit():
                        target = 'backend@127.0.0.1:' + port
                    break
        if mode == LICENSED_MODE:
            permission_path = catalog / 'data/approvals/image_permission.json'
            if not permission_path.is_file():
                blockers.append('IMAGE_IMPORT_PERMISSION_MISSING')
            else:
                amazon_image_import.permission(
                    permission_path, seed, image_digest, target, ready,
                )
    except StopIteration:
        pass
    except (OSError, ValueError, KeyError, TypeError):
        blockers.append('IMAGE_IMPORT_PERMISSION_OR_INVENTORY_INVALID')

    if not shutil.which('docker'):
        blockers.append('DOCKER_CLI_MISSING')
    # git-lfs is not always required for WebP files; pointers are caught by
    # Catalog-Data validation. Do not require the executable unconditionally.
    return {
        'ready': not blockers,
        'blockers': sorted(set(blockers)),
        'media_mode': mode,
        'catalog_products': package['product_count'],
        'authorized_images': package['image_count'],
        'package_valid': package['package_valid'],
        'state': 'READY_FOR_EXECUTION' if not blockers else 'BLOCKED_APPROVAL_OR_ASSETS',
    }


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--catalog', type=Path, required=True)
    parser.add_argument('--env-file', type=Path, required=True)
    parser.add_argument('--data-only', action='store_true')
    parser.add_argument('--local-preview', action='store_true')
    args = parser.parse_args()
    try:
        report = run(args.catalog, args.env_file, args.data_only, args.local_preview)
        print(json.dumps(report, indent=2, sort_keys=True))
        sys.exit(0 if report['ready'] else 3)
    except (OSError, ValueError, KeyError, TypeError, json.JSONDecodeError, ImportError) as error:
        print(json.dumps({
            'ready': False, 'blockers': ['PREFLIGHT_INVALID_PACKAGE'], 'detail': str(error),
        }))
        sys.exit(3)
