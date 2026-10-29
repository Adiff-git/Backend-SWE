import json
from pathlib import Path
import hashlib
import sqlite3
import tempfile
import unittest
import uuid

from prepare_enrichment import prepare
import normalize


class PrepareEnrichmentTest(unittest.TestCase):
    def test_selects_exact_pilot_identities_without_creating_or_guessing(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            seed = root / 'seed.json'
            source = root / 'normalized.jsonl'
            seed.write_text(json.dumps({'products': [
                {'product_id': 'p1', 'variant_id': 'v1', 'identity': 'site|url1'},
                {'product_id': 'p2', 'variant_id': 'v2', 'identity': 'site|url2'},
            ]}))
            source.write_text('\n'.join(json.dumps(row) for row in [
                {'product_id': 'p1', 'variant_id': 'v1', 'source':'site', 'source_key':'url1', 'brand': {'code':'approved'},
                 'technical_specifications': {'values': {'ram': {'value': 8}}}, 'issues': ['UNMAPPED_ATTRIBUTES']},
                {'product_id': 'unrelated', 'variant_id': 'new', 'brand': {'code':'approved'}},
            ]) + '\n')
            report = prepare(seed, source, root / 'out')
            self.assertEqual(report['products_examined'], 2)
            self.assertEqual(report['matched_source_identities'], 1)
            self.assertEqual(report['unmatched_pilot_products'], 1)
            self.assertEqual(report['brand_candidates'], 1)
            self.assertEqual(report['specification_candidates'], 1)
            self.assertEqual(report['confirmed_database_updates'], 0)
            self.assertEqual(len((root / 'out/pilot-enrichment-candidates.jsonl').read_text().splitlines()), 1)

    def test_rejects_duplicate_seed_identity(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            seed = root / 'seed.json'; seed.write_text(json.dumps({'products': [
                {'product_id':'same','variant_id':'v1'}, {'product_id':'same','variant_id':'v2'}]}))
            source = root / 'source.jsonl'; source.write_text('')
            with self.assertRaises(ValueError): prepare(seed, source, root / 'out')

    def test_ambiguous_source_is_excluded_from_manifest(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            seed = root/'seed.json'; seed.write_text(json.dumps({'products': [
                {'product_id':'p1','variant_id':'v1','identity':'site|url1'}]}))
            row = {'product_id':'p1','variant_id':'v1','source':'site','source_key':'url1'}
            source = root/'source.jsonl'; source.write_text(json.dumps(row)+'\n'+json.dumps(row)+'\n')
            report = prepare(seed,source,root/'out')
            self.assertEqual(report['ambiguous_pilot_products'],1)
            self.assertEqual(report['matched_source_identities'],0)
            self.assertEqual((root/'out/pilot-enrichment-candidates.jsonl').read_text(),'')

    def test_reads_only_exact_pilot_source_rows_from_sqlite(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            url = 'https://cellphones.com.vn/pilot-source.html'
            pid = normalize.proposed_uuid('cellphones', url)
            vid = str(uuid.uuid5(uuid.UUID(pid), 'variant|single'))
            seed = root / 'seed.json'
            seed.write_text(json.dumps({'products': [{'product_id':pid,'variant_id':vid,
                                                       'identity':'cellphones|'+url}]}))
            db = root / 'source.sqlite'
            raw = {'id':'one','source':'cellphones','source_url':url,'name':'Pilot source',
                   'brand':'Lenovo','breadcrumbs':['CELLPHONES','Laptop','Pilot source'],
                   'specifications':{}}
            with sqlite3.connect(db) as connection:
                connection.execute('CREATE TABLE products(id TEXT,site TEXT,url TEXT,payload TEXT)')
                connection.execute('INSERT INTO products VALUES(?,?,?,?)', ('one','cellphones',url,json.dumps(raw)))
                connection.execute('INSERT INTO products VALUES(?,?,?,?)', ('other','cellphones','https://cellphones.com.vn/other.html','{}'))
            before = hashlib.sha256(db.read_bytes()).digest()
            report = prepare(seed, None, root/'out', db)
            self.assertEqual(report['matched_source_identities'], 1)
            self.assertEqual(report['matched_source_observations'], 1)
            self.assertEqual(hashlib.sha256(db.read_bytes()).digest(), before)
