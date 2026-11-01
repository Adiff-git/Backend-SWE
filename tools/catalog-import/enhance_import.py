#!/usr/bin/env python3
"""Review-bound metadata enrichment of existing variants. Default is read-only."""
import argparse
import hashlib
import json
from pathlib import Path
import uuid

import import_postgres as legacy

EMPTY = {"schema_version": 1, "values": {}}


def digest(value):
    return hashlib.sha256(json.dumps(value, ensure_ascii=False, sort_keys=True,
                                     separators=(",", ":"), default=str).encode()).hexdigest()


def file_sha256(path):
    with Path(path).open('rb') as stream:
        return hashlib.file_digest(stream,'sha256').hexdigest()


def rows(path, registry_dir=None):
    from enhance_normalize import normalize_record, load_registries
    registries = load_registries(registry_dir) if registry_dir else load_registries()
    seen = set()
    with Path(path).open(encoding="utf-8") as stream:
        for line in stream:
            row = json.loads(line)
            key = (row['source'], row['source_key'])
            if key in seen:
                raise ValueError('Duplicate source identity: ' + str(key))
            seen.add(key)
            if not row.get('product_id'):
                continue  # source quarantine already retained in normalization report
            from enhance_normalize import normalized_hash
            source_json = row['raw_source_json']
            if hashlib.sha256(source_json.encode()).hexdigest() != row['source_payload_sha256']:
                raise ValueError('Raw source hash mismatch')
            regenerated = normalize_record((row['source_record_id'],row['source'],row['source_url'],source_json),registries)
            if regenerated.get('normalized_payload_sha256') != row.get('normalized_payload_sha256'):
                raise ValueError('Normalized row disagrees with source or current registry')
            if row.get('normalized_payload_sha256') != normalized_hash(row):
                raise ValueError('Normalized content hash mismatch')
            expected = legacy.normalize.proposed_uuid(row['source'], row['source_key'])
            if row['product_id'] != expected or row['variant_id'] != str(legacy.variant_id(uuid.UUID(expected))):
                raise ValueError('Source identity differs from existing deterministic algorithm')
            yield row


def current(db, row, lock=False):
    suffix = ' FOR UPDATE OF p,v' if lock else ''
    data = db.execute('SELECT p.id,p.category_id,p.name,p.description,p.status,p.brand_id,'
                      'v.id,v.sku,v.selling_price,v.option_combination,v.technical_specifications '
                      'FROM products p JOIN product_variants v ON v.product_id=p.id '
                      'WHERE p.id=%s ORDER BY v.id' + suffix, (row['product_id'],)).fetchall()
    if not data:
        return None
    if len(data) != 1 or str(data[0][6]) != row['variant_id']:
        raise ValueError('Ambiguous existing variant ownership')
    return list(data[0])


def proposal(db, row, lock=False):
    state = current(db, row, lock)
    link = db.execute('SELECT product_variant_id,source_payload_sha256,normalized_content_sha256 FROM catalog_source_links WHERE source=%s AND source_key=%s',
                      (row['source'], row['source_key'])).fetchone()
    if link and str(link[0]) != row['variant_id']:
        raise ValueError('Source identity belongs to another variant')
    brand = row.get('brand')
    desired_brand = brand['id'] if brand else None
    desired_specs = row['technical_specifications']
    if state:
        if desired_brand and state[5] and str(state[5]) != desired_brand:
            raise ValueError('Existing brand conflict')
        old_values = state[10]['values']
        new_values = desired_specs['values']
        if any(k in old_values and old_values[k] != v for k,v in new_values.items()):
            raise ValueError('Existing technical specification conflict')
        merged = {'schema_version': 1, 'values': dict(old_values, **new_values)}
        changed_brand = bool(desired_brand and not state[5])
        changed_specs = merged != state[10]
        receipt_changed = not link or link[1:] != (row['source_payload_sha256'],row['normalized_payload_sha256'])
        operation = 'ENRICH' if changed_brand or changed_specs or receipt_changed else 'UNCHANGED'
        return dict(operation=operation, expected_before_sha256=digest(state),
                    brand_update=changed_brand, specification_update=changed_specs,
                    specifications=merged, state=state)
    return dict(operation='UNMATCHED', expected_before_sha256=digest(None))


def execute_item(db, row, approval, manifest_hash):
    from psycopg.types.json import Jsonb
    plan = proposal(db, row, lock=True)
    if plan['operation'] == 'UNCHANGED':
        return 'UNCHANGED'
    if any(approval.get(k) != row.get(k) for k in
           ('source', 'source_key', 'source_payload_sha256', 'normalized_payload_sha256')):
        raise ValueError('Approval is not bound to normalized source facts')
    if approval.get('operation') != plan['operation'] or approval.get('expected_before_sha256') != plan['expected_before_sha256']:
        raise ValueError('Target changed since reviewed dry run')
    if plan['operation'] != 'ENRICH':
        raise ValueError('Record is not eligible')
    brand = row.get('brand')
    if brand:
        found = db.execute('SELECT id,name FROM brands WHERE code=%s', (brand['code'],)).fetchone()
        if found and (str(found[0]), found[1]) != (brand['id'], brand['name']):
            raise ValueError('Canonical brand conflicts with target registry')
        if not found:
            db.execute('INSERT INTO brands(id,code,name) VALUES (%s,%s,%s)',
                       (brand['id'], brand['code'], brand['name']))
    if plan['brand_update']:
        db.execute('UPDATE products SET brand_id=%s WHERE id=%s', (brand['id'], row['product_id']))
    db.execute('UPDATE product_variants SET technical_specifications=%s WHERE id=%s',
               (Jsonb(plan['specifications']), row['variant_id']))
    db.execute('INSERT INTO catalog_source_links(id,source,source_key,source_url,source_record_id,product_variant_id,'
               'source_payload_sha256,normalized_content_sha256,normalization_version,approved_manifest_sha256,updated_at) '
               'VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,clock_timestamp()) '
               'ON CONFLICT(source,source_key) DO UPDATE SET source_payload_sha256=EXCLUDED.source_payload_sha256,'
               'normalized_content_sha256=EXCLUDED.normalized_content_sha256,normalization_version=EXCLUDED.normalization_version,'
               'approved_manifest_sha256=EXCLUDED.approved_manifest_sha256,updated_at=EXCLUDED.updated_at '
               'WHERE catalog_source_links.product_variant_id=EXCLUDED.product_variant_id RETURNING id',
               (str(uuid.uuid5(legacy.normalize.NAMESPACE, 'source|' + row['source'] + '|' + row['source_key'])),
                row['source'], row['source_key'], row['source_url'], row['source_record_id'], row['variant_id'],
                row['source_payload_sha256'], row['normalized_payload_sha256'], row['normalization_version'], manifest_hash)).fetchone()
    if not db.execute('SELECT 1 FROM catalog_source_links WHERE source=%s AND source_key=%s AND product_variant_id=%s',(row['source'],row['source_key'],row['variant_id'])).fetchone():
        raise ValueError('Concurrent source ownership conflict')
    return plan['operation']


def run(args):
    # Validate every identity before any target mutation. A second pass streams bounded batches.
    count = sum(1 for _ in rows(args.records, getattr(args,'registry_dir',None)))
    approvals = {}
    manifest_hash = None
    if args.approvals:
        raw = args.approvals.read_bytes(); manifest_hash = hashlib.sha256(raw).hexdigest()
        for item in json.loads(raw):
            key = (item['source'], item['source_key'])
            if key in approvals:
                raise ValueError('Duplicate approval identity')
            approvals[key] = item
    if args.apply:
        if not manifest_hash or not args.backup or not args.backup_sha256:
            raise ValueError('Apply requires approvals and verified backup/recovery artifact')
        if file_sha256(args.backup) != args.backup_sha256:
            raise ValueError('Backup SHA-256 does not match reviewed recovery artifact')
        if args.confirm_apply != args.confirm_target:
            raise ValueError('Explicit apply confirmation must equal target identity')
    if getattr(args,'report',None): args.report.parent.mkdir(parents=True,exist_ok=True)
    db = legacy.target_connection(args, read_only=not args.apply)
    result = {'source_records': count, 'applied': bool(args.apply), 'items': [], 'counts': {}}
    try:
        if args.apply:
            found = set()
            for row in rows(args.records, getattr(args,'registry_dir',None)):
                key = (row['source'],row['source_key'])
                if key in approvals:
                    found.add(key)
                    plan = proposal(db,row)
                    approval = approvals[key]
                    if plan['operation'] == 'UNMATCHED':
                        raise ValueError('Approval references a source absent from the existing Catalog')
                    if plan['operation'] != 'UNCHANGED':
                        if any(approval.get(k) != row.get(k) for k in ('source_payload_sha256','normalized_payload_sha256')) or approval.get('operation') != plan['operation'] or approval.get('expected_before_sha256') != plan['expected_before_sha256']:
                            raise ValueError('Whole-manifest preflight found unapproved content or target drift')
            if found != set(approvals):
                raise ValueError('Approval references absent source identity')
            db.commit()  # finish target/schema preflight before bounded write transactions
        batch = []
        def process(batch):
            for row in batch:
                key = (row['source'], row['source_key'])
                if args.apply:
                    if key not in approvals:
                        continue  # only explicitly approved rows may be applied
                    operation = execute_item(db, row, approvals[key], manifest_hash)
                    item = dict(source=key[0], source_key=key[1], operation=operation)
                else:
                    try:
                        plan = proposal(db, row)
                        item = {k:v for k,v in plan.items() if k not in ('state','specifications')}
                    except ValueError as failure:
                        item = dict(operation='CONFLICT', reason=str(failure))
                    item.update({k:row[k] for k in ('source','source_key','source_payload_sha256','normalized_payload_sha256')})
                result['items'].append(item)
                operation = item['operation']; result['counts'][operation] = result['counts'].get(operation, 0)+1
        def commit_batch(items):
            items=sorted(items,key=lambda row: row['product_id'])
            before_count=len(result['items']); before_counts=dict(result['counts'])
            try:
                if args.apply:
                    with db.transaction(): process(items)
                else: process(items)
            except BaseException:
                del result['items'][before_count:];result['counts']=before_counts
                raise
            if args.apply and getattr(args,'report',None):
                args.report.write_text(json.dumps(result,ensure_ascii=False,indent=2,default=str)+'\n')
        for row in rows(args.records, getattr(args,'registry_dir',None)):
            batch.append(row)
            if len(batch) >= args.batch_size:
                commit_batch(batch)
                batch = []
        if batch:
            commit_batch(batch)
        if not args.apply: db.rollback()
    except BaseException as failure:
        result['error']=str(failure)
        if getattr(args,'report',None):
            args.report.parent.mkdir(parents=True,exist_ok=True)
            args.report.write_text(json.dumps(result,ensure_ascii=False,indent=2,default=str)+'\n')
        raise
    finally:
        db.close()
    return result


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--registry-dir',type=Path)
    p.add_argument('--records', type=Path, required=True)
    p.add_argument('--approvals', type=Path)
    p.add_argument('--report', type=Path, required=True)
    p.add_argument('--apply', action='store_true')
    p.add_argument('--backup', type=Path); p.add_argument('--backup-sha256')
    p.add_argument('--confirm-apply'); p.add_argument('--batch-size', type=int, default=100)
    p.add_argument('--dsn-env', default='CATALOG_IMPORT_DSN')
    p.add_argument('--target-kind', choices=['disposable','local-development'], required=True)
    p.add_argument('--expect-database', required=True); p.add_argument('--expect-host', required=True)
    p.add_argument('--expect-port', type=int, required=True); p.add_argument('--confirm-target', required=True)
    args=p.parse_args()
    if not 1 <= args.batch_size <= 1000: p.error('batch-size must be 1..1000')
    try:
        report=run(args)
    except (ValueError, legacy.ImportErrorDetail) as failure:
        p.exit(1,str(failure)+'\n')
    args.report.parent.mkdir(parents=True,exist_ok=True)
    args.report.write_text(json.dumps(report,ensure_ascii=False,indent=2,default=str)+'\n')
    print(json.dumps(report['counts']))

if __name__ == '__main__': main()
