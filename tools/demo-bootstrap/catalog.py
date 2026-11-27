"""Read-only source integrity and reviewed catalog validation primitives."""
from __future__ import annotations

from collections import Counter
import io
import json
from pathlib import Path
import shutil
import tarfile
import tempfile
from typing import BinaryIO, Any
import uuid
import csv
import copy
from decimal import Decimal
import re

from bootstrap import (SOURCE_REVISION,SOURCE_MANIFEST_SHA256, BootstrapError, atomic_json, digest_bytes,
                       digest_file, member_path, read_json, require, unique_fields,
                       invalid_constant)


def jsonl(path: Path) -> list[dict[str, Any]]:
    result = []
    with path.open(encoding="utf-8") as stream:
        for line in stream:
            if line.strip():
                try:
                    value = json.loads(line, object_pairs_hook=unique_fields, parse_constant=invalid_constant)
                except (ValueError, UnicodeError):
                    raise BootstrapError("INVALID_JSONL", "Invalid source record encoding") from None
                require(isinstance(value, dict), "INVALID_JSONL", "Source records must be objects")
                result.append(value)
    return result


def snapshot_archive(stream: BinaryIO, output: Path) -> dict[str, Any]:
    """Consume host `git archive <fixed revision>` without needing Git in the image."""
    require(not output.exists() and not output.is_symlink(), "SNAPSHOT_EXISTS",
            "Exact Git snapshot output must be a new directory", 2)
    output.parent.mkdir(parents=True, exist_ok=True)
    temporary = Path(tempfile.mkdtemp(prefix=".git-snapshot-", dir=output.parent))
    count = total = 0
    seen: set[str] = set()
    try:
        with tarfile.open(fileobj=stream, mode="r|") as archive:
            for entry in archive:
                require(archive.pax_headers.get("comment") == SOURCE_REVISION,
                        "SOURCE_REVISION_MISMATCH", "Archive must identify the approved Git revision")
                require(entry.isdir() or entry.isfile(), "UNSAFE_ARCHIVE", "Archive links/devices are forbidden")
                name = entry.name.rstrip("/")
                require(name not in seen, "UNSAFE_ARCHIVE", "Duplicate archive entry")
                seen.add(name)
                target = member_path(temporary, name)
                if entry.isdir():
                    target.mkdir(parents=True, exist_ok=True)
                    continue
                total += entry.size
                count += 1
                require(entry.size <= 32 * 1024 * 1024 and total <= 512 * 1024 * 1024
                        and count <= 10000, "ARCHIVE_TOO_LARGE", "Source archive exceeds package limits")
                target.parent.mkdir(parents=True, exist_ok=True)
                source = archive.extractfile(entry)
                require(source is not None, "INVALID_ARCHIVE", "Missing archive member data")
                with target.open("xb") as destination:
                    shutil.copyfileobj(source, destination, length=1024 * 1024)
            require(archive.pax_headers.get("comment") == SOURCE_REVISION and count > 0,
                    "SOURCE_REVISION_MISMATCH", "Archive does not identify the approved Git revision")
        atomic_json(temporary / ".bootstrap-source-snapshot.json",
                    {"source_revision": SOURCE_REVISION, "files": count, "bytes": total})
        temporary.rename(output)
    except BaseException:
        # Only delete the fresh temporary directory constructed inside output.parent.
        if temporary.exists():
            shutil.rmtree(temporary)
        raise
    return {"source_revision": SOURCE_REVISION, "snapshot_files": count, "snapshot_bytes": total}


def _valid_uuid(value: Any) -> str:
    try:
        return str(uuid.UUID(value))
    except (ValueError, TypeError, AttributeError):
        raise BootstrapError("INVALID_SOURCE_ID", "Source UUID is invalid") from None


def verify_source(working: Path, canonical: Path, *, decode_images: bool = True) -> dict[str, Any]:
    """Validate exact Git bytes; accept only the observed checkout CRLF conversion."""
    from PIL import Image

    snapshot = read_json(member_path(canonical, ".bootstrap-source-snapshot.json"))
    require(snapshot.get("source_revision") == SOURCE_REVISION,
            "SOURCE_REVISION_MISMATCH", "Git snapshot revision is incompatible")
    manifest = read_json(member_path(canonical, "manifests/catalog_manifest.json"))
    require(digest_file(member_path(canonical,'manifests/catalog_manifest.json'))==SOURCE_MANIFEST_SHA256,
            'SOURCE_MANIFEST_MISMATCH','Source manifest differs from the fixed approved Git blob')
    require(manifest.get("version") == "amazon-mvp1500-seed-1", "SOURCE_VERSION_MISMATCH",
            "Unsupported source package version")
    newline_paths: list[str] = []
    checked = 0
    for exact in canonical.rglob("*"):
        if not exact.is_file() or exact.name == ".bootstrap-source-snapshot.json":
            continue
        relative = exact.relative_to(canonical).as_posix()
        current = member_path(working, relative)
        require(current.is_file(), "SOURCE_FILE_MISSING", "Tracked source file is missing")
        a, b = exact.read_bytes(), current.read_bytes()
        if a != b:
            # Only LF -> CRLF checkout conversion of nonbinary source text is approved.
            require(b"\x00" not in a and b"\x00" not in b and b.replace(b"\r\n", b"\n") == a,
                    "SOURCE_WORKTREE_DRIFT", "Source bytes differ beyond approved checkout newlines")
            newline_paths.append(relative)
        checked += 1
    hashes = manifest.get("file_sha256", {})
    require(isinstance(hashes, dict) and bool(hashes), "INVALID_SOURCE_MANIFEST", "Source hashes are missing")
    for name, expected in hashes.items():
        require(digest_file(member_path(canonical, name)) == expected,
                "SOURCE_CHECKSUM_MISMATCH", "Exact Git source checksum mismatch")
    expected_list = "".join(f"{hashes[name]}  {name}\n" for name in sorted(hashes))
    require(member_path(canonical, "manifests/checksums.sha256").read_text(encoding="utf-8") == expected_list,
            "SOURCE_CHECKSUM_LIST_MISMATCH", "Source checksum list differs from the manifest")
    products = jsonl(member_path(canonical, "data/products.jsonl"))
    variants = jsonl(member_path(canonical, "data/variants.jsonl"))
    specifications = jsonl(member_path(canonical, "data/specifications.jsonl"))
    images = read_json(member_path(canonical, "manifests/image_manifest.json"))["images"]
    require(len(products) == len(variants) == len(specifications) == len(images) == 1500,
            "SOURCE_COUNT_MISMATCH", "Source package counts must be exactly 1500")
    product_ids: set[str] = set()
    variant_ids: set[str] = set()
    asins: set[str] = set()
    skus: set[str] = set()
    for row, sibling in zip(products, variants):
        product, variant = row["product"], sibling["variant"]
        asin = row["parent_asin"]
        require(asin == sibling["parent_asin"] and asin not in asins,
                "SOURCE_IDENTITY_MISMATCH", "Source identity is inconsistent or repeated")
        pid, vid = _valid_uuid(product["id_proposal"]), _valid_uuid(variant["id_proposal"])
        sku = variant["sku_proposal"]
        require(pid not in product_ids and vid not in variant_ids and sku.lower() not in skus
                and variant["product_id_proposal"] == pid, "SOURCE_IDENTITY_MISMATCH",
                "Source product/variant/SKU identity is inconsistent or repeated")
        require(product["status"] == "DRAFT" and variant["option_combination_proposal"] == {},
                "SOURCE_POLICY_DRIFT", "Source publication or variant policy changed")
        asins.add(asin)
        product_ids.add(pid)
        variant_ids.add(vid)
        skus.add(sku.lower())
    image_products: set[str] = set()
    image_bytes = 0
    for image in images:
        pid = image["product_id_proposal"]
        require(pid in product_ids and pid not in image_products, "SOURCE_IMAGE_MAPPING",
                "Image ownership must map once to each product")
        image_products.add(pid)
        path = member_path(canonical, image["asset_path"])
        require(path.is_file() and digest_file(path) == image["sha256"],
                "SOURCE_IMAGE_CHECKSUM", "Image bytes do not match source evidence")
        require(path.stat().st_size == image["file_size_bytes"] and path.stat().st_size <= 10485760,
                "SOURCE_IMAGE_METADATA", "Image size is invalid")
        if decode_images:
            with Image.open(path) as im:
                require(im.format == "WEBP" and im.size == (image["width"], image["height"]),
                        "SOURCE_IMAGE_METADATA", "Image content type or dimensions differ")
                im.verify()
        image_bytes += path.stat().st_size
    require(dict(Counter(row["product"]["category_label"] for row in products))
            == manifest["category_distribution"], "SOURCE_CATEGORY_DISTRIBUTION",
            "Category distribution differs from the approved source manifest")
    return {"source_revision": SOURCE_REVISION, "source_files": checked, "products": 1500,
            "variants": 1500, "images": 1500, "image_bytes": image_bytes,
            "verified_manifest_files": len(hashes), "newline_only_paths": newline_paths,
            "image_decode_verified": decode_images, "source_modified": False}


def approved_source(root: Path) -> tuple[list[dict[str, Any]], dict[str, Any]]:
    """Apply approved overlays without modifying draft source records."""
    require(digest_file(member_path(root,'manifests/catalog_manifest.json'))==SOURCE_MANIFEST_SHA256,
            'SOURCE_MANIFEST_MISMATCH','Source manifest differs from the fixed approved Git blob')
    manifest=read_json(member_path(root,'manifests/catalog_manifest.json'))
    require(all(digest_file(member_path(root,name))==expected for name,expected in manifest['file_sha256'].items()),
            'SOURCE_CHECKSUM_MISMATCH','Consumed source payload differs from the approved package')
    rows = jsonl(member_path(root, "data/products.jsonl"))
    specification_rows = jsonl(member_path(root, "data/specifications.jsonl"))
    specifications = {r["parent_asin"]: r for r in specification_rows}
    require(len(specification_rows) == len(specifications) == 1500, "SOURCE_SPECIFICATION", "Specification evidence must cover every source product")
    with member_path(root, "data/approvals/amazon_import_approved_1500.csv").open(encoding="utf-8", newline="") as stream:
        approvals = list(csv.DictReader(stream))
    by_asin = {r["parent_asin"]: r for r in approvals}
    require(len(rows) == len(approvals) == len(by_asin) == 1500, "SOURCE_APPROVAL", "Approval rows must cover every product exactly once")
    categories = read_json(member_path(root, "data/category_mapping.json"))["categories"]
    category_map = {r["source_category"]: r for r in categories}
    require(len(categories) == 10 and all(r["approval"] == "APPROVED" for r in categories),
            "SOURCE_APPROVAL", "Category mapping is not approved")
    result = []
    for row in rows:
        approval = by_asin.get(row["parent_asin"], {})
        require(all(approval.get(k) == "APPROVED" for k in
                    ("category_approval", "sku_policy_approval", "variant_approval", "price_approval", "import_approval")),
                "SOURCE_APPROVAL", "Source overlay lacks required approval")
        require(approval.get('approved_sku')==row['variant']['sku_proposal'],
                'SOURCE_APPROVAL','CSV SKU conflicts with the preserved original variant SKU')
        approved_brand=approval.get('approved_brand_uuid')
        proposed_brand=row['product'].get('brand_reference_proposal') or {}
        require(not approved_brand or approved_brand==proposed_brand.get('registry_id'),
                'SOURCE_APPROVAL','CSV brand identity conflicts with source evidence')
        price = Decimal(approval["approved_selling_price_vnd"])
        require(price.is_finite() and price >= 0 and price == price.to_integral_value(),
                "INVALID_PRICE", "CSV-approved price is not whole nonnegative VND")
        category = category_map[row["product"]["category_label"]]
        require(not approval["approved_category_uuid"] or approval["approved_category_uuid"] == category["category_uuid"],
                "SOURCE_APPROVAL", "CSV and category mapping disagree")
        item = copy.deepcopy(row)
        item["analysis_specification_record"] = specifications[row["parent_asin"]]
        item["approved_overlay"] = {"category_id": category["category_uuid"], "sku": approval["approved_sku"],
                                    "base_price": int(price), "publication": "LOCAL_PUBLISHED_USER_APPROVED"}
        result.append(item)
    return result, category_map


def validate_specifications(document: Any) -> None:
    require(isinstance(document, dict) and set(document) == {"schema_version", "values"}
            and document["schema_version"] == 1 and isinstance(document["values"], dict),
            "INVALID_SPECIFICATIONS", "Specifications require the existing schema_version 1 shape")
    for key, value in document["values"].items():
        require(bool(re.fullmatch(r"[a-z][a-z0-9_]*", key)), "INVALID_SPECIFICATIONS", "Specification key is not canonical")
        if isinstance(value, dict):
            require(set(value) == {"value", "unit"} and isinstance(value["unit"], str) and bool(value["unit"].strip()),
                    "INVALID_SPECIFICATIONS", "Quantity specifications need value and unit")
            number = Decimal(str(value["value"]))
            require(number.is_finite() and isinstance(value["value"], (int, float)) and not isinstance(value["value"], bool),
                    "INVALID_SPECIFICATIONS", "Quantity specifications must contain a finite number")
        else:
            require(isinstance(value, (str, int, float, bool)) and (not isinstance(value, str) or bool(value.strip())),
                    "INVALID_SPECIFICATIONS", "Specification scalar is invalid")
            if isinstance(value, (int, float)):
                require(Decimal(str(value)).is_finite(), "INVALID_SPECIFICATIONS", "Nonfinite specification")


def resolve_proposal(source: dict[str, Any], proposal: dict[str, Any], rules: dict[str, Any],
                     pricing: dict[str, Any]) -> dict[str, Any]:
    """Validate data proposals; PostgreSQL remains final option identity authority."""
    from bootstrap import canonical_bytes
    from scenarios import logical_uuid, synthetic_sku, synthetic_price
    product, original = source["product"], source["variant"]
    pid = product["id_proposal"]
    require(proposal.get("product_id") == pid and proposal.get("source_identity") == source["source_identity"]
            and proposal.get("source_record_sha256") == digest_bytes(canonical_bytes(source)),
            "PROPOSAL_IDENTITY", "Proposal is not bound to this exact source record and overlay")
    options = proposal.get("combinations", [{}])
    dimensions = proposal.get("dimensions", {})
    require(isinstance(options, list) and (len(options) == 1 or 2 <= len(options) <= 6)
            and isinstance(dimensions, dict), "INVALID_VARIANTS", "Proposal requires one fallback or 2–6 variants")
    require(all(isinstance(o, dict) and set(o) == set(dimensions) and
                all(isinstance(v, str) and bool(v.strip()) for v in o.values()) for o in options),
            "INVALID_OPTIONS", "All sibling option maps must share nonblank string dimensions")
    require(len({canonical_bytes(o) for o in options}) == len(options), "DUPLICATE_OPTIONS", "Duplicate option combination")
    require(options[0] == dimensions and (bool(dimensions) or len(options) == 1),
            "ORIGINAL_OPTIONS", "First variant must represent the evidenced original configuration")
    permitted = rules["category_candidates"][product["category_label"]]
    require(set(dimensions).issubset(permitted), "INVALID_DIMENSION", "Dimension is inappropriate for this category")
    evidence = proposal.get("original_evidence", {})
    for dimension, value in dimensions.items():
        entry = evidence.get(dimension, {})
        path, quote = entry.get("path", ""), entry.get("quote", "")
        cursor: Any = source
        try:
            for part in path.split("."):
                cursor = cursor[int(part)] if isinstance(cursor, list) else cursor[part]
        except (KeyError, IndexError, ValueError, TypeError):
            raise BootstrapError("OPTION_EVIDENCE", "Original option evidence path is absent") from None
        quantity_label = (str(cursor.get("value")) + " " + str(cursor.get("unit"))) if isinstance(cursor, dict) else None
        normalized_quote=False
        if isinstance(cursor,str) and isinstance(quote,str) and quote in cursor and rules['dimension_definitions'][dimension]['type']=='quantity':
            match=re.fullmatch(r'([0-9]+(?:\.[0-9]+)?)\s*(mAh|W|m|meters?|ft|feet|GB)',quote,re.I)
            if match:
                number=Decimal(match[1]);unit=match[2].lower()
                expected_unit=rules['dimension_definitions'][dimension]['unit']
                if unit in {'ft','feet'} and expected_unit=='m': number*=Decimal('0.3048');unit='m'
                if unit in {'meter','meters'}:unit='m'
                normalized_quote=unit==expected_unit.lower() and value==format(number.normalize(),'f')+' '+expected_unit
        require(isinstance(quote, str) and bool(quote)
                and ((quote in str(cursor) and value in quote) or normalized_quote or (quantity_label is not None and quote == quantity_label and value == quantity_label)),
                "OPTION_EVIDENCE", "Original option value lacks matching source evidence")
    updates = proposal.get("specification_updates", [{} for _ in options])
    require(len(updates) == len(options) and all(isinstance(u, dict) for u in updates),
            "INVALID_SPECIFICATIONS", "One specification update map is required per variant")
    require(not updates[0], "SOURCE_SPECIFICATION", "Original specification changes need explicit correction review")
    name = proposal.get("name", product["name"])
    require(isinstance(name,str) and bool(name.strip()),'NAME_NORMALIZATION','Normalized product name must remain nonblank')
    # General title rewriting is deliberately unsupported; exact span removal only.
    if name != product["name"]:
        edit = proposal.get("name_edit_evidence", {})
        removed = edit.get("removed", "")
        require(isinstance(removed, str) and bool(removed) and product["name"].count(removed) == 1
                and product["name"].replace(removed, "").strip() == name
                and edit.get("dimension") in dimensions,
                "NAME_NORMALIZATION", "Title edit must be one evidenced option phrase removal")
    variants = []
    dimension_specs = {"RAM": "physical_ram", "Storage": "internal_storage", "Capacity": "battery_capacity",
                       "Power": "power", "Cable Length": "cable_length", "Pack Size": "pack_size"}
    for dimension,label in dimensions.items():
        key=dimension_specs.get(dimension)
        fact=original['technical_specifications']['values'].get(key) if key else None
        if fact is not None:
            if isinstance(fact,dict):
                expected_label=format(Decimal(str(fact['value'])).normalize(),'f')+' '+fact['unit']
            else:
                expected_label=str(fact)
            require(label==expected_label,'SPECIFICATION_CONFLICT','Original option conflicts with its existing typed source specification')
    for index, option in enumerate(options):
        spec = copy.deepcopy(original["technical_specifications"])
        changed = {d for d in dimensions if option[d] != dimensions[d]}
        require(not any(d not in {'Color',*pricing['log2_coefficients'],*pricing['linear_ratio_dimensions']} for d in changed)
                or 'PRICE_NEUTRAL_DIMENSION_REVIEW' in proposal.get('flags',[]),
                'UNPRICED_DIMENSION','Non-priced dimensions require explicit neutral-price review flags')
        expected_spec_keys = {dimension_specs[d] for d in changed if d in dimension_specs}
        require(set(updates[index]).issubset(expected_spec_keys), "SPECIFICATION_CONFLICT",
                "Synthetic variant may change only option-dependent specifications")
        ratios = {}
        for dimension in changed:
            definition = rules["dimension_definitions"][dimension]
            if definition["type"] in {"quantity", "integer"}:
                def amount(label):
                    match = re.fullmatch(r"([0-9]+(?:\.[0-9]+)?)(?:\s+([A-Za-z]+))?", label)
                    require(match is not None and (match[2] or "count") == definition["unit"],
                            "INVALID_PRICE_RATIO", "Synthetic ratio must use comparable canonical units")
                    return match[1]
                ratios[dimension] = (amount(option[dimension]), amount(dimensions[dimension]))
                key = dimension_specs[dimension]
                expected_value = ({"value": float(ratios[dimension][0]), "unit": definition["unit"]}
                                  if definition["type"] == "quantity" else int(Decimal(ratios[dimension][0])))
                require(updates[index].get(key) == expected_value, "SPECIFICATION_CONFLICT",
                        "Synthetic option and typed specification must agree")
        spec["values"].update(updates[index])
        if product["category_label"] == "Laptop" and "internal_storage" in spec["values"]:
            storage = spec["values"]["internal_storage"]
            if isinstance(storage, dict) and storage.get("unit") == "GB":
                spec["values"]["installed_storage_gb"] = copy.deepcopy(storage)
        validate_specifications(spec)
        price, factors = synthetic_price(source["approved_overlay"]["base_price"], ratios, pricing)
        variants.append({"id": original["id_proposal"] if index == 0 else logical_uuid("variant", [pid, option]),
                         "product_id": pid, "sku": source["approved_overlay"]["sku"] if index == 0 else synthetic_sku(source["approved_overlay"]["sku"], option),
                         "option_combination": option, "base_price": source["approved_overlay"]["base_price"] if index == 0 else price,
                         "selling_price": source["approved_overlay"]["base_price"] if index == 0 else price,
                         "technical_specifications": spec, "origin": "SOURCE" if index == 0 else "SYNTHETIC_DEMO",
                         "price_factors": factors})
    return {"product": {"id": pid, "name": name, "description": product.get("description"),
                         "category_id": source["approved_overlay"]["category_id"], "status": "PUBLISHED",
                         "category_label": product["category_label"], "source_identity": source["source_identity"]},
            "variants": variants, "flags": proposal.get("flags", []), "enriched": len(variants) > 1}
