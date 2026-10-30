#!/usr/bin/env python3
"""Read-only crawler SQLite to deterministic, review-only catalog manifests."""

import argparse
import collections
import csv
import hashlib
import html
import json
import re
import sqlite3
from contextlib import closing
import sys
import unicodedata
import uuid
from decimal import Decimal, InvalidOperation
from pathlib import Path
from urllib.parse import urlsplit

NAMESPACE = uuid.UUID("8f776a17-4fe9-4b13-82af-3e134ba6e55d")
EXPECTED_COLUMNS = {"id", "site", "url", "payload"}
OUTCOMES = ("STRUCTURAL_CANDIDATE", "CATEGORY_OR_FAMILY_ONLY", "NEEDS_ENRICHMENT", "INVALID")
ROOTS = {"cellphones": "CELLPHONES", "tgdd": "Thegioididong.com - Điện thoại, Laptop, Phụ kiện, Đồng hồ chính hãng"}
HOSTS = {"cellphones": "cellphones.com.vn", "tgdd": "www.thegioididong.com"}


def clean(value):
    if not isinstance(value, str):
        return None
    value = re.sub(r"<[^>]*>", " ", value)
    value = unicodedata.normalize("NFC", html.unescape(value))
    return " ".join(value.split()) or None


def source_identity(source, source_url):
    """URL is stable across crawler SKU changes; separate retailers never merge."""
    return f"{source}|{source_url}"


def proposed_uuid(source, source_url):
    return str(uuid.uuid5(NAMESPACE, source_identity(source, source_url)))


def valid_url(value):
    if not isinstance(value, str):
        return False
    p = urlsplit(value)
    return p.scheme in ("http", "https") and bool(p.netloc) and not p.username and not p.password


def category_path(d, name):
    source = d.get("source")
    explicit = clean(d.get("category"))
    crumbs = d.get("breadcrumbs")
    if crumbs is None:
        crumbs = []
    if not isinstance(crumbs, list) or any(not isinstance(x, str) for x in crumbs):
        return None, "CATEGORY_AMBIGUOUS", "LOW"
    crumbs = [clean(x) for x in crumbs]
    if any(x is None for x in crumbs):
        return None, "CATEGORY_AMBIGUOUS", "LOW"
    if crumbs and crumbs[0] == ROOTS.get(source):
        crumbs = crumbs[1:]
    elif crumbs:
        return None, "CATEGORY_AMBIGUOUS", "LOW"
    if explicit:
        # TGDD breadcrumb labels are often promotional titles, not taxonomy nodes.
        return [explicit], None, "MEDIUM"
    if not crumbs:
        return None, "CATEGORY_MISSING", "LOW"
    if name and crumbs[-1].casefold() == name.casefold():
        path = crumbs[:-1]
    else:
        # A final breadcrumb that is not the product may be a category or a
        # different product; never silently choose.
        return None, "CATEGORY_AMBIGUOUS", "LOW"
    if not path:
        return None, "CATEGORY_MISSING", "LOW"
    return path, None, "MEDIUM"


def price_info(d):
    offers = d.get("offers")
    if not isinstance(offers, list) or not offers:
        return None, False, "EXACT_PRICE_MISSING"
    if any(not isinstance(o, dict) for o in offers):
        return None, False, "SOURCE_PAYLOAD_INVALID"
    exact = [o for o in offers if o.get("type") == "Offer"]
    if not exact:
        if all(o.get("type") == "AggregateOffer" for o in offers):
            return None, False, "AGGREGATE_OFFER_ONLY"
        return None, False, "EXACT_PRICE_MISSING"
    values = []
    for o in exact:
        raw = o.get("price")
        if o.get("currency") != "VND" or isinstance(raw, bool) or not isinstance(raw, (str, int)):
            return None, False, "PRICE_INVALID"
        if not re.fullmatch(r"\d+(?:\.\d+)?", str(raw)):
            return None, False, "PRICE_INVALID"
        try:
            p = Decimal(str(raw))
        except InvalidOperation:
            return None, False, "PRICE_INVALID"
        if not p.is_finite() or p < 0 or p != p.to_integral_value():
            return None, False, "PRICE_INVALID"
        values.append(int(p))
    if len(set(values)) != 1:
        return None, False, "VARIANT_MAPPING_UNCONFIRMED"
    return str(values[0]), True, ("ZERO_PRICE_REVIEW" if values[0] == 0 else None)


def base_record(row):
    db_id, site, db_url, payload = row
    reasons = set()
    try:
        d = json.loads(payload)
        if not isinstance(d, dict):
            raise ValueError("payload is not object")
    except (ValueError, TypeError):
        d = {}
        reasons.add("SOURCE_PAYLOAD_INVALID")
    source, sid, url = d.get("source"), d.get("id"), d.get("source_url")
    if not isinstance(source, str) or source not in HOSTS or source != site or not isinstance(sid, str) or not sid.strip() or sid != db_id or not valid_url(url) or url != db_url or (valid_url(url) and urlsplit(url).hostname != HOSTS.get(source)):
        reasons.add("SOURCE_IDENTITY_INVALID")
    name = clean(d.get("name"))
    if not name:
        reasons.add("NAME_MISSING")
    desc = clean(d.get("description"))
    path, category_issue, confidence = category_path(d, name)
    if category_issue:
        reasons.add(category_issue)
    amount, exact, price_issue = price_info(d)
    if price_issue:
        reasons.add(price_issue)
    sku = clean(d.get("sku"))
    if not sku:
        reasons.add("SKU_MISSING")
    elif sku.casefold() in {"0", "n/a", "na", "null", "none", "unknown"}:
        reasons.add("PLACEHOLDER_SKU")
    raw_images = d.get("images")
    if not isinstance(raw_images, list):
        raw_images = []
        reasons.add("IMAGES_MALFORMED")
    images = sorted(set(x for x in raw_images if valid_url(x)))
    if len(images) != len(set(str(x) for x in raw_images)):
        reasons.add("IMAGE_URL_INVALID")
    raw = d.get("raw_product")
    if not isinstance(raw, dict):
        raw = {}
    variant_group = bool(raw.get("hasVariant")) or "ProductGroup" in str(raw.get("@type", ""))
    if variant_group:
        reasons.add("VARIANT_MAPPING_UNCONFIRMED")
    identity = source_identity(source, url) if "SOURCE_IDENTITY_INVALID" not in reasons else None
    return {
        "source": source, "source_product_id": sid, "source_url": url,
        "source_identity": identity,
        "proposed_product_uuid": proposed_uuid(source, url) if identity else None,
        "product_name": name, "description": desc, "proposed_status": "DRAFT",
        "source_category": clean(d.get("category")), "breadcrumbs": d.get("breadcrumbs"),
        "proposed_category_path": path, "category_confidence": confidence,
        "proposed_category_reference": None,
        "variant": {
            "source_sku": sku, "sku_provenance": "crawler_payload.sku" if sku else None,
            "source_exact_price_vnd": amount, "exact_source_price_available": exact,
            "selling_price_approved": False, "sku_structurally_valid": bool(sku and "PLACEHOLDER_SKU" not in reasons),
            "sku_approved_for_target_catalog": False, "variant_mapping_approved": False,
            "proposed_options": {} if exact and amount is not None and int(amount) > 0 and sku and "PLACEHOLDER_SKU" not in reasons and not variant_group else None,
        },
        "external_image_urls": images, "validation_reasons": reasons,
        "_raw_category_key": (source, tuple(path)) if path else None,
        "_original_category_path": tuple((d.get("breadcrumbs") or [])[1:-1]) if source == "tgdd" and isinstance(d.get("breadcrumbs"), list) else tuple(path or []),
    }


def classify(r):
    reasons = r["validation_reasons"]
    if "SOURCE_PAYLOAD_INVALID" in reasons or "SOURCE_IDENTITY_INVALID" in reasons or "NAME_MISSING" in reasons or "DUPLICATE_SOURCE_IDENTITY" in reasons:
        outcome = "INVALID"
    elif "AGGREGATE_OFFER_ONLY" in reasons:
        outcome = "CATEGORY_OR_FAMILY_ONLY"
    elif reasons - {"SELLING_PRICE_UNAPPROVED", "SKU_APPROVAL_REQUIRED", "VARIANT_APPROVAL_REQUIRED", "PRODUCT_NAME_REPEATED"}:
        outcome = "NEEDS_ENRICHMENT"
    else:
        outcome = "STRUCTURAL_CANDIDATE"
    r["validation_status"] = outcome
    r["validation_reasons"] = sorted(reasons)
    return r


def normalize(rows):
    records = [base_record(row) for row in rows]
    identities = collections.Counter(r["source_identity"] for r in records if r["source_identity"])
    skus = collections.Counter(r["variant"]["source_sku"].casefold() for r in records if r["variant"]["sku_structurally_valid"])
    names = collections.Counter(r["product_name"].casefold() for r in records if r["product_name"])
    for r in records:
        reasons = r["validation_reasons"]
        if r["source_identity"] and identities[r["source_identity"]] > 1:
            reasons.add("DUPLICATE_SOURCE_IDENTITY")
        sku = r["variant"]["source_sku"]
        if sku and r["variant"]["sku_structurally_valid"] and skus[sku.casefold()] > 1:
            reasons.add("DUPLICATE_SKU")
            r["variant"]["sku_structurally_valid"] = False
        if r["product_name"] and names[r["product_name"].casefold()] > 1:
            reasons.add("PRODUCT_NAME_REPEATED")
        if r["variant"]["exact_source_price_available"]:
            reasons.add("SELLING_PRICE_UNAPPROVED")
        if sku and "PLACEHOLDER_SKU" not in reasons:
            reasons.add("SKU_APPROVAL_REQUIRED")
        if r["variant"]["proposed_options"] is not None:
            reasons.add("VARIANT_APPROVAL_REQUIRED")
        classify(r)
    return sorted(records, key=lambda r: ((r["source"] or ""), (r["source_product_id"] or ""), (r["source_url"] or "")))


def category_rows(records):
    groups = collections.defaultdict(list)
    for r in records:
        key = r["_raw_category_key"]
        if key:
            groups[(key[0], r["_original_category_path"], key[1])].append(r)
    out = []
    for (source, original_path, path), members in sorted(groups.items()):
        ref = str(uuid.uuid5(NAMESPACE, "category|" + source + "|" + json.dumps(path, ensure_ascii=False)))
        for r in members:
            r["proposed_category_reference"] = ref
        out.append({"source": source, "original_category_path": json.dumps(list(original_path), ensure_ascii=False),
                    "proposed_normalized_category_path": json.dumps(list(path), ensure_ascii=False),
                    "proposed_category_reference": ref, "record_count": len(members),
                    "mapping_confidence": "MEDIUM", "manual_approval_required": "true",
                    "example_source_product_id": members[0]["source_product_id"],
                    "example_source_url": members[0]["source_url"]})
    return out


def public_record(r):
    return {k: v for k, v in r.items() if not k.startswith("_")}


def summary(records, categories):
    outcomes = collections.Counter(r["validation_status"] for r in records)
    reasons = collections.Counter(code for r in records for code in r["validation_reasons"])
    all_sku_counts = collections.Counter(r["variant"]["source_sku"].casefold() for r in records if r["variant"]["source_sku"])
    sku_counts = {k: v for k, v in all_sku_counts.items() if k != "0"}
    return {"source_records": len(records), "outcomes": {k: outcomes[k] for k in OUTCOMES},
            "reasons": dict(sorted(reasons.items())), "category_mapping_rows": len(categories),
            "normalized_category_paths": len(set(r["proposed_category_reference"] for r in categories)),
            "sku_collision_groups_including_placeholder": sum(v > 1 for v in all_sku_counts.values()),
            "sku_collision_extra_records_including_placeholder": sum(v - 1 for v in all_sku_counts.values()),
            "sku_collision_groups_excluding_placeholder": sum(v > 1 for v in sku_counts.values()),
            "sku_collision_extra_records_excluding_placeholder": sum(v - 1 for v in sku_counts.values()),
            "exact_price_records": sum(r["variant"]["exact_source_price_available"] for r in records),
            "positive_exact_price_structural_candidates": outcomes["STRUCTURAL_CANDIDATE"],
            "approved_for_import": 0, "approved_for_publication": 0}


def write_jsonl(path, records):
    with path.open("w", encoding="utf-8", newline="\n") as f:
        for r in records:
            f.write(json.dumps(public_record(r), ensure_ascii=False, sort_keys=True, separators=(",", ":")) + "\n")


def run(input_db, output_dir, dry_run=False):
    uri = input_db.resolve().as_uri() + "?mode=ro"
    with closing(sqlite3.connect(uri, uri=True)) as db:
        db.execute("PRAGMA query_only=ON")
        columns = {row[1] for row in db.execute("PRAGMA table_info(products)")}
        if not EXPECTED_COLUMNS <= columns:
            raise ValueError(f"products columns missing: {sorted(EXPECTED_COLUMNS - columns)}")
        rows = db.execute("SELECT id,site,url,payload FROM products ORDER BY id,site,url").fetchall()
    records = normalize(rows)
    categories = category_rows(records)
    stats = summary(records, categories)
    if sum(stats["outcomes"].values()) != len(rows):
        raise AssertionError("classification does not reconcile")
    if dry_run:
        return stats
    output_dir.mkdir(parents=True, exist_ok=True)
    with (output_dir / "normalized_categories.csv").open("w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=["source", "original_category_path", "proposed_normalized_category_path", "proposed_category_reference", "record_count", "mapping_confidence", "manual_approval_required", "example_source_product_id", "example_source_url"])
        writer.writeheader()
        writer.writerows(categories)
    write_jsonl(output_dir / "normalized_product_candidates.jsonl", (r for r in records if r["validation_status"] == "STRUCTURAL_CANDIDATE"))
    write_jsonl(output_dir / "pending_or_rejected_records.jsonl", (r for r in records if r["validation_status"] != "STRUCTURAL_CANDIDATE"))
    (output_dir / "validation_summary.json").write_text(json.dumps(stats, ensure_ascii=False, sort_keys=True, indent=2) + "\n", encoding="utf-8")
    report = ["# Catalog normalization report", "", "Review-only output. No PostgreSQL connection or writes.", "", f"Source records: {stats['source_records']}", "", "## Outcomes", ""]
    report += [f"- {k}: {stats['outcomes'][k]}" for k in OUTCOMES]
    report += ["", f"Normalized category paths: {stats['normalized_category_paths']}", f"Category mapping rows: {len(categories)}", f"Nonplaceholder SKU collision groups: {stats['sku_collision_groups_excluding_placeholder']}", "", "## Validation reasons", ""]
    report += [f"- {k}: {v}" for k, v in sorted(stats["reasons"].items(), key=lambda item: (-item[1], item[0]))]
    report += ["", "Structural candidates have retailer source prices and source SKUs only. Selling price, SKU and variant approval are all false. Every proposed product status is DRAFT. Category paths require manual approval. External image URLs are trace metadata only.", ""]
    (output_dir / "normalization_report.md").write_text("\n".join(report), encoding="utf-8")
    return stats


def main():
    default_db = Path(__file__).resolve().parents[3] / "electronics-crawler/data/catalogue.sqlite"
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--input-db", type=Path, default=default_db)
    p.add_argument("--output-dir", type=Path, default=Path(__file__).resolve().parent / "output")
    p.add_argument("--dry-run", action="store_true", help="validate and count without creating output files")
    args = p.parse_args()
    stats = run(args.input_db, args.output_dir, args.dry_run)
    print(json.dumps(stats, ensure_ascii=False, sort_keys=True, indent=2))


if __name__ == "__main__":
    main()
