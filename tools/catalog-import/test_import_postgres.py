"""Synthetic approved fixtures; never uses crawler products for write tests."""

import argparse
import csv
from contextlib import closing
import json
import os
import subprocess
import sys
import tempfile
import unittest
import uuid
from pathlib import Path

import psycopg
from testcontainers.community.postgres import PostgresContainer

import import_postgres as importer
import normalize
from prepare_pilot import FIELDS, manifest_row

ROOT = Path(__file__).resolve().parent
REPO = ROOT.parents[1]


def fixture(number, sku=None, category=None):
    source = "cellphones"
    url = f"https://cellphones.com.vn/synthetic-import-fixture-{number}.html"
    path = category or ["Synthetic Test Category", "Devices"]
    name = f"Synthetic approved product {number}"
    return {
        "source": source, "source_product_id": f"synthetic-{number}",
        "source_url": url, "source_identity": f"{source}|{url}",
        "proposed_product_uuid": normalize.proposed_uuid(source, url),
        "product_name": name, "description": "Synthetic approved test fixture",
        "proposed_category_path": path,
        "proposed_category_reference": str(importer.category_id(source, path)),
        "category_confidence": "MEDIUM", "validation_status": "STRUCTURAL_CANDIDATE",
        "validation_reasons": ["SELLING_PRICE_UNAPPROVED", "SKU_APPROVAL_REQUIRED", "VARIANT_APPROVAL_REQUIRED"],
        "variant": {"source_sku": sku or f"SYN-{number}", "sku_structurally_valid": True,
                    "exact_source_price_available": True, "source_exact_price_vnd": "100000",
                    "proposed_options": {}},
    }


def approved_row(candidate, published=False):
    row = manifest_row(candidate)
    for field in importer.APPROVALS[:5]:
        row[field] = "APPROVED"
    row["approved_selling_price_vnd"] = "110000"
    if published:
        row["publication_approval"] = "APPROVED"
    row["review_notes"] = "SYNTHETIC FIXTURE: explicit test approvals only."
    return row


class ImportPostgresIT(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.container = PostgresContainer("postgres:17-alpine", username="test", password="test",
                                         dbname="catalog_import_test")
        cls.container.start()
        cls.host = cls.container.get_container_host_ip()
        cls.port = int(cls.container.get_exposed_port(5432))
        cls.dsn = f"postgresql://test:test@{cls.host}:{cls.port}/catalog_import_test"
        cls.old_dsn = os.environ.get("CATALOG_IMPORT_DSN")
        os.environ["CATALOG_IMPORT_DSN"] = cls.dsn
        classpath_file = Path(os.environ.get("CATALOG_IMPORT_TEST_CLASSPATH_FILE",
                                             "/tmp/backend_catalog_import_classpath.txt"))
        if not classpath_file.is_file():
            raise RuntimeError("Build Maven classpath first; see catalog-import README")
        environment = dict(os.environ, IMPORT_TEST_JDBC_URL=f"jdbc:postgresql://{cls.host}:{cls.port}/catalog_import_test",
                           IMPORT_TEST_DB_USER="test", IMPORT_TEST_DB_PASSWORD="test")
        subprocess.run(["java", "--class-path", classpath_file.read_text().strip(),
                        str(ROOT / "test_support/FlywayBootstrap.java"), str(importer.MIGRATIONS)],
                       env=environment, check=True, capture_output=True, text=True)

    @classmethod
    def tearDownClass(cls):
        if cls.old_dsn is None:
            os.environ.pop("CATALOG_IMPORT_DSN", None)
        else:
            os.environ["CATALOG_IMPORT_DSN"] = cls.old_dsn
        cls.container.stop()

    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix="catalog-import-test-")
        self.directory = Path(self.temporary.name)

    def tearDown(self):
        with psycopg.connect(self.dsn) as connection:
            connection.execute("DELETE FROM product_variants")
            connection.execute("DELETE FROM products")
            connection.execute("DELETE FROM categories")
        self.temporary.cleanup()

    def args(self, rows, candidates=None, execute=False, check_db=False):
        candidates = candidates or [fixture(i + 1) for i in range(len(rows))]
        manifest = self.directory / "approval.csv"
        source = self.directory / "candidates.jsonl"
        with manifest.open("w", encoding="utf-8", newline="") as output:
            writer = csv.DictWriter(output, fieldnames=FIELDS)
            writer.writeheader()
            writer.writerows(rows)
        with source.open("w", encoding="utf-8") as output:
            for candidate in candidates:
                output.write(json.dumps(candidate, ensure_ascii=False) + "\n")
        return argparse.Namespace(manifest=manifest, candidates=source, report=None,
                                  execute=execute, check_db=check_db, dsn_env="CATALOG_IMPORT_DSN",
                                  target_kind="disposable", expect_database="catalog_import_test",
                                  expect_host=self.host, expect_port=self.port,
                                  confirm_target=f"catalog_import_test@{self.host}:{self.port}")

    def count(self, table):
        with psycopg.connect(self.dsn) as connection:
            return connection.execute(f"SELECT count(*) FROM {table}").fetchone()[0]

    def test_flyway_nine_versions_and_exact_checksums(self):
        with psycopg.connect(self.dsn) as connection:
            importer.validate_history(connection)
            self.assertEqual(connection.execute("SELECT count(*) FROM flyway_schema_history WHERE success").fetchone()[0], 9)

    def test_approved_category_product_variant_draft_and_idempotent_rerun(self):
        candidate = fixture(1)
        args = self.args([approved_row(candidate)], [candidate], execute=True)
        first = importer.run(args)
        second = importer.run(args)
        self.assertEqual(first["mutations"]["products_created"], 1)
        self.assertEqual(first["mutations"]["variants_created"], 1)
        self.assertEqual(first["mutations"]["categories_created"], 2)
        self.assertEqual(second["mutations"]["unchanged_products"], 1)
        self.assertEqual(second["mutations"]["products_created"], 0)
        with psycopg.connect(self.dsn) as connection:
            product = connection.execute("SELECT category_id,name,status FROM products WHERE id=%s",
                                         (uuid.UUID(candidate["proposed_product_uuid"]),)).fetchone()
            variant = connection.execute("SELECT id,option_combination,selling_price FROM product_variants").fetchone()
            self.assertEqual(product, (importer.category_id("cellphones", candidate["proposed_category_path"]),
                                       candidate["product_name"], "DRAFT"))
            self.assertEqual(variant, (importer.variant_id(uuid.UUID(candidate["proposed_product_uuid"])), {}, 110000))
            self.assertEqual(connection.execute("SELECT count(*) FROM product_images").fetchone()[0], 0)

    def test_explicit_publication_approval_only(self):
        candidate = fixture(2)
        importer.run(self.args([approved_row(candidate, published=True)], [candidate], execute=True))
        with psycopg.connect(self.dsn) as connection:
            self.assertEqual(connection.execute("SELECT status FROM products").fetchone()[0], "PUBLISHED")

    def test_standalone_cli_execute_with_explicit_disposable_target(self):
        candidate = fixture(20)
        args = self.args([approved_row(candidate)], [candidate])
        command = [sys.executable, str(ROOT / "import_postgres.py"),
                   "--manifest", str(args.manifest), "--candidates", str(args.candidates),
                   "--execute", "--target-kind", "disposable",
                   "--expect-database", args.expect_database, "--expect-host", args.expect_host,
                   "--expect-port", str(args.expect_port), "--confirm-target", args.confirm_target]
        result = subprocess.run(command, capture_output=True, text=True, check=False)
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(json.loads(result.stdout)["mutations"]["products_created"], 1)
        self.assertEqual(self.count("products"), 1)

    def test_offline_dry_run_requires_no_database(self):
        candidate = fixture(3)
        args = self.args([approved_row(candidate)], [candidate])
        args.dsn_env = "NONEXISTENT_IMPORT_TEST_DSN"
        report = importer.run(args)
        self.assertEqual(report["eligible"], 1)
        self.assertFalse(report["database_conflicts_checked"])
        self.assertEqual(self.count("products"), 0)

    def test_missing_approvals_stay_pending(self):
        candidate = fixture(4)
        report = importer.run(self.args([manifest_row(candidate)], [candidate]))
        self.assertEqual(report["eligible"], 0)
        self.assertEqual(len(report["pending"]), 1)
        self.assertEqual(len(report["rejected"]), 0)

    def test_missing_selling_price_approval_rejects_write(self):
        candidate = fixture(5)
        row = approved_row(candidate)
        row["selling_price_approval"] = "NOT APPROVED"
        with self.assertRaisesRegex(importer.ImportErrorDetail, "rejected"):
            importer.run(self.args([row], [candidate], execute=True))
        self.assertEqual(self.count("products"), 0)

    def test_missing_and_invalid_approved_price_rejected(self):
        candidate = fixture(6)
        for price in ("", "0", "1.5", "NaN", "-1"):
            with self.subTest(price=price):
                row = approved_row(candidate)
                row["approved_selling_price_vnd"] = price
                report = importer.run(self.args([row], [candidate]))
                self.assertIn("APPROVED_SELLING_PRICE_MUST_BE_POSITIVE_WHOLE_VND", report["rejected"][0]["reasons"])

    def test_invalid_variant_options_rejected(self):
        candidate = fixture(7)
        for options in ('[]', '{"color": 5}', '{"": "red"}', '{"color": " "}'):
            with self.subTest(options=options):
                row = approved_row(candidate)
                row["proposed_variant_options"] = options
                report = importer.run(self.args([row], [candidate]))
                self.assertEqual(len(report["rejected"]), 1)

    def test_invalid_category_reference_rejected(self):
        candidate = fixture(8)
        row = approved_row(candidate)
        row["proposed_category_reference"] = str(uuid.uuid4())
        report = importer.run(self.args([row], [candidate]))
        self.assertIn("CATEGORY_REFERENCE_MISMATCH", report["rejected"][0]["reasons"])

    def test_unknown_approved_existing_category_rejected(self):
        candidate = fixture(9)
        row = approved_row(candidate)
        row["approved_category_uuid"] = str(uuid.uuid4())
        with self.assertRaisesRegex(importer.ImportErrorDetail, "category reference"):
            importer.run(self.args([row], [candidate], execute=True))
        self.assertEqual(self.count("products"), 0)

    def test_duplicate_source_identity_rejected(self):
        candidate = fixture(10)
        row = approved_row(candidate)
        report = importer.run(self.args([row, row], [candidate]))
        self.assertEqual(len(report["rejected"]), 2)
        self.assertEqual(len(report["duplicate_source_identities"]), 1)

    def test_duplicate_approved_sku_rejected(self):
        first, second = fixture(11), fixture(12)
        rows = [approved_row(first), approved_row(second)]
        rows[1]["proposed_catalog_sku"] = rows[0]["proposed_catalog_sku"].lower()
        report = importer.run(self.args(rows, [first, second]))
        self.assertEqual(len(report["rejected"]), 2)
        self.assertEqual(report["eligible"], 0)

    def test_existing_product_conflict_preserves_row(self):
        candidate = fixture(13)
        row = approved_row(candidate)
        importer.run(self.args([row], [candidate], execute=True))
        with psycopg.connect(self.dsn) as connection:
            connection.execute("UPDATE products SET name='Externally changed' WHERE id=%s",
                               (uuid.UUID(candidate["proposed_product_uuid"]),))
        with self.assertRaisesRegex(importer.ImportErrorDetail, "Existing product ID"):
            importer.run(self.args([row], [candidate], execute=True))
        self.assertEqual(self.count("products"), 1)

    def test_case_insensitive_existing_sku_conflict_and_transaction_rollback(self):
        prior = fixture(14, sku="COLLISION")
        importer.run(self.args([approved_row(prior)], [prior], execute=True))
        good = fixture(15, category=["Synthetic Test Category", "New branch"])
        conflict = fixture(16, sku="collision")
        with self.assertRaisesRegex(importer.ImportErrorDetail, "SKU"):
            importer.run(self.args([approved_row(good), approved_row(conflict)],
                                   [good, conflict], execute=True))
        self.assertEqual(self.count("products"), 1)
        self.assertEqual(self.count("categories"), 2)

    def test_read_only_database_check_finds_conflict_without_write(self):
        candidate = fixture(17)
        args = self.args([approved_row(candidate)], [candidate], check_db=True)
        self.assertTrue(importer.run(args)["database_conflicts_checked"])
        with self.assertRaisesRegex(importer.ImportErrorDetail, "category reference"):
            # An explicit external category must exist; preview remains read-only.
            row = approved_row(candidate)
            row["approved_category_uuid"] = str(uuid.uuid4())
            importer.run(self.args([row], [candidate], check_db=True))
        self.assertEqual(self.count("products"), 0)

    def test_wrong_target_confirmation_refused(self):
        candidate = fixture(18)
        args = self.args([approved_row(candidate)], [candidate], execute=True)
        args.confirm_target = "wrong-target"
        with self.assertRaisesRegex(importer.ImportErrorDetail, "confirmed"):
            importer.run(args)
        self.assertEqual(self.count("products"), 0)

    def test_fk_and_deferred_variant_constraint_reject_invalid_raw_sql(self):
        with self.assertRaises(psycopg.errors.ForeignKeyViolation):
            with closing(psycopg.connect(self.dsn)) as connection:
                with connection.transaction():
                    connection.execute("INSERT INTO product_variants(id,product_id,sku,option_combination,selling_price) VALUES (%s,%s,%s,%s,%s)",
                                       (uuid.uuid4(), uuid.uuid4(), "NO-PRODUCT", psycopg.types.json.Jsonb({}), 1))
        with self.assertRaises(psycopg.errors.CheckViolation):
            with closing(psycopg.connect(self.dsn)) as connection:
                with connection.transaction():
                    cid = uuid.uuid4()
                    connection.execute("INSERT INTO categories(id,name) VALUES (%s,%s)", (cid, "Deferred test"))
                    connection.execute("INSERT INTO products(id,category_id,name,status) VALUES (%s,%s,%s,%s)",
                                       (uuid.uuid4(), cid, "Missing variant", "DRAFT"))
        self.assertEqual(self.count("products"), 0)

    def test_deterministic_product_variant_category_ids(self):
        candidate = fixture(19)
        self.assertEqual(candidate["proposed_product_uuid"], normalize.proposed_uuid("cellphones", candidate["source_url"]))
        self.assertEqual(importer.variant_id(uuid.UUID(candidate["proposed_product_uuid"])),
                         importer.variant_id(uuid.UUID(candidate["proposed_product_uuid"])))
        self.assertEqual(importer.category_id("cellphones", candidate["proposed_category_path"]),
                         uuid.UUID(candidate["proposed_category_reference"]))


if __name__ == "__main__":
    unittest.main(verbosity=2)
