# Engineering Contracts

Paths below are under `demo/src/main/java/com/example/demo/` unless specified otherwise. New names are proposed components, not existing implementation claims.

## Catalog authority and projections

- Extend `catalog/entity/Product`, `ProductVariant`; own SQL functions and integrity under new `demo/src/main/resources/db/migration/` files. Add `catalog/dto/response/VariantPricing`, `CheckoutCatalogView` and option dimension/value DTOs. ProductCatalogConverter maps prepared data only.
- `catalog/repository/ProductVariantRepository` exposes the `CatalogPricingRepository` fragment and `CatalogPricingRow`/`CatalogDetailVariantRow` projections; checkout/cart state DTOs carry prepared prices. The old base-only `CartVariantView` compatibility projection is not used by pricing operations. No consumer calls another domain's repository or reimplements direct-sale math.
- Add a catalog service/validator component for dimension aggregation and canonical-code-point ordering (service interface plus Impl if business service). Canonical normalized identity is obtained from database projections. Avoid reuse of accent-stripping ProductSearchNormalizer.
- Change CatalogCheckoutService purchase result to `{pricingInstant, states}`. Lock parents then variants in existing UUID ordering/batches; load protected values, capture Clock instant once, price all states with that instant. Values must be refreshed by projections after locks, not stale persistence-context entities. Missing variants preserve existing reason precedence.
- `CatalogSearchSql` computes a priced variant relation before eligible_base. Bind explicit instant; use same effective expression for bounds, display, extrema and rank. Extend ProductSearchCriteria/CandidatePage to carry pricing and range snapshots from the same candidate/count statement. Batch metadata signing must not reprice from later configuration. Existing publication signing revalidation remains.
- Catalog detail uses one coherent pricing/dimension projection for the response (transaction snapshot or one projection statement), retaining existing publication locks/signing and batched inventory. Do not globally raise isolation or add EAGER associations. Bounded per-use-case projection queries are acceptable; no per-variant SQL loop.

## Cart and voucher time

- Add immutable `catalog/dto/response/PreparedOperationPricing` and thread it through CatalogCartReadService, cart seed/preparation/presentation and voucher preview/wallet flows, including CartPreviewServiceImpl and CartVoucherTransactionServiceImpl. One set-based bound-ID-array query obtains coherent base/sale inputs and evaluates prices once at the operation Instant under existing READ COMMITTED. Reuse that object for eligibility, subtotal, discount and final response; remove redundant catalog pricing reads, including the second read in applyVoucher. Preserve publication/availability revalidation without repricing. No cross-request cache or new pricing-only locks/isolation.
- Cart mutation validation uses current effective prices/availability and retains source-target lock coordination/quantity/selection behavior. Existing exact line multiplication consumes effective price; it does not calculate sale percentages.
- Change VoucherService.lockCheckoutVoucher to accept `Instant pricingInstant`. Keep voucher/grant/quota lock order and checks, but remove its independent clock capture for checkout. Other public wallet/list flows receive their owning response event. No new checkout call from Voucher or Catalog.

## Checkout, Order and durable receipts

- CheckoutServiceImpl receives protected Catalog view, validates stock, totals effective lines and passes shared instant to voucher, order creation, inventory reservations and receipt persistence. Preserve initial committed-receipt lookup/fence/rechecks, cart coordination and rollback.
- Explicitly extend native `order/repository/OrderCreationRepositoryImpl.insertHistoricalItem` INSERT columns/parameter bindings, audit all other affected persistence writers, and extend `order/dto/request/CreateOrderCommand`, creation service and item snapshots; order owns invariant checks and repository writes. Checkout passes authoritative prepared pricing evidence rather than mutable catalog entities.
- Extend `checkout/converter/CheckoutConverter`, receipt DTOs, `CheckoutReceiptRepository.insertSuccess` and precommit decode for v3. Keep explicit v1/v2 paths with original serialization shapes; do not auto-detect v3 merely by voucherDiscount presence. Use stored receipt_version as authoritative on replay.
- Add DB v3 exact-shape/amount/snapshot equality validation alongside old validators. Version-dispatch `check_checkout_receipt_integrity` and constraints; preserve original v1/v2 receipts/order comparisons and immutable update protections. Unit/codecs tests alone cannot prove these database boundaries.

## Review queries and historical projection

- Extend `ReviewListRequest.parse`; retain allowlist, single-value validation, existing numeric parsing and ID DESC ties. Nullable booleans distinguish omitted from explicit false.
- Add a custom review read repository/predicate component for scoped product/rating/image/comment matching, used identically by count and list. Bind values and allowlist ordering; no dynamic caller field paths. Use correlated EXISTS/NOT EXISTS for images. Use one fixed whitespace expression for both comment true and false.
- Preserve ReviewReadServiceImpl REPEATABLE_READ transaction and bounded page enrichment. Add OrderReviewReadService `getReviewOptionSnapshots(Collection<Long> itemIds)` returning item ID to immutable label map, maximum current page size 100. This internal projection has no actor argument because public reviews are already scoped; it does not expose arbitrary order details over HTTP. Service owns OrderItemRepository access and returns only snapshots.
- ReviewConverter receives already-prepared purchasedOptions/images/authors. Statistics service groups product-wide ratings and zero-fills 1–5; retain average null/two-decimal HALF_UP. No new review entity or write semantics.

## Normalization capability and integrity

- Migration preflight verifies built-in ICU provider/root collation and NFC support on PostgreSQL17, with explicit failure rather than fallback. Fixed whitespace and root lower are centralized in versioned SQL functions. Record provider/version; do not depend on deployment default collation.
- Unicode fixtures include NFC/NFD Vietnamese accents, Turkish I variants, sigma, NBSP, supplementary code points and duplicate canonical keys. Java presentation ordering compares code points, not UTF-16 code units. Database-trigger validation is the final integrity boundary for SQL writers as well as JPA.
- Deferred trigger must lock affected parent rows before a fresh child snapshot, including inserts, edits, parent moves and deletes. Check uniform normalized dimension sets and duplicate normalized combinations at commit. Canonical JSON object construction must reject collisions first.

## Required implementation evidence

Extend existing catalog detail/search/migration tests, ReviewReadServiceIT/ReviewStatisticsServiceImplTest/ProductReviewControllerIT, OrderReviewReadServiceIT, CheckoutConverterTest/CheckoutMigrationIT/CheckoutConcurrencyIT and corresponding cart/voucher flows where responsibilities fit. Add dedicated function/upgrade tests only for new behavior. Verify class filenames before generating task selectors; do not create aliases of existing tests.

Required synchronized independent-transaction overlap scenarios: sale/base edit versus checkout; sale expiry while later inventory/voucher locks wait; simultaneous variant writers attempting canonical duplicates/dimension mismatch; same/different checkout keys and each affected cart mutation category. Assert coherent all-item prices, one instant, no duplicated effects and rollback. Sequential retries or arbitrary sleeps do not establish overlap safety.

Upgrade evidence includes original v1/v2 body replay, v3 exact shape, additive migration from prefeature fixture, unknown legacy history, no-sale/new active snapshots, direct SQL bypass attempts and failed collision preflight without data mutation. Preserve image access/security, product visibility and owned history regressions. Feature plan and quickstart define targeted/checkpoint/full verification and conditional reruns.

## Foundation and persisted evidence gates

Install V28 -> V29 -> V30 in Foundation, with no Flyway out-of-order. V30 nullable expansion retains the current all-null v1/v2 write shape; enforce complete sale evidence on every v3 item and reject partial evidence. Foundation tests execute current no-sale catalog/cart/voucher/checkout/order behavior after each migration stage and all together, including native v2 writes/replays. Base fixture/mapping changes belong to Foundation; old base writers are not permitted alongside the new model. Audit failures require an explicit staging/dependency report, not silent cleanup. Complete US2 before enabling active sales.

Add integration tests for native persisted sale fields: commit checkout/order creation, clear the persistence context or read in a separate transaction, reload actual PostgreSQL rows and compare original/effective/discount/active percentage/validity/pricingInstant against immutable receipt. Include no-sale and active-zero-base cases and direct invalid/partial evidence rejection. Do not use only CreatedOrderSeed or in-memory entity assertions.

Add operation-pricing regression tests with barriers at the former duplicate-read points: edit base in one case, sale percentage/enabled/bounds in separate cases. In-flight operation values remain coherent across validation/discount/subtotal/response, no second pricing query occurs, next request observes the edit and checkout independently revalidates current pricing/vouchers. Preserve existing inventory/quota/fence/cart mutation overlap tests.

Image-candidate/gallery probes must use metadata-only reads rather than re-evaluating prices. Existing publication/availability revalidation retains its guarantees; if it removes rows, recompute eligible totals/voucher calculation from the retained prepared prices without a new catalog pricing read. Foundation isolated function/constraint tests may exercise active sale inputs without enabling incomplete application sale behavior.
