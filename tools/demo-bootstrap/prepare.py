"""Offline maintainer preparation. Never imported by the startup AI-free path."""
from __future__ import annotations

import argparse
from collections import Counter, defaultdict
import copy
import json
from pathlib import Path
import re
import shutil
import zlib
import html
import sys
import os
from decimal import Decimal

from bootstrap import (BootstrapError, SOURCE_REVISION, ARTIFACT_VERSION, atomic_json, canonical_bytes,
                       digest_bytes, digest_file, member_path, read_json, require)
from catalog import approved_source, jsonl, resolve_proposal,verify_source
from scenarios import StableRandom, commerce_choices, logical_uuid, frozen_order_choices, promotion_choices

RULE_ROOT = Path(__file__).parent / "rules"


def write_jsonl(path: Path, rows) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    require(not path.exists(), "OUTPUT_EXISTS", "Never replace an existing preparation file")
    with path.open("xb") as stream:
        for row in rows:
            stream.write(canonical_bytes(row) + b"\n")


def new_workspace(path: Path) -> None:
    require(not path.exists() and not path.is_symlink(), "OUTPUT_EXISTS", "Preparation output must be a new directory")
    require(not any(p.is_symlink() for p in path.parents), "UNSAFE_PATH", "Preparation output has symlink parents")
    path.mkdir(parents=True)


def conservative_proposal(row: dict) -> dict:
    """Transparent AI-authored rule assistance, not a fabricated model response.

    Every record still needs offline proposal/review accounting. Ambiguous cases
    remain single variants instead of manufacturing original configuration facts.
    """
    title = row["product"]["name"]
    category = row["product"]["category_label"]
    proposal = {"product_id": row["product"]["id_proposal"], "source_identity": row["source_identity"],
                "source_record_sha256": digest_bytes(canonical_bytes(row)), "dimensions": {},
                "original_evidence": {}, "combinations": [{}], "specification_updates": [{}],
                "name": title, "flags": [], "rationale": "Single-variant fallback: no unambiguous original option evidence"}
    # Keep original color spelling exactly. Multiple color mentions, compatible
    # model lists and multi-packs require human/product-specific interpretation.
    colors = list(re.finditer(r"\b(?:Black|White|Blue|Red|Pink|Silver|Gold|Green|Gray|Grey|Purple)\b", title))
    values = {m.group(0) for m in colors}
    if len(values) == 1 and title.count(colors[0].group(0)) == 1 and not row.get("source_references", {}).get("specification_review_issues"):
        color = colors[0].group(0)
        sibling = "White" if color != "White" else "Black"
        brand=row['product'].get('brand_reference_proposal') or {}
        if not re.search(r'\b'+re.escape(color)+r'\b',row['product'].get('description') or '',re.I) and not re.search(r'\b'+re.escape(color)+r'\b',brand.get('name',''),re.I):
            proposal.update(dimensions={"Color": color}, original_evidence={"Color": {"path": "product.name", "quote": color}},
                            combinations=[{"Color": color}, {"Color": sibling}], specification_updates=[{}, {}],
                            name=title.replace(color,'').strip(),name_edit_evidence={'removed':color,'dimension':'Color'},
                            rationale="Exact original title color; neutral family title and synthetic cosmetic sibling reusing product gallery",
                            flags=[])
    if len(values) > 1:
        proposal["flags"].append("AMBIGUOUS_ORIGINAL_COLOR_FALLBACK")
    if row.get("source_references", {}).get("specification_review_issues"):
        proposal["flags"].append("SOURCE_SPECIFICATION_REVIEW_ISSUES")
    # Numeric options use typed source quantities, never guessed title numbers.
    # Do not retain an option-specific phrase in the public family title.
    candidates = {"Laptop": [("RAM", "physical_ram"), ("Storage", "internal_storage")],
                  "Tablet": [("Storage", "internal_storage")],
                  "Smartphone": [("Storage", "internal_storage")],
                  "Charger & Power Bank": [("Capacity", "battery_capacity"), ("Power", "power")],
                  "Phone Accessories": [("Cable Length", "cable_length")],
                  "Computer Accessories": [("Cable Length", "cable_length")]}.get(category, [])
    if not proposal["flags"]:
        for dimension, key in candidates:
            quantity = row["variant"]["technical_specifications"]["values"].get(key)
            if not isinstance(quantity, dict) or not isinstance(quantity.get("value"), (int, float)) or quantity["value"] <= 0:
                continue
            original_label = str(quantity["value"]) + " " + quantity["unit"]
            number = str(quantity["value"])
            phrase = re.search(r"(?<![0-9])" + re.escape(number) + r"\s*" + re.escape(quantity["unit"]) + r"(?![A-Za-z0-9])", title)
            if phrase is None or title.count(phrase.group(0)) != 1 or re.search(r'(?<![0-9])'+re.escape(number)+r'\s*'+re.escape(quantity['unit'])+r'(?![A-Za-z0-9])',row['product'].get('description') or '',re.I):
                continue
            synthetic = quantity["value"] * 2
            synthetic_label = str(synthetic) + " " + quantity["unit"]
            proposal.update(dimensions={dimension: original_label},
                            original_evidence={dimension: {"path": "variant.technical_specifications.values." + key, "quote": original_label}},
                            combinations=[{dimension: original_label}, {dimension: synthetic_label}],
                            specification_updates=[{}, {key: {"value": synthetic, "unit": quantity["unit"]}}],
                            name=title.replace(phrase.group(0), "").strip(),
                            name_edit_evidence={"removed": phrase.group(0), "dimension": dimension},
                            flags=["SYNTHETIC_CONFIGURATION_AND_TITLE_REQUIRES_REVIEW"],
                            rationale="Typed original source quantity and exact title span; synthetic sibling uses P-31 and matching specification")
            break
    # Accessories have their own evidenced dimensions rather than a forced
    # electronics Color x Storage grid. Conversions remain explicit provenance.
    if category in {'Headphone & Earbuds','Speaker','Charger & Power Bank','Phone Accessories','Computer Accessories'}:
        specific=[]
        if category=='Charger & Power Bank':
            specific += [('Capacity','battery_capacity',r'\b([0-9]+(?:\.[0-9]+)?)\s*mAh\b','mAh'),
                         ('Power','power',r'\b([0-9]+(?:\.[0-9]+)?)\s*W\b','W')]
        if category in {'Phone Accessories','Computer Accessories'}:
            specific += [('Cable Length','cable_length',r'\b([0-9]+(?:\.[0-9]+)?)\s*(m|meters?|ft|feet)\b','m')]
        specific += [('Pack Size','pack_size',r'\b([0-9]+)\s*[- ]?\s*(?:Pack|pcs|Pieces|Count)\b','count')]
        for dimension,key,pattern,unit in specific:
            matches=list(re.finditer(pattern,title,re.I))
            if len(matches)!=1 or proposal['flags'] and proposal['flags']!=['SYNTHETIC_CONFIGURATION_AND_TITLE_REQUIRES_REVIEW']:
                continue
            match=matches[0];quote=match.group(0)
            if title.count(quote)!=1 or re.search(pattern,row['product'].get('description') or '',re.I):continue
            number=Decimal(match[1])
            if number<=0 or (unit=='count' and number>10):continue
            if dimension=='Cable Length' and match[2].lower() in {'ft','feet'}:number*=Decimal('0.3048')
            original_label=format(number.normalize(),'f')+(' '+unit if unit!='count' else '')
            synthetic=number*2
            synthetic_label=format(synthetic.normalize(),'f')+(' '+unit if unit!='count' else '')
            update={key:int(synthetic) if unit=='count' else {'value':float(synthetic),'unit':unit}}
            proposal.update(dimensions={dimension:original_label},original_evidence={dimension:{'path':'product.name','quote':quote}},
                            combinations=[{dimension:original_label},{dimension:synthetic_label}],specification_updates=[{},update],
                            name=title.replace(quote,'').strip(),name_edit_evidence={'removed':quote,'dimension':dimension},
                            flags=['SYNTHETIC_CONFIGURATION_AND_TITLE_REQUIRES_REVIEW'],
                            rationale='Product-specific original title quantity; explicit canonical units and synthetic P-31 sibling')
            break
    # Conflicting RAM statements remain visible source facts, but must not be
    # extrapolated into synthetic configurations.
    physical=row['variant']['technical_specifications']['values'].get('physical_ram')
    raw_features=row.get('analysis_specification_record',{}).get('unmapped_source_text',{}).get('features',[])
    if isinstance(physical,dict) and physical.get('unit')=='GB':
        mentioned=[Decimal(m[1]) for m in re.finditer(r'\b([0-9]+(?:\.[0-9]+)?)\s*GB\s*RAM\b',title+' '+(row['product'].get('description') or '')+' '+' '.join(str(f) for f in raw_features),re.I)]
        if any(value!=Decimal(str(physical['value'])) for value in mentioned):
            proposal.update(dimensions={},original_evidence={},combinations=[{}],specification_updates=[{}],name=title,
                            flags=['CONTRADICTORY_RAM_EVIDENCE_FALLBACK'],rationale='Original RAM evidence conflicts across source fields; retain approved original only')
            proposal.pop('name_edit_evidence',None)
    dimension_keys={'RAM':'physical_ram','Storage':'internal_storage','Capacity':'battery_capacity','Power':'power','Cable Length':'cable_length','Pack Size':'pack_size'}
    for dimension,label in list(proposal['dimensions'].items()):
        fact=row['variant']['technical_specifications']['values'].get(dimension_keys.get(dimension))
        if fact is not None:
            expected=(format(Decimal(str(fact['value'])).normalize(),'f')+' '+fact['unit']) if isinstance(fact,dict) else str(fact)
            if label!=expected:
                proposal.update(dimensions={},original_evidence={},combinations=[{}],specification_updates=[{}],name=title,
                                flags=['ORIGINAL_OPTION_SPEC_CONFLICT_FALLBACK'],rationale='Original title option conflicts with typed source specification; retain original only')
                proposal.pop('name_edit_evidence',None)
                break
    proposal["analysis"] = {"mode": "AI_AUTHORED_RULE_ASSISTANCE", "model_invocation": "NONE",
                            "category": category, "source_fields_considered":
                            ["product.name", "product.category_label", "product.description",
                             "variant.technical_specifications", "source_references.specification_evidence", "variant"]}
    return proposal


def analyze(source: Path, output: Path) -> dict:
    verify_source(source,source)
    rows, _ = approved_source(source)
    new_workspace(output)
    # Each packet contains all fields needed for a separate offline model review.
    write_jsonl(output / "evidence/products.jsonl", rows)
    write_jsonl(output / "proposals/products.jsonl", (conservative_proposal(r) for r in rows))
    identity = {"source_revision": SOURCE_REVISION, "source_products": len(rows), "analysis_records": len(rows),
                "source_manifest_sha256": digest_file(source / "manifests/catalog_manifest.json"),
                "prompt_sha256": digest_file(RULE_ROOT / "enrichment-prompt.md"),
                "proposal_sha256": digest_file(output / "proposals/products.jsonl"),
                "analysis_mode": "AI_AUTHORED_RULE_ASSISTANCE", "model_api_calls": 0,
                "model_identity": "NO_SEPARATE_MODEL_INVOCATION", "approved": False}
    atomic_json(output / "analysis.json", identity)
    return identity


def payload_inventory(root: Path) -> dict:
    files={}
    for p in sorted(root.rglob('*')):
        require(not p.is_symlink(),'UNSAFE_PATH','Preparation payload symlinks are forbidden')
        if p.is_file() and not p.relative_to(root).as_posix().startswith('review/') and p.name not in {'manifest.json','candidate.json','database-validation.json'}:
            files[p.relative_to(root).as_posix()]={'sha256':digest_file(p),'bytes':p.stat().st_size}
    return files


def candidate(source: Path, workspace: Path, output: Path) -> dict:
    rows, category_map = approved_source(source)
    proposals = jsonl(member_path(workspace, "proposals/products.jsonl"))
    analysis=read_json(workspace/'analysis.json')
    require(analysis.get('source_revision')==SOURCE_REVISION and analysis.get('proposal_sha256')==digest_file(workspace/'proposals/products.jsonl'),
            'PROPOSAL_IDENTITY','Proposal bytes differ from their recorded offline capture identity')
    by_id = {p["product_id"]: p for p in proposals}
    require(len(proposals) == len(by_id) == 1500, "PROPOSAL_COVERAGE", "Exactly one proposal per source product is required")
    options, pricing = read_json(RULE_ROOT / "options.json"), read_json(RULE_ROOT / "pricing.json")
    distributions = read_json(RULE_ROOT / "distributions.json")
    resolved = [resolve_proposal(r, by_id[r["product"]["id_proposal"]], options, pricing) for r in rows]
    variants = [v for r in resolved for v in r["variants"]]
    require(len({v["id"] for v in variants}) == len(variants)
            and len({v["sku"].lower() for v in variants}) == len(variants) <= 9000,
            "DUPLICATE_VARIANT", "Variant UUID/SKU uniqueness or total limit violated")
    new_workspace(output)
    brands = {}
    product_rows = []
    provenance = []
    duplicate_groups = defaultdict(list)
    for source_row, resolved_row in zip(rows, resolved):
        product = resolved_row["product"]
        brand = source_row["product"].get("brand_reference_proposal")
        evidence = source_row.get("source_references", {}).get("brand_evidence", [])
        if brand and any(e.get("code") == brand["code"] and e.get("source") != "brand_proxy_unverified" for e in evidence):
            bid = brand["registry_id"]
            brands[bid] = {"id": bid, "code": brand["code"], "name": brand["name"]}
            product["brand_id"] = bid
        else:
            product["brand_id"] = None
            resolved_row["flags"].append("BRAND_UNCONFIRMED_OMITTED")
        product_rows.append(product)
        duplicate_groups[re.sub(r"[^a-z0-9]+", " ", source_row["product"]["name"].lower()).strip()].append(product["id"])
        provenance.append({"product_id": product["id"], "source_identity": source_row["source_identity"],
                           "original_name":source_row['product']['name'],"original_description":source_row['product'].get('description'),
                           "source_record_sha256": digest_bytes(canonical_bytes(source_row)),
                           "original_variant_id": source_row["variant"]["id_proposal"],
                           "original_sku": source_row["approved_overlay"]["sku"],
                           "original_base_price": source_row["approved_overlay"]["base_price"],
                           "source_fact_fields": ["name", "description", "category", "original_variant", "image"],
                           "synthetic_fields": ["sibling_options", "sibling_price", "sibling_specifications", "local_publication"],
                           "proposal": by_id[product["id"]]})
    write_jsonl(output / "catalog/products.jsonl", sorted(product_rows, key=lambda r: r["id"]))
    write_jsonl(output / "catalog/variants.jsonl", sorted(variants, key=lambda r: r["id"]))
    atomic_json(output / "catalog/categories.json", sorted(
        [{"id": c["category_uuid"], "name": c["source_category"], "parent_category_id": c["parent_uuid"]}
         for c in category_map.values()], key=lambda c: (c["parent_category_id"] is not None, c["id"])))
    atomic_json(output / "catalog/brands.json", sorted(brands.values(), key=lambda r: r["id"]))
    images = []
    for original in read_json(source / "manifests/image_manifest.json")["images"]:
        pid = original["product_id_proposal"]
        images.append({"id": logical_uuid("image", pid), "product_id": pid,
                       "source_identity": next(r["source_identity"] for r in rows if r["product"]["id_proposal"] == pid),
                       "source_path": original["asset_path"], "sha256": original["sha256"],
                       "bytes": original["file_size_bytes"], "content_type": "image/webp",
                       "width": original["width"], "height": original["height"],
                       "object_key": f"images/demo-bootstrap/demo-v1/{pid}/{original['sha256']}.webp",
                       "alt_text": "Source product gallery image", "display_order": 0, "is_primary": True})
    atomic_json(output / "assets/images.json", sorted(images, key=lambda r: r["product_id"]))
    write_jsonl(output / "provenance/fields.jsonl", sorted(provenance, key=lambda r: r["product_id"]))
    write_jsonl(output / "provenance/source-links.jsonl", [])
    if (workspace/'proposals/prompt.md').is_file():
        shutil.copyfile(workspace/'proposals/prompt.md',output/'provenance/model-prompt.md')
    atomic_json(output / "provenance/omissions.json", {"source_links_omitted": 1500,
                "reason": "Source URL/raw payload hash/approval manifest evidence incomplete; never fabricated",
                "duplicate_suggestions": [ids for ids in duplicate_groups.values() if len(ids) > 1]})
    recipe = commerce_choices([p["id"] for p in product_rows], distributions)
    status_counts, item_counts = Counter(), Counter()
    def orders():
        for row in frozen_order_choices(recipe, variants):
            status_counts[row["status"]] += 1
            item_counts[row["status"]] += len(row["items"])
            yield row
    write_jsonl(output / "scenarios/orders.jsonl", orders())
    recipe.update(status_counts=dict(status_counts), item_counts=dict(item_counts),
                  total_orders=sum(status_counts.values()), total_items=sum(item_counts.values()))
    require(status_counts["COMPLETED"]>=9000 and item_counts["COMPLETED"]==recipe["completed_item_total"],
            "ORDER_CAPACITY", "Frozen order scheduling lost completed item capacity")
    atomic_json(output / "scenarios/recipe.json", recipe)
    atomic_json(output / "scenarios/promotions.json", promotion_choices([p["id"] for p in product_rows], [v["id"] for v in variants], distributions))
    journeys = ["browse", "variants", "cart", "checkout", "stock", "vouchers", "orders", "cancellation",
                "reviews", "wishlist", "history", "behavior"]
    atomic_json(output / "scenarios/demo-accounts.json", [{"id": i+1, "username": f"demo_{journey}", "journey": journey}
                                                          for i, journey in enumerate(journeys)])
    atomic_json(output / "usage-scope.json", distributions["usage_scope"])
    for path in RULE_ROOT.glob("*"):
        if path.is_file():
            destination = output / "rules" / path.name
            destination.parent.mkdir(exist_ok=True)
            shutil.copyfile(path, destination)
    inventory = payload_inventory(output)
    digest = digest_bytes(canonical_bytes(inventory))
    enriched = [r for r in resolved if r["enriched"]]
    required = {r["product"]["id"] for r in resolved if r["flags"]}
    for category in sorted(category_map):
        members = [r["product"]["id"] for r in enriched if r["product"]["category_label"] == category]
        required.update(StableRandom("human-review:" + category).shuffle(sorted(members))[:(len(members)+9)//10])
    summary = {"payload_digest": digest, "source_revision": SOURCE_REVISION, "products": 1500,
               "variants": len(variants), "enriched_products": len(enriched),
               "fallback_products": 1500-len(enriched), "review_total": recipe["review_total"],
               "completed_item_total": recipe["completed_item_total"],
               "status_counts": dict(status_counts), "total_orders":recipe["total_orders"], "total_items":recipe["total_items"],
               "required_review_product_ids": sorted(required), "unresolved_error_count": 0,
               "approved": False, "files": inventory, "analysis": analysis}
    atomic_json(output / "candidate.json", summary)
    return {k: v for k, v in summary.items() if k not in {"files", "required_review_product_ids"}}


def validate_reviews(root: Path, decisions: Path) -> dict:
    summary = read_json(root / "candidate.json")
    require(payload_inventory(root) == summary["files"], "CANDIDATE_CHANGED", "Candidate bytes changed after review selection")
    require(digest_bytes(canonical_bytes(summary['files']))==summary['payload_digest'],'CANDIDATE_CHANGED','Candidate payload digest is invalid')
    fields=jsonl(root/'provenance/fields.jsonl')
    products=jsonl(root/'catalog/products.jsonl')
    variant_counts=Counter(r['product_id'] for r in jsonl(root/'catalog/variants.jsonl'))
    required={r['product_id'] for r in fields if r['proposal'].get('flags')}
    for category in sorted({p['category_label'] for p in products}):
        members=sorted(p['id'] for p in products if p['category_label']==category and variant_counts[p['id']]>1)
        required.update(StableRandom('human-review:'+category).shuffle(members)[:(len(members)+9)//10])
    require(sorted(required)==summary['required_review_product_ids'],'REVIEW_COVERAGE','Required human review selection changed')
    rows = jsonl(decisions)
    mapping = {r.get("product_id"): r for r in rows}
    require(len(mapping) == len(rows), "DUPLICATE_REVIEW", "Duplicate product review decisions")
    require(set(mapping).issubset({p['id'] for p in products}),'REVIEW_COVERAGE','Human review includes an unknown product identity')
    require(required.issubset(mapping),'HUMAN_REVIEW_REQUIRED','Required human review decisions are missing')
    for pid in mapping:
        row = mapping.get(pid, {})
        require(row.get("payload_digest") == summary["payload_digest"] and row.get("decision") == "APPROVE"
                and row.get("reviewer_kind") == "HUMAN" and isinstance(row.get("reviewer"), str)
                and bool(row["reviewer"].strip()) and isinstance(row.get('reference'),str) and bool(row['reference'].strip()),
                "HUMAN_REVIEW_REQUIRED", "Every flagged/sample product needs a genuine digest-bound human approval")
    return {"approved": True, "reviewed_products": len(rows), "payload_digest": summary["payload_digest"],
            "unresolved_error_count": 0, "decisions_sha256": digest_file(decisions),
            "required_review_product_ids":sorted(required)}


def review_package(root: Path) -> dict:
    summary=read_json(root/'candidate.json')
    provenance={r['product_id']:r for r in jsonl(root/'provenance/fields.jsonl')}
    products={r['id']:r for r in jsonl(root/'catalog/products.jsonl')}
    variants=defaultdict(list)
    for row in jsonl(root/'catalog/variants.jsonl'):
        variants[row['product_id']].append(row)
    escape=lambda value:html.escape(str(value),quote=True)
    sections=[]
    for pid in summary['required_review_product_ids']:
        source=provenance[pid]
        proposal=source['proposal']
        details=''.join('<li>'+escape(v['origin'])+': '+escape(v['option_combination'])+'; '+escape(v['base_price'])+' VND; specs '+escape(v['technical_specifications'])+'</li>'
                        for v in variants[pid])
        sections.append('<details><summary>'+escape(products[pid]['category_label'])+' — '+escape(products[pid]['name'])+'</summary>'
                        '<p>Product '+escape(pid)+'; source '+escape(source['source_identity'])+'</p>'
                        '<p>Flags: '+escape(proposal.get('flags',[]))+'</p><p>'+escape(proposal.get('rationale',''))+'</p>'
                        '<p>Original option evidence: '+escape(proposal.get('original_evidence',{}))+'</p>'
                        '<p>Title edit: '+escape(proposal.get('name_edit_evidence',{}))+'</p><ul>'+details+'</ul></details>')
        sections[-1]=sections[-1].replace('<p>Flags:', '<p>Original title: '+escape(source.get('original_name',''))+'</p><p>Original description: '+escape(source.get('original_description',''))+'</p><p>Flags:',1)
    page='<!doctype html><meta charset="utf-8"><title>Private demo candidate review</title><style>body{font:16px system-ui;max-width:1100px;margin:32px auto;padding:16px;line-height:1.5}details{border:1px solid #bbb;padding:12px;margin:8px 0}summary{cursor:pointer;font-weight:600}code{overflow-wrap:anywhere}</style>'
    page+='<h1>Private educational candidate — NOT APPROVED</h1><p>Team/instructor access only. Public redistribution and commercial use prohibited. Copyright and reuse permissions UNVERIFIED.</p>'
    page+='<p>Payload digest: <code>'+escape(summary['payload_digest'])+'</code></p><p>'+escape(len(sections))+' required human decisions. Review all flagged records and the deterministic category sample; record genuine decisions in pending-decisions.jsonl. No automatic approval is generated.</p>'
    page+='<p>Scope: '+escape({k:summary[k] for k in ('products','variants','enriched_products','fallback_products','review_total','total_orders','status_counts','total_items')})+'</p>'
    page+=''.join(sections)
    destination=root/'review'/'index.html'
    destination.parent.mkdir(exist_ok=True)
    require(not destination.exists(),'OUTPUT_EXISTS','Review package already exists')
    destination.write_text(page,encoding='utf-8')
    return {'required_reviews':len(sections),'payload_digest':summary['payload_digest'],'approved':False}


def migration_contract(directory: Path) -> dict:
    files=sorted(directory.glob('V*__*.sql'),key=lambda p:int(p.name.split('__')[0][1:]))
    require(len(files)==30,'MIGRATION_INCOMPATIBLE','Expected exactly the reviewed V1–V30 migration set')
    history=[]
    for path in files:
        checksum=0
        for line in path.read_text(encoding='utf-8-sig').splitlines():
            checksum=zlib.crc32(line.encode('utf-8'),checksum)
        if checksum>=2**31:checksum-=2**32
        version,description=path.stem.split('__',1)
        history.append({'version':version[1:],'description':description.replace('_',' '),'type':'SQL',
                        'script':path.name,'checksum':checksum,'success':True})
    permissions=['MANAGE_USER_PROFILE','MANAGE_ADDRESS','MANAGE_WISHLIST','MANAGE_CART','MANAGE_ORDER','MANAGE_CHECKOUT']
    return {'migration_history':history,'migration_fingerprint':{'files':{p.name:digest_file(p) for p in files},'history':history},
            'baseline_records':{'roles':[{'name':'USER'}],'permissions':[{'name':p} for p in permissions],
                                'role_permissions':[{'role':'USER','permission':p} for p in permissions]}}


def database_validate(root: Path, migrations: Path, decisions: Path) -> dict:
    import database
    from psycopg.types.json import Jsonb
    review=validate_reviews(root,decisions)
    compatibility=migration_contract(migrations)
    with database.connect() as connection:
        database.capability_probe(connection,migrated=True)
        database.compare_history(database.migration_history(connection),compatibility['migration_history'])
        database.validate_baseline(connection,compatibility['baseline_records'],allow_subset=False)
        require(not database.business_population(connection),'FOREIGN_DATA','Preparation validation requires migrated empty/baseline business state')
        maps=defaultdict(set)
        keys=defaultdict(set)
        for variant in jsonl(root/'catalog/variants.jsonl'):
            normalized=connection.execute('SELECT catalog_option_map_v1(%s) AS options', (Jsonb(variant['option_combination']),)).fetchone()['options']
            pid=variant['product_id']
            value=canonical_bytes(normalized)
            require(value not in maps[pid],'DUPLICATE_OPTIONS','Database canonicalization found duplicate combinations')
            maps[pid].add(value);keys[pid].add(tuple(sorted(normalized)))
            require(connection.execute('SELECT catalog_effective_price_v1(%s,NULL,FALSE,NULL,NULL,now()) AS price',
                                       (variant['base_price'],)).fetchone()['price']==variant['base_price'],
                    'INVALID_PRICE','Database rejected base price')
        require(all(len(k)==1 for k in keys.values()),'INVALID_DIMENSIONS','Database canonical dimensions differ across siblings')
        schema=database.schema_fingerprint(connection)
    evidence={'result':'PASS','payload_digest':review['payload_digest'],'variant_count':sum(map(len,maps.values())),
              'migration_fingerprint':compatibility['migration_fingerprint'],'schema_fingerprint':schema,
              'scope':'Read-only PostgreSQL option/price/schema validation; no business import'}
    atomic_json(root/'database-validation.json',evidence)
    return evidence


def migration_state(root: Path, migrations: Path) -> dict:
    """Private preparer capability stage; no final approval digest is required."""
    import database
    summary=read_json(root/'candidate.json')
    require(payload_inventory(root)==summary['files'] and digest_bytes(canonical_bytes(summary['files']))==summary['payload_digest'],
            'CANDIDATE_CHANGED','Preparation candidate fingerprint changed')
    compatibility=migration_contract(migrations)
    with database.connect() as connection:
        database.capability_probe(connection)
        require(database.metadata(connection) is None,'MIGRATOR_FORBIDDEN','Preparation must not run a migrator against an existing initialization attempt')
        database.validate_baseline(connection,compatibility['baseline_records'])
        require(not database.business_population(connection),'FOREIGN_DATA','Preparation migrator requires empty/baseline business state')
        actual=database.migration_history(connection)
        database.compare_history(actual,compatibility['migration_history'],allow_prefix=True)
        ready=len(actual)==len(compatibility['migration_history'])
        if ready:
            database.capability_probe(connection,migrated=True)
            database.validate_baseline(connection,compatibility['baseline_records'],allow_subset=False)
    return {'state':'READY' if ready else 'NEEDS_MIGRATION','business_imported':False,'payload_digest':summary['payload_digest']}


def freeze(source: Path, root: Path, output: Path, decisions: Path, migrations: Path, startup_script: Path) -> dict:
    from runtime import importer_digest
    summary=read_json(root/'candidate.json')
    review=validate_reviews(root,decisions)
    validation=read_json(root/'database-validation.json')
    compatibility=migration_contract(migrations)
    require(validation.get('result')=='PASS' and validation.get('payload_digest')==summary['payload_digest']
            and validation.get('migration_fingerprint')==compatibility['migration_fingerprint'],
            'DATABASE_VALIDATION_REQUIRED','Candidate lacks matching migrated PostgreSQL validation')
    verify_source(source,source)
    new_workspace(output)
    for name in summary['files']:
        destination=member_path(output,name)
        destination.parent.mkdir(parents=True,exist_ok=True)
        shutil.copyfile(member_path(root,name),destination)
    (output/'review').mkdir()
    shutil.copyfile(decisions,output/'review/decisions.jsonl')
    atomic_json(output/'review/summary.json',review)
    compatibility['importer_digest']=importer_digest()
    compatibility['schema_fingerprint']=validation['schema_fingerprint']
    require(startup_script.is_file() and not startup_script.is_symlink(),'STARTUP_IDENTITY','Mount the actual startup script read-only for release identity')
    compatibility['startup_sha256']=digest_file(startup_script)
    manifest={'contract_version':ARTIFACT_VERSION,'dataset_id':'university-private-commerce-demo','dataset_version':output.name,
              'source':{'revision':SOURCE_REVISION,'manifest_sha256':digest_file(source/'manifests/catalog_manifest.json'),
                        'files':{p.relative_to(source).as_posix():digest_file(p) for p in sorted(source.rglob('*'))
                                 if p.is_file() and p.name!='.bootstrap-source-snapshot.json'}},
              'files':payload_inventory(output), 'importer_compatibility':compatibility,
              'scope':{k:summary[k] for k in ('products','variants','enriched_products','fallback_products','review_total',
                                             'completed_item_total','status_counts','total_orders','total_items')},
              'generation':{**summary['analysis'],'seed':20261126,'python_runtime':sys.version,
                            'dependency_lock_sha256':digest_file(Path(__file__).parent/'requirements.lock'),
                            'tooling_digest':importer_digest()},'review':review,
              'rules':{p.name:digest_file(p) for p in sorted((root/'rules').glob('*')) if p.is_file()},
              'approvals':{'local_publication':'User-approved dedicated local demo scope; source remains DRAFT',
                           'original_price_csv_sha256':digest_file(source/'data/approvals/amazon_import_approved_1500.csv'),
                           'generated_rows_review_payload':summary['payload_digest']},
              'usage_scope':read_json(root/'usage-scope.json')}
    # Include review payloads in final digest; avoid the candidate payload digest cycle.
    manifest['files'].update({p.relative_to(output).as_posix():{'bytes':p.stat().st_size,'sha256':digest_file(p)}
                              for p in (output/'review').glob('*')})
    manifest['artifact_digest']=digest_bytes(canonical_bytes(manifest))
    atomic_json(output/'manifest.json',manifest)
    return {'artifact_digest':manifest['artifact_digest'],'scope':manifest['scope'],'approved':False,
            'next':'Obtain genuine human approval of this final digest; record it with approve-artifact before startup'}


def main() -> None:
    parser = argparse.ArgumentParser(description="Offline maintainer preparation; not a startup mode")
    parser.add_argument("stage", choices=["analyze", "capture-proposals", "candidate", "review-template", "review-package", "validate-review", "migrate", "migration-state", "database-validate", "freeze", "approve-artifact"])
    parser.add_argument("--source", type=Path)
    parser.add_argument("--workspace", type=Path)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--decisions", type=Path)
    parser.add_argument("--migrations", type=Path)
    parser.add_argument('--digest')
    parser.add_argument('--reviewer')
    parser.add_argument('--reference')
    parser.add_argument('--env-file',type=Path,default=Path('.env.local'))
    parser.add_argument('--compose-overlay',type=Path)
    parser.add_argument('--input',type=Path)
    parser.add_argument('--model')
    parser.add_argument('--prompt',type=Path)
    parser.add_argument('--startup-script',type=Path,default=Path('/startup-script'))
    args = parser.parse_args()
    if args.stage == "analyze":
        result = analyze(args.source, args.output)
    elif args.stage=='capture-proposals':
        require(bool(args.model and args.model.strip()) and bool(args.reference) and args.prompt is not None,
                'MODEL_IDENTITY','Record the actual offline model, prompt and response reference')
        rows=jsonl(args.input)
        require(len(rows)==len({r.get('product_id') for r in rows})==1500,'PROPOSAL_COVERAGE','Offline responses must cover every source product exactly once')
        new_workspace(args.output)
        write_jsonl(args.output/'proposals/products.jsonl',rows)
        prior=read_json(args.workspace/'analysis.json')
        shutil.copyfile(args.prompt,args.output/'proposals/prompt.md')
        shutil.copyfile(args.input,args.output/'proposals/raw-response.jsonl')
        prior.pop('model_api_calls',None)
        prior.update(analysis_mode='OFFLINE_MODEL_PROPOSALS',model_identity=args.model,prompt_sha256=digest_file(args.prompt),
                     response_sha256=digest_file(args.input),response_reference=args.reference,
                     proposal_sha256=digest_file(args.output/'proposals/products.jsonl'),model_api_calls_performed_by_this_tool=0,approved=False)
        atomic_json(args.output/'analysis.json',prior)
        result={'captured_products':1500,'approved':False,'model_identity':args.model}
    elif args.stage == "candidate":
        result = candidate(args.source, args.workspace, args.output)
    elif args.stage == "review-template":
        summary = read_json(args.workspace / "candidate.json")
        write_jsonl(args.output, ({"product_id": pid, "payload_digest": summary["payload_digest"],
                                 "decision": "PENDING", "reviewer_kind": "HUMAN", "reviewer": "", "reference": ""}
                                for pid in summary["required_review_product_ids"]))
        if os.name=='posix':
            args.output.parent.chmod(0o2770)
            args.output.chmod(0o660)
        result = {"required_reviews": len(summary["required_review_product_ids"]), "payload_digest": summary["payload_digest"]}
    elif args.stage == 'migrate':
        summary=read_json(args.workspace/'candidate.json')
        require(payload_inventory(args.workspace)==summary['files'] and digest_bytes(canonical_bytes(summary['files']))==summary['payload_digest'],
                'CANDIDATE_CHANGED','Preparation candidate fingerprint changed before infrastructure effects')
        from preparation_runtime import migrate
        result=migrate(args.workspace,args.env_file,args.compose_overlay)
    elif args.stage == 'migration-state':
        result=migration_state(args.workspace,args.migrations)
    elif args.stage == 'approve-artifact':
        manifest=read_json(args.workspace/'manifest.json')
        require(args.digest==manifest['artifact_digest']==digest_bytes(canonical_bytes({k:v for k,v in manifest.items() if k!='artifact_digest'})),
                'ARTIFACT_CHECKSUM','Final artifact digest differs from the human-approved digest')
        require(bool(args.reviewer and args.reviewer.strip()) and bool(args.reference),'HUMAN_REVIEW_REQUIRED','Actual human reviewer/reference are required')
        approval={'artifact_digest':args.digest,'decision':'APPROVE','reviewer_kind':'HUMAN','reviewer':args.reviewer,'reference':args.reference}
        require(not (args.workspace/'release-approval.json').exists(),'OUTPUT_EXISTS','Final approval already exists')
        atomic_json(args.workspace/'release-approval.json',approval)
        result={'approved':True,'artifact_digest':args.digest}
    elif args.stage == 'review-package':
        result=review_package(args.workspace)
    elif args.stage == 'database-validate':
        result = database_validate(args.workspace,args.migrations,args.decisions)
    elif args.stage == 'freeze':
        result = freeze(args.source,args.workspace,args.output,args.decisions,args.migrations,args.startup_script)
    else:
        result = validate_reviews(args.workspace, args.decisions)
    print(canonical_bytes(result).decode())


if __name__ == "__main__":
    try:
        main()
    except BootstrapError as error:
        print(canonical_bytes({"result": "FAIL", "code": error.code, "message": error.public_message}).decode())
        raise SystemExit(error.exit_code)
    except Exception:
        print('{"result":"FAIL","code":"PREPARATION_FAILED","message":"Offline preparation failed; candidate remains unapproved"}')
        raise SystemExit(5)
