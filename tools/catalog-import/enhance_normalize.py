#!/usr/bin/env python3
"""Streaming read-only crawler normalization. Output is review evidence, never approval."""
import argparse
from collections import Counter
from decimal import Decimal, InvalidOperation
import hashlib
import json
from pathlib import Path
import re
import sqlite3
import tempfile
import unicodedata
import uuid
from urllib.parse import urlsplit
import normalize

ROOT = Path(__file__).resolve().parent
REGISTRIES = ROOT / 'registries'
VERSION = 'catalog-enhancement-v1'


def canonical(value):
    return ' '.join(unicodedata.normalize('NFC', value).split()) if isinstance(value, str) else None


def digest(value):
    return hashlib.sha256(json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(',', ':')).encode()).hexdigest()


def load_registries(directory=REGISTRIES):
    directory = Path(directory)
    registries = {name: json.loads((directory / (name + '-v1.json')).read_text()) for name in ('brands', 'categories', 'attributes')}
    if any(v.get('version') != 1 for v in registries.values()):
        raise ValueError('Unsupported registry version')
    aliases = {}
    codes = set()
    brand_ids = set()
    for brand in registries['brands']['brands']:
        uuid.UUID(brand['id'])
        if brand['code'] in codes or brand['id'] in brand_ids:
            raise ValueError('Duplicate canonical Brand identity')
        codes.add(brand['code']); brand_ids.add(brand['id'])
        for label in brand['aliases']:
            key = canonical(label).casefold()
            if key in aliases and aliases[key] != brand:
                raise ValueError('Conflicting brand alias: ' + key)
            aliases[key] = brand
    registries['brand_aliases'] = aliases
    mappings = {}
    for mapping in registries['categories']['mappings']:
        key = (mapping['source'], tuple(mapping['path']))
        uuid.UUID(mapping['category_id'])
        if key in mappings and mappings[key] != mapping['category_id']:
            raise ValueError('Conflicting category mapping')
        mappings[key] = mapping['category_id']
    registries['category_mappings'] = mappings
    registries['sha256'] = digest({k: registries[k] for k in ('brands','categories','attributes')})
    return registries


def parse_value(raw, definition):
    value = canonical(raw)
    if not value or value.casefold() in {'undefined', '-', 'hãng không công bố', 'null'}:
        raise ValueError('Missing or unknown value')
    kind = definition['type']
    if kind == 'text':
        return value
    if kind == 'resolution':
        match = re.fullmatch(r'(\d{3,5})\s*[x×]\s*(\d{3,5})(?:\s*pixels?)?(?:\s*\([^()]*\))?', value, re.IGNORECASE)
        if not match:
            raise ValueError('Ambiguous display resolution')
        return f'{int(match[1])}x{int(match[2])}'
    if kind == 'storage_gb':
        match = re.match(r'^(\d+)\s*GB\b(.*)$', value, re.IGNORECASE)
        if not match or int(match[1]) <= 0 or int(match[1]) > 1000000:
            raise ValueError('No unambiguous installed GB capacity')
        rest = match[2].strip()
        if rest and not re.match(r'^(?:SSD|PCIe|M\.2)\b', rest, re.IGNORECASE):
            raise ValueError('Ambiguous storage configuration')
        if '+' in rest or (re.search(r'\d+\s*(?:GB|TB)\b', rest, re.IGNORECASE) and 'tối đa' not in rest.casefold()):
            raise ValueError('Multiple storage capacities')
        return {'value': int(match[1]), 'unit': 'GB'}
    if kind == 'battery_wh':
        amounts = re.findall(r'(\d+(?:[.,]\d+)?)\s*(?:WHrs|Wh|watt[\s\-‑]*giờ)\b', value, re.IGNORECASE)
        if len(amounts) != 1:
            raise ValueError('No single battery Wh capacity')
        amount = Decimal(amounts[0].replace(',', '.'))
        if not Decimal(0) < amount <= Decimal(100000):
            raise ValueError('Invalid battery Wh capacity')
        return {'value': int(amount) if amount == amount.to_integral_value() else float(amount), 'unit': 'Wh'}
    if kind == 'battery_mah':
        match = re.fullmatch(r'(\d{1,3}(?:\.\d{3})+|\d+)\s*mAh', value, re.IGNORECASE)
        if not match:
            raise ValueError('No single battery mAh capacity')
        amount = int(match[1].replace('.', ''))
        if not 0 < amount <= 1000000:
            raise ValueError('Invalid battery mAh capacity')
        return {'value': amount, 'unit': 'mAh'}
    if kind == 'boolean':
        known = {'có': True, 'không': False}
        if value.casefold() not in known:
            raise ValueError('Invalid boolean')
        return known[value.casefold()]
    if kind == 'quantity':
        unit = definition['unit']
        unit_patterns = {'GB': r'GB', 'inch': r'inch(?:es)?', 'Hz': r'Hz', 'g': r'(?:kg|g)'}
        match = re.fullmatch(r'([0-9]+(?:\.[0-9]+)?)\s*' + unit_patterns[unit], value, re.IGNORECASE)
        if not match:
            raise ValueError('Ambiguous quantity or unapproved unit conversion')
        amount = Decimal(match[1])
        if unit == 'g' and value.lower().endswith('kg'):
            amount *= Decimal(1000)
        if not amount.is_finite() or amount <= 0 or amount > Decimal('1000000000000') or amount.as_tuple().exponent < -6:
            raise ValueError('Invalid quantity')
        # JSON has decimal numbers. Quantities in this allowlist are bounded and exact
        # to source precision; Decimal remains authoritative until JSON serialization.
        number = int(amount) if amount == amount.to_integral_value() else float(amount)
        return {'value': number, 'unit': unit}
    raise ValueError('Unsupported attribute type')


def normalize_record(row, registries):
    rid, site, url, payload = row
    issues = []
    try:
        raw = json.loads(payload)
        if not isinstance(raw, dict):
            raise ValueError('Expected object')
    except (ValueError, TypeError):
        return {'source_record_id': rid, 'source': site, 'source_url': url, 'source_key': url,
                'disposition': 'QUARANTINED', 'issues': ['INVALID_JSON'], 'raw_payload': payload,
                'creation_eligible': False}
    identity_valid = raw.get('id') == rid and raw.get('source') == site and raw.get('source_url') == url and normalize.valid_url(url) and site in normalize.HOSTS and urlsplit(url).hostname == normalize.HOSTS[site]
    if not identity_valid:
        issues.append('INVALID_SOURCE_IDENTITY')
    path, category_issue, _ = normalize.category_path(raw, normalize.clean(raw.get('name')))
    if category_issue:
        issues.append(category_issue)
    category_id = registries['category_mappings'].get((site, tuple(path or [])))
    if category_id is None:
        issues.append('UNREVIEWED_CATEGORY_MAPPING')
    if raw.get('brand') is not None and not isinstance(raw.get('brand'), str):
        issues.append('INVALID_BRAND_TYPE')
    label = canonical(raw.get('brand'))
    brand = registries['brand_aliases'].get(label.casefold() if label else None)
    if brand is None:
        issues.append('BRAND_MISSING' if not label else 'BRAND_UNRESOLVED')
    document = {'schema_version': 1, 'values': {}}
    unmapped = {}
    invalid = {}
    specs = raw.get('specifications') or {}
    if not isinstance(specs, dict):
        specs = {}; issues.append('INVALID_SPECIFICATIONS')
    conflicts = set()
    definitions = registries['attributes']['attributes']
    for raw_key, raw_value in specs.items():
        matches = [a for a in definitions if canonical(raw_key).casefold() in {canonical(x).casefold() for x in a['aliases']} and set(path or []).intersection(a['categories'])]
        if not matches:
            unmapped[raw_key] = raw_value
            continue
        definition = matches[0]
        key = definition['key']
        try:
            value = parse_value(raw_value, definition)
        except (ValueError, InvalidOperation):
            invalid[raw_key] = raw_value
            continue
        values = document['values']
        if key in conflicts:
            continue
        if key in values and values[key] != value:
            conflicts.add(key); values.pop(key); issues.append('ATTRIBUTE_CONFLICT:' + key)
        else:
            values[key] = value
    if unmapped: issues.append('UNMAPPED_ATTRIBUTES')
    if invalid: issues.append('INVALID_ATTRIBUTE_VALUES')
    amount, exact, price_issue = normalize.price_info(raw)
    if price_issue: issues.append(price_issue)
    sku = canonical(raw.get('sku'))
    if not sku or sku.casefold() in {'0','n/a','na','null','none','unknown'}:
        issues.append('UNRELIABLE_SKU')
    name = canonical(raw.get('name'))
    if not name or name.casefold() == 'undefined': issues.append('UNUSABLE_NAME')
    product_id = normalize.proposed_uuid(site, url) if identity_valid else None
    result = {'source': site, 'source_key': url, 'source_url': url, 'source_record_id': rid,
        'source_payload_sha256': hashlib.sha256(payload.encode()).hexdigest(), 'raw_source_json': payload,
        'product_id': product_id, 'variant_id': str(uuid.uuid5(uuid.UUID(product_id), 'variant|single')) if product_id else None,
        'brand': {k: brand[k] for k in ('id','code','name')} if brand else None,
        'technical_specifications': document, 'source_category_path': path, 'category_id': category_id,
        'source_sku': sku, 'source_price_vnd': amount, 'name': name, 'description': normalize.clean(raw.get('description')),
        'normalization_version': VERSION, 'registry_sha256': registries['sha256'],
        'unmapped_attributes': unmapped, 'invalid_attributes': invalid, 'raw_payload': raw, 'issues': sorted(set(issues))}
    result['creation_eligible'] = bool(identity_valid and category_id and name and name.casefold() != 'undefined' and sku and 'UNRELIABLE_SKU' not in issues and exact and amount and int(amount) > 0)
    result['enrichment_candidate'] = bool(identity_valid and (brand or document['values']))
    result['disposition'] = ('QUARANTINED' if not identity_valid else 'NON_PRODUCT_OBSERVATION' if price_issue == 'AGGREGATE_OFFER_ONLY' else 'REQUIRES_REVIEW' if issues else 'ACCEPTED_WITH_NORMALIZATION')
    result['normalized_payload_sha256'] = normalized_hash(result)
    return result


def normalized_hash(record):
    return digest({k: record[k] for k in ('source','source_key','source_payload_sha256','product_id','variant_id','brand','technical_specifications','category_id','registry_sha256','normalization_version','creation_eligible','source_sku','source_price_vnd','name','description','raw_payload','raw_source_json')})


def run(input_db, output_dir, registry_dir=REGISTRIES):
    registries = load_registries(registry_dir)
    output_dir = Path(output_dir); output_dir.mkdir(parents=True, exist_ok=True)
    counts, issue_counts, brands, unmapped = Counter(), Counter(), Counter(), Counter()
    # SQLite temporary table provides disk-backed duplicate detection, bounded Python memory.
    temporary = tempfile.TemporaryDirectory(prefix='catalog-normalization-')
    seen = sqlite3.connect(str(Path(temporary.name)/'identities.sqlite'))
    seen.execute('CREATE TABLE identities(source TEXT,source_key TEXT,PRIMARY KEY(source,source_key))')
    with sqlite3.connect(Path(input_db).resolve().as_uri() + '?mode=ro', uri=True) as source, (output_dir/'records.jsonl').open('w') as target:
        source.execute('PRAGMA query_only=ON'); source.execute('BEGIN')
        for row in source.execute('SELECT id,site,url,payload FROM products ORDER BY id'):
            record = normalize_record(row, registries)
            try: seen.execute('INSERT INTO identities VALUES(?,?)',(record['source'],record['source_key']))
            except sqlite3.IntegrityError:
                record['issues'].append('DUPLICATE_SOURCE_IDENTITY'); record['creation_eligible']=False;record['disposition']='QUARANTINED';counts['duplicate_identities']+=1
            counts['total_source_records']+=1; counts[record['disposition']]+=1
            counts['new_creation_candidates']+=record['creation_eligible'];counts['enrichment_candidates']+=record.get('enrichment_candidate',False)
            counts['records_with_normalized_brand']+=bool(record.get('brand'));counts['records_with_normalized_specs']+=bool(record.get('technical_specifications',{}).get('values'))
            issue_counts.update(record['issues'])
            if 'BRAND_UNRESOLVED' in record['issues']:brands[str(record.get('raw_payload',{}).get('brand'))]+=1
            unmapped.update(record.get('unmapped_attributes',{}).keys())
            target.write(json.dumps(record,ensure_ascii=False,sort_keys=True,separators=(',',':'))+'\n')
    seen.close(); temporary.cleanup()
    counts.update({k:0 for k in ('ACCEPTED','ACCEPTED_WITH_NORMALIZATION','QUARANTINED','REQUIRES_REVIEW','NON_PRODUCT_OBSERVATION','duplicate_identities') if k not in counts})
    summary={'normalization_version':VERSION,'registry_sha256':registries['sha256'],'counts':dict(counts),'issues':dict(issue_counts),'unresolved_brands':dict(brands),'unmapped_attribute_counts':dict(unmapped),'approval':'No mutation or publication approval is implied. Enrichment candidates are not target-matched records.'}
    (output_dir/'summary.json').write_text(json.dumps(summary,ensure_ascii=False,sort_keys=True,indent=2)+'\n')
    return summary


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--input-db',type=Path,required=True)
    parser.add_argument('--output-dir',type=Path,required=True)
    parser.add_argument('--registry-dir',type=Path,default=REGISTRIES)
    args=parser.parse_args(); print(json.dumps(run(args.input_db,args.output_dir,args.registry_dir),ensure_ascii=False,indent=2))

if __name__=='__main__': main()
