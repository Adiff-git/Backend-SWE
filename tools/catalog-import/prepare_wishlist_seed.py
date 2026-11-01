#!/usr/bin/env python3
"""Prepare a local, image-free 1,000-published Catalog seed for review.

The output contains retailer-derived data. Preparing it locally does not authorize
uploading, sharing, or publishing it.
"""
import argparse
import hashlib
import json
from pathlib import Path

import portable_seed


def prepare(source: Path, destination: Path):
    _, original = portable_seed.load_seed(source)
    if len(original) != 1000 or sum(p['status'] == 'PUBLISHED' for p in original) != 5:
        raise ValueError('Unexpected original seed')
    source_data = json.loads((source / 'catalog.json').read_text())
    original_ids = {str(p['product_id']) for p in original}
    if {p['product_id'] for p in source_data['products']} != original_ids:
        raise ValueError('Product UUIDs differ from validated seed')
    for product in source_data['products']:
        product['status'] = 'PUBLISHED'
    raw = (json.dumps(source_data, ensure_ascii=False, sort_keys=True, indent=2) + '\n').encode()
    source_manifest = json.loads((source / 'manifest.json').read_text())
    manifest = dict(source_manifest)
    manifest['version'] = portable_seed.WISHLIST_VERSION
    manifest['counts'] = portable_seed.SEED_COUNTS[portable_seed.WISHLIST_VERSION]
    manifest['sha256'] = hashlib.sha256(raw).hexdigest()
    manifest['parent_sha256'] = source_manifest['sha256']
    manifest['policy'] = 'PRIVATE LOCAL DEVELOPMENT ONLY; retailer-derived text; historical demo prices; no images'
    destination.mkdir(parents=True, exist_ok=False)
    (destination / 'catalog.json').write_bytes(raw)
    (destination / 'manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, sort_keys=True, indent=2) + '\n')
    _, derived = portable_seed.load_seed(destination)
    if {p['product_id'] for p in derived} != {p['product_id'] for p in original}:
        raise ValueError('Derived Product UUIDs differ')
    (destination / 'SHA256SUMS').write_text(''.join(
        hashlib.sha256((destination / name).read_bytes()).hexdigest() + '  ' + name + '\n'
        for name in ('catalog.json', 'manifest.json')))
    return manifest


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    manifest = prepare(args.source, args.output)
    print(json.dumps({'version': manifest['version'], 'counts': manifest['counts'],
                      'catalog_sha256': manifest['sha256']}))


if __name__ == '__main__':
    main()
