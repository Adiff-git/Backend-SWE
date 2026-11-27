# Feature Specification: Variant Selection, Review Queries and Direct Sales

**Feature Branch**: `draft/version-1` (unchanged)

**Created**: 2026-11-26

**Status**: Implemented in the working tree; targeted/story checkpoints passed, final performance and unrestricted verification in progress. Business decisions and analysis resolutions confirmed 2026-11-26.

**Input**: Enhance existing Backend-SWE option combinations, review queries and direct sale pricing while preserving cart, inventory, voucher, history and checkout recovery contracts.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Select one concrete combination (Priority: P1)

As a shopper, I select option values and identify one variant, SKU, current prices and availability before adding it to my cart.

**Why this priority**: Color and size must identify one purchase, rather than separate purchased variants.

**Independent Test**: Browse a default-variant product and a Color/Size product, select values and add a resolved variant.

**Acceptance Scenarios**:

1. **Given** Red/M exists, Red/L is absent and Blue/M has no available stock, **When** selecting each, **Then** distinguish purchasable, nonexistent and existing-unavailable; choosing only Color is incomplete.
2. **Given** one variant with empty options, **When** viewed, **Then** it is the default without fictitious dimensions.
3. **Given** one variant, **When** adding quantities 2 then 3, **Then** one selected row has quantity 5; a resulting quantity above 999 fails unchanged.
4. **Given** source A and absent same-product B, **When** changing to B, **Then** quantity resets to 1 and source selection is preserved; if B already exists, remove A and preserve B's quantity and selection. Changing to the same variant is a no-op.
5. **Given** another product's variant or an invalid selection, **When** submitted for this product, **Then** reject without mutation.
6. **Given** equivalent normalized combinations, **When** catalog data is validated, **Then** reject duplicates without merging identities or generating missing combinations.

### User Story 2 - See and purchase the current sale price (Priority: P1)

As a shopper, I see honest original/effective price pairs and purchase at current prices with a separate order voucher.

**Why this priority**: Display, payable totals and history must agree.

**Independent Test**: Use no-sale, active-sale and inactive-sale fixtures; view a cart, checkout and replay the purchase.

**Acceptance Scenarios**:

1. **Given** base 1,000,000 VND and applicable 20% reduction, **When** viewed/purchased, **Then** original is 1,000,000 and effective is 800,000 VND.
2. **Given** no sale, disabled, future or expired sale, **When** viewed, **Then** effective equals base and no active reduction is advertised.
3. **Given** differently priced variants, **When** viewing ranges or selecting one, **Then** use effective ranges and pairs from the same variant, never unrelated original/sale extrema.
4. **Given** a sale changes after cart view, **When** checkout occurs, **Then** all included lines use current prices at one checkout pricing instant; previews do not lock prices.
5. **Given** sale-priced items and a voucher, **When** purchasing, **Then** voucher eligibility uses the after-sale subtotal and the discount remains order-level without item voucher allocations.
6. **Given** a successful purchase and later name/SKU/options/price changes, **When** history is read or checkout replayed, **Then** original purchase facts remain intact and replay creates no order, reservation or voucher use.
7. **Given** fractional percentage results, zero base or exact validity boundaries, **When** priced, **Then** the confirmed rounding/time rules agree across display, cart and checkout.
8. **Given** base 101 VND, sale 12.50% and quantity 3, **When** priced, **Then** unit discount is 12 VND, effective unit price is 89 VND and line total is 267 VND. A 100% sale yields zero; 0%, negative, above 100 and more than two decimal places are invalid configuration.
9. **Given** start T1 and end T2, **When** the common pricing instant equals T1, **Then** the sale applies; at T2 it does not. A checkout that crosses T2 retains the result from its captured instant.
10. **Given** bases 1,000,000 and 1,500,000 VND under 20%, **When** viewing the range, **Then** effective endpoints are 800,000 and 1,200,000, paired respectively with their own bases. A 10% uncapped voucher on one 800,000 VND line discounts the order by 80,000 and payable is 720,000 VND, subject to existing eligibility.

11. **Given** a cart/voucher operation has prepared pricing, **When** base prices or sale configuration change at a former redundant read point, **Then** its eligibility, subtotal, voucher calculation and response reuse that operation's coherent prepared values; a later request and checkout obtain fresh authoritative inputs.
12. **Given** a v3 checkout has committed, **When** its order items are reloaded from PostgreSQL through a fresh read, **Then** original/effective/discount/percentage/bounds/time snapshots match the purchase and receipt and satisfy the stored constraints.

### User Story 3 - Find relevant reviews (Priority: P2)

As a shopper, I filter and sort reviews without changing the overall product rating.

**Why this priority**: Relevant experiences should be discoverable without misleading aggregates.

**Independent Test**: Browse mixed ratings, timestamps, comments and images with filters and all supported sorts.

**Acceptance Scenarios**:

1. **Given** mixed ratings, **When** applying the exact-rating filter, **Then** every result matches and pagination counts describe only matches.
2. **Given** image/comment predicates, **When** combined with rating, **Then** predicates are conjunctive and apply before pagination; a review with three images appears once and retains image display order.
3. **Given** tied ratings/timestamps, **When** traversing all pages under each supported sort, **Then** the unique tie-breaker prevents duplicates/omissions for unchanged data.
4. **Given** invalid, empty, repeated or unknown query values, **When** requested, **Then** return safe validation errors rather than arbitrary field sorting.
5. **Given** any list filter, **When** overall average/count or the rating distribution is read, **Then** it remains product-wide.

### Edge Cases

- Missing/extra dimensions, unknown values, case/whitespace differences, duplicate normalized keys, inconsistent dimension sets and legacy collisions.
- Empty default combinations alongside optioned variants; missing inventory, fully reserved stock, hidden products and quantities 1/999/1000.
- Empty review matches, out-of-range pages, null/whitespace comments, multiple images, tied sorts and unavailable image URLs.
- Zero base, maximum reduction, smallest percentage increment, fractional results, sale start/end during checkout, base changes during active sales and different variant base prices under one product-wide percentage.
- No eligible checkout rows, invalid selected voucher, concurrent sale edits, rollback after reservation, lost response and old receipt versions.

## Requirements *(mandatory)*

### Functional Requirements

#### Confirmed option and cart behavior

- **FR-001**: Reuse Product/ProductVariant. Each concrete purchasable combination belongs to one product and retains its own variant identifier/SKU. Do not create duplicate variant models or treat individual dimensions as purchases.
- **FR-002**: Detail MUST expose deterministic dimensions/values and enough variant options, IDs, SKU, current prices and inventory availability to classify incomplete, nonexistent, unavailable and purchasable selections. No theoretical combinations are automatically generated.
- **FR-003**: Retain existing variant-ID cart/checkout commands. A new option-value command is excluded; if later approved, the server must resolve the complete selection and verify membership rather than trust client-generated identities.
- **FR-004**: Reject blank/invalid names and values and equivalent combinations within a product. Retain the existing one-or-more-variants invariant and one empty-map default without optioned siblings. Apply the confirmed normalization and ordering rules below.
- **FR-005**: Preserve cart merge/change/selection behavior in Story 1, quantities 1–999 and feature010 full-quantity availability checks. Cross-product changes fail unchanged. Cart never reserves inventory and observed availability never guarantees checkout.

#### Confirmed review behavior

- **FR-006**: Keep reviews separately paginated through the existing product-review list. No unbounded detail embedding or new preview is required.
- **FR-007**: Apply the specified filters conjunctively before paging and identically to counts. Preserve page default 0, size default 20, size 1–100 and empty out-of-range pages.
- **FR-008**: Preserve existing allowlisted sorts `createdAt,desc` (default), `createdAt,asc`, `rating,desc`, `rating,asc`, followed by review ID descending. Stable traversal assumes unchanged data; do not promise a frozen dataset across later writes.
- **FR-009**: Preserve ordered images, safe author summaries and existing safe image handling. Image matching MUST NOT multiply reviews/counts. Overall average/count remain independent of list filters.

#### Confirmed pricing, checkout and history behavior

- **FR-010**: Add direct sales separately from vouchers and expose original/base and effective prices per variant. Different variants may have different bases. A general promotion engine is excluded.
- **FR-011**: Base and sale configuration determine effective price through one consistent rule. Never permit independently edited contradictory base/percentage/effective values. Preserve nonnegative whole-VND money and existing arithmetic bounds. Current sellingPrice does not prove an earlier original price.
- **FR-012**: Cards, detail, cart reads/mutations and catalog price filtering/sorting MUST use consistent effective prices. Selected variants expose exact original/effective prices and the applicable direct reduction.
- **FR-013**: Effective minimum/maximum use the existing catalog price-eligible variant population with effective prices substituted; stock must not silently redefine catalog price eligibility. Each accompanying original price MUST belong to that same endpoint variant. Retain deterministic representative-variant ties; no eligible price yields null, not invented zero.
- **FR-014**: Checkout revalidates authoritative variant/publication/quantity/price and locked inventory with existing precedence. All included items use one captured pricing instant and coherent sale/base configuration; later processing must not individually reprice lines. Replay resolves before new pricing/voucher work.
- **FR-015**: Unit effective price × quantity gives line total; sum included lines for merchandise subtotal; apply the eligible selected order voucher once; payable=subtotal−voucher discount. Allow stacking using the after-sale subtotal and preserve existing caps, minimum-spend and invalid-selected-voucher behavior. Do not allocate vouchers to items.
- **FR-016**: Future order items preserve product name, retained variant reference, SKU, option labels, quantity, original price, effective purchased unit price, applicable direct-sale evidence and line total. Keep separate order voucher snapshots. Later edits/deletions never rewrite order/receipt facts.
- **FR-017**: Preserve atomic order/cart/inventory/voucher/receipt writes, existing ownership/permissions and checkout idempotency. Replay returns the stored outcome without another order, reservation or voucher use. Failures roll back all effects.
- **FR-018**: Use additive migrations; preserve existing IDs, inventory links, paid amounts, options and receipt versions. Unknown historic original/sale facts remain unknown. Never modify applied migrations, silently merge variants or invent historic discounts.
- **FR-019**: Before rollout, audit actual price provenance, normalized collisions and inconsistent dimension sets. Backfill preserves current payable amounts. Initialize base=current sellingPrice with no sale; this initializes future pricing only, not recovered historical original value. Invalid/colliding data requires a report and approved remediation.
- **FR-020**: Preserve public visibility and owned authenticated cart/checkout/order rules, safe ProblemDetail errors and current status mappings. Reads remain 200, checkout/replay 201, invalid review queries 400 and hidden/missing public products safe 404. No unrestricted sale-write route is authorized.
- **FR-021**: Planning must assess legacy sellingPrice consumers, price filters/ranges, immutable receipt versions, migration compatibility, bounded fetch plans and filtered counts. Follow existing architecture and risk-based testing, including real transaction/concurrency checks where affected.
- **FR-022**: Preserve feature007 search's same-variant combined price/specification matching, representative-variant UUID tie-breakers, nullable orphan compatibility and approved workload performance/query budgets while substituting effective prices. Never match a price on one variant and specifications on another. Preserve global case-insensitive SKU uniqueness and technical specification contracts.

#### Confirmed business decisions

The user selected **Q1: A, Q2: A, Q3: A** on 2026-11-26. The following rules are binding requirements.

- **FR-023 — Sale scope**: Persist one product-wide percentage sale configuration applied to each variant's own persisted base price. Support an enabled flag and optional UTC start/end. Exclude fixed-amount discounts, variant-specific configurations and sale-management APIs; use controlled fixtures/test data in this feature.
- **FR-024 — Price source of truth**: Persist base prices and sale configuration; derive effective prices without a separately editable or authoritative persisted discounted price. Percentage must be greater than 0 and at most 100, with at most two decimal places. For an active sale, discount=floor(base×percentage/100) whole VND and effective=base−discount. Round the unit discount before multiplying quantity; zero base remains zero. A 0% configuration is invalid; no sale is represented by absence or inactivity.
- **FR-025 — Validity**: A sale is active when enabled, start is absent or pricingInstant>=start, and end is absent or pricingInstant<end. Present start must precede end. Use one captured checkout instant for all direct-sale and voucher validity checks.
- **FR-026 — Stacking**: Allow vouchers on directly discounted merchandise. Voucher eligibility/minimum spend and percentage calculation use the included merchandise subtotal after direct reductions; retain existing zero-subtotal, cap and invalid-selected-voucher rules. Voucher discounts remain order-level.
- **FR-027 — Review and options**: Support exact single rating, image/comment boolean filters, all four existing sorts, product-wide rating distribution and historical purchased options in review responses. Derive ordered option dimensions/values using the identity rules below; do not store merchandising order.

#### Approved analysis resolutions (2026-11-26)

- **FR-028 — Persisted purchase evidence**: Update all affected persistence writers, including native order-item INSERT columns and parameter bindings. Every completed v3 purchase must persist all required sale snapshots and satisfy v3 integrity. Verify committed PostgreSQL rows through a fresh persistence context/independent read, not only commands or in-memory objects.
- **FR-029 — Ordered Foundation schema**: Install all feature schema migrations in Foundation, in ascending version order, before independent US1/US2 implementation/testing. Do not enable Flyway out-of-order. Intermediate no-sale behavior must remain compatible: expanded nullable snapshot storage preserves v1/v2 writes/replays, while v3 requires complete evidence. Upgrade tests must exercise existing writers after each schema stage. No active sales are enabled until US2 and its native writer/receipt/checkout integration pass. This is implementation staging, not permission for old/new pricing writers to coexist in production.
- **FR-030 — Operation-scoped prepared pricing**: Obtain one coherent set of base/sale inputs and evaluate prices once using one operation instant. Reuse immutable prepared pricing for voucher eligibility, discount/subtotal calculation and response construction; remove redundant pricing reads. Do not reuse it across requests. Retain default isolation/concurrency policies; checkout independently obtains current protected pricing and revalidates vouchers, inventory and purchase eligibility. Regression coverage must change base prices and sale configuration between the former read points.

### Confirmed Rules and Query Contract

| Area | Required behavior |
| --- | --- |
| Option order | Derive dimensions/values from variants; require one normalized dimension set per optioned product. Sort dimensions/values by normalized Unicode code-point order. No manually stored merchandising order. |
| Identity | Unicode NFC, trim surrounding Unicode whitespace, locale-independent lowercase; preserve internal whitespace and accents. Ignore map key order. Reject duplicate normalized keys/blank values. Keep display labels; choose the lowest variant UUID's label deterministically if labels differ for one identity. Renaming never changes history. |
| Rating | Optional single `rating=1` through `rating=5`; omitted means any; repeated/multiple ratings rejected. |
| Flags | Optional `hasImages=true/false`, `hasComment=true/false`; omitted means unrestricted. False means absent images/absent nonblank comment. Comment presence requires a non-whitespace Unicode code point. Image presence means attached references regardless of transient URL-signing failures. |
| Validation | Empty, literal null, malformed, repeated or unknown parameters return 400; booleans accept lowercase true/false only. Preserve existing page/size/sort validation. Exclude dates, rating sets and variant-ID review filters. |
| Review additions | Product-wide zero-filled 1–5 distribution sums to total count; retain existing average precision/no-review semantics. Purchased options come from immutable order-item snapshots through the owning service, without recipient details or private order identifiers. |
| Inactive sale | Absent, disabled, future or expired sale returns base=effective, direct discount zero and no active percentage. |
| Base edits | During active sale, valid base changes affect future effective prices under the same percentage, never old orders. Invalid bases retain current ineligibility rules. |
| Card percentage | Product-wide model reports active configured percentage alongside variant price pairs. Rounded realized ratios may differ from configured percentage. |
| Time | Omitted start/end means unbounded on that side; present start precedes end. Capture checkout instant after acquiring a coherent catalog purchase view, before direct-price/voucher evaluation; use it for every included line and voucher check. One display instant per response. |
| Management | Controlled fixtures/test data only. No sale-management API or pricing permission is introduced by this feature. |

### Key Entities *(include if feature involves data)*

- **Product/variant**: Existing parent and concrete optioned SKU with UUID identity, labels and variant-level base; no duplicate model.
- **Direct sale configuration**: One persisted product-wide percentage, enabled state and optional validity bounds determining derived per-variant effective prices.
- **Review/images**: Existing verified-purchase review, rating, optional comment and ordered images; queries reuse it.
- **Order item/receipt**: Existing immutable purchase facts with additive sale evidence; missing historic facts stay unknown.
- **Inventory/voucher**: Existing availability/reservations and separate order-level entitlement/discount; no replacement engine.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of default/complete/incomplete/missing/unavailable selection fixtures classify correctly; zero cross-product purchases or duplicate normalized combinations are accepted.
- **SC-002**: Every approved filter combination returns exactly expected IDs/counts; unchanged-data traversal has zero duplicates/omissions and preserves image order.
- **SC-003**: No-sale, active/inactive, rounding, zero-base, time-boundary and different-base examples agree across display/cart/purchase with zero VND discrepancy under approved rules.
- **SC-004**: Every sale-plus-voucher example matches approved subtotal/discount/payable calculations, with zero item voucher allocations.
- **SC-005**: All historical reads/replays retain original purchase facts after later changes, with zero additional orders, reservations or voucher uses.
- **SC-006**: Upgrade fixtures preserve 100% of old paid amounts/options/receipts; every normalization conflict is reported, with zero silent merges or fabricated historical original prices.

## Assumptions

- Dependencies: current constitution and features 001 catalog, 005 catalog enhancement, 007 search, 007 cart, 008 orders, 009 checkout, 010 inventory, 011 vouchers and 012 reviews. Existing code is evidence, not automatic approval of new policy.
- Scope covers consumer catalog/cart/checkout/order/review behavior and additive compatibility. Frontend implementation, promotion engines, stock administration, new payment/shipping behavior and unrelated security changes are excluded. Pricing administration, fixed-amount direct discounts and variant-specific sale configuration are excluded.
- Q1–Q3 were confirmed as A/A/A on 2026-11-26. The rules and acceptance expectations in this specification reflect those choices.
- Documentation only: retain current branch, create one feature, run document checks. Runtime behavior and application tests are not certified by this work.

## Existing Capabilities and Evidence

See [repository-context.md](repository-context.md) for inspected source, verified ProjectCV reference, existing capabilities, missing behavior and compatibility limitations.
