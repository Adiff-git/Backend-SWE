# Tasks: Variant Selection, Review Queries and Direct Sales

**Input**: [spec.md](spec.md), [plan.md](plan.md), [research.md](research.md), [data-model.md](data-model.md), [contracts](contracts/api.md), [engineering](contracts/engineering.md), [quickstart.md](quickstart.md).
**Branch**: `draft/version-1` (unchanged). **Created**: 2026-11-26. **Decisions**: Q1=A/Q2=A/Q3=A.

**Tests**: Required by feature acceptance/plan and constitution v1.2.0. Write meaningful tests before each implementation increment and confirm behavior-specific red failures, not merely syntax/setup errors. Preserve domain/layer Test/IT organization, existing infrastructure and real transaction boundaries. No production implementation is performed by task generation.

## Format and Path Conventions

`- [ ] Tnnn [P?] [USn?] Action with repository-relative file paths`. `[P]` identifies independent test-authoring tasks within a phase after its entry prerequisites; it does not bypass dependencies or authorize agents. Production paths are under `demo/src/main/java/com/example/demo/`, tests under matching `demo/src/test/java/com/example/demo/`; proposed new files are explicit. All migrations are Foundation prerequisites. Migrations V28–V30 are tentative until T001 checks the live tree; never alter applied migrations. Retain user untracked files. No automatic commits/pushes.

## Phase 1: Setup (Shared Infrastructure)

Existing project only; no new dependencies, commits or infrastructure provisioning.

- [X] T001 Record Java25, wrapper/Docker/PostgreSQL17 availability, actual branch, existing Test/IT discovery configuration and current migration maximum in `specs/013-variant-review-sale/implementation-validation.md`; use `demo/pom.xml` and `demo/src/test/resources/application-test.properties`, with exact commands and unexecuted gates. Do not infer runtime readiness from configuration.

- [X] T002 Create read-only rollout preflight SQL in `specs/013-variant-review-sale/validation/preflight.sql` for price provenance/invalid prices, canonical key/combination collisions, dimension sets and ICU/NFC capability; report IDs/reasons/provider version without mutating data. Use disposable fixtures initially; populated-environment audit remains a rollout gate, not permission to connect/deploy.

- [X] T003 Prepare explicit no-sale/active/disabled/future/expired and sparse/default option fixture cases in `demo/src/test/java/com/example/demo/fixture/catalog/CatalogSaleFixtures.java` and record baseline prefeature order/receipt expectations in `specs/013-variant-review-sale/implementation-validation.md`; use fresh graphs and existing fixture conventions, no production loader/API.

## Phase 2: Foundation (All Ordered Schema and Compatibility)

All schema installation is shared: V28 -> V29 -> V30. Write meaningful boundary/upgrade tests before implementing their migrations; all Foundation gates pass before independent story work. No active-sale rollout or Flyway out-of-order.

- [X] T004 Add SQL price-function/ICU capability tests in `demo/src/test/java/com/example/demo/catalog/repository/CatalogPricingRepositoryIT.java`: 101 at12.50%=89, zero/100%, start-inclusive/end-exclusive, disabled/future/expired, invalid base and invalid percentage precision. Exercise actual PostgreSQL, explicit timestamps and capability failure, not a mocked Java formula.

- [X] T005 Add additive upgrade/backfill/mirror tests in `demo/src/test/java/com/example/demo/catalog/CatalogSaleMigrationIT.java`: prefeature prices initialize future base only, old order amounts/options/receipt bodies and inventory references unchanged; invalid legacy values block without mutation and independent deprecated-price edits fail.

- [X] T006 Add staged Foundation compatibility coverage in `demo/src/test/java/com/example/demo/checkout/repository/CheckoutFoundationCompatibilityIT.java`: migrate prefeature fixtures through V28, V29 and V30 individually and together; run existing native v2 order INSERT/receipt creation/replay, no-sale cart/voucher/read behavior and base-mapping fixtures. Assert null legacy evidence accepted, partial evidence rejected and v3 incomplete evidence rejected, with no out-of-order setting.

- [X] T007 Add new migration `demo/src/main/resources/db/migration/V28__catalog_sale_pricing.sql` (renumber only if T001 finds a newer version): product fields `sale_percentage NUMERIC` nullable, `sale_enabled BOOLEAN NOT NULL DEFAULT FALSE`, UTC bounds; enforce "Percentage absent means no configured sale: enabled=false and bounds=null", "Present percentage is finite, >0, <=100, with no more than two fractional decimal places; reject, never round invalid configuration" and "Present start must be earlier than present end". Keep one product configuration; no scheduler/management API.

- [X] T008 Complete base backfill, constraints and deprecated mirror protection in `demo/src/main/resources/db/migration/V28__catalog_sale_pricing.sql`: "base_price NUMERIC", "make NOT NULL after successful preflight", "Require finite, integral and nonnegative values"; derive selling_price mirror from base and reject independent edits, preserving columns/applied migrations and explicit cutover limits.

- [X] T009 Implement canonical `catalog_effective_price_v1` with no clock reads in `demo/src/main/resources/db/migration/V28__catalog_sale_pricing.sql`: inactive returns base, active returns base−floor(base×percentage/100), invalid base returns null and null pricing instant errors; use raw NUMERIC validation rather than typmod rounding. Install versioned NFC/explicit White_Space/root-lowercase SQL helper and deterministic ICU und collation for later option/review use; fail missing capability without locale fallback.

- [X] T010 Map product sale/base storage in `demo/src/main/java/com/example/demo/catalog/entity/Product.java` and `demo/src/main/java/com/example/demo/catalog/entity/ProductVariant.java`, add `demo/src/main/java/com/example/demo/catalog/dto/response/VariantPricing.java` with original/effective/discount/nullable active percentage/pricedAt, and adapt existing catalog fixture writers to base. Preserve UUID/SKU/options/technical facts and prevent Java direct-sale recalculation.

- [X] T011 Add canonical-map/deferred integrity tests in `demo/src/test/java/com/example/demo/catalog/repository/VariantOptionIntegrityIT.java`: NFC/NFD accents, Turkish I, sigma, NBSP, supplementary code points, duplicate canonical keys/maps and inconsistent sibling dimensions. Assert commit failures and two synchronized independent writers including parent moves/deletes; no sleep-only overlap proof.

- [X] T012 Add option integrity migration `demo/src/main/resources/db/migration/V29__variant_option_identity.sql` using the canonical helper: "At least one variant per product", "Blank/null/non-string option names/values are invalid", "One empty-map default can exist only as the sole variant", "Canonical maps are unique within one product" and "Every optioned sibling has the same canonical key set". Reject colliding keys before JSON construction; lock affected parents in UUID order before fresh deferred checks; audit, never auto-merge.

- [X] T013 Add receipt/order migration `demo/src/main/resources/db/migration/V30__direct_sale_order_snapshots.sql`: nullable legacy NUMERIC original/discount/percentage snapshots and TIMESTAMPTZ sale bounds/pricing instant; "Legacy items leave all new fields null", "Items created by the completed v3 flow require original/discount/instant" and "original−discount=paid, 0<=discount<=original, integral money and consistent active-sale evidence". No-sale original=paid/discount0/percentage+bounds null; active zero-price retains percentage. New snapshot time equals placedAt; enforce immutability. Keep nullable expansion compatible with existing all-null v1/v2 writes during intermediate no-sale stages; reject partially populated evidence, never require v3 fields merely because the order was created after migration.

- [X] T014 Complete v3 validators/version constraints/deferred receipt-order equality in `demo/src/main/resources/db/migration/V30__direct_sale_order_snapshots.sql`; "Accept versions 1/2/3", "Keep old validators and stored bodies unchanged" and preserve customer/key/fingerprint/fence retention. Require evidence on every v3 item and exact version-specific shape; do not weaken v1/v2 comparisons. Run the existing native v2 writer/replay path after schema installation; do not alter existing stored body shape or enable Flyway out-of-order.

- [X] T015 Review and document intermediate schema compatibility in `specs/013-variant-review-sale/implementation-validation.md` against `demo/src/main/resources/db/migration/` and existing writers. Confirm V30 version-aware nullable expansion instead of unconditional new-item requirements; V28 mapping/fixture adaptation and V29 normalized-data preflight precede story work. If a real incompatibility remains, report it and propose staged dependencies before implementation continues; no silent cleanup or Flyway workaround.

- [X] T016 Run foundation function/migration/identity/intermediate-compatibility tests and existing `demo/src/test/java/com/example/demo/catalog/CatalogMigrationsIT.java` in ascending V28/V29/V30 stage order and on the full schema. Record commands/counts in `specs/013-variant-review-sale/implementation-validation.md`; all schema plus base/mirror fixture adaptations, old no-sale cart/checkout/voucher/order behavior and v1/v2 writes/replays must pass before any story. Report incompatible data/writers/capability before proceeding; do not enable active sales or out-of-order.

## Phase 3: User Story 1 — Select one concrete combination (Priority: P1, MVP)

Independent test: default and sparse option metadata resolve an existing UUID, classify absent/unavailable combinations, preserve cart rules and historical labels. Schema integrity is already installed in Foundation.

- [X] T017 [P] [US1] Extend detail contract tests in `demo/src/test/java/com/example/demo/catalog/controller/ProductDetailControllerTest.java` and `demo/src/test/java/com/example/demo/catalog/controller/ProductDetailApiIT.java` for optionDimensions/selectionOptions, label precedence, default empty metadata, sparse combinations and unavailable stock without a new resolution endpoint.

- [X] T018 [US1] Expose canonical map read projections through `demo/src/main/java/com/example/demo/catalog/repository/ProductVariantRepository.java`; add `demo/src/main/java/com/example/demo/catalog/dto/response/OptionDimension.java` and `demo/src/main/java/com/example/demo/catalog/dto/response/OptionValue.java` with canonical key/display label. Keep original optionCombination unchanged and use the database normalizer, not ProductSearchNormalizer.

- [X] T019 [US1] Implement deterministic dimension aggregation in `demo/src/main/java/com/example/demo/catalog/service/ProductOptionService.java` and `demo/src/main/java/com/example/demo/catalog/service/ProductOptionServiceImpl.java`: canonical code-point ordering, lowest variant UUID label source, uniform dimensions and empty-default rules. Add focused ordering/label tests in `demo/src/test/java/com/example/demo/catalog/service/ProductOptionServiceImplTest.java`; no UTF-16-only comparator or repository calls from converter.

- [X] T020 [US1] Integrate optionDimensions and variant selectionOptions into `demo/src/main/java/com/example/demo/catalog/dto/response/ProductDetail.java`, `demo/src/main/java/com/example/demo/catalog/dto/response/VariantSummary.java`, `demo/src/main/java/com/example/demo/catalog/service/ProductCatalogServiceImpl.java` and `demo/src/main/java/com/example/demo/catalog/converter/ProductCatalogConverter.java`; batch prepared values and retain publication/image/inventory behavior.

- [X] T021 [US1] Extend `demo/src/test/java/com/example/demo/cart/service/CartServiceIT.java` and `demo/src/test/java/com/example/demo/cart/service/CartCheckoutServiceIT.java` for exact UUID resolution, same-variant2+3 merge, quantity1/999/1000, absent-target reset to1 with selection retained, present-target source removal with target untouched, cross-product rejection and full-quantity stock rules; preserve historical labels after later option changes.

- [X] T022 [US1] Run the US1 detail/integrity/cart tests, including existing `demo/src/test/java/com/example/demo/catalog/repository/ProductDetailRepositoryIT.java`; record independent MVP evidence and normalization/collision-upgrade checks in `specs/013-variant-review-sale/implementation-validation.md`. Do not label US2 sales or full-feature completion verified.

## Phase 4: User Story 2 — See and purchase current sale prices (Priority: P1)

Independent test: default variants suffice; coherent prepared cart/voucher pricing, fresh checkout, persisted v3 snapshots and immutable replay. All schema exists after Foundation; active sales are enabled only after this entire story passes.

- [X] T023 [P] [US2] Extend price contracts in `demo/src/test/java/com/example/demo/catalog/controller/ProductListingControllerTest.java`, `demo/src/test/java/com/example/demo/catalog/controller/ProductDetailControllerTest.java` and `demo/src/test/java/com/example/demo/catalog/repository/ProductSearchRepositoryIT.java`: effective price bounds/sorts before paging, same-variant spec matching, paired extrema, deterministic ties and orphan null compatibility.

- [X] T024 [P] [US2] Extend snapshot/codec tests in `demo/src/test/java/com/example/demo/checkout/converter/CheckoutConverterTest.java` and `demo/src/test/java/com/example/demo/checkout/repository/CheckoutMigrationIT.java` for exact v3 shape, original/discount/time consistency, old v1/v2 shape replay and forbidden mutation; ensure old nullable columns cannot bypass new-order evidence.

- [X] T025 [P] [US2] Extend `demo/src/test/java/com/example/demo/cart/service/CartVoucherServiceIT.java` and `demo/src/test/java/com/example/demo/voucher/service/VoucherServiceIT.java` for after-sale minimum spend, percent/cap, zero-subtotal and invalid selected voucher policies and a common event across cart/wallet previews.

- [X] T026 [US2] Add prepared-pricing regression tests in `demo/src/test/java/com/example/demo/cart/service/CartPreparedPricingIT.java`: synchronize edits to base prices and separately sale percentage/enabled/start/end at the former duplicate catalog-read points, then assert eligibility, subtotal, voucher amount and response share the original prepared values and pricing query executes once. Assert later request and checkout obtain fresh inputs/current voucher validity; retain READ_COMMITTED and existing inventory/quota/checkout overlap guarantees.

- [X] T027 [US2] Add pricing/time projections in `demo/src/main/java/com/example/demo/catalog/repository/ProductVariantRepository.java`, `demo/src/main/java/com/example/demo/catalog/repository/CartVariantView.java`, `demo/src/main/java/com/example/demo/catalog/dto/response/CartVariantState.java` and `demo/src/main/java/com/example/demo/catalog/dto/response/CheckoutVariantState.java`; call the SQL authority and carry prepared money/config evidence without recomputing sales.

- [X] T028 [US2] Add pricingInstant and paired candidate/range payloads in `demo/src/main/java/com/example/demo/catalog/search/ProductSearchCriteria.java` and `demo/src/main/java/com/example/demo/catalog/search/ProductCandidatePage.java`; integrate priced variants into `demo/src/main/java/com/example/demo/catalog/repository/CatalogSearchSql.java` and `demo/src/main/java/com/example/demo/catalog/repository/ProductSearchRepositoryImpl.java` before bounds/eligibility/count/ranking. Preserve relevance, same-variant matching, UUID ties, safe nulls and bound caller values.

- [X] T029 [US2] Integrate snapshot-consistent listing/detail price metadata and ranges in `demo/src/main/java/com/example/demo/catalog/service/ProductCatalogServiceImpl.java`, `demo/src/main/java/com/example/demo/catalog/dto/response/ProductListingItem.java`, `demo/src/main/java/com/example/demo/catalog/dto/response/ProductDetail.java` and `demo/src/main/java/com/example/demo/catalog/converter/ProductCatalogConverter.java`; keep existing sellingPrice/displayPrice effective, range originals paired to endpoint UUIDs and unavailable-price fields safe. Later image enrichment must not reprice candidates. Recommendation cards must use the Catalog service's bounded prepared range projection rather than the deprecated base-price mirror; cover effective/original endpoint values in RecommendationIT.

- [X] T030 [US2] Implement CP1 by running affected catalog detail/listing/search/migration tests and recording exact commands/results in `specs/013-variant-review-sale/implementation-validation.md`; preserve inherited SQL-count/performance assertions, while final workload measurements remain T054.

- [X] T031 [US2] Return `demo/src/main/java/com/example/demo/catalog/dto/response/CheckoutCatalogView.java` from `demo/src/main/java/com/example/demo/catalog/service/CatalogCheckoutService.java` and `demo/src/main/java/com/example/demo/catalog/service/CatalogCheckoutServiceImpl.java`: lock all sorted parent/variant batches, refresh protected view, capture microsecond Clock instant once and price states at it. Preserve missing-reference/publication/price precedence and no new lock aggregate.

- [X] T032 [US2] Implement `demo/src/main/java/com/example/demo/catalog/dto/response/PreparedOperationPricing.java` as a deep-immutable operation-local pricing map/Instant; update `demo/src/main/java/com/example/demo/catalog/service/CatalogCartReadService.java`, `demo/src/main/java/com/example/demo/catalog/service/CatalogCartReadServiceImpl.java`, `demo/src/main/java/com/example/demo/cart/service/impl/CartPreviewServiceImpl.java`, `demo/src/main/java/com/example/demo/cart/service/impl/CartVoucherTransactionServiceImpl.java` and `demo/src/main/java/com/example/demo/cart/service/impl/CartWalletReadServiceImpl.java` plus callers. Obtain base/sale inputs and evaluated prices in one bound-ID-array statement under existing READ_COMMITTED, reuse the prepared values across voucher validation/totals/response, and remove redundant pricing reads including the second applyVoucher catalog read. Retain publication/availability revalidation without repricing, no pricing-only isolation/lock increase and no cross-request reuse; checkout prepares independently.

- [X] T033 [US2] Update `demo/src/main/java/com/example/demo/cart/service/impl/CartTransactionServiceImpl.java`, `demo/src/main/java/com/example/demo/cart/calculator/CartCalculator.java`, `demo/src/main/java/com/example/demo/cart/converter/CartConverter.java` and `demo/src/main/java/com/example/demo/cart/dto/response/CartItemResponse.java` to use effective unit prices/prepared pricing metadata for reads and mutations, preserving hidden/unavailable redaction, quantity/selection/merge rules and exact line/subtotal arithmetic.

- [X] T034 [US2] Change `demo/src/main/java/com/example/demo/voucher/service/VoucherService.java` and `demo/src/main/java/com/example/demo/voucher/service/impl/VoucherServiceImpl.java` checkout quote to accept captured pricingInstant, retaining entitlement/quota lock order and current counters; calculate eligibility/discount on after-sale subtotal without item allocation. No new clock call may move checkout validity.

- [X] T035 [US2] Add committed-row integration tests in `demo/src/test/java/com/example/demo/order/service/OrderSaleSnapshotIT.java` using real native creation/checkout and PostgreSQL. Commit, clear persistence context or reload via an independent transaction, compare actual stored columns to v3 receipt for no-sale, active, zero-base/100%, bounded/unbounded and rounding cases; verify tampered/partial evidence fails and later catalog edits do not mutate snapshots. No in-memory-only assertions.

- [X] T036 [US2] Extend `demo/src/main/java/com/example/demo/order/entity/OrderItem.java`, `demo/src/main/java/com/example/demo/order/dto/request/CreateOrderCommand.java`, `demo/src/main/java/com/example/demo/order/service/impl/OrderCreationServiceImpl.java` and owning validation for immutable original/direct-sale/time evidence, exact totals and legacy-null handling; no cross-domain repository access or live-catalog historical reconstruction. Update `demo/src/main/java/com/example/demo/order/validator/OrderValidator.java` explicitly for v3 evidence consistency; intermediate v1/v2 all-null schema compatibility must not become a v3 application bypass.

- [X] T037 [US2] Explicitly update native order-item INSERT columns/parameter bindings in `demo/src/main/java/com/example/demo/order/repository/OrderCreationRepositoryImpl.java` and its interface as needed; audit and update every other affected SQL/JPA writer and projection. Persist all original/effective/unit-discount/percentage/bounds/pricingInstant snapshots so v3 deferred integrity passes. Do not assume OrderItem entity or CreateOrderCommand changes alter native SQL.

- [X] T038 [US2] Expose new evidence through `demo/src/main/java/com/example/demo/order/dto/response/OrderItemResponse.java` and `demo/src/main/java/com/example/demo/order/converter/OrderConverter.java`, preserving nullable unknown legacy originals and original paid prices/options after catalog/sale deletion or rename.

- [X] T039 [US2] Add v3 receipt item DTOs in `demo/src/main/java/com/example/demo/checkout/dto/response/CheckoutItemReceiptV3.java` and extend `demo/src/main/java/com/example/demo/checkout/converter/CheckoutConverter.java` plus `demo/src/main/java/com/example/demo/checkout/repository/CheckoutReceiptRepository.java` for explicit version3 insertion/precommit decoding and v1/v2 dispatch without adding new fields to old replay shapes.

- [X] T040 [US2] Integrate common Catalog view/time into `demo/src/main/java/com/example/demo/checkout/service/impl/CheckoutServiceImpl.java` and `demo/src/main/java/com/example/demo/checkout/calculator/CheckoutCalculator.java`: effective prices first, then stock-qualified merchandise and voucher, new snapshot command/receipt3, same timestamp for order/reservation/redemption/receipt. Preserve fence/rechecks, ownership, cart consumption and all-or-nothing rollback.

- [X] T041 [US2] Extend `demo/src/test/java/com/example/demo/checkout/service/CheckoutConcurrencyIT.java` for synchronized sale/base writers vs checkout, all-item coherence, expiry during inventory/voucher waits, same/different keys and affected cart mutation overlap. Prove one pricing instant, no duplicated reservation/redemption/order and original replay after price/sale edits; independent transactions, deterministic barriers, no retries-as-proof.

- [X] T042 [US2] Extend `demo/src/test/java/com/example/demo/e2e/CheckoutFlowIT.java` and `demo/src/test/java/com/example/demo/e2e/VoucherFlowIT.java` for 1m/20%=800k, quantity3 at101/12.5%=267, differing bases/ranges, stacking80k on800k, boundary/base edits, historical labels/prices and forced failure after reservations with full rollback; include free/100%-sale cases. Commit the resulting purchase and reload actual PostgreSQL items to compare receipt evidence; do not stop at in-memory command assertions.

- [X] T043 [US2] Run CP2 targeted plus connected cart/checkout/inventory/voucher/order regression and old/new receipt migration tests, documenting selectors/counts and per-revision evidence in `specs/013-variant-review-sale/implementation-validation.md`. Recheck no Inventory/voucher work on replay and no new management route/security bypass.

## Phase 5: User Story 3 — Find relevant product reviews (Priority: P2)

Independent test: mixed review fixture, filtered counts/ties/image multiplicity and safe historic labels/statistics using existing purchases; no active sale required.

- [X] T044 [P] [US3] Extend strict query/MVC tests in `demo/src/test/java/com/example/demo/review/controller/ProductReviewControllerTest.java` for exact rating1–5, nullable true/false flags, all four sorts, defaults and ID ties; reject empty/null-literal/malformed/repeated/unknown parameters, rating sets, date/variant filters and arbitrary sort fields with existing safe400.

- [X] T045 [P] [US3] Extend `demo/src/test/java/com/example/demo/review/repository/ProductReviewReadRepositoryIT.java` for rating × omitted/true/false flags × four sorts, Unicode whitespace/null comments, several images per review, filtered count/out-of-range pages and tied ordering. Count and list must match with no image-join multiplicity.

- [X] T046 [P] [US3] Extend safe historical projection tests in `demo/src/test/java/com/example/demo/order/service/OrderReviewReadServiceIT.java` and `demo/src/test/java/com/example/demo/review/service/ReviewHistoryIT.java` for bounded batch, renamed/deleted variants, null only when legacy source absent, and no order/recipient/customer leakage or per-review queries.

- [X] T047 [US3] Extend `demo/src/main/java/com/example/demo/review/dto/request/ReviewListRequest.java` parser with nullable single rating and booleans, retaining "page default 0, size default 20, size 1–100", strict single-value allowlist and four sorts followed by ID DESC; explicit false differs from omission.

- [X] T048 [US3] Implement shared scoped count/page predicate component in `demo/src/main/java/com/example/demo/review/repository/ProductReviewQueryRepository.java` and `demo/src/main/java/com/example/demo/review/repository/ProductReviewQueryRepositoryImpl.java`, integrating `demo/src/main/java/com/example/demo/review/repository/ProductReviewRepository.java`; use bound values, EXISTS/NOT EXISTS image references and the exact fixed Unicode White_Space comment rule for positive/negative predicates.

- [X] T049 [US3] Add `getReviewOptionSnapshots(Collection<Long> itemIds)` to `demo/src/main/java/com/example/demo/order/service/OrderReviewReadService.java`, implement in `demo/src/main/java/com/example/demo/order/service/impl/OrderReviewReadServiceImpl.java` using `demo/src/main/java/com/example/demo/order/repository/OrderItemRepository.java`; return itemID→immutable labels only, "maximum current page size 100", no owned-order locks or arbitrary public order endpoint.

- [X] T050 [US3] Integrate filtered page/count and one bounded historical batch in `demo/src/main/java/com/example/demo/review/service/impl/ReviewReadServiceImpl.java`, `demo/src/main/java/com/example/demo/review/dto/response/ReviewResponse.java` and `demo/src/main/java/com/example/demo/review/converter/ReviewConverter.java`; preserve REPEATABLE_READ, separately ordered images, safe authors and converters free of repository/normalization work.

- [X] T051 [US3] Add product-wide grouped rating counts through `demo/src/main/java/com/example/demo/review/repository/ProductReviewRepository.java`, `demo/src/main/java/com/example/demo/review/service/impl/ReviewStatisticsServiceImpl.java` and `demo/src/main/java/com/example/demo/review/dto/response/ProductRatingSummary.java`: "zero-fill 1–5 and sum to reviewCount", "average remains null for zero and existing two-decimal HALF_UP otherwise". Retain `/rating-summary` route and no-query/no-store behavior.

- [X] T052 [US3] Extend `demo/src/test/java/com/example/demo/review/service/ReviewReadServiceIT.java`, `demo/src/test/java/com/example/demo/review/service/ReviewStatisticsServiceImplTest.java`, `demo/src/test/java/com/example/demo/review/converter/ReviewConverterTest.java` and `demo/src/test/java/com/example/demo/review/controller/ProductReviewControllerIT.java` for response shapes, filtered pages, unchanged aggregates, zero bins, safe images/authors and bounded queries at page20/100.

- [X] T053 [US3] Run CP3 including `demo/src/test/java/com/example/demo/e2e/ReviewFlowIT.java`, history/read/repository/API and Order snapshot tests; retain published-only safe404 and owned edit/purchase eligibility regressions, record scope/counts in `specs/013-variant-review-sale/implementation-validation.md`.

## Phase 6: Polish & Cross-Cutting Verification

After all story checkpoints, preserve targeted/checkpoint/full-suite evidence and authorized scope.

- [X] T054 Run inherited catalog workload/performance and SQL-budget checks in `demo/src/test/java/com/example/demo/catalog/ProductSearchPerformanceIT.java` and relevant source-workload tests using `specs/007-product-search-filter/evidence/performance-policy.md`; two serial JVM runs,20 warmups/100 measured size20 requests per scenario, preserve p95 thresholds and query counts. Record current EXPLAIN and measurements in `specs/013-variant-review-sale/implementation-validation.md`; optimize only measured affected paths.

- [X] T055 Complete migration/cutover rehearsal on disposable prefeature fixtures through `demo/src/test/java/com/example/demo/catalog/CatalogSaleMigrationIT.java` and `demo/src/test/java/com/example/demo/checkout/repository/CheckoutMigrationIT.java`, checking ICU/version evidence, all schema constraints, audit failures without mutation, preserved IDs/paid amounts/options/receipt bodies, no mixed-version writers and v1/v2/v3 replay. Document production audit not executed if no authorized dataset is available.

- [X] T056 Run affected public/private security, safe image access, order-history, inventory/cancellation, rollback and checkout/cart concurrency regressions identified in `specs/013-variant-review-sale/plan.md`; record exact tests/commands and remaining uncertainty in `specs/013-variant-review-sale/implementation-validation.md`, without weakening assertions or claiming mock evidence proves real overlap.

- [X] T057 Review requirement coverage and API compatibility, update examples/verified commands in `specs/013-variant-review-sale/quickstart.md` and completion evidence in `specs/013-variant-review-sale/implementation-validation.md`; verify no management API, new dependency/plugin, public private-data leak, unbounded collection or duplicate pricing authority was introduced.

- [ ] T058 **Deferred by user: no further performance tests at this time; functional verification excludes the four benchmark/plan classes and is not this unrestricted gate.** Run one unrestricted `./mvnw.cmd verify` from `demo/` without selectors/skip flags/exclusions, inspect fresh `demo/target/surefire-reports/`, `demo/target/failsafe-reports/` and packaging discovery, and record revision/environment/counts/results in `specs/013-variant-review-sale/implementation-validation.md`. Required feature-specific concurrency/performance tests still run if omitted by normal discovery; environmental blocks stay blocked, never passed.

- [X] T059 After any isolated fix rerun affected checks first; repeat full verification only for broad/shared changes or unresolved regression uncertainty, recording trigger and revision in `specs/013-variant-review-sale/implementation-validation.md`. Close tasks only with actual evidence, disclose missing gates, and leave commits/pushes/deployments to explicit user instructions.

## Dependencies & Execution Order

### Phase and Story Dependencies

```text
Setup T001–T003
  -> Foundation T004–T016: tests -> V28 -> V29 -> V30 -> intermediate compatibility gate
      +-> US1 T017–T022: option presentation/cart MVP
      +-> US2 T023–T043: prepared pricing/native snapshots/checkout
      +-> US3 T044–T053: review queries/history
All story checkpoints -> final T054–T059
```

The default order is listed priority order. After Foundation all schema is installed in ascending order and all stories are independently implementable/testable on it; no story introduces a missing lower migration later. US1/US2 share detail files, so serialize conflicting edits even if test/implementation branches are otherwise independent. Do not enable Flyway out-of-order. US2 must finish before active sales are enabled; Foundation and US1 do not claim complete sale support.

### Detailed prerequisites

| Prerequisites | Next tasks |
| --- | --- |
| T001–T003 | T004–T006 boundary/upgrade tests, then T007–T010 V28 and base fixture adaptation |
| T010 | T011 canonical integrity tests, T012 V29, T013–T014 V30/versioned checks |
| T007–T014 | T015 compatibility review and T016 passing Foundation gate; schema-stage tests also exercise intermediate states |
| T016 | US1 T017 detail tests, T018 projections, T019 aggregation, T020 response, T021 cart tests, T022 MVP checkpoint |
| T016 | US2 T023–T025 independent contract tests and T026 prepared-pricing regressions |
| T023–T026 | T027 projections, T028 search, T029 display, T030 CP1 |
| T027 | T031 fresh checkout view, T032 prepared cart/wallet operation model, T033 cart consumers |
| T025 + T032 | T034 voucher integration |
| T016 | T035 committed PostgreSQL reload tests before T036 command/validation, T037 native writer, T038 order detail, T039 receipt codec |
| T030–T039 | T040 checkout orchestration, T041 concurrency, T042 flows, T043 CP2 |
| T016 | US3 T044–T046 independent contract/query/history tests |
| T044–T045 | T047 parser and T048 query |
| T046 | T049 bounded Order historical projection |
| T047–T049 | T050 review service, T051 summary, T052 API/service tests, T053 CP3 |
| T022 + T043 + T053 | T054 performance, T055 upgrade, T056 connected regression, T057 docs |
| T054–T057 | T058 unrestricted verify, T059 evidence/fix rerun decision |

No compatibility test bypasses preflight/data integrity. Foundation schema tests use v3-shaped test rows to prove strictness while existing application checkout still emits v2. Committed native snapshot reload tests turn green only after the US2 writers/orchestration are complete. Tests can initially fail for the intended missing behavior; checkpoints cannot pass on compile/setup failures. Intermediate v2 compatibility is scoped to no-sale staging, not permission for feature-complete v3 checkout to omit evidence.

## Parallel Execution Examples

`[P]` means independent test authoring after Foundation; it does not authorize agents or simultaneous resource-heavy runs.

- US1: T017 detail contracts can be authored independently of the already completed Foundation integrity tests; projection/aggregation/response edits remain sequential.
- US2: T023 catalog contracts, T024 codec/migration contracts and T025 cart/voucher contracts touch different files and may be authored together; T026 and T035 are separate regression boundaries but run database-heavy work serially as appropriate.
- US3: T044 query/MVC, T045 repository filter and T046 historical projection tests can be authored together. Orchestration waits for both query and Order interface.

## Implementation Strategy

1. Complete all ordered schema in Foundation with no sale configured. V30 expands nullable storage and branches strictness by receipt version; v1/v2 all-null evidence remains valid. V28 base/mirror fixture writers and V29 normalized-data preflight belong to Foundation. Failures require a staging/dependency report, never silent coercion.
2. Demonstrate US1 as the option-selection MVP, without claiming sale/review completion.
3. Complete US2 through native INSERT bindings, committed snapshot reloads, operation-pricing races, current-data checkout, v3 replay and existing inventory/voucher concurrency. Only then enable active-sale fixtures for feature acceptance; no production deployment is authorized.
4. Complete US3 independently using immutable historic purchase fixtures.
5. Complete cross-domain/performance/upgrade and unrestricted verification. No tests or production files are implemented by this documentation update; all tasks stay unchecked.

## Requirement and Acceptance Coverage

| Requirements / outcomes | Implementation and evidence tasks |
| --- | --- |
| FR-001–005, FR-027 options; SC-001 | T009–T012, T017–T022 |
| FR-006–009, FR-027 reviews; SC-002 | T044–T053 |
| FR-010–013, FR-022–025 pricing/search; SC-003 | T004–T010, T023, T027–T033, T042, T054 |
| FR-014–017, FR-025–026 checkout/voucher; SC-004–005 | T006, T013–T014, T024–T025, T031–T043, T055–T056 |
| FR-018–021 compatibility/security; SC-006 | T001–T016, T024, T035–T039, T046, T055–T059 |
| FR-028 persisted PostgreSQL evidence (G1) | T035–T037, T039–T043, T055 |
| FR-029 Foundation migration order/staging (I1) | T006–T016, T055 |
| FR-030 immutable operation pricing (U1) | T026–T034, T040–T043 |

## Verification and Completion Rules

- Constitution XIII applies: select affected unit/MVC/repository/service/API/flow checks after each change. Record exact commands/selectors, tested revision, environment, current discovery counts, failures/fixes/skips and limitations in `specs/013-variant-review-sale/implementation-validation.md`.
- Foundation=T016, option MVP=T022, CP1=T030, CP2=T043, CP3=T053. PostgreSQL reload evidence must cross an actual commit/fresh read; commands and managed entities alone are insufficient. Synchronized independent transactions prove concurrency; preserve existing cart/inventory/voucher/fence guarantees.
- T058 runs unrestricted `./mvnw.cmd verify` from `demo/` with no selective/skip flags; inspect fresh Surefire/Failsafe reports and packaging. Run required feature-specific concurrency/performance checks even if default discovery omits them.
- Localized fixes rerun affected checks first; repeat full suite only for broad/shared changes or unresolved uncertainty, recording trigger/revision. Never relabel earlier runs. Environmental blocks remain unverified; do not weaken assertions or claim skipped work passed.
- No new libraries/plugins, higher isolation or pricing-only locks, cross-request prepared cache, management API, out-of-order Flyway, applied-migration edits, auto-commits/pushes or deployment. Preserve unrelated untracked output files.

## Task Generation Validation

Revised for user-approved G1/I1/U1 on 2026-11-26. At task generation all 59 tasks were unchecked (current execution status is shown above): US1=6, US2=21, US3=10, shared/final=22. Seven independent test-authoring tasks carry `[P]`. All V28/V29/V30 installation tasks are in Foundation; IDs and graph were renumbered together. Runtime/schema/compatibility behavior remains unexecuted until implementation.
