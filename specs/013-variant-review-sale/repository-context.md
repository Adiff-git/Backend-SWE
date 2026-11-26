# Repository Evidence and Compatibility Assessment

Inspected 2026-11-26 on `draft/version-1`. Source inspection only; no application tests or live dataset audit.

## Governance and template

Read constitution v1.2.0, installed specify instructions and the resolved spec-template using `.specify/scripts/powershell/resolve-template.ps1`. No extension hooks or template override files were present; no repository AGENTS.md was found. Sequential numbering yields 013; no hook requires a branch switch. Reviewed cart, checkout, inventory, voucher and review specifications, catalog source and migrations. Earlier catalog/enhancement/search specs remain compatibility dependencies.

## Backend-SWE findings

Paths below begin at `demo/src/main/java/com/example/demo/` unless specified otherwise.

| Evidence | Existing | Missing/change required |
| --- | --- | --- |
| `catalog/entity/Product.java`, `ProductVariant.java` | Product parent; UUID variants own SKU, optionCombination map and sellingPrice. | Sale configuration, authoritative base initialization, explicit deterministic option metadata. |
| `demo/src/main/resources/db/migration/V1__create_catalog_core.sql`, `V3__enforce_catalog_relationships.sql` | Exact map uniqueness; finite integral prices; deferred constraints require variants, nonblank string options and no default alongside multiple variants. | Normalized-equivalence audit/protection and consistent dimension sets; preserve applied migrations/identities. |
| `catalog/dto/response/ProductDetail.java`, `VariantSummary.java`, `catalog/converter/ProductCatalogConverter.java` | Variant options, sellingPrice in VND and inventory availability; listing representative price/variant. | Original/effective pairs, dimension ordering, effective ranges and matching price search semantics. |
| `specs/007-shopping-cart/spec.md`, `cart/validator/CartValidator.java` | Merge by variant; absent-target reset to 1; existing target unchanged; quantities 1–999. | Preserve rules and use effective prices in reads/mutations. |
| `catalog/service/CatalogCheckoutServiceImpl.java`, `checkout/service/impl/CheckoutServiceImpl.java` | Locks catalog parent status and variants; validates rows, locks stock, calculates totals, quotes voucher, creates order/reservations and receipt atomically. Replay occurs before new purchase work. | Single sale/voucher pricing instant: current voucher event is captured after item calculation, so this is not already satisfied. |
| `checkout/calculator/CheckoutCalculator.java`, `voucher/calculator/VoucherCalculator.java` | Exact whole-VND unit×quantity; voucher percentage supports hundredths and floors the discount, applying caps/order limits. | Explicitly approve matching direct-sale arithmetic rather than assume decimal money. |
| `order/entity/OrderItem.java` | Immutable name/SKU/options, quantity, paid unit price and line total; optional catalog references. | Add future original/direct-sale evidence; no fabricated backfill for history. |
| `review/dto/request/ReviewListRequest.java` | Strict page/size/sort; all four requested sorts already supported with ID-descending tie-breaker. | Add approved filters, preserve validation/defaults. |
| `review/service/impl/ReviewReadServiceImpl.java`, `review/repository/ProductReviewRepository.java` | Unfiltered product count/page; separate ordered-image fetch; safe author/image enrichment. | Identical filtered page/count predicates and no image-driven multiplication. |
| `review/dto/response/ReviewResponse.java`, `ProductRatingSummary.java` | Product average/count; no purchased options or distribution fields. | Confirm additive historic options and product-wide distribution. |

## Verified ProjectCV reference

Root: `C:/Users/TrinhThanh/Downloads/ProjectCV/Backend/demo/src/main/java/com/example/demo/`.

- `engagement/dto/request/ReviewFilter.java`: rating set 1–5, variantId, hasImages, hasComment and date bounds.
- `engagement/repository/query/ReviewSpecifications.java`: conjunctive predicates, rating membership, image presence/absence, whitespace-aware comments and dates.
- `engagement/controller/ProductReviewController.java`: separately paginated list/statistics; public id/createdAt/rating sort allowlist. Backend-SWE keeps its narrower four-sort contract.
- `engagement/repository/query/ReviewQueryRepository.java`: variant-parent validation and product-wide histogram/statistics. Do not copy numeric catalog identities or live variant traversal for historical labels.
- `catalog/service/impl/ProductVariantServiceImpl.java`, `catalog/util/ProductVariantUtils.java`: concrete lookup, quantity validation and stock-aware purchasability. Shop status rules do not apply here. No frontend option-selection algorithm was inspected or claimed.

## Compatibility and verification limits

Also inspected catalog001, enhancement005 and search007 specifications. Catalog confirms whole-VND currency, global case-insensitive SKU uniqueness and nonempty maps for multi-variant products. Search requires a single variant satisfying combined price/specification predicates, lowest qualifying price with UUID ties and existing scenario-specific performance budgets; effective-price integration must retain these semantics. This supplements the earlier source review without claiming those checks were executed.

No live data was inspected. Actual price provenance, collision counts and dimension consistency require rollout auditing. Existing sellingPrice is a current price, not proof of historic original value. Preserve existing paid snapshots/receipt versions and inventory references. Do not silently merge collisions.

Implementation verification must cover filtered page/count/image multiplicity; strict query validation; direct-sale rounding/time boundaries; coherent checkout under concurrent sale edits; cart/inventory/voucher/order integration; failed-checkout rollback; original/new receipt replay and additive migration fixtures. Apply constitution risk-based checks and required implementation full-verification gate. Documentation-only checks do not establish application correctness.

## Analysis resolution source anchors (2026-11-26)

`order/repository/OrderCreationRepositoryImpl.java:28` performs the native item INSERT; entity-only changes cannot persist added columns. `cart/service/impl/CartVoucherTransactionServiceImpl.java:37–46` fetches catalog presentation before validation and again before final response; one timestamp alone does not freeze base/configuration across those READ_COMMITTED statements. `V26__voucher_support.sql` accepts exact receipt versions 1/2 and preserves deferred order equality. Foundation V30 therefore expands nullable storage/version acceptance while preserving existing v1/v2 writes; v3 adds strict evidence. No source or migration implementation was changed during documentation remediation.
