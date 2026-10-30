#!/usr/bin/env python3
"""Select a reproducible, unapproved fifty-row review sample."""

import argparse
import csv
import json
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parent
GROUPS = (
    ("cellphones", "Điện thoại", "Điện thoại"),
    ("cellphones", "Laptop", "Laptop"),
    ("cellphones", "Máy tính bảng", "Máy tính bảng"),
    ("cellphones", "Phụ kiện", "Cáp"),
    ("cellphones", "Âm thanh", "Tai nghe"),
    ("cellphones", "Đồ gia dụng", "Nồi cơm"),
    ("tgdd", "Laptop", "Laptop"),
    ("tgdd", "Ốp lưng đ.thoại", "Ốp lưng"),
    ("tgdd", "Máy cạo râu", "Máy cạo râu"),
    ("tgdd", "Máy massage cá nhân", "Máy massage"),
)
FIELDS = (
    "source_retailer", "source_product_id", "source_url", "proposed_product_uuid",
    "product_name", "description", "proposed_category", "proposed_category_reference",
    "approved_category_uuid", "source_sku", "proposed_catalog_sku",
    "exact_source_price_vnd", "approved_selling_price_vnd", "currency",
    "proposed_variant_options", "import_approval", "sku_approval",
    "selling_price_approval", "category_approval", "variant_mapping_approval",
    "publication_approval", "review_notes",
)


def select(records):
    groups = defaultdict(list)
    for record in records:
        variant = record["variant"]
        path = record["proposed_category_path"]
        if (record["validation_status"] != "STRUCTURAL_CANDIDATE"
                or not record["description"] or not path
                or record["category_confidence"] != "MEDIUM"
                or variant["proposed_options"] != {}
                or not variant["sku_structurally_valid"]
                or int(variant["source_exact_price_vnd"] or 0) <= 0
                or "PRODUCT_NAME_REPEATED" in record["validation_reasons"]):
            continue
        groups[(record["source"], path[0])].append(record)
    selected = []
    for source, root, prefix in GROUPS:
        choices = sorted((r for r in groups[(source, root)]
                          if r["product_name"].casefold().startswith(prefix.casefold())),
                         key=lambda r: (r["source_product_id"], r["source_url"]))
        if len(choices) < 5:
            raise ValueError(f"Not enough eligible candidates for {source}/{root}/{prefix}")
        selected.extend(choices[:5])
    return selected


def manifest_row(record):
    variant = record["variant"]
    return {
        "source_retailer": record["source"],
        "source_product_id": record["source_product_id"],
        "source_url": record["source_url"],
        "proposed_product_uuid": record["proposed_product_uuid"],
        "product_name": record["product_name"],
        "description": record["description"],
        "proposed_category": json.dumps(record["proposed_category_path"], ensure_ascii=False),
        "proposed_category_reference": record["proposed_category_reference"],
        "approved_category_uuid": "",
        "source_sku": variant["source_sku"],
        "proposed_catalog_sku": variant["source_sku"],
        "exact_source_price_vnd": variant["source_exact_price_vnd"],
        "approved_selling_price_vnd": "",
        "currency": "VND",
        "proposed_variant_options": json.dumps(variant["proposed_options"], ensure_ascii=False),
        "import_approval": "NOT APPROVED",
        "sku_approval": "NOT APPROVED",
        "selling_price_approval": "NOT APPROVED",
        "category_approval": "NOT APPROVED",
        "variant_mapping_approval": "NOT APPROVED",
        "publication_approval": "NOT APPROVED",
        "review_notes": "Source facts only. Review retailer SKU, category path, single-variant mapping, and selling price independently.",
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--candidates", type=Path, default=ROOT / "output/normalized_product_candidates.jsonl")
    parser.add_argument("--output", type=Path, default=ROOT / "review/demo-products-approval.csv")
    args = parser.parse_args()
    with args.candidates.open(encoding="utf-8") as source:
        selected = select(json.loads(line) for line in source if line.strip())
    if args.output.exists():
        raise SystemExit(f"Review file already exists; choose a new --output path: {args.output}")
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open("w", encoding="utf-8", newline="") as target:
        writer = csv.DictWriter(target, fieldnames=FIELDS, lineterminator="\n")
        writer.writeheader()
        writer.writerows(manifest_row(record) for record in selected)
    print(json.dumps({"selected": len(selected), "output": str(args.output)}, ensure_ascii=False))


if __name__ == "__main__":
    main()
