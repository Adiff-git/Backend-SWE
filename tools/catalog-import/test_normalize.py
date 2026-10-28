import hashlib
import json
import sqlite3
from contextlib import closing
import tempfile
import unittest
from pathlib import Path

import normalize as n


def payload(i=1, **overrides):
    d = {
        "id": f"id-{i}", "source": "cellphones", "source_url": f"https://cellphones.com.vn/p{i}.html",
        "name": "Điện thoại chính hãng", "description": " Mô tả  sản phẩm ",
        "category": None, "breadcrumbs": ["CELLPHONES", "Điện thoại", "Điện thoại chính hãng"],
        "sku": f"SKU-{i}", "offers": [{"type": "Offer", "price": "120000", "currency": "VND"}],
        "images": ["https://example.com/image.jpg"], "raw_product": {"@type": "Product"},
    }
    d.update(overrides)
    return d


def row(d):
    return d["id"], d["source"], d["source_url"], json.dumps(d, ensure_ascii=False)


class NormalizationTests(unittest.TestCase):
    def one(self, d):
        return n.normalize([row(d)])[0]

    def test_valid_exact_and_unicode(self):
        r = self.one(payload())
        self.assertEqual(r["validation_status"], "STRUCTURAL_CANDIDATE")
        self.assertEqual(r["product_name"], "Điện thoại chính hãng")
        self.assertEqual(r["variant"]["source_exact_price_vnd"], "120000")
        self.assertFalse(r["variant"]["selling_price_approved"])
        self.assertEqual(r["variant"]["proposed_options"], {})

    def test_aggregate_range_is_not_price(self):
        r = self.one(payload(offers=[{"type": "AggregateOffer", "low_price": "100", "high_price": "200", "currency": "VND"}]))
        self.assertEqual(r["validation_status"], "CATEGORY_OR_FAMILY_ONLY")
        self.assertIsNone(r["variant"]["source_exact_price_vnd"])
        self.assertIn("AGGREGATE_OFFER_ONLY", r["validation_reasons"])

    def test_zero_price(self):
        r = self.one(payload(offers=[{"type": "Offer", "price": "0", "currency": "VND"}]))
        self.assertEqual(r["validation_status"], "NEEDS_ENRICHMENT")
        self.assertIn("ZERO_PRICE_REVIEW", r["validation_reasons"])

    def test_missing_and_placeholder_sku(self):
        self.assertIn("SKU_MISSING", self.one(payload(sku=None))["validation_reasons"])
        self.assertIn("PLACEHOLDER_SKU", self.one(payload(sku="0"))["validation_reasons"])

    def test_duplicate_sku_ignores_case(self):
        a, b = payload(1, sku="AbC"), payload(2, sku="abc")
        records = n.normalize([row(a), row(b)])
        self.assertTrue(all("DUPLICATE_SKU" in r["validation_reasons"] for r in records))

    def test_missing_category(self):
        r = self.one(payload(breadcrumbs=["CELLPHONES", "Điện thoại chính hãng"]))
        self.assertIn("CATEGORY_MISSING", r["validation_reasons"])

    def test_ambiguous_breadcrumb(self):
        r = self.one(payload(breadcrumbs=["CELLPHONES", "Điện thoại", "Khác tên"]))
        self.assertIn("CATEGORY_AMBIGUOUS", r["validation_reasons"])

    def test_uuid_stable_when_sku_changes(self):
        a = self.one(payload(sku="X"))
        b = self.one(payload(sku="Y"))
        self.assertEqual(a["proposed_product_uuid"], b["proposed_product_uuid"])

    def test_missing_or_malformed_optional_fields(self):
        r = self.one(payload(description=None, images=None, raw_product=None))
        self.assertIsNone(r["description"])
        self.assertIn("IMAGES_MALFORMED", r["validation_reasons"])

    def test_duplicate_source_identity_and_repeated_name(self):
        a, b = payload(1), payload(2)
        b["source_url"] = a["source_url"]
        records = n.normalize([row(a), row(b)])
        self.assertTrue(all("DUPLICATE_SOURCE_IDENTITY" in r["validation_reasons"] for r in records))
        self.assertTrue(all("PRODUCT_NAME_REPEATED" in r["validation_reasons"] for r in records))

    def test_invalid_payload(self):
        r = n.normalize([("id-1", "cellphones", "https://example.com/p", "{")])[0]
        self.assertEqual(r["validation_status"], "INVALID")

    def test_repeatable_output_and_read_only_source(self):
        with tempfile.TemporaryDirectory() as root:
            root = Path(root)
            db = root / "source.sqlite"
            with closing(sqlite3.connect(db)) as c, c:
                c.execute("CREATE TABLE products (id TEXT PRIMARY KEY, site TEXT, url TEXT, payload TEXT)")
                c.execute("INSERT INTO products VALUES (?,?,?,?)", row(payload()))
            before = hashlib.sha256(db.read_bytes()).hexdigest()
            n.run(db, root / "a")
            n.run(db, root / "b")
            for name in ("normalized_categories.csv", "normalized_product_candidates.jsonl", "pending_or_rejected_records.jsonl", "validation_summary.json", "normalization_report.md"):
                self.assertEqual((root / "a" / name).read_bytes(), (root / "b" / name).read_bytes())
            self.assertEqual(before, hashlib.sha256(db.read_bytes()).hexdigest())
            n.run(db, root / "uncreated", dry_run=True)
            self.assertFalse((root / "uncreated").exists())


if __name__ == "__main__":
    unittest.main()
