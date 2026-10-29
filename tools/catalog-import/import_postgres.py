#!/usr/bin/env python3
"""Review-gated Catalog importer. Offline dry-run is the default."""

import argparse
import collections
import csv
import json
import os
import re
import sys
import uuid
import zlib
from decimal import Decimal, InvalidOperation
from pathlib import Path
from urllib.parse import unquote, urlsplit

import normalize
from prepare_pilot import FIELDS

ROOT = Path(__file__).resolve().parent
MIGRATIONS = ROOT.parents[1] / "demo/src/main/resources/db/migration"
APPROVALS = ("import_approval", "sku_approval", "selling_price_approval",
             "category_approval", "variant_mapping_approval", "publication_approval")
MIGRATION_FILES = (
    "V1__create_catalog_core.sql", "V2__create_product_images.sql",
    "V3__enforce_catalog_relationships.sql", "V4__create_users.sql",
    "V5__create_addresses.sql", "V6__authorities_and_username_policy.sql",
    "V7__authentication_sessions.sql", "V8__image_uploads.sql",
    "V9__user_profile_images.sql", "V10__catalog_data_enhancement.sql",
)


class ImportErrorDetail(Exception):
    pass


def category_id(source, path):
    return uuid.uuid5(normalize.NAMESPACE,
                      "category|" + source + "|" + json.dumps(path, ensure_ascii=False))


def variant_id(product_id):
    # This importer supports one approved variant per source product.
    return uuid.uuid5(product_id, "variant|single")


def flyway_checksum(path):
    """Flyway SQL checksum: CRC32 of UTF-8 lines without line terminators."""
    checksum = 0
    with path.open(encoding="utf-8-sig", newline=None) as stream:
        for line in stream:
            checksum = zlib.crc32(line.rstrip("\r\n").encode("utf-8"), checksum)
    return checksum - 2**32 if checksum >= 2**31 else checksum


def read_candidates(path):
    records = {}
    duplicates = set()
    with path.open(encoding="utf-8") as stream:
        for line_number, line in enumerate(stream, 1):
            if not line.strip():
                continue
            record = json.loads(line)
            identity = record.get("source_identity")
            if identity in records:
                duplicates.add(identity)
            records[identity] = record
    return records, duplicates


def read_manifest(path):
    with path.open(encoding="utf-8-sig", newline="") as stream:
        reader = csv.DictReader(stream)
        if reader.fieldnames != list(FIELDS):
            raise ImportErrorDetail("Manifest columns do not match the documented approval schema")
        rows = list(reader)
    if any(set(row) != set(FIELDS) or any(value is None for value in row.values()) for row in rows):
        raise ImportErrorDetail("Manifest contains an incomplete or extra-field CSV row")
    if not rows:
        raise ImportErrorDetail("Manifest is empty")
    return rows


def valid_price(raw):
    if not re.fullmatch(r"[0-9]+(?:\.0+)?", raw or ""):
        return None
    try:
        amount = Decimal(raw)
    except InvalidOperation:
        return None
    return amount if amount.is_finite() and amount > 0 and amount == amount.to_integral_value() else None


def valid_options(raw):
    try:
        value = json.loads(raw)
    except (TypeError, ValueError):
        return None
    if not isinstance(value, dict):
        return None
    if any(not isinstance(k, str) or not k.strip() or not isinstance(v, str)
           or not v.strip() for k, v in value.items()):
        return None
    return value


def inspect_row(row, candidate, duplicate_identity=False):
    issues = []
    identity = row["source_retailer"] + "|" + row["source_url"]
    if duplicate_identity:
        issues.append("DUPLICATE_SOURCE_IDENTITY")
    if candidate is None:
        issues.append("SOURCE_IDENTITY_NOT_IN_CANDIDATES")
        return None, issues
    variant = candidate.get("variant") or {}
    path = candidate.get("proposed_category_path")
    facts = {
        "source_retailer": candidate.get("source"),
        "source_product_id": candidate.get("source_product_id"),
        "source_url": candidate.get("source_url"),
        "proposed_product_uuid": candidate.get("proposed_product_uuid"),
        "product_name": candidate.get("product_name"),
        "description": candidate.get("description") or "",
        "proposed_category": json.dumps(path, ensure_ascii=False),
        "proposed_category_reference": candidate.get("proposed_category_reference"),
        "source_sku": variant.get("source_sku"),
        "exact_source_price_vnd": variant.get("source_exact_price_vnd"),
        "currency": "VND",
        "proposed_variant_options": json.dumps(variant.get("proposed_options"), ensure_ascii=False),
    }
    for field, expected in facts.items():
        actual = row[field]
        if field in ("proposed_category", "proposed_variant_options"):
            try:
                actual = json.loads(actual)
                expected = json.loads(expected)
            except (TypeError, ValueError):
                pass
        if actual != expected:
            issues.append("SOURCE_FACT_MISMATCH:" + field)
    if (candidate.get("validation_status") != "STRUCTURAL_CANDIDATE"
            or not candidate.get("source_identity") == identity
            or not candidate.get("variant", {}).get("exact_source_price_available")
            or not candidate.get("variant", {}).get("sku_structurally_valid")
            or not isinstance(path, list) or not path
            or any(not isinstance(x, str) or not x.strip() for x in path)
            or valid_price(str(variant.get("source_exact_price_vnd") or "")) is None):
        issues.append("SOURCE_NOT_SUITABLE_FOR_SINGLE_VARIANT_IMPORT")
    try:
        product_uuid = uuid.UUID(row["proposed_product_uuid"])
        category_ref = uuid.UUID(row["proposed_category_reference"])
        if product_uuid != uuid.UUID(normalize.proposed_uuid(row["source_retailer"], row["source_url"])):
            issues.append("PRODUCT_UUID_MISMATCH")
        if category_ref != category_id(row["source_retailer"], path):
            issues.append("CATEGORY_REFERENCE_MISMATCH")
    except (ValueError, TypeError):
        issues.append("INVALID_DETERMINISTIC_ID")
        return None, issues
    for field in APPROVALS:
        if row[field] not in ("APPROVED", "NOT APPROVED"):
            issues.append("INVALID_APPROVAL_VALUE:" + field)
    if row["currency"] != "VND":
        issues.append("CURRENCY_MUST_BE_VND")
    approved = row["import_approval"] == "APPROVED"
    if not approved:
        issues.extend("APPROVAL_REQUIRED:" + field for field in APPROVALS[:5]
                      if row[field] != "APPROVED")
        if not row["approved_selling_price_vnd"]:
            issues.append("APPROVED_SELLING_PRICE_REQUIRED")
        return None, issues
    for field in APPROVALS[1:5]:
        if row[field] != "APPROVED":
            issues.append("APPROVAL_REQUIRED:" + field)
    price = valid_price(row["approved_selling_price_vnd"])
    if price is None:
        issues.append("APPROVED_SELLING_PRICE_MUST_BE_POSITIVE_WHOLE_VND")
    sku = row["proposed_catalog_sku"]
    if not sku or not sku.strip() or sku != sku.strip() or sku.casefold() in {"0", "na", "n/a", "none", "null", "unknown"}:
        issues.append("APPROVED_CATALOG_SKU_INVALID")
    options = valid_options(row["proposed_variant_options"])
    if options is None:
        issues.append("VARIANT_OPTIONS_MUST_BE_FLAT_NONBLANK_STRING_MAP")
    target_category = category_ref
    if row["approved_category_uuid"]:
        try:
            target_category = uuid.UUID(row["approved_category_uuid"])
        except ValueError:
            issues.append("APPROVED_CATEGORY_UUID_INVALID")
    if issues:
        return None, issues
    return {
        "identity": identity, "product_id": product_uuid, "category_id": target_category,
        "category_path": path, "source": row["source_retailer"],
        "create_category_path": not bool(row["approved_category_uuid"]),
        "name": row["product_name"], "description": row["description"] or None,
        "status": "PUBLISHED" if row["publication_approval"] == "APPROVED" else "DRAFT",
        "variant_id": variant_id(product_uuid), "sku": sku, "price": price, "options": options,
    }, []


def plan(manifest, candidates, candidate_duplicates):
    rows = read_manifest(manifest)
    identities = collections.Counter(r["source_retailer"] + "|" + r["source_url"] for r in rows)
    plans, pending, rejected = [], [], []
    for index, row in enumerate(rows, 2):
        identity = row["source_retailer"] + "|" + row["source_url"]
        item, issues = inspect_row(row, candidates.get(identity),
                                   identities[identity] > 1 or identity in candidate_duplicates)
        entry = {"line": index, "source_identity": identity, "reasons": issues}
        if item:
            plans.append(item)
        elif row["import_approval"] == "APPROVED" or any(x.startswith(("SOURCE_", "DUPLICATE_", "INVALID_", "PRODUCT_UUID_", "CATEGORY_REFERENCE_")) for x in issues):
            rejected.append(entry)
        else:
            pending.append(entry)
    sku_counts = collections.Counter(p["sku"].casefold() for p in plans)
    proposed_sku_counts = collections.Counter(r["proposed_catalog_sku"].casefold() for r in rows
                                              if r["proposed_catalog_sku"].strip())
    for item in list(plans):
        if sku_counts[item["sku"].casefold()] > 1:
            rejected.append({"source_identity": item["identity"], "reasons": ["DUPLICATE_APPROVED_SKU"]})
            plans.remove(item)
    categories = {}
    for row in rows:
        identity = row["source_retailer"] + "|" + row["source_url"]
        candidate = candidates.get(identity)
        path = candidate.get("proposed_category_path") if candidate else None
        if not isinstance(path, list) or not path:
            continue
        for length in range(1, len(path) + 1):
            prefix = path[:length]
            cid = category_id(row["source_retailer"], prefix)
            categories[str(cid)] = {"id": str(cid), "name": prefix[-1],
                                    "parent_id": str(category_id(row["source_retailer"], prefix[:-1])) if length > 1 else None}
    proposals = [{"source_identity": r["source_retailer"] + "|" + r["source_url"],
                  "product_id": r["proposed_product_uuid"],
                  "category_reference": r["proposed_category_reference"],
                  "variant_id": str(variant_id(uuid.UUID(r["proposed_product_uuid"]))) if re.fullmatch(r"[0-9a-fA-F-]{36}", r["proposed_product_uuid"]) else None,
                  "proposed_sku": r["proposed_catalog_sku"],
                  "approved_price_vnd": r["approved_selling_price_vnd"] or None}
                 for r in rows]
    return {"manifest_rows": len(rows), "eligible": len(plans), "pending": pending,
            "rejected": rejected, "duplicate_source_identities": sorted(k for k, v in identities.items() if v > 1),
            "duplicate_proposed_skus": sorted(k for k, v in proposed_sku_counts.items() if v > 1),
            "proposed_categories": sorted(categories.values(), key=lambda c: c["id"]),
            "proposed_products_and_variants": proposals,
            "database_conflicts_checked": False}, plans


def target_connection(args, read_only):
    if not (args.target_kind and args.expect_database and args.expect_host
            and args.expect_port and args.confirm_target):
        raise ImportErrorDetail("Database access requires target kind, expected database/host/port, and exact confirmation")
    if args.target_kind not in ("disposable", "local-development"):
        raise ImportErrorDetail("Unknown database target kind")
    dsn = os.environ.get(args.dsn_env)
    if not dsn:
        raise ImportErrorDetail("Database DSN environment variable is unset")
    parsed = urlsplit(dsn)
    host = parsed.hostname
    database = unquote(parsed.path.lstrip("/"))
    if (parsed.scheme not in ("postgresql", "postgres") or host not in ("localhost", "127.0.0.1", "::1")
            or parsed.port is None or database != args.expect_database
            or host != args.expect_host or parsed.port != args.expect_port
            or args.confirm_target != f"{database}@{host}:{parsed.port}"):
        raise ImportErrorDetail("Target URL does not match the explicitly confirmed loopback database")
    if args.target_kind == "local-development" and database != "backend":
        raise ImportErrorDetail("Local development target must be the configured backend database")
    if args.target_kind == "disposable" and not database.endswith("_test"):
        raise ImportErrorDetail("Disposable target database name must end in _test")
    try:
        import psycopg
    except ImportError as failure:
        raise ImportErrorDetail("Install requirements-import.txt before database access") from failure
    try:
        connection = psycopg.connect(dsn, connect_timeout=5)
    except psycopg.Error:
        raise ImportErrorDetail("Cannot connect to the explicitly confirmed PostgreSQL target") from None
    try:
        if read_only:
            connection.execute("SET TRANSACTION READ ONLY")
        actual_db, schema, version = connection.execute(
            "SELECT current_database(), current_schema(), current_setting('server_version_num')::integer").fetchone()
        if actual_db != database or schema != "public" or not 170000 <= version < 180000:
            raise ImportErrorDetail("Connected database/schema/version does not match PostgreSQL 17 public target")
        validate_history(connection)
        return connection
    except BaseException:
        connection.rollback()
        connection.close()
        raise


def validate_history(connection):
    try:
        history = connection.execute(
            "SELECT version, script, checksum, success FROM public.flyway_schema_history ORDER BY installed_rank").fetchall()
    except Exception as failure:
        raise ImportErrorDetail("Flyway schema history is missing or unreadable") from failure
    expected = [(str(index), name, flyway_checksum(MIGRATIONS / name), True)
                for index, name in enumerate(MIGRATION_FILES, 1)]
    if history != expected:
        raise ImportErrorDetail("Flyway history differs from the exact successful unified V1-V10 sequence")
    for table in ("categories", "products", "product_variants", "product_images"):
        if connection.execute("SELECT to_regclass(%s)", ("public." + table,)).fetchone()[0] is None:
            raise ImportErrorDetail("Required catalog table is missing: " + table)


def ensure_category(connection, cid, name, parent_id):
    existing = connection.execute(
        "SELECT name, parent_category_id FROM public.categories WHERE id = %s", (cid,)).fetchone()
    if existing:
        if existing != (name, parent_id):
            raise ImportErrorDetail("Existing category ID conflicts with approved category hierarchy")
        return False
    connection.execute("INSERT INTO public.categories(id,name,parent_category_id) VALUES (%s,%s,%s)",
                       (cid, name, parent_id))
    return True


def inspect_existing(connection, item, allow_missing_category=False):
    found_category = connection.execute("SELECT id FROM public.categories WHERE id = %s", (item["category_id"],)).fetchone()
    if not found_category and not allow_missing_category:
        raise ImportErrorDetail("Approved category reference does not exist")
    sku_owner = connection.execute("SELECT id, product_id FROM public.product_variants WHERE lower(sku) = lower(%s)",
                                   (item["sku"],)).fetchone()
    if sku_owner and sku_owner != (item["variant_id"], item["product_id"]):
        raise ImportErrorDetail("Case-insensitive SKU already belongs to a different variant")
    product = connection.execute("SELECT category_id,name,description,status FROM public.products WHERE id = %s",
                                 (item["product_id"],)).fetchone()
    if product is None:
        if sku_owner:
            raise ImportErrorDetail("Variant SKU exists but deterministic product is absent")
        return False
    if product != (item["category_id"], item["name"], item["description"], item["status"]):
        raise ImportErrorDetail("Existing product ID has different approved fields; no update performed")
    variants = connection.execute(
        "SELECT id,sku,option_combination,selling_price FROM public.product_variants WHERE product_id = %s",
        (item["product_id"],)).fetchall()
    if len(variants) != 1 or variants[0] != (item["variant_id"], item["sku"], item["options"], item["price"]):
        raise ImportErrorDetail("Existing product variant differs from approved single-variant mapping")
    return True


def apply_plans(connection, plans, execute):
    from psycopg.types.json import Jsonb
    counts = {"categories_created": 0, "products_created": 0,
              "variants_created": 0, "unchanged_products": 0}
    for item in plans:
        if item["create_category_path"]:
            for length in range(1, len(item["category_path"]) + 1):
                prefix = item["category_path"][:length]
                cid = category_id(item["source"], prefix)
                parent = category_id(item["source"], prefix[:-1]) if length > 1 else None
                if execute:
                    counts["categories_created"] += ensure_category(connection, cid, prefix[-1], parent)
                elif connection.execute("SELECT name,parent_category_id FROM public.categories WHERE id=%s", (cid,)).fetchone() not in (None, (prefix[-1], parent)):
                    raise ImportErrorDetail("Existing category ID conflicts with approved category hierarchy")
        already = inspect_existing(connection, item,
                                   allow_missing_category=not execute and item["create_category_path"])
        if already:
            counts["unchanged_products"] += 1
            continue
        if execute:
            connection.execute("INSERT INTO public.products(id,category_id,name,description,status) VALUES (%s,%s,%s,%s,%s)",
                               (item["product_id"], item["category_id"], item["name"], item["description"], item["status"]))
            connection.execute("INSERT INTO public.product_variants(id,product_id,sku,option_combination,selling_price) VALUES (%s,%s,%s,%s,%s)",
                               (item["variant_id"], item["product_id"], item["sku"], Jsonb(item["options"]), item["price"]))
            counts["products_created"] += 1
            counts["variants_created"] += 1
    return counts


def save_report(report, path):
    if path:
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(report, ensure_ascii=False, indent=2, default=str) + "\n", encoding="utf-8")


def run(args):
    candidates, candidate_duplicates = read_candidates(args.candidates)
    report, plans = plan(args.manifest, candidates, candidate_duplicates)
    report["mode"] = "execute" if args.execute else "dry-run"
    report["mutations"] = None
    if args.execute and report["rejected"]:
        first = report["rejected"][0]
        report["error"] = (f"{len(report['rejected'])} rejected row(s); first at line {first.get('line', '?')}: "
                           + ", ".join(first["reasons"]) + "; no database write attempted")
        save_report(report, args.report)
        raise ImportErrorDetail(report["error"])
    if args.execute and not plans:
        report["error"] = "No fully approved records are eligible; no database write attempted"
        save_report(report, args.report)
        raise ImportErrorDetail(report["error"])
    if args.execute or args.check_db:
        try:
            connection = target_connection(args, read_only=not args.execute)
            try:
                report["database_conflicts_checked"] = True
                report["mutations"] = apply_plans(connection, plans, args.execute)
                if args.execute:
                    connection.commit()  # Deferred Catalog triggers execute here.
                else:
                    connection.rollback()
            except BaseException as failure:
                connection.rollback()
                if isinstance(failure, ImportErrorDetail):
                    raise
                if getattr(failure, "sqlstate", None):
                    constraint = getattr(getattr(failure, "diag", None), "constraint_name", None)
                    raise ImportErrorDetail("PostgreSQL rejected the import"
                                            + (f" at constraint {constraint}" if constraint else "")
                                            + f" (SQLSTATE {failure.sqlstate}); transaction rolled back") from None
                raise ImportErrorDetail("Import failed; transaction rolled back") from None
            finally:
                connection.close()
        except ImportErrorDetail as failure:
            report["error"] = str(failure)
            report["mutations"] = None
            save_report(report, args.report)
            raise
    save_report(report, args.report)
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", type=Path, default=ROOT / "review/demo-products-approval.csv")
    parser.add_argument("--candidates", type=Path, default=ROOT / "output/normalized_product_candidates.jsonl")
    parser.add_argument("--report", type=Path)
    action = parser.add_mutually_exclusive_group()
    action.add_argument("--dry-run", action="store_true", help="Default: offline validation with no database connection")
    action.add_argument("--check-db", action="store_true", help="Read-only target and conflict check")
    action.add_argument("--execute", action="store_true", help="Write approved rows in one transaction")
    parser.add_argument("--dsn-env", default="CATALOG_IMPORT_DSN")
    parser.add_argument("--target-kind", choices=("disposable", "local-development"))
    parser.add_argument("--expect-database")
    parser.add_argument("--expect-host")
    parser.add_argument("--expect-port", type=int)
    parser.add_argument("--confirm-target")
    args = parser.parse_args()
    try:
        report = run(args)
    except (ImportErrorDetail, OSError, ValueError, KeyError, json.JSONDecodeError) as failure:
        print(json.dumps({"error": str(failure), "database_password_printed": False}, ensure_ascii=False), file=sys.stderr)
        return 2
    print(json.dumps({"mode": report["mode"], "manifest_rows": report["manifest_rows"],
                      "eligible": report["eligible"], "pending": len(report["pending"]),
                      "rejected": len(report["rejected"]),
                      "duplicate_proposed_skus": len(report["duplicate_proposed_skus"]),
                      "proposed_categories": len(report["proposed_categories"]),
                      "proposed_products_and_variants": len(report["proposed_products_and_variants"]),
                      "database_conflicts_checked": report["database_conflicts_checked"],
                      "mutations": report["mutations"],
                      "details_report": str(args.report) if args.report else None}, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    sys.exit(main())
