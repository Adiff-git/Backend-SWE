# Data Model and Migration Design

All changes below are proposed implementation work. Preserve existing UUID catalog identifiers and numeric customer/order/review IDs. Current latest migration is V27; allocate new V28+ filenames only after rechecking the tree.

## Product — existing `products`

Add nullable `sale_percentage NUMERIC` (not NUMERIC(5,2), which could round invalid input before validation), `sale_enabled BOOLEAN NOT NULL DEFAULT FALSE`, `sale_starts_at TIMESTAMPTZ`, `sale_ends_at TIMESTAMPTZ`.

- Percentage absent means no configured sale: enabled=false and bounds=null. Present percentage is finite, >0, <=100, with no more than two fractional decimal places; reject, never round invalid configuration.
- A configured sale can be disabled and retain valid bounds/percentage. Present start must be earlier than present end.
- Active is derived, never persisted: enabled AND (start absent OR at>=start) AND (end absent OR at<end).
- One product owns at most one configuration. State transitions absent/configured-disabled/future/active/expired are derived from these fields and explicit time, without scheduler writes.
- Sale edits acquire the same parent-row coordination as checkout. No management API or permission is added.

## ProductVariant — existing `product_variants`

Add `base_price NUMERIC`, backfill from selling_price and make NOT NULL after successful preflight. Require finite, integral and nonnegative values and retain current amount bounds at response/checkout boundaries. Effective price is never separately authoritative storage.

Retain `selling_price` as a deprecated base mirror to keep schema changes additive. A BEFORE trigger sets it from base on new writes; an independent mirror edit is rejected. Existing app writers/fixtures migrate to base in the same coordinated cutover. Both columns equal after backfill; neither the old column nor the field name in historical code proves a pre-discount original. No mixed old/new pricing deployment is supported.

Existing option_combination remains presentation labels. The canonical SQL map projection is derived from it; no independently editable canonical identity is introduced. The deferred V3-style parent-coordinated check enforces:

1. At least one variant per product.
2. Blank/null/non-string option names/values are invalid.
3. One empty-map default can exist only as the sole variant.
4. Canonical maps are unique within one product.
5. Every optioned sibling has the same canonical key set.

Canonicalizer: NFC -> trim edge Unicode White_Space -> root Unicode lowercase. White_Space is U+0009–000D, U+0020, U+0085, U+00A0, U+1680, U+2000–200A, U+2028–2029, U+202F, U+205F, U+3000. Preserve internal whitespace/accents. Explicit deterministic ICU root collation is a capability prerequisite. Detect duplicate canonical keys before constructing JSONB, rather than silently overwriting entries. Deferred checks lock affected parents in UUID order before fresh child snapshots; concurrent writers must not both commit a duplicate or mismatched dimension set.

## Canonical price projection

`catalog_effective_price_v1(base,percentage,enabled,start,end,at)` uses no current timestamp. Invalid base returns null for existing eligibility classification. With valid configuration it returns base if inactive, otherwise base−floor(base×percentage/100). Unit discount=base−effective; active percentage is nullable. Null at is an internal contract error, not an implicit clock lookup. Root SQL function/schema constraints are the only formula authority.

Response domain value `VariantPricing`: original/effective money, direct unit discount money, nullable configured active percentage, pricedAt. It is prepared by Catalog and consumed by converters/calculators without recalculating the sale. Existing unit/line integer multiplication and order-level voucher calculation remain separate responsibilities.

## OrderItem — existing `order_items`

Add nullable legacy-compatible `original_unit_price_snapshot NUMERIC`, `direct_unit_discount_snapshot NUMERIC`, `direct_sale_percentage_snapshot NUMERIC`, `direct_sale_starts_at_snapshot TIMESTAMPTZ`, `direct_sale_ends_at_snapshot TIMESTAMPTZ` and `pricing_instant_snapshot TIMESTAMPTZ`.

- Existing `unit_price_snapshot` is actual effective purchased price, and line_total=unit×quantity remains exact.
- Legacy items leave all new fields null. Do not infer original from paid price.
- Items created by the completed v3 flow require original/discount/instant and enforce original−discount=paid, 0<=discount<=original, integral money and consistent active-sale evidence. No sale means original=paid, discount=0, percentage/bounds=null; an active sale on zero base retains active percentage with zero discount.
- New snapshot time equals order placedAt/common checkout instant. Active sale bounds/percentage explain validity at purchase; snapshots remain immutable with existing names/SKU/options/quantity.
- Integrity requires all new items created by v3 checkout to carry evidence; nullable legacy columns cannot become a bypass for new v3 orders. Extend deferred order/receipt validation to enforce this.

## CheckoutReceipt — existing durable receipt

Accept versions 1/2/3 via additive constraints/functions. v3 contains new item evidence; stored placedAt is the common pricing instant. Version-specific exact-shape validators and deferred comparison to stored order items must remain strict. Keep old validators and stored bodies unchanged. Codecs preserve old response shapes without inserting new null fields. Retain current customer/operation/key uniqueness, fingerprint, fence and order-lifetime retention.

## Reviews and rating summary

No new review model/table or duplicated purchased-option column. Existing product_reviews.order_item_id links immutable options. Add a bounded Order repository projection keyed by item ID, exposed through OrderReviewReadService to Review only. Product-wide grouped counts zero-fill each rating 1–5 and sum to reviewCount; average remains null for zero and existing two-decimal HALF_UP otherwise. Filters do not alter summary.

Indexes: reuse existing product/review/image indexes first; inspect EXPLAIN for filtered page/count. Add a composite product/rating/ID or product/time/ID index only when measured plans justify it. Image existence must use its review-reference index. Never replace filtered correctness with an index assumption.

## Immutable operation pricing

`PreparedOperationPricing` is a request/operation-local immutable value (Lombok response/internal DTO under Catalog) containing evaluation Instant and variant-ID-indexed prepared original/effective/discount/active-sale evidence. Deep-copy maps and values; do not cache in entities, sessions, shared beans or across requests. Catalog obtains all relevant base/product-sale inputs in one set-based statement snapshot (bound variant ID array) under existing READ COMMITTED, with one bound evaluation instant. Avoid independent pricing batches with different statement snapshots. Inventory/image/publication work retains its existing boundaries; publication/availability revalidation may redact/exclude rows but must not reprice them. Derive totals/voucher eligibility from the final visible/eligible prepared set, not a mixture of old prices and newly reread prices.

Cart owner/coordinator prepares after establishing its existing operation/cart view. Voucher application reuses the same object for initial eligibility, selected-voucher update and final response; remove the second catalog pricing read. Subsequent operations always prepare anew. Checkout does not consume cart's prepared object: it independently locks/loads current catalog values, captures its own Instant and performs current inventory/voucher checks. No isolation increase or new locking is introduced solely for response pricing coherence.

## Foundation installation and intermediate compatibility

V28, V29 and V30 (or their rechecked ascending successors) are all installed in Foundation. A migration adding globally required new-item evidence would break the current native v2 writer; therefore V30 is expansion with version-aware enforcement: v1/v2 may retain the existing all-null evidence shape, v3 requires every item to have complete validated evidence. Reject partial populated evidence for every version. Preserve exact old validators, bodies and immutable history. Do not use Flyway out-of-order or a permissive v3 fallback.

V28 base/mirror requires matching base mappings and fixture writers in Foundation; it is not promised compatible with an unmodified old catalog pricing writer. Current consumer behavior with no configured sale must pass a Foundation compatibility gate after all schema stages. V29 stricter identities require preflight-compatible data and fixture updates, not silent coercion. No mixed old/new pricing writers or active sale rollout is permitted during intermediate stages. Isolated SQL/function/constraint test fixtures may exercise active configurations in Foundation; they do not activate an incomplete application sale flow. If real data/capability/writer checks fail, block at Foundation and report a dependency/staging correction before implementation continues; do not bypass checks or invent original history.

## Upgrade/cutover sequence

1. Read-only audit actual base values, canonical-key collisions, combination collisions and inconsistent dimension sets; produce IDs/reasons. Audit existing ICU root capability/version and normalization examples. Block migration for unresolved invalid data; no auto-merge, zeroing or original-price reconstruction.
2. Add price/config columns/functions; backfill base=current price and no sale; preserve IDs/order/receipt bytes. Install mirror integrity and canonical deferred checks after audit. Migration failure must roll back atomically.
3. Add nullable historical evidence and v3 validators; branch receipt checks by version without changing applied V1–V27 files.
4. Verify Foundation intermediate compatibility, including existing no-sale cart/checkout/voucher/order behavior and v2 writes/replays after V28, V29 and V30 individually and together; complete US2 native INSERT bindings, committed-row reload tests and v3 purchase path before enabling sales. Deploy matching consumers/writers together, using base storage/derived responses. No old application writes during cutover. Verify successful old receipt replay before enabling sale fixtures.
5. Validate all constraints, old/new fixture upgrades, current data counts/references and price totals. Application rollback to old pricing semantics while sales are configured is unsupported; use a tested forward corrective migration, never overwrite historic receipts or edit applied migrations.

No production migration is executed during planning.
