# Research Decisions

**Date**: 2026-11-26. Source-based research; no application/DB execution. Confirmed business decisions remain those in spec.md. No unresolved design clarification remains.

## R1 — One effective-price authority

**Decision**: Add a canonical immutable SQL function `catalog_effective_price_v1(base, percentage, enabled, starts_at, ends_at, pricing_at)`, with explicit arguments and no clock reads. Return null for missing/nonfinite/fractional/negative base; otherwise base or base−floor(base×percentage/100) according to validity. Schema rejects invalid sale configuration. Derive original/effective/unit discount/active percentage in catalog projections from that function and configuration. Catalog services supply prepared DTOs to consumers; no Java duplicate formula.

**Rationale**: `catalog/repository/CatalogSearchSql.java` applies price bounds and chooses representative prices inside its native single-statement candidate/count query. A Java-only calculator cannot price before database pagination. One SQL authority covers search, detail, cart and checkout without drifting rounding.

**Alternatives considered**: Independent Java/SQL formulas duplicate authority; persisted effective prices require transition jobs and contradict Q2=A; post-pagination recalculation breaks counts and ordering.

## R2 — Additive base transition and product sale fields

**Decision**: Add `base_price` to variants and nullable percentage plus enabled/start/end to products. Backfill each base from its current selling_price with no configured sale. Retain selling_price as a deprecated, DB-maintained mirror of base; application projections expose derived prices under the old response fields. Prevent direct independent edits of the mirror with a trigger. New inserts/updates supply base; a coordinated application cutover is required, with no mixed old/new pricing writers.

**Rationale**: Existing prices may have been manually reduced; they cannot recover original history. Additive storage avoids altering applied migrations or destructive column removal, while one editable base avoids contradictions. There is no management endpoint in scope.

**Alternatives considered**: Renaming/dropping selling_price is not additive; reinterpreting old prices as proven originals invents history; leaving two editable prices violates source-of-truth rules. Invalid negative legacy prices are reported by preflight and block rollout pending remediation; do not silently coerce.

## R3 — Stable explicit time and lock view

**Decision**: Put sale fields on Product to share existing parent locks. `CatalogCheckoutServiceImpl` locks sorted parents then variants and returns `CheckoutCatalogView(pricingInstant, states)` after loading all protected values and capturing a microsecond-truncated Clock instant. `VoucherService.lockCheckoutVoucher` receives this Instant rather than capturing its own. Reuse it for order, reservation, redemption and receipt event. Capture one display/cart response instant and pass it through catalog, preview and wallet voucher calculations.

**Rationale**: Current CheckoutServiceImpl computes merchandise before voucher service captures time. Existing catalog locks already protect prices/status, and product sale fields require no new aggregate lock order. Later inventory/voucher waits do not move the approved purchase instant. Quantity/quota eligibility still uses the locked current counters.

**Alternatives considered**: Per-line now() breaks boundary consistency; transaction-start time predates required catalog view; new promotion locks add complexity. Do not add automatic retries or change global isolation.

## R4 — Search candidate snapshot and paired prices

**Decision**: Add explicit instant to ProductSearchCriteria and SQL-bound parameters. Calculate priced variants before price/specification eligibility, ranking, count and range extraction; candidate rows return paired prices and sale evidence so later enrichment never reprices. Carry fields through ProductCandidatePage. Preserve same-variant matches, relevance, UUID ties, orphan compatibility and approved performance/query-count gates.

**Rationale**: Current SQL uses selling_price in both bounds and display_amount. Updating only DTO conversion gives wrong membership/counts. Avoid loading all variants after paging merely to calculate ranges.

**Alternatives considered**: Requerying current prices during enrichment can cross a schedule/config change; independent product min/original min creates misleading pairs.

## R5 — Canonical option identity at the database boundary

**Decision**: Use one database canonicalizer for option identity, called both by integrity checks and read projections. It performs NFC, trims a fixed Unicode White_Space set at edges and applies root Unicode lowercase via an explicitly named deterministic ICU `und` collation. Preserve internal whitespace/accents and labels. Verify PostgreSQL ICU support as a migration prerequisite; no extension/library is added. SQL returns normalized maps to Catalog; Java compares code points for deterministic ordering and does not implement another normalization algorithm. Record collation provider/version in upgrade evidence; upgrades require collision audit/revalidation.

**Rationale**: Search normalization strips accents and cannot be reused. Writable canonical columns alone permit bypass. Root collation avoids host default locale; explicit Unicode whitespace avoids Java/PostgreSQL regex differences. Existing V3 deferred checks coordinate on Product and can be extended for normalized duplicates and dimension equality.

**Alternatives considered**: Locale-default lower/ASCII-only trim violate accepted Unicode semantics; Java-only checks miss SQL writers; case-insensitive comparison without NFC is incomplete. Missing ICU on a deployment is an explicit preflight failure, never a silent fallback.

## R6 — Review query and safe history

**Decision**: Extend strict ReviewListRequest with nullable exact rating/booleans. One custom query predicate builder drives count and page; use EXISTS/NOT EXISTS for image references. Comment presence uses the same fixed White_Space set. Keep REPEATABLE_READ and separate batched ordered images/authors. Batch immutable option snapshots through a new OrderReviewReadService method using scoped page review item IDs; no owned-order lock/read API is reused for anonymous enrichment.

**Rationale**: Existing ReviewReadServiceImpl already batches enrichment. OrderReviewReadService.getReviewPurchase enforces owned completed purchases and locks; it is unsuitable for public list display. Review snapshots already exist in OrderItem, so duplication is unnecessary.

**Alternatives considered**: Image joins multiply rows/counts; live variant options rewrite history; per-review lookups introduce N+1. Product rating summary stays independent, average null at count zero and HALF_UP two decimals otherwise; histogram zero-fills 1–5.

## R7 — Receipt version 3 and legacy invariants

**Decision**: Add original/direct-sale item evidence to new orders and receipt v3. Preserve v1/v2 codecs and validators and never rewrite their stored bodies. Add version-dispatched DB receipt validation/deferred comparison in a new migration; update repository hardcoded version and converter precommit decode to 3. Old order evidence is null/unknown, not fabricated. New no-sale evidence is known: original=effective, discount=0, percentage=null.

**Rationale**: CheckoutConverter supports only v1/v2; CheckoutReceiptRepository inserts v2. V26 exact-shape validators and deferred receipt/order equality reject naive additive receipt fields.

**Alternatives considered**: Mutating old receipt JSON breaks immutable replay; blindly adding nullable fields to old DTO serialization changes historical response shape; dropping validators weakens integrity.

## R8 — Verification/tooling evidence

**Decision**: No dependency/build upgrades. Use existing Maven wrapper, Testcontainers and risk-based checks. Latest current migration is V27; reserve V28+ at implementation only after checking intervening migrations. Bash setup unavailable here, supplied PowerShell setup succeeded. No application tests run for planning.

**Rationale**: Planning authorizes design artifacts, not production changes or infrastructure provisioning. POM verifies Java25/Boot4.1.2/Failsafe but no combined static-analysis gate.

**Alternatives considered**: Installing tools or modifying infrastructure during design is unnecessary scope; treating source inspection as passing runtime evidence is invalid.

## R9 — Approved G1/I1/U1 resolutions

**Decision**: All V28–V30 installation and schema integrity tests are Foundation prerequisites. v3 strict evidence is version-gated while v1/v2 intermediate writes retain all-null sale evidence. Adapt base mappings/fixture writers in Foundation and forbid active sales until the complete US2 purchase path. Explicitly change OrderCreationRepositoryImpl.insertHistoricalItem native columns/bindings and audit other affected writers. Verify committed snapshots through a fresh PostgreSQL reload.

**Rationale**: Entity mapping alone does not affect native INSERTs; requiring sale evidence unconditionally at schema expansion would break the current v2 writer. Ordered shared installation removes Flyway branch ordering problems while retaining independent feature-story tests.

**Alternatives considered**: Out-of-order Flyway is prohibited; global evidence enforcement before v3 cutover breaks intermediate behavior; relaxing v3 evidence violates history requirements. If preflight exposes incompatible real data or a writer not adapted in Foundation, stop and report staged dependency changes rather than modifying data automatically.

**Decision**: Use an immutable PreparedOperationPricing value with one set-based input/pricing statement and one explicit instant per operation; reuse it across cart/voucher validation, totals and presentation. Existing publication/stock revalidation can exclude/redact without repricing. Checkout obtains its own fresh protected view, not a previous request's value.

**Rationale**: Passing one instant to two separate READ COMMITTED queries does not freeze sale/base configuration. Reusing the prepared value removes that race without higher isolation or pricing-only locks.

**Alternatives considered**: Repeated reads at the same timestamp still see different inputs; caching across requests violates current-price checkout; a broad isolation/lock increase is unnecessary for this response-local consistency.
