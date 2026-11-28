"""Versioned deterministic choices and relative-time primitives (never runtime AI)."""
from __future__ import annotations

import calendar
from datetime import datetime, timezone
from decimal import Decimal, ROUND_HALF_UP, localcontext
import hashlib
from typing import Any
import uuid
from collections import Counter
import math
import heapq
from datetime import timedelta

from bootstrap import canonical_bytes, require

SEED = 20261126
NAMESPACE = uuid.UUID("8a640d78-c0bd-5b62-b550-cb3f0ece988a")


class StableRandom:
    """SHA256 counter stream with unbiased rejection sampling, independent of random.py."""
    def __init__(self, stream: str, seed: int = SEED):
        self.key = canonical_bytes([seed, stream])
        self.counter = 0

    def below(self, n: int) -> int:
        require(n > 0, "INVALID_RANDOM_BOUND", "Random bound must be positive")
        size = max(1, (n.bit_length() + 7) // 8)
        limit = (1 << (size * 8)) - ((1 << (size * 8)) % n)
        while True:
            data = b""
            while len(data) < size:
                data += hashlib.sha256(self.key + self.counter.to_bytes(16, "big")).digest()
                self.counter += 1
            value = int.from_bytes(data[:size], "big")
            if value < limit:
                return value % n

    def between(self, low: int, high: int) -> int:
        require(high >= low, "INVALID_RANDOM_BOUND", "Random inclusive range is invalid")
        return low + self.below(high - low + 1)

    def shuffle(self, values: list[Any]) -> list[Any]:
        result = list(values)
        for i in range(len(result) - 1, 0, -1):
            j = self.below(i + 1)
            result[i], result[j] = result[j], result[i]
        return result


def logical_uuid(kind: str, identity: Any) -> str:
    return str(uuid.uuid5(NAMESPACE, kind + ":" + canonical_bytes(identity).decode("utf-8")))


def synthetic_sku(original_sku: str, canonical_options: dict[str, str]) -> str:
    require(original_sku.isascii() and bool(original_sku), "INVALID_SKU", "Source SKU must be nonblank ASCII")
    suffix = hashlib.sha256(canonical_bytes(canonical_options)).hexdigest()[:16].upper()
    return original_sku + "-SYN-" + suffix


def month_offset(anchor: datetime, months: int) -> datetime:
    require(anchor.tzinfo is not None and anchor.utcoffset() is not None,
            "INVALID_ANCHOR", "Import anchor must be timezone-aware")
    anchor = anchor.astimezone(timezone.utc)
    absolute = anchor.year * 12 + anchor.month - 1 + months
    year, month = divmod(absolute, 12)
    month += 1
    return anchor.replace(year=year, month=month,
                          day=min(anchor.day, calendar.monthrange(year, month)[1]))


def synthetic_price(original: int, ratios: dict[str, tuple[str, str]], rules: dict[str, Any]) -> tuple[int, dict[str, str]]:
    require(isinstance(original, int) and original >= 0, "INVALID_PRICE", "Original price must be nonnegative whole VND")
    factors: dict[str, str] = {}
    with localcontext() as context:
        context.prec = 50
        multiplier = Decimal(1)
        for dimension in sorted(ratios):
            new, old = (Decimal(v) for v in ratios[dimension])
            require(new.is_finite() and old.is_finite() and new > 0 and old > 0,
                    "INVALID_PRICE_RATIO", "Price quantities must be finite and positive")
            ratio = new / old
            if dimension in rules["log2_coefficients"]:
                factor = 1 + Decimal(rules["log2_coefficients"][dimension]) * ratio.ln() / Decimal(2).ln()
            elif dimension in rules["linear_ratio_dimensions"]:
                factor = ratio
            else:
                factor = Decimal(1)
            require(factor > 0, "INVALID_PRICE_FACTOR", "Nonpositive price factors are not allowed")
            factors[dimension] = str(factor)
            multiplier *= factor
        low, high = map(Decimal, rules["combined_multiplier_bounds"])
        multiplier = max(low, min(high, multiplier))
        price = int((Decimal(original) * multiplier).quantize(Decimal(1), rounding=ROUND_HALF_UP))
    return price, factors


def commerce_choices(product_ids: list[str], rules: dict[str, Any]) -> dict[str, Any]:
    """Freeze all stochastic choices offline; totals follow demand, never cap it."""
    require(len(product_ids) == len(set(product_ids)) == 1500, "SCENARIO_PRODUCTS", "Commerce recipe requires 1500 unique products")
    ids = sorted(product_ids)
    assignment = StableRandom("review-count-assignment").shuffle(ids)
    products: dict[str, Any] = {}
    offset = 0
    for group_index, group in enumerate(rules["reviews"]["count_groups"]):
        for pid in assignment[offset:offset + group["products"]]:
            count = StableRandom("review-count:" + pid).between(group["minimum"], group["maximum"])
            products[pid] = {"review_count": count, "count_group": group_index, "ratings": []}
        offset += group["products"]
    reviewed = [pid for pid in ids if products[pid]["review_count"]]
    # The (4,4.5] band is impossible for a single integer review. Allocate that
    # constrained band first, uniformly among eligible products, then shuffle the rest.
    feasible = StableRandom("rating-feasible-assignment").shuffle([p for p in reviewed if products[p]["review_count"] >= 2])
    constrained = rules["reviews"]["average_groups"][3]["products"]
    require(len(feasible) >= constrained, "RATING_FEASIBILITY", "Not enough feasible products for exact rating bands")
    chosen = set(feasible[:constrained])
    for pid in chosen:
        products[pid]["average_group"] = 3
    remaining = StableRandom("rating-assignment").shuffle([pid for pid in reviewed if pid not in chosen])
    offset = 0
    for index, group in enumerate(rules["reviews"]["average_groups"]):
        if index == 3:
            continue
        for pid in remaining[offset:offset + group["products"]]:
            products[pid]["average_group"] = index
        offset += group["products"]
    for pid in reviewed:
        record = products[pid]
        n = record["review_count"]
        group = rules["reviews"]["average_groups"][record["average_group"]]
        lower, upper = Decimal(group["lower"]), Decimal(group["upper"])
        # SQL ROUND(numeric,2) and Java HALF_UP agree for these positive averages.
        sums = [s for s in range(n, 5*n + 1)
                if ((Decimal(s)/n).quantize(Decimal('.01'), rounding=ROUND_HALF_UP) >= lower
                    if group["lower_inclusive"] else
                    (Decimal(s)/n).quantize(Decimal('.01'), rounding=ROUND_HALF_UP) > lower)
                and (Decimal(s)/n).quantize(Decimal('.01'), rounding=ROUND_HALF_UP) <= upper]
        require(bool(sums), "RATING_FEASIBILITY", "Assigned rating band has no feasible integer-review average")
        rng = StableRandom("ratings:" + pid)
        total = sums[rng.below(len(sums))]
        remaining_sum = total
        ratings = []
        for i in range(n):
            left = n-i-1
            rating = rng.between(max(1, remaining_sum-5*left), min(5, remaining_sum-left))
            ratings.append(rating)
            remaining_sum -= rating
        record["ratings"] = rng.shuffle(ratings)
    zeros = [p for p in ids if products[p]["review_count"] == 0]
    never = set(StableRandom("never-purchased").shuffle(zeros)[:150])
    others = StableRandom("popularity-assignment").shuffle([p for p in ids if p not in never])
    offset = 0
    demand_by_tier = []
    for tier in rules["popularity"][:-1]:
        members = others[offset:offset+tier["products"]]
        offset += tier["products"]
        for pid in members:
            products[pid].update(tier=tier["name"], completed_items=products[pid]["review_count"]+1)
        demand_by_tier.append((tier, members, sum(products[p]["completed_items"] for p in members)))
    for pid in never:
        products[pid].update(tier="never-purchased", completed_items=0)
    # Exact integer percentages by selecting a multiple of 100; no review redraws.
    total = max(36000, max(math.ceil(demand*100/tier["item_weight_percent"])
                           for tier, _, demand in demand_by_tier))
    total = math.ceil(total/100)*100
    for tier, members, demand in demand_by_tier:
        extra = total*tier["item_weight_percent"]//100-demand
        shuffled = StableRandom("popularity-extra:" + tier["name"]).shuffle(members)
        quotient, remainder = divmod(extra, len(shuffled))
        for i, pid in enumerate(shuffled):
            products[pid]["completed_items"] += quotient + (i < remainder)
    recipe = {"version": "demo-commerce-recipe-v1", "seed": SEED,
              "products": {pid: products[pid] for pid in ids},
              "scheduling": "round-robin-product-quotas-distinct-variants-v1",
              "review_total": sum(p["review_count"] for p in products.values()),
              "completed_item_total": total,
              "completed_order_minimum": 9000,
              "fixed_status_counts": {k: rules["orders"][k] for k in ("PENDING", "CONFIRMED", "SHIPPING", "CANCELLED", "FAILED")}}
    validate_review_choices(recipe, rules)
    return recipe


def validate_review_choices(recipe: dict[str, Any], rules: dict[str, Any]) -> None:
    counts, averages, tiers = Counter(), Counter(), Counter()
    total = 0
    for row in recipe["products"].values():
        n, ratings = row["review_count"], row["ratings"]
        require(len(ratings) == n and all(type(r) is int and 1 <= r <= 5 for r in ratings),
                "INVALID_RECIPE", "Review ratings must be actual eligible integer records")
        bins = [i for i, g in enumerate(rules["reviews"]["count_groups"]) if g["minimum"] <= n <= g["maximum"]]
        require(len(bins) == 1, "INVALID_RECIPE", "Review count falls outside exact bins")
        counts[bins[0]] += 1
        if n:
            avg = (Decimal(sum(ratings))/n).quantize(Decimal('.01'), rounding=ROUND_HALF_UP)
            matching = [i for i, g in enumerate(rules["reviews"]["average_groups"])
                        if (avg >= Decimal(g["lower"]) if g["lower_inclusive"] else avg > Decimal(g["lower"]))
                        and avg <= Decimal(g["upper"])]
            require(len(matching) == 1, "INVALID_RECIPE", "Actual average falls outside exact rating bins")
            averages[matching[0]] += 1
        require(row["completed_items"] >= n, "REVIEW_CAPACITY", "Reviewed product lacks eligible purchase capacity")
        require(row["tier"] != "never-purchased" or (n == 0 and row["completed_items"] == 0),
                "NEVER_PURCHASED", "Never-purchased products must have no sales/reviews")
        tiers[row["tier"]] += 1
        total += n
    require([counts[i] for i in range(5)] == [g["products"] for g in rules["reviews"]["count_groups"]]
            and [averages[i] for i in range(5)] == [g["products"] for g in rules["reviews"]["average_groups"]]
            and tiers == Counter({g["name"]: g["products"] for g in rules["popularity"]})
            and total == recipe["review_total"], "EXACT_DISTRIBUTION", "Frozen recipe violates an exact distribution")


def frozen_order_choices(recipe: dict[str, Any], variants: list[dict[str, Any]]):
    """Deterministic scheduling with deferred reinsertion prevents sibling repeats.

    Persist these rows in the artifact: runtime does not draw orders or reviews.
    """
    by_product: dict[str, list[str]] = {}
    for variant in sorted(variants, key=lambda r: r["id"]):
        by_product.setdefault(variant["product_id"], []).append(variant["id"])
    queue = [(-r["completed_items"], i, pid) for i, (pid, r) in enumerate(recipe["products"].items()) if r["completed_items"]]
    heapq.heapify(queue)
    seen = Counter()
    order_id = 0
    sizes = (4, 4, 3, 4, 2, 4, 1, 4)
    while queue:
        order_id += 1
        held, items = [], []
        for _ in range(min(sizes[(order_id-1) % len(sizes)], len(queue))):
            negative, rank, pid = heapq.heappop(queue)
            ordinal = seen[pid]
            seen[pid] += 1
            ratings = recipe["products"][pid]["ratings"]
            # First item stays unreviewed for real demo journeys.
            rating = ratings[ordinal-1] if 1 <= ordinal <= len(ratings) else None
            items.append({"product_id": pid, "product_variant_id": by_product[pid][ordinal % len(by_product[pid])],
                          "quantity": 1, "rating": rating})
            if negative+1 < 0:
                held.append((negative+1, rank, pid))
        for entry in held:
            heapq.heappush(queue, entry)
        yield {"id": order_id, "customer_id": (order_id-1) % 500+1, "status": "COMPLETED", "items": items}
    purchased = [pid for pid, r in recipe["products"].items() if r["tier"] != "never-purchased"]
    for status, count in recipe["fixed_status_counts"].items():
        for index in range(count):
            order_id += 1
            rng = StableRandom(f"open-items:{status}:{index}")
            selected = rng.shuffle(purchased)[:rng.between(1,4)]
            yield {"id": order_id, "customer_id": index % 500+1, "status": status,
                   "items": [{"product_id": pid, "product_variant_id": by_product[pid][index % len(by_product[pid])],
                              "quantity": rng.between(1,3), "rating": None} for pid in selected]}


def promotion_choices(product_ids: list[str], variant_ids: list[str], rules: dict[str, Any]) -> dict[str, Any]:
    product_ids = StableRandom("sale-assignment").shuffle(sorted(product_ids))
    sales, stocks, offset = {}, {}, 0
    for name in ("none", "active", "scheduled", "expired", "disabled"):
        for i, pid in enumerate(product_ids[offset:offset + rules["sales"][name]]):
            sales[pid] = {"state": name, "percentage": None if name == "none" else rules["sales"]["percentages"][i % 5]}
        offset += rules["sales"][name]
    shuffled = StableRandom("stock-assignment").shuffle(sorted(variant_ids))
    healthy, low = round(len(shuffled)*.90), round(len(shuffled)*.08)
    for i, vid in enumerate(shuffled):
        limits = (20,200) if i < healthy else (1,5) if i < healthy+low else (0,0)
        stocks[vid] = StableRandom("stock:"+vid).between(*limits)
    return {"sales": sales, "available_stock": stocks}


def resolved_graph(root, anchor: datetime, private_password: str):
    """Yield data records at one saved UTC anchor; all choices were frozen offline."""
    import bcrypt
    from bootstrap import read_json
    from catalog import jsonl
    anchor = anchor.astimezone(timezone.utc)
    stamp = lambda dt: dt.isoformat(timespec="microseconds")
    products = jsonl(root / "catalog/products.jsonl")
    variants = jsonl(root / "catalog/variants.jsonl")
    recipe = read_json(root / "scenarios/recipe.json")
    promotions = read_json(root / "scenarios/promotions.json")
    rules = read_json(root / "rules/distributions.json")
    accounts = {r["id"]: r for r in read_json(root / "scenarios/demo-accounts.json")}
    for row in read_json(root / "catalog/categories.json"):
        yield "categories", row
    for row in read_json(root / "catalog/brands.json"):
        yield "brands", row
    for row in products:
        row = dict(row)
        sale = promotions["sales"][row["id"]]
        state = sale["state"]
        start, end = {"none": (None,None), "active": (anchor-timedelta(days=30),anchor+timedelta(days=30)),
                      "scheduled": (anchor+timedelta(days=7),anchor+timedelta(days=37)),
                      "expired": (anchor-timedelta(days=60),anchor-timedelta(days=30)),
                      "disabled": (anchor-timedelta(days=30),anchor+timedelta(days=30))}[state]
        row.update(sale_percentage=sale["percentage"],sale_enabled=state not in {"none","disabled"},
                   sale_starts_at=stamp(start) if start else None,sale_ends_at=stamp(end) if end else None)
        yield "products", row
    for row in variants:
        yield "product_variants", row
    for row in read_json(root / "assets/images.json"):
        yield "product_images", {**row, "byte_size": row["bytes"], "created_at": stamp(anchor)}
    for row in jsonl(root / "provenance/source-links.jsonl"):
        yield "catalog_source_links", {**row,"updated_at":stamp(anchor)}
    for row in jsonl(root / 'provenance/fields.jsonl'):
        yield 'original_variants',{'id':row['original_variant_id'],'sku':row['original_sku'],'base_price':row['original_base_price']}
    for pid,record in recipe['products'].items():
        yield 'coverage',{'product_id':pid,**{k:record[k] for k in ('tier','review_count','completed_items')},
                         'expected_rating_sum':sum(record['ratings'])}
    for uid in range(1,501):
        username = accounts[uid]["username"] if uid in accounts else f"synthetic_customer_{uid:03d}"
        password = bcrypt.hashpw(private_password.encode(),bcrypt.gensalt(rounds=10)).decode() if uid in accounts else None
        yield "users", {"id":uid,"display_name":f"Synthetic Customer {uid:03d}","username":username,
                        "password":password,"email":f"{username}@demo.example.invalid","phone_number":f"09{uid:08d}",
                        "account_status":"ACTIVE","joined_at":stamp(month_offset(anchor,-13))}
        aid = uid*2-1 if uid <=12 else uid+12
        yield "addresses", {"id":aid,"recipient_name":f"Synthetic Customer {uid:03d}","recipient_phone":f"09{uid:08d}",
                            "address_text":f"Educational demo address {uid:03d}, Ho Chi Minh City", "is_default":True,"user_id":uid}
        if uid<=12:
            yield "addresses", {"id":aid+1,"recipient_name":f"Synthetic Customer {uid:03d}","recipient_phone":f"09{uid:08d}",
                                "address_text":f"Educational alternate address {uid:03d}","is_default":False,"user_id":uid}
    # Quotas are real records; AVAILABLE demo grants remain even for exhausted definitions.
    for index, definition in enumerate(rules["vouchers"],1):
        state = definition["scenario"]
        start,end = ((7,37) if state=="scheduled" else (-60,-30) if state=="expired" else (-400,30))
        yield "vouchers", {"id":index,"code":definition["code"],"title":"Educational "+definition["scenario"],
                           "discount_type":definition["type"],"discount_value":definition["value"],
                           "minimum_subtotal":definition["minimum"],"maximum_discount":definition.get("maximum_discount"),
                           "valid_from":stamp(anchor+timedelta(days=start)),"valid_until":stamp(anchor+timedelta(days=end)),
                           "enabled":state!="disabled","global_usage_limit":definition.get("global_limit",5000),
                           "per_customer_usage_limit":definition.get("customer_limit",2)}
        for uid in range(1,13):
            identity=(index-1)*12+uid
            yield "voucher_customer_usages",{"id":identity,"voucher_id":index,"customer_id":uid}
            yield "voucher_entitlements",{"id":identity,"voucher_customer_usage_id":identity,"voucher_id":index,
                                          "customer_id":uid,"granted_at":stamp(month_offset(anchor,-13))}
    # Match historical exhaustion to early, genuine, sufficiently valuable orders.
    redemption_targets={7:20,8:1}
    redemption_counts=Counter()
    usages={(vid,uid):(vid-1)*12+uid for vid in range(1,9) for uid in range(1,13)}
    extra_usage=96
    grant_id=96
    redemption_id=0
    products_by_id={p["id"]:p for p in products}
    variants_by_id={v["id"]:v for v in variants}
    item_id=review_id=0
    history_start=month_offset(anchor,-12)
    span=(anchor-timedelta(days=7)-history_start).total_seconds()
    completed=recipe["status_counts"]["COMPLETED"]
    for order in jsonl_stream(root / "scenarios/orders.jsonl"):
        oid,uid,status=order["id"],order["customer_id"],order["status"]
        placed=(history_start+timedelta(seconds=span*(oid-1)/max(1,completed-1)) if status=="COMPLETED"
                else anchor-timedelta(hours=1+(oid%144)))
        completed_at=placed+timedelta(hours=24) if status=="COMPLETED" else None
        cancelled_at=placed+timedelta(minutes=30) if status=="CANCELLED" else None
        yield "orders",{"id":oid,"customer_id":uid,"order_number":f"DEMO-{oid:08d}","placed_at":stamp(placed),"status":status,
                        "recipient_name_snapshot":f"Synthetic Customer {uid:03d}","recipient_phone_snapshot":f"09{uid:08d}",
                        "delivery_address_snapshot":f"Educational demo address {uid:03d}, Ho Chi Minh City",
                        "completed_at":stamp(completed_at) if completed_at else None,"cancelled_at":stamp(cancelled_at) if cancelled_at else None,
                        "cancellation_reason":"Synthetic cancellation" if cancelled_at else None}
        for item in order["items"]:
            item_id+=1
            yield "order_items", {"id":item_id,"order_id":oid,"product_variant_id":item["product_variant_id"],"quantity":item["quantity"]}
            if item["rating"] is not None:
                review_id+=1
                reviewed=completed_at+timedelta(hours=1)
                yield "product_reviews",{"id":review_id,"order_item_id":item_id,"author_id":uid,"product_id":item["product_id"],
                                         "rating":item["rating"],"comment":f"Synthetic educational purchase review: {item['rating']}/5.",
                                         "created_at":stamp(reviewed),"updated_at":stamp(reviewed)}
        # Lower-bound subtotal remains eligible even under maximum approved 30% sale.
        subtotal_lower=sum(int(variants_by_id[i["product_variant_id"]]["base_price"])*i["quantity"]*70//100 for i in order["items"])
        if status=="COMPLETED" and subtotal_lower>=500000:
            for vid,limit in redemption_targets.items():
                if redemption_counts[vid]>=limit or (vid==8 and uid!=1):
                    continue
                if (vid,uid) not in usages:
                    extra_usage+=1;usages[vid,uid]=extra_usage
                    yield "voucher_customer_usages",{"id":extra_usage,"voucher_id":vid,"customer_id":uid}
                usage=usages[vid,uid]
                # Global voucher allows at most two historical purchases/customer.
                if redemption_counts["customer",vid,uid]>= (1 if vid==8 else 2):
                    continue
                grant_id+=1;redemption_id+=1
                yield "voucher_entitlements",{"id":grant_id,"voucher_customer_usage_id":usage,"voucher_id":vid,
                                              "customer_id":uid,"granted_at":stamp(month_offset(anchor,-13))}
                yield "voucher_redemptions",{"id":redemption_id,"voucher_id":vid,"voucher_entitlement_id":grant_id,
                                             "voucher_customer_usage_id":usage,"customer_id":uid,"order_id":oid,"redeemed_at":stamp(placed)}
                redemption_counts[vid]+=1;redemption_counts["customer",vid,uid]+=1
                break
    require(redemption_counts[7]==20 and redemption_counts[8]==1,"VOUCHER_CAPACITY","Frozen purchases cannot satisfy actual voucher exhaustion")
    for vid,available in promotions["available_stock"].items():
        yield "stock",{"variant_id":vid,"available":available}
    variant_ids=sorted(variants_by_id)
    unavailable=sorted(vid for vid,n in promotions['available_stock'].items() if n==0)
    cart_item_id=0
    for uid in range(1,301):
        yield "carts",{"id":uid,"user_id":uid,"voucher_entitlement_id":uid if uid<=12 else None}
        count=2+(uid%5)
        cart_variants=set()
        for j in range(count):
            cart_item_id+=1
            vid=variant_ids[(uid*7+j)%len(variant_ids)]
            if uid==5 and j==0 and unavailable:
                vid=unavailable[0]
            offset=uid*7+j
            while vid in cart_variants:
                offset+=1
                vid=variant_ids[offset%len(variant_ids)]
            cart_variants.add(vid)
            yield "cart_items",{"id":cart_item_id,"cart_id":uid,"product_variant_id":vid,"quantity":1+j%3,
                                "selected":j%2==0,"added_at":stamp(anchor-timedelta(hours=j+1))}
    ids=sorted(products_by_id)
    for uid in range(1,501):
        for j in range(7):
            yield "wishlist_items",{"id":(uid-1)*7+j+1,"user_id":uid,"product_id":ids[(uid*17+j*31)%1500],
                                     "added_at":stamp(anchor-timedelta(days=j+1))}
    for uid in range(1,13):
        categories=sorted({p['category_label'] for p in products})
        category=categories[(uid-1)%len(categories)]
        interest=sorted(p['id'] for p in products if p['category_label']==category)
        query=' '.join(__import__('re').findall(r'[a-z]+',category.lower()))
        for j in range(30):
            identity=[uid,j]
            kind=("PRODUCT_VIEW","SEARCH","PRODUCT_CLICK")[j%3]
            yield "user_behavior_events",{"id":logical_uuid("event",identity),"customer_id":uid,"event_type":kind,
                                          "product_id":None if kind=="SEARCH" else interest[(uid*13+j)%len(interest)],
                                          "search_query":query if kind=="SEARCH" else None,
                                          "normalized_query":query if kind=="SEARCH" else None,
                                          "client_event_id":logical_uuid("client-event",identity),
                                          "source_context":"PRODUCT_LIST" if kind=="PRODUCT_CLICK" else None,
                                          "occurred_at":stamp(anchor-timedelta(hours=j*23+uid))}


def jsonl_stream(path):
    import json
    from bootstrap import unique_fields, invalid_constant
    with path.open(encoding="utf-8") as stream:
        for line in stream:
            if line.strip():
                yield json.loads(line,object_pairs_hook=unique_fields,parse_constant=invalid_constant)
