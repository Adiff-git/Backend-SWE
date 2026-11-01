#!/usr/bin/env python3
"""Select source metadata for the immutable 1,000-product pilot seed, offline.

This is a source-to-seed eligibility report, not a live database dry run or
approval to update any target. The target preview must still check current rows.
"""
import argparse
import json
import sqlite3
from collections import Counter
from pathlib import Path


def prepare(seed, normalized, output, input_db=None):
    catalog = json.loads(Path(seed).read_text(encoding='utf-8'))
    products = catalog['products']
    by_product = {p['product_id']: p for p in products}
    if len(by_product) != len(products):
        raise ValueError('Pilot seed contains duplicate product IDs')
    variants = {p['variant_id'] for p in products}
    if len(variants) != len(products):
        raise ValueError('Pilot seed contains duplicate variant IDs')
    matches = {}
    ambiguous = set()
    counters = Counter()
    unknown_attributes = Counter()
    if input_db is not None:
        from enhance_normalize import load_registries, normalize_record
        database = sqlite3.connect(f'file:{Path(input_db).resolve()}?mode=ro', uri=True)
        database.execute('PRAGMA query_only=ON')
        registry = load_registries()
        def source_rows():
            try:
                for pilot in products:
                    site, url = pilot['identity'].split('|', 1)
                    for raw in database.execute('SELECT id,site,url,payload FROM products WHERE site=? AND url=?', (site,url)):
                        yield normalize_record(raw, registry)
            finally:
                database.close()
        source = source_rows()
    else:
        def source_rows():
            with Path(normalized).open(encoding='utf-8') as stream:
                for line in stream:
                    yield json.loads(line)
        source = source_rows()
    for row in source:
            pilot = by_product.get(row.get('product_id'))
            if pilot is None:
                continue
            counters['matched_source_observations'] += 1
            if row['source'] + '|' + row['source_key'] != pilot['identity'] or row['variant_id'] != pilot['variant_id']:
                counters['variant_identity_conflicts'] += 1
                ambiguous.add(pilot['product_id'])
                matches.pop(pilot['product_id'], None)
                continue
            if pilot['product_id'] in matches or pilot['product_id'] in ambiguous:
                counters['ambiguous_product_matches'] += 1
                ambiguous.add(pilot['product_id'])
                matches.pop(pilot['product_id'], None)
                continue
            matches[pilot['product_id']] = row
    for row in matches.values():
        if row.get('brand'):
            counters['brand_candidates'] += 1
        elif row.get('raw_payload', {}).get('brand'):
            counters['unresolved_brand'] += 1
        if row.get('technical_specifications', {}).get('values'):
            counters['specification_candidates'] += 1
        for issue in row.get('issues', []):
            if isinstance(issue, str) and 'ATTRIBUTE' in issue:
                unknown_attributes[issue] += 1
    report = dict(evidence='immutable pilot seed and exact source identities, not live PostgreSQL',
                  products_examined=len(products), variants_examined=len(variants),
                  matched_source_identities=len(matches),
                  unmatched_pilot_products=len(products)-len(matches)-len(ambiguous),
                  ambiguous_pilot_products=len(ambiguous),
                  **counters, unsupported_attribute_issues=dict(unknown_attributes),
                  protected_product_records=len(products),
                  confirmed_unchanged_metadata=None, confirmed_database_updates=0)
    output = Path(output)
    output.mkdir(parents=True, exist_ok=True)
    manifest = output / 'pilot-enrichment-candidates.jsonl'
    with manifest.open('w', encoding='utf-8') as target:
        for product_id in sorted(matches):
            target.write(json.dumps(matches[product_id], ensure_ascii=False, sort_keys=True) + '\n')
    (output / 'pilot-enrichment-summary.json').write_text(json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True)+'\n', encoding='utf-8')
    return report


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--seed', type=Path, required=True)
    inputs = parser.add_mutually_exclusive_group(required=True)
    inputs.add_argument('--normalized', type=Path)
    inputs.add_argument('--input-db', type=Path)
    parser.add_argument('--output-dir', type=Path, required=True)
    args = parser.parse_args()
    print(json.dumps(prepare(args.seed, args.normalized, args.output_dir, args.input_db), ensure_ascii=False, sort_keys=True))
