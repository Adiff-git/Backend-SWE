#!/usr/bin/env python3
"""Read-only technical validation of a local authentic-image catalog preview.

This module NEVER authorizes media upload, sharing, or publication.
"""
import hashlib
import json
from pathlib import Path, PurePosixPath

EXPECTED = 1500
PREVIEW_MODE = 'AUTHENTIC_MEDIA_LOCAL_PREVIEW'
GIT_LFS_POINTER = b'version https://git-lfs.github.com/spec/v1'


def _json(path):
    return json.loads(path.read_text(encoding='utf-8'))


def _jsonl(path):
    return [json.loads(line) for line in path.read_text(encoding='utf-8').splitlines() if line.strip()]


def _safe_file(root, relative, prefix=None):
    """Return file under root; reject absolute/parent/symlink traversal."""
    if not isinstance(relative, str) or '\\' in relative or not relative:
        raise ValueError('invalid asset path')
    posix = PurePosixPath(relative)
    if posix.is_absolute() or '..' in posix.parts or '.' in posix.parts:
        raise ValueError('unsafe asset path')
    if prefix is not None and (len(posix.parts) != 3 or posix.parts[:2] != prefix):
        raise ValueError('unexpected asset directory')
    candidate = root.joinpath(*posix.parts)
    if not candidate.resolve().is_relative_to(root.resolve()):
        raise ValueError('asset escapes catalog directory')
    if not candidate.is_file() or candidate.is_symlink():
        raise ValueError('missing or symlinked asset')
    return candidate


def validate_local_preview(catalog, env_file=None):
    """No DB, Docker, API, S3, or network calls. Never writes files."""
    catalog = Path(catalog).resolve()
    problems = []
    count = 0
    fixtures = 0
    duplicate_content_groups = {}
    distinct_content_count = 0
    product_count = 0
    image_count = 0

    def fail(code, detail=''):
        if len(problems) < 25:
            problems.append(code + (':' + str(detail) if detail else ''))

    try:
        if not catalog.is_dir():
            raise ValueError('CATALOG_DIRECTORY_MISSING')
        manifest = _json(catalog / 'manifests/catalog_manifest.json')
        images_doc = _json(catalog / 'manifests/image_manifest.json')
        products = _jsonl(catalog / 'data/products.jsonl')
        variants = _jsonl(catalog / 'data/variants.jsonl')
        specs = _jsonl(catalog / 'data/specifications.jsonl')
        product_count = len(products)
        if manifest.get('image_rights_status') != PREVIEW_MODE:
            fail('NOT_AUTHENTIC_LOCAL_PREVIEW')
        if manifest.get('data_approval_status') != 'APPROVED':
            fail('PRODUCT_DATA_NOT_APPROVED')
        if not (len(products) == len(variants) == len(specs) == EXPECTED):
            fail('CATALOG_COUNT_MISMATCH')
        if manifest.get('product_count') != EXPECTED or manifest.get('image_count') != EXPECTED:
            fail('CATALOG_MANIFEST_COUNT_MISMATCH')

        # Validate every metadata checksum and the canonical checksum list.
        checksums = manifest.get('file_sha256')
        if not isinstance(checksums, dict) or not checksums:
            fail('FILE_CHECKSUM_MANIFEST_INVALID')
        else:
            required = {'data/products.jsonl', 'data/variants.jsonl',
                        'data/specifications.jsonl', 'manifests/image_manifest.json'}
            if not required.issubset(checksums):
                fail('REQUIRED_FILE_CHECKSUMS_MISSING')
            for relative, expected_sha in checksums.items():
                try:
                    path = _safe_file(catalog, relative)
                    data = path.read_bytes()
                    if data.startswith(GIT_LFS_POINTER):
                        fail('GIT_LFS_POINTER', relative)
                    if hashlib.sha256(data).hexdigest() != expected_sha:
                        fail('METADATA_CHECKSUM_MISMATCH', relative)
                except (OSError, ValueError) as err:
                    fail('METADATA_FILE_INVALID', f'{relative}: {err}')
            canonical = ''.join(f'{checksums[path]}  {path}\n' for path in sorted(checksums))
            if (catalog / 'manifests/checksums.sha256').read_text(encoding='utf-8') != canonical:
                fail('CHECKSUM_LIST_MISMATCH')

        by_asin = {}
        for p, v, spec in zip(products, variants, specs):
            asin = p.get('parent_asin')
            product_id = (p.get('product') or {}).get('id_proposal')
            variant = v.get('variant') or {}
            if not isinstance(asin, str) or not asin or asin in by_asin:
                fail('DUPLICATE_OR_INVALID_PRODUCT_ASIN', asin)
            if v.get('parent_asin') != asin or spec.get('parent_asin') != asin:
                fail('PRODUCT_VARIANT_SPEC_IDENTITY_MISMATCH', asin)
            if variant.get('product_id_proposal') != product_id:
                fail('VARIANT_PRODUCT_UUID_MISMATCH', asin)
            if not product_id:
                fail('PRODUCT_UUID_MISSING', asin)
            by_asin[asin] = product_id
        if len(by_asin) != EXPECTED:
            fail('PRODUCT_IDENTITIES_INCOMPLETE')

        images = images_doc.get('images')
        if not isinstance(images, list):
            raise ValueError('IMAGE_MANIFEST_IMAGES_NOT_ARRAY')
        if len(images) != EXPECTED:
            fail('IMAGE_COUNT_MISMATCH')
        if {im.get('parent_asin') for im in images if isinstance(im, dict)} != set(by_asin):
            fail('IMAGE_ASIN_SET_MISMATCH')
        seen_asins, seen_paths, seen_hashes = set(), set(), {}
        from PIL import Image
        for im in images:
            if not isinstance(im, dict):
                fail('INVALID_IMAGE_MANIFEST_ENTRY')
                continue
            asin, relative = im.get('parent_asin'), im.get('asset_path')
            if asin in seen_asins:
                fail('DUPLICATE_IMAGE_ASIN', asin)
            seen_asins.add(asin)
            if relative in seen_paths:
                fail('DUPLICATE_IMAGE_PATH', relative)
            seen_paths.add(relative)
            if im.get('product_id_proposal') != by_asin.get(asin):
                fail('IMAGE_PRODUCT_UUID_MISMATCH', asin)
            if im.get('rights_status') not in ('UNKNOWN',):
                fail('UNEXPECTED_IMAGE_RIGHTS_STATUS', asin)
            try:
                path = _safe_file(catalog, relative, ('images', 'products'))
                if path.suffix.lower() != '.webp':
                    fail('NOT_WEBP_ASSET_PATH', relative)
                data = path.read_bytes()
                sha = hashlib.sha256(data).hexdigest()
                expected_sha = (im.get('validation') or {}).get('sha256')
                if expected_sha != sha:
                    fail('IMAGE_SHA256_MISMATCH', relative)
                # Identical bytes can legitimately be shared by distinct ASINs.
                # Keep a visible quality-review warning, not a checksum failure.
                # Unique ASINs and asset paths remain hard requirements above.
                if sha in seen_hashes:
                    duplicate_content_groups.setdefault(sha, [seen_hashes[sha]]).append(relative)
                else:
                    seen_hashes[sha] = relative
                if len(data) != im.get('file_size_bytes'):
                    fail('IMAGE_SIZE_MISMATCH', relative)
                if data.startswith(GIT_LFS_POINTER):
                    fail('GIT_LFS_POINTER_IMAGE', relative)
                with Image.open(path) as decoded:
                    decoded.load()
                    if decoded.format != 'WEBP' or decoded.size != (im.get('width'), im.get('height')):
                        fail('INVALID_WEBP_DIMENSIONS', relative)
                count += 1
            except (OSError, ValueError, TypeError) as err:
                fail('IMAGE_FILE_INVALID', f'{relative}: {err}')

        distinct_content_count = len(seen_hashes)
        asset_dir = catalog / 'images/products'
        actual_assets = {f'images/products/{p.name}' for p in asset_dir.iterdir() if p.is_file()} if asset_dir.is_dir() else set()
        if actual_assets != seen_paths:
            fail('ACTUAL_IMAGE_ASSET_SET_MISMATCH')
        fixture_dir = catalog / 'images/fixtures'
        if fixture_dir.is_dir():
            fixtures = sum(1 for p in fixture_dir.rglob('*') if p.is_file())
        if fixtures:
            fail('FIXTURE_IMAGES_PRESENT')
        if count != EXPECTED:
            fail('VALIDATED_IMAGE_COUNT_INCOMPLETE')
    except (OSError, ValueError, TypeError, KeyError, json.JSONDecodeError, ImportError) as err:
        fail('LOCAL_PREVIEW_INVALID', err)

    passed = not problems
    return {
        'ready': passed,  # Technical-preview readiness only.
        'state': 'LOCAL_PREVIEW_VALIDATED' if passed else 'LOCAL_PREVIEW_INVALID',
        'technical_integrity': 'PASS' if passed else 'FAIL',
        'catalog_products': product_count,
        'validated_image_count': count,
        'real_image_count': count,
        'fixture_image_count': fixtures,
        'authorized_image_count': 0,
        'media_mode': PREVIEW_MODE,
        'redistribution_rights': 'UNKNOWN',
        'release_ready': False,
        'upload_allowed': False,
        'duplicate_content_image_count': sum(len(paths) - 1 for paths in duplicate_content_groups.values()),
        'duplicate_content_group_count': len(duplicate_content_groups),
        'distinct_image_content_count': distinct_content_count,
        'manual_image_quality_review_required': bool(duplicate_content_groups),
        'warnings': ([
            {'code': 'DUPLICATE_IMAGE_CONTENT', 'sha256': digest,
             'image_count': len(paths), 'example_paths': paths[:5]}
            for digest, paths in list(duplicate_content_groups.items())[:10]
        ]),
        'blockers': problems,
    }
