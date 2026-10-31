#!/usr/bin/env python3
"""Fail-closed ProductImage importer using the existing Backend upload API.

Supports ONLY product photographs with separately documented redistribution
rights. Generated fixtures are never accepted. Existing *different* media
is deliberately NOT replaced: the repository's verified replacement API and
primary-image semantics must be reviewed before that operation is implemented.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import sys
from urllib.parse import urlsplit

import requests
import import_postgres as importer
import import_product_images as media

LICENSED_MODE = 'LICENSED_PRODUCT_IMAGES'
EXPECTED = 1500


def package_validation(catalog):
    """Run the same checksum, identity and rights gate as Catalog-Data."""
    catalog = Path(catalog).resolve()
    scripts = str(catalog / 'scripts')
    if scripts not in sys.path:
        sys.path.insert(0, scripts)
    import validate_catalog
    report = validate_catalog.validate(catalog)
    if not report.get('ready_for_bootstrap'):
        raise ValueError('CATALOG_PACKAGE_NOT_RELEASE_READY')
    return report


def prepare(catalog):
    """Read and validate local image bytes without any network/database writes.

    A record is included in `ready` only if its bytes match the manifest; legal
    authorization is evaluated separately by permission() before execution.
    """
    catalog = Path(catalog).resolve()
    seed = json.loads((catalog / 'manifests/catalog_manifest.json').read_text(encoding='utf-8'))
    if seed.get('image_rights_status') != LICENSED_MODE:
        raise ValueError('REAL_PRODUCT_IMAGES_REQUIRED_NO_FIXTURE_FALLBACK')
    raw = (catalog / 'manifests/image_manifest.json').read_bytes()
    image_manifest = json.loads(raw)
    with (catalog / 'data/products.jsonl').open(encoding='utf-8') as source:
        rows = [json.loads(line) for line in source if line.strip()]
    if any(not isinstance(row, dict) or not row.get('parent_asin') for row in rows):
        raise ValueError('IMAGE_MANIFEST_PRODUCT_ACCOUNTING')
    products = {row['parent_asin']: row for row in rows}
    entries = image_manifest.get('images', [])
    if (len(rows) != EXPECTED or len(products) != EXPECTED
            or len(entries) != EXPECTED
            or {entry.get('parent_asin') for entry in entries} != set(products)):
        raise ValueError('IMAGE_MANIFEST_PRODUCT_ACCOUNTING')

    ready, blocked = [], []
    for entry in entries:
        asin = entry['parent_asin']
        product = products[asin]
        if entry.get('product_id_proposal') != product['product']['id_proposal']:
            raise ValueError('IMAGE_PRODUCT_IDENTITY_MISMATCH:' + asin)
        relative = entry.get('asset_path')
        if not relative or entry.get('status') != 'VALIDATED_AUTHORIZED_ASSET':
            blocked.append(asin)
            continue
        path = Path(relative)
        if (path.is_absolute() or '..' in path.parts or len(path.parts) != 3
                or path.parts[0] != 'images'
                or path.parts[1] != 'products'
                or path.name != asin + '.webp'):
            raise ValueError('UNSAFE_OR_MISMATCHED_IMAGE_PATH:' + asin)
        full = catalog / path
        if (full.is_symlink() or not full.is_file()
                or not full.resolve().is_relative_to((catalog / 'images').resolve())):
            raise ValueError('MISSING_OR_UNSAFE_IMAGE_ASSET:' + asin)
        data = full.read_bytes()
        actual = media.validate_bytes(data)
        expected = entry.get('validation') or {}
        if (any(actual.get(k) != expected.get(k)
                for k in ('sha256', 'byte_size', 'width', 'height', 'content_type'))
                or actual['sha256'] != entry.get('sha256')
                or actual['byte_size'] != entry.get('file_size_bytes')):
            raise ValueError('IMAGE_BYTES_OR_METADATA_CHANGED:' + asin)
        ready.append((entry, product, full, actual))
    return seed, hashlib.sha256(raw).hexdigest(), ready, blocked


def permission(path, seed, manifest_hash, target, ready):
    """Fail closed unless a reviewed, exact-scope distribution record exists.

    Manifest flags alone do not demonstrate copyright/license ownership. The
    team must independently verify the referenced evidence before deployment.
    """
    if len(ready) != EXPECTED or seed.get('data_approval_status') != 'APPROVED':
        raise ValueError('MEDIA_INVENTORY_OR_DATA_APPROVAL_INVALID')
    if seed.get('image_rights_status') != LICENSED_MODE:
        raise ValueError('REAL_PRODUCT_IMAGES_REQUIRED_NO_FIXTURE_FALLBACK')
    if path is None or not Path(path).is_file() or not target:
        raise ValueError('IMAGE_REDISTRIBUTION_PERMISSION_REQUIRED')
    record = json.loads(Path(path).read_text(encoding='utf-8'))
    if (record.get('decision') != 'APPROVED'
            or record.get('target') != target
            or record.get('image_manifest_sha256') != manifest_hash
            or not record.get('approved_by')
            or not record.get('evidence_reference')):
        raise ValueError('IMAGE_REDISTRIBUTION_PERMISSION_MISSING_OR_MISMATCHED')
    allowed = record.get('parent_asins', [])
    if (len(allowed) != EXPECTED
            or set(allowed) != {item[0]['parent_asin'] for item in ready}):
        raise ValueError('IMAGE_OUTSIDE_PERMISSION_SCOPE')
    for entry, _, _, _ in ready:
        if (entry.get('asset_path') != f"images/products/{entry['parent_asin']}.webp"
                or entry.get('rights_status') != 'LICENSED_FOR_REDISTRIBUTION'
                or entry.get('status') != 'VALIDATED_AUTHORIZED_ASSET'
                or not entry.get('rights_evidence_reference')):
            raise ValueError('IMAGE_RIGHTS_UNVERIFIED:' + entry['parent_asin'])


def existing_images(connection, product_id):
    rows = connection.execute(
        'SELECT id,object_key FROM public.product_images WHERE product_id=%s',
        (product_id,),
    ).fetchall()
    return [(str(image_id), object_key) for image_id, object_key in rows]


def storage_client():
    import boto3
    from botocore.config import Config
    endpoint = os.environ.get('LOCAL_STORAGE_ENDPOINT')
    parsed = urlsplit(endpoint or '')
    if (parsed.scheme != 'http' or parsed.hostname not in ('127.0.0.1', 'localhost')
            or not parsed.port or parsed.username or parsed.password):
        raise ValueError('LOCAL_STORAGE_ENDPOINT_REQUIRED')
    client = boto3.client(
        's3', endpoint_url=endpoint, region_name='us-east-1',
        aws_access_key_id='test', aws_secret_access_key='test',
        config=Config(connect_timeout=5, read_timeout=15),
    )
    return client, os.environ.get('LOCAL_STORAGE_BUCKET', 'backend-images')


def stored_sha(client, bucket, object_key):
    if not object_key:
        raise ValueError('PRODUCT_IMAGE_OBJECT_KEY_MISSING')
    response = client.get_object(Bucket=bucket, Key=object_key)
    raw = response['Body'].read(media.MAX_BYTES + 1)
    return media.validate_bytes(raw)['sha256']


def verify_stored(args, image_id, expected_sha):
    db = importer.target_connection(args, read_only=True)
    try:
        row = db.execute(
            'SELECT object_key FROM public.product_images WHERE id=%s', (image_id,)
        ).fetchone()
        db.rollback()
    finally:
        db.close()
    if not row:
        raise ValueError('PRODUCT_IMAGE_METADATA_MISSING')
    client, bucket = storage_client()
    if stored_sha(client, bucket, row[0]) != expected_sha:
        raise ValueError('STORED_IMAGE_HASH_MISMATCH')


def run(args):
    seed, manifest_hash, ready, blocked = prepare(args.catalog)
    report = {
        'mode': args.mode, 'media_rights_mode': seed.get('image_rights_status'),
        'selected': len(ready) + len(blocked), 'validated_local_assets': len(ready),
        'missing_or_pending_assets': len(blocked), 'completed': 0,
        'database_checked': False, 'errors': [],
    }
    if args.mode == 'dry-run':
        return report
    if blocked or len(ready) != EXPECTED:
        raise ValueError('ALL_1500_RELEASE_AUTHORIZED_IMAGES_REQUIRED_BEFORE_EXECUTION')
    package_validation(args.catalog)
    if args.mode == 'verify-only':
        # Existing demo volumes must contain the exact PRODUCT photograph
        # bytes, not merely 1,500 ProductImage records or generated fixtures.
        if not args.confirm_target or not args.permission:
            raise ValueError('REDISTRIBUTION_PERMISSION_AND_TARGET_REQUIRED')
        permission(args.permission, seed, manifest_hash, args.confirm_target, ready)
        client, bucket = storage_client()
        connection = importer.target_connection(args, read_only=True)
        try:
            for entry, product, _, metadata in ready:
                pid = product['product']['id_proposal']
                rows = existing_images(connection, pid)
                if len(rows) != 1:
                    raise ValueError('EXISTING_PRODUCT_IMAGE_COUNT_MISMATCH:' + entry['parent_asin'])
                if stored_sha(client, bucket, rows[0][1]) != metadata['sha256']:
                    raise ValueError('STORED_REAL_IMAGE_HASH_MISMATCH:' + entry['parent_asin'])
                report['completed'] += 1
            connection.rollback()
            report['database_checked'] = True
        finally:
            connection.close()
        return report
    base = urlsplit(args.api_base or '')
    if (base.scheme != 'http' or base.hostname not in ('127.0.0.1', 'localhost')
            or not base.port or base.path or base.query or base.fragment):
        raise ValueError('API_MUST_BE_EXPLICIT_LOOPBACK')
    if not args.admin_credentials or not args.confirm_target:
        raise ValueError('ADMIN_CREDENTIALS_AND_TARGET_REQUIRED')
    if not args.permission:
        raise ValueError('REDISTRIBUTION_PERMISSION_REQUIRED')
    permission(args.permission, seed, manifest_hash, args.confirm_target, ready)
    token = json.loads(args.admin_credentials.read_text(encoding='utf-8')).get('token')
    if not token:
        raise ValueError('ADMIN_TOKEN_MISSING')
    if args.checkpoint is None:
        raise ValueError('CHECKPOINT_REQUIRED')
    checkpoint = args.checkpoint
    state = (json.loads(checkpoint.read_text(encoding='utf-8')) if checkpoint.exists()
             else {'target': args.confirm_target, 'manifest_sha256': manifest_hash, 'images': {}})
    if (state.get('target') != args.confirm_target
            or state.get('manifest_sha256') != manifest_hash
            or not isinstance(state.get('images'), dict)):
        raise ValueError('IMAGE_CHECKPOINT_TARGET_OR_MANIFEST_MISMATCH')

    # Full read-only inspection BEFORE the first upload. A different existing
    # asset cannot safely be replaced using only the
    # upload/complete API whose primary-image behavior is not verified.
    client, bucket = storage_client()
    matching_existing = {}
    connection = importer.target_connection(args, read_only=True)
    try:
        for entry, product, _, metadata in ready:
            pid = product['product']['id_proposal']
            row = connection.execute(
                'SELECT status FROM public.products WHERE id=%s', (pid,)
            ).fetchone()
            if not row or row[0] not in ('DRAFT', 'PUBLISHED'):
                raise ValueError('IMAGE_TARGET_PRODUCT_MISSING_OR_INVALID')
            rows = existing_images(connection, pid)
            if len(rows) > 1:
                raise ValueError('EXISTING_PRODUCT_HAS_MULTIPLE_IMAGES_REVIEW_REQUIRED')
            if rows:
                image_id, key = rows[0]
                if stored_sha(client, bucket, key) != metadata['sha256']:
                    raise ValueError('EXISTING_IMAGE_REPLACEMENT_REQUIRES_REVIEWED_BACKEND_API')
                matching_existing[entry['parent_asin']] = image_id
        report['database_checked'] = True
        connection.rollback()
    finally:
        connection.close()

    session = requests.Session()
    session.trust_env = False
    session.headers['Authorization'] = 'Bearer ' + token
    prefix = args.api_base.rstrip('/')
    for entry, product, path, metadata in ready:
        asin = entry['parent_asin']
        known = state['images'].get(asin)
        if known and known.get('sha256') != metadata['sha256']:
            raise ValueError('IMAGE_CHECKPOINT_CONTENT_CHANGED')
        if asin in matching_existing:
            image_id = matching_existing[asin]
            state['images'][asin] = {
                'image_id': image_id, 'sha256': metadata['sha256'], 'completed': True,
            }
            media.save(checkpoint, state)
            report['completed'] += 1
            continue
        if known and known.get('completed'):
            raise ValueError('CHECKPOINT_IMAGE_METADATA_MISSING')
        endpoint = prefix + '/api/products/' + product['product']['id_proposal'] + '/images/uploads'
        data = path.read_bytes()
        if not known:
            response = media.api_call(
                session, 'POST', endpoint,
                json={
                    'contentType': metadata['content_type'], 'byteSize': len(data),
                    'altText': product['product']['name'][:200],
                },
            ).json()
            known = {
                'upload_id': response['uploadId'], 'put_url': response['uploadUrl'],
                'sha256': metadata['sha256'], 'completed': False,
            }
            state['images'][asin] = known
            media.save(checkpoint, state)
        expected = urlsplit(os.environ.get('LOCAL_STORAGE_ENDPOINT', ''))
        signed = urlsplit(known['put_url'])
        if (signed.scheme != 'http' or signed.username or signed.password
                or (signed.hostname, signed.port) != (expected.hostname, expected.port)):
            raise ValueError('SIGNED_STORAGE_ENDPOINT_MISMATCH')
        result = media.put_object(known['put_url'], data, metadata['content_type'])
        if result.status_code not in (200, 204):
            if result.status_code not in (400, 403):
                raise ValueError('SIGNED_PUT_FAILED')
            response = media.api_call(
                session, 'POST', endpoint,
                json={
                    'contentType': metadata['content_type'], 'byteSize': len(data),
                    'altText': product['product']['name'][:200],
                },
            ).json()
            known.update(upload_id=response['uploadId'], put_url=response['uploadUrl'])
            media.save(checkpoint, state)
            refreshed = urlsplit(known['put_url'])
            if (refreshed.scheme != 'http' or refreshed.username or refreshed.password
                    or (refreshed.hostname, refreshed.port) != (expected.hostname, expected.port)):
                raise ValueError('SIGNED_STORAGE_ENDPOINT_MISMATCH')
            result = media.put_object(known['put_url'], data, metadata['content_type'])
            if result.status_code not in (200, 204):
                raise ValueError('SIGNED_PUT_FAILED')
        completed = media.api_call(
            session, 'POST', endpoint + '/' + known['upload_id'] + '/complete', json={}
        ).json()
        known.update(image_id=completed['imageId'], completed=True)
        known.pop('put_url', None)
        media.save(checkpoint, state)
        report['completed'] += 1
        verify_stored(args, known['image_id'], metadata['sha256'])
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--catalog', required=True, type=Path)
    parser.add_argument('--mode', choices=('dry-run', 'verify-only', 'execute'), default='dry-run')
    parser.add_argument('--permission', type=Path)
    parser.add_argument('--admin-credentials', type=Path)
    parser.add_argument('--checkpoint', type=Path)
    parser.add_argument('--api-base')
    for name in ('target-kind', 'expect-database', 'expect-host', 'confirm-target'):
        parser.add_argument('--' + name)
    parser.add_argument('--expect-port', type=int)
    parser.add_argument('--dsn-env', default='CATALOG_IMPORT_DSN')
    args = parser.parse_args()
    try:
        print(json.dumps(run(args), ensure_ascii=False, indent=2))
        return 0
    except (OSError, ValueError, KeyError, TypeError, importer.ImportErrorDetail) as failure:
        print(json.dumps({'error': str(failure), 'secrets_printed': False}), file=sys.stderr)
        return 2


if __name__ == '__main__':
    sys.exit(main())
