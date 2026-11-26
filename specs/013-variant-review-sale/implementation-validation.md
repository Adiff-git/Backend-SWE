# Feature 013 implementation evidence

## Working state and scope

- Started from `86512d7ab1e0bb262f783c05c0051d6be46b1983` on `draft/version-1`.
- Existing untracked `output/` and feature documentation retained. No commit/push/deployment.
- Requirements checklist: 16/16 checked; reviewed read-only. No extension hooks configured.
- Existing `.gitignore`, `demo/.gitignore` and `demo/.dockerignore` cover Java outputs, local configuration and editor files; no unrelated ignore edits needed.

## Environment check (T001)

- Explicit JDK executable: `C:\Program Files\Java\jdk-25.0.4.1\bin\java.exe -version`: Oracle Java 25.0.4.1.
- `demo/mvnw.cmd -version`: Maven 3.9.16, Java 25.0.4.1, Windows 11.
- Docker Desktop was initially stopped. Started the existing installation hidden; engine is Docker 29.8.0.
- Sandbox Docker pipe access is denied; escalated `docker version --format '{{.Server.Version}}'` succeeds. Integration commands require Docker access outside the sandbox.
- Latest applied-source migration before implementation: V27. V28–V30 available.
- POM binds Failsafe integration-test/verify. Actual discovery is recorded per run below; configuration is not passing evidence.

## Verification runs

Application verification has not completed. Required Foundation, story checkpoints, concurrency/performance and unrestricted `mvnw.cmd verify` remain open until their actual results are recorded here.

### Foundation red runs

- `mvnw.cmd -B -ntp -Dit.test=CatalogPricingRepositoryIT verify`: 5 pricing IT cases reached PostgreSQL17.12; 1 failure/4 errors, all due to missing pricing/identity functions. Expected preimplementation red, not passing evidence. Earlier normal unit discovery also ran; this selective integration run is not a full gate.
- `mvnw.cmd -B -ntp -Dtest=CatalogSaleMigrationIT,CheckoutFoundationCompatibilityIT test`: 4 cases, 1 failure/3 errors due to absent target28 migration. Expected preimplementation red. Explicit Test selector executes these real PostgreSQL IT classes through Surefire for targeted feedback.
- `mvnw.cmd -B -ntp -Dtest=CatalogPricingRepositoryIT,CatalogSaleMigrationIT test`: after initial V28, 8 cases/7 errors. Populated-table upgrade exposed pending deferred-trigger events before ALTER TABLE; fixed by validating pending events inside migration before DDL. JPA query mappings were updated from removed sellingPrice field to basePrice. Affected rerun pending; no pass claimed.
- Cached PostgreSQL17 image configuration includes `--with-icu`; actual root collation/function behavior is exercised in integration tests, not inferred solely from this flag.

### V28 targeted green

`mvnw.cmd -B -ntp -Dtest=CatalogPricingRepositoryIT,CatalogSaleMigrationIT test`: 8 tests, 0 failures/errors/skips (3 migration tests + 5 repository integration tests), BUILD SUCCESS. Executed against disposable PostgreSQL17.12, Docker29.8.0 and Java25.0.4.1. Verified populated-data backfill, rejected invalid legacy price, constrained percentage/bounds, read-only mirror, exact unit rounding/time/zero behavior and ICU/NFC identity. Scope is V28/base mappings and updated JPQL/fixture; not full Foundation or feature acceptance. T006 compatibility is still open until V29/V30 are installed.

### V29 and V30 targeted evidence

- `mvnw.cmd -B -ntp -Dtest=VariantOptionIntegrityIT test`: pre-V29 red, 4 tests/3 expected failures (equivalent combinations, duplicate normalized keys and concurrent canonical duplicates); sparse valid combinations passed.
- `mvnw.cmd -B -ntp -Dtest=VariantOptionIntegrityIT,CatalogSaleMigrationIT test`: 7 tests/0 failures/errors/skips after V29 and stronger upgrade assertions preserving actual old receipt/item JSON and inventory identity.
- `mvnw.cmd -B -ntp -Dtest=CheckoutFoundationCompatibilityIT test`: 1 integration scenario/0 failures/errors/skips; ordered V28->V29->V30, six committed v1/v2 orders/receipts and one committed v3 item/receipt, unknown old snapshots and immutable new evidence. Isolated schema fixture, not completed v3 application flow.
- `mvnw.cmd -B -ntp -Dtest=CatalogMigrationsIT,CartCheckoutServiceIT test`: 18 tests/0 failures/errors/skips (15 catalog migration tests + 3 real cart/checkout seed-consumption tests). Updated latest-schema count to30 and fixture INSERTs to base_price; negative base now asserts the approved constraint rejection rather than the old permissive schema.
- Actual old CheckoutService native-writer/voucher/replay/rollback subset is running before Foundation gate. Full-suite and story gates remain open.

### Real native checkout compatibility

`mvnw.cmd -B -ntp -Dtest=CheckoutServiceIT#checkout_shouldReserveFullQuantityWithoutDeductingStockAndReplayWithoutInventoryWork+checkout_shouldConsumeOnlyChosenEntitlementAndPreserveDiscountOnReplay+checkout_shouldRollBackOrderAndCartWhenDatabaseRejectsReceiptAfterConsumption test`: 3 tests/0 failures/errors/skips. Existing v2 native checkout, Inventory reservations, owned voucher consumption/replay and a forced receipt rejection rollback pass on V30. This is a targeted intermediate-compatibility checkpoint, not the unrestricted gate. Added parent-move and final-variant deletion integrity scenarios are being verified separately.

The reviewable source/test patch is `implementation.diff`; it includes tracked changes and new source files (which ordinary unstaged git diff would omit). It is regenerated as work proceeds.

### Foundation gate

`mvnw.cmd -B -ntp -Dtest=VariantOptionIntegrityIT test` after adding parent-move and last-variant deletion scenarios: 6 tests/0 failures/errors/skips. Foundation T001–T016 is complete based on the scoped evidence above: ordered schema/upgrade functions, real v2 purchase compatibility and rollback, strict v3 fixture, canonical concurrent integrity, existing catalog migrations and cart seed behavior. Runtime activation of sales and all user-story/full-suite gates remain incomplete. No production dataset audit or deployment performed.

### US1 options

- New response-contract red run: `mvnw.cmd -B -ntp -Dtest=ProductDetailControllerTest,ProductDetailApiIT#getProduct_shouldExposeOrderedDimensionsAndCanonicalVariantSelections test`: 4 cases/2 expected failures for absent optionDimensions/selectionOptions.
- After implementation: `mvnw.cmd -B -ntp -Dtest=ProductOptionServiceImplTest,ProductCatalogServiceImplTest,ProductKeywordSearchServiceImplTest,ProductDetailControllerTest,ProductDetailApiIT test`: 36 cases/0 failures/errors/skips. Includes full detail API behavior and existing search/service regressions, canonical option metadata, code-point ordering, lowest unsigned UUID label precedence and empty default dimensions.
- Implementation uses a computed Hibernate JSON projection in the existing variant fetch for normalized identity plus original labels, retaining database normalization authority and detail query-count budgets. ProductOptionService aggregates/order dimensions; converter only maps prepared values. This is the concrete projection choice for T018, rather than a separate per-detail query.
- Cart fixtures now write base_price. The old negative-price cart test asserts new schema rejection then verifies recovery from stock unavailability; it does not disable integrity to inject invalid data. Existing validation unit coverage remains in force.
- Cart/detail repository/history checkpoint is running. US2/US3 and full verification remain open.

### US1 regression checkpoint

- `mvnw.cmd -B -ntp -Dtest=CartServiceIT,CartCheckoutServiceIT,ProductDetailRepositoryIT,ReviewHistoryIT test`: 26 cases, 7 fixture errors under required uniform option dimensions; six cart sibling fixtures and one detail repository fixture used choice/color on the same product. Corrected only those dimensions, retaining behavior/assertions and the new constraint.
- `mvnw.cmd -B -ntp -Dtest=CartServiceIT,ProductDetailRepositoryIT test`: 21 cases, 1 failure; the replacement stock-shortage scenario expected the old invalid-price exception. Corrected to the existing CartStockConflictException.
- Localized rerun `mvnw.cmd -B -ntp -Dtest=CartServiceIT#update_shouldRejectInvalidBaseAndRecoverStockUnavailableSourceThroughEligibleSibling test`: 1 case/0 failures/errors/skips. Other 20 cases passed on the immediately preceding revision; they are not relabeled as a fresh complete run. CartCheckoutServiceIT3 and ReviewHistoryIT2 passed on the initial checkpoint revision. The localized assertion/fixture corrections do not change production behavior; full relevant regression remains part of later integration/full gates.
- US1 option-selection MVP complete; US2 red tests now running. No active-sale application flow is claimed working yet.

### US2 red tests and initial catalog integration

- New sale detail/search/cart/v3 codec tests failed for the intended missing feature. Prepared-pricing fixture initially violated the existing uppercase voucher-code constraint; corrected the fixture, then `mvnw.cmd -B -ntp -Dtest=CartPreparedPricingIT test` reached all5 scenarios with5 expected behavior failures and0 setup errors. Independent committed base edits expose mixed operation pricing; configuration edits are not yet reflected by current-price cart reads.
- Added canonical active-percentage projection shared with the V28 price function, one set-based CatalogPricingRepository projection with a bound UUID array, paired range metadata and effective-price search CTE. V28 is new development migration, only exercised on disposable databases here; existing V1–V27 remain unchanged.
- `mvnw.cmd -B -ntp -Dtest=CatalogPricingRepositoryIT,ProductDetailApiIT,ProductSearchRepositoryIT,ProductCatalogServiceImplTest,ProductKeywordSearchServiceImplTest test`:60 cases; pricing5/detail API8/service15/keyword service8 passed; search24 failed due to a missing separator in final SQL ORDER BY composition. Separator fixed; search-only rerun pending. No complete CP1 pass claimed for this revision.

## Prepared cart pricing increment

`demo/.\mvnw.cmd -B -ntp -Dtest=CartPreparedPricingIT,CartVoucherServiceIT,ProductDetailApiIT test`: 30 tests, zero failures/errors. Five independent base/percentage/enabled/start/end edits at the former duplicate-read boundary retain the prepared prices, then observe fresh prices on the next request. This revision predates the subsequent v3 checkout/wallet changes.

## v3 checkout and concurrency increment

Target `CheckoutConverterTest,OrderSaleSnapshotIT,CartPreparedPricingIT,CartServiceIT,CheckoutServiceImplTest,CatalogCheckoutServiceIT`: 49 tests, 2 failures (percentage scale on replay and 105-row cart SQL budget), zero errors. Fixes normalize prepared percentage representation and remove redundant inventory/publication probes.

Target `OrderSaleSnapshotIT,CartServiceIT#getCart_shouldReturnAll105RowsInOrderWithoutWritesOrPerRowQueries,CheckoutServiceIT,CheckoutConcurrencyIT`: 97 tests, 2 failures (same cart budget, old expected receipt version2), zero errors. All 53 existing checkout concurrency cases passed; all five committed snapshot/replay cases passed. The two failures were corrected subsequently and remain subject to rerun. Sale snapshots cover no-sale,12.50% rounding,zero-base/100%,bounded20%,nonzero-base100%; service calls commit before independent JDBC reads.

## Sale regression checkpoint follow-up

Target `CartServiceIT#getCart_shouldReturnAll105RowsInOrderWithoutWritesOrPerRowQueries,CheckoutConcurrencyIT#checkout_shouldCaptureCoherentAddressAndCatalogAcrossRealCompetingWrites+checkout_shouldKeepCapturedSaleAndVoucherInstantAcrossInventoryLockWait,CheckoutServiceIT#checkout_shouldConsumeOnlyChosenEntitlementAndPreserveDiscountOnReplay,VoucherFlowIT,CartPreparedPricingIT,CatalogCartReadServiceImplTest,OrderCreationServiceIT`:35 tests,zero failures/errors. Includes synchronized sale/base writers vs protected checkout, Clock advancement across observed PostgreSQL inventory lock wait, three committed HTTP sale cases (1m/20%,101/12.50%×3,100%), separate80k voucher stacking, prepared query count exactlyonce/operation, fresh checkout, query budget and native order creation. Existing 53-case checkout concurrency suite passed on immediately preceding revision; remaining broader checkpoint is pending.

## Resumed verification

Implementation resumed after the usage interruption without resetting files. The initial review test command was blocked by a compile error in the adapted RecommendationIT corruption fixture (wrong transaction-helper variable); corrected to the existing `tx` bean before rerun. This compile failure is not review behavior evidence. The refreshed implementation.diff includes tracked modifications and new Java/SQL files; existing output/ remains untouched.

## CP1/CP2 broad regression run

Command from demo/: `.\mvnw.cmd -B -ntp -Dtest=ProductListingControllerTest,ProductDetailControllerTest,ProductDetailApiIT,ProductCatalogServiceImplTest,ProductKeywordSearchServiceImplTest,ProductSearchRepositoryIT,CatalogPricingRepositoryIT,CatalogSaleMigrationIT,CatalogMigrationsIT,CatalogContractIT,CatalogCartReadServiceIT,CartPreparedPricingIT,CartServiceIT,CartVoucherServiceIT,CheckoutServiceIT,CheckoutConcurrencyIT,CheckoutConverterTest,CheckoutMigrationIT,CheckoutFoundationCompatibilityIT,OrderSaleSnapshotIT,OrderCreationServiceIT,OrderServiceIT,VoucherServiceIT,CheckoutFlowIT,VoucherFlowIT,OrderValidatorTest,CatalogConstraintsIT,CatalogRelationshipsIT test`.

305 tests:302 passed,2 failures,1 error. All57 checkout concurrency cases, listing/detail/search/contract tests, native snapshot reloads, migration/replay compatibility, HTTP flows and connected cart/voucher/order tests passed except the explicit failures below. Three failures: mirror trigger used generic23514 rather than23502 for missing required base; concurrent-delete fixture mixed choice/color dimensions; one reservation quantity assertion accidentally changed2to3 alongside receipt-version update. Fixed the first with standard null-error metadata, aligned fixture dimensions, restored quantity2. Existing applied migrations V1–27 remain unchanged. Subsequent rerun includes affected tests and two new after-sale eligibility/wallet-event cases; do not relabel this broad run as a fresh all-pass run.

Review preimplementation command `-Dtest=ProductReviewControllerTest,ReviewReadServiceIT#filters_shouldUseUnicodeCommentPresenceAndKeepProductWideStatisticsAndHistory test`:26 tests,6 expected MVC failures and1 expected service error because parser rejects the approved new filters. This establishes behavior-specific red evidence after fixing compilation.

CP1/CP2 affected follow-up selector `CatalogConstraintsIT,CatalogSaleMigrationIT,CheckoutServiceIT#checkout_shouldReserveFullQuantityWithoutDeductingStockAndReplayWithoutInventoryWork,CartVoucherServiceIT#sale_shouldApplyMinimumAndPercentageCapToAfterSaleSubtotalAndClearFreeCartSelection,VoucherServiceIT#wallet_shouldShareOnePreparedSaleAndVoucherEventThenRefreshNextRequest`:14 tests,zero failures/errors. Catalog null/error metadata and concurrent-delete fixture corrections pass; selected free-cart voucher cleanup, after-sale cap/minimum and shared wallet event/fresh next request pass. This closes the localized checkpoint failures; final full discovery/performance gates remain pending.

Review parser increment selector `ProductReviewControllerTest,ReviewReadServiceIT#filters_shouldUseUnicodeCommentPresenceAndKeepProductWideStatisticsAndHistory,ReviewHistoryIT#purchasedOptions_shouldComeFromOrderSnapshotAfterLiveVariantRenameAndRemoval`:27 tests,2 expected failures,zero errors. All parser/MVC cases passed; service returned4instead of2 matching reviews and historical purchasedOptions was absent after variant removal. Query/history implementation followed these failures. First green attempt had a test generic assertThat overload compile error; explicit JsonNode type corrected before rerun.

## CP3 review checkpoint

`.\mvnw.cmd -B -ntp -Dtest=ProductReviewControllerTest,ProductReviewReadRepositoryIT,ReviewReadServiceIT,ReviewHistoryIT,OrderReviewReadServiceIT,ReviewStatisticsServiceImplTest,ReviewConverterTest,ReviewReadServiceImplTest,ReviewServiceImplTest,ProductReviewControllerIT,ReviewFlowIT test`:45 tests,zero failures/errors. Repository test covers6rating choices×3image states×3comment states×4sorts=216 combinations with identical count/page predicates, two images per matching review and ID ties. Real history test removes purchased live variant while labels remain from Order; internal batch rejects101IDs and returns immutable maps. Existing page1/20/100 SQL budgets and public safe404/no-store/author behavior pass. Additional review write/concurrency and Order snapshot regression follows; a later refinement of the statistics unit fixture remains to rerun.

CP3 connected follow-up `.\mvnw.cmd -B -ntp -Dtest=CartTransactionServiceImplTest,CartVoucherServiceImplTest,ReviewStatisticsServiceImplTest,ReviewConcurrencyIT,ReviewServiceIT,ReviewUpdateServiceIT,OrderSaleSnapshotIT test`:32 tests,zero failures/errors. Explicit prepared-model mocks preserve cart stock/merge/coordination assertions; review writes, synchronized concurrency, image rollback and real committed Order sale snapshots pass after review integration.

## Final review refinements before unrestricted verification

All production Cart preview callers now pass the immutable operation instant explicitly, including empty/hidden rows; pricing preparation is separate from fresh inventory/publication observations. This avoids fallback Clock sampling when no eligible price metadata remains. Existing four-argument preview remains a compatibility wrapper for isolated legacy callers/tests. Cart query/consistency/stock tests must rerun on this refinement. Historical DirectSaleSnapshot was placed in Order (checkout reuses it), avoiding a new Order-to-Checkout dependency. No catalog SQL/normalization/pricing authority changed during performance run1; that run compiled before these Cart/DTO refinements and remains attached to that revision.

Static review: no added dependency/plugin, controller route, higher transaction isolation, pricing-only row lock, cross-request prepared cache, Flyway out-of-order setting, applied V1–27 edit, commit/push/deployment or production-data connection. Order native INSERT and historical read projections explicitly bind/read all new evidence fields; application creation validation requires prepared evidence while nullable all-null schema shape remains for legacy v1/v2 staging. Existing default isolation and catalog/inventory/voucher/fence lock policies are retained. Populated-environment preflight remains an unexecuted rollout gate.

## Performance run1

`.\mvnw.cmd -B -ntp -Dtest=ProductSearchPerformanceIT,ProductSourceWorkloadPerformanceIT,SearchQueryPlanIT,CatalogPublicReadPerformanceIT,CheckoutFoundationCompatibilityIT,VoucherFlowIT test` (log redirected to target/feature013-performance-run1.log):11 tests,zero failures/errors; independent JVM PID8056,PostgreSQL17.12. Source workload retained20warmups/100measured requests per scenario,size20,poolmax2/minidle0. All7scenario p95 thresholds and inherited SQL budgets passed individually: browse70.202ms,broad830.672,fuzzy874.894,selective385.794,brand/category15.915,combined158.456,price/spec33.045.5–6data statements. Synthetic workload and current EXPLAIN/oracle/skew assertions passed; historical gallery/detail Hibernate budgets remained unchanged. The detail path additionally uses one bounded JDBC prepared-pricing statement; a new all-JDBC assertion records total≤6while retaining inherited Hibernate≤5. This explicitly measures the additional set-based projection rather than presenting Hibernate statistics as all-JDBC counts.

Full samples,plans/runtime/PID and command log are preserved under validation/performance-run1/. Files from previous historical runs were not used (fresh source PID8056; old source PID5188 was discarded). Migration compatibility includes direct partial evidence rejection,tampered v3 rejection and deferred rejection of all-null legacy items masquerading as v3. HTTP fixtures passed this compiled revision; subsequent Cart/DTO/test refinements require their targeted rerun.

## Refinement regression checkpoint

`.\mvnw.cmd -B -ntp -Dtest=CartPreparedPricingIT,CartServiceIT,CartVoucherServiceIT,VoucherServiceIT,CartTransactionServiceImplTest,CartVoucherServiceImplTest,CatalogCartReadServiceImplTest,CatalogQueryCountIT,CatalogPricingRepositoryIT,CatalogSaleMigrationIT,OrderSaleSnapshotIT,CheckoutConverterTest,OrderValidatorTest,CheckoutFoundationCompatibilityIT,VoucherFlowIT test`:103 tests,zero failures/errors (log target/feature013-polish-regression.log). Explicit instant propagation works for hidden/empty-state previews without extra Clock sampling; fresh stock/publication observations retain the prepared prices. New metadata is redacted on unavailable rows. Cart105-row SQL budget and detail all-JDBC≤6plus inherited Hibernate≤5pass. Root-case/whitespace/supplementary identity cases and actual V28→V29 duplicate/dimension audits reject without mutating rows; all v3 partial/tampered/bypass tests pass.10%voucher on800k yields80k through committed HTTP flow; original snapshots/replay remain correct after catalog edits. A disabled voucher introduced after cart preview causes checkout failure with no order; explicit retry after re-enabling obtains a fresh pricing view.

The only later edit before run2 adds ICU provider/current-version fields to the existing test evidence query; application/catalog pricing SQL is unchanged. Final unrestricted clean verify will exercise it with fresh reports and no stale compiled classes.

Detail follow-up from measured all-JDBC count: canonical option labels, original labels/SKU/specifications and prepared prices now share CatalogDetailVariantRow from one bound-product/instant statement. Removing the separate variant fetch restores total detail<=5 alongside inherited Hibernate<=5; no threshold is relaxed. Cart/checkout retain the unchanged bound-UUID-array price SELECT. Search/listing workload SQL is unchanged, so the two dedicated measurements remain valid for those paths. Affected detail/cart/checkout checks run after performance run2.

## Performance run2

`.\mvnw.cmd -B -ntp -Dtest=ProductSearchPerformanceIT,ProductSourceWorkloadPerformanceIT test`:3 tests,zero failures/errors; independent JVM PID30560,serial after run1. Same20warmups/100samples,size20,pool2 and unchanged per-scenario thresholds/SQL budgets. All7source scenarios pass independently (no averaging across JVMs). Full measurements/runtime/source hash/SQL plans and log: validation/performance-run2/.

[
  {
    "scenario": "browse",
    "p95Ms": 69.5224,
    "budgetMs": 500.0,
    "maxDataStatements": 5
  },
  {
    "scenario": "broad-laptop",
    "p95Ms": 810.5983,
    "budgetMs": 1500.0,
    "maxDataStatements": 6
  },
  {
    "scenario": "fuzzy",
    "p95Ms": 858.943,
    "budgetMs": 1500.0,
    "maxDataStatements": 6
  },
  {
    "scenario": "selective",
    "p95Ms": 374.0809,
    "budgetMs": 1000.0,
    "maxDataStatements": 6
  },
  {
    "scenario": "brand-category",
    "p95Ms": 16.512,
    "budgetMs": 500.0,
    "maxDataStatements": 5
  },
  {
    "scenario": "combined",
    "p95Ms": 155.104,
    "budgetMs": 500.0,
    "maxDataStatements": 6
  },
  {
    "scenario": "price-specification",
    "p95Ms": 33.4224,
    "budgetMs": 500.0,
    "maxDataStatements": 5
  }
]

## User-directed performance deferral

User instruction: "don't need to run performance test at the moment". No additional performance/plan benchmark will be started. The last detail command had already completed:64 tests,1 failure,zero errors. All detail API/contract/query-count/native snapshots/cart/review checks passed, including the combined detail projection with total JDBC<=5; the sole failure was an old converter field-list assertion that omitted the approved additive pricing/priceRange fields. Corrected that assertion, preserving exact-field validation. The included public read benchmark had completed before this instruction was processed.

Remaining verification runs functional discovery with explicit exclusions for ProductSearchPerformanceIT,ProductSourceWorkloadPerformanceIT,CatalogPublicReadPerformanceIT,SearchQueryPlanIT. It must not be reported as unrestricted verification. T058 unrestricted gate is deferred because it includes performance classes, following this explicit user override; completed dedicated performance runs remain recorded as historical evidence.

Converter field-list follow-up `-Dtest=ProductCatalogConverterTest test`:6 tests,zero failures/errors. All-JDBC detail count is now5on the combined price/options projection, with API/contract/cart/checkout/history checks passing on the compiled detail revision. Starting fresh functional `clean verify` with explicit benchmark/plan exclusions per user request. The prior target directory was verified as the expected workspace directory (not a link) before Maven clean; existing unrelated output/ remains untouched. Fresh Surefire/Failsafe reports and packaging will be inspected. This is not unrestricted T058 verification.

Functional clean attempt stopped before tests because a pre-existing scratch frontend checkout was stored under demo/target/pa3-review. Maven had removed its HEAD/config/index and .env.example before failing on read-only packed Git objects. Recovered repository metadata from surviving refs/object packs and the source commit/remote recorded in its existing PA3 archive; restored only the missing tracked .env.example. All other working files were retained. Git HEAD633aa14542c9373129769d25f16babde501dcfc9,clean status and fsck --full --no-reflogs exit0 are recorded in validation/scratch-checkout-recovery.json; no checkout/reset,commit,push or source change was used. Default metadata was reconstructed; deleted local reflogs/config customization cannot be verified byte-for-byte. Failed clean log:validation/functional-clean-attempt.log. No further module-wide clean will run. Only explicit resolved non-linked Maven-generated classes/test-classes/reports/status/generated-sources directories were removed, preserving pa3-review and other target evidence. Functional verify now runs without clean and with the requested benchmark exclusions.

Functional verify reached723 unit/MVC cases:722passed,1failure,zero errors/skips; integration phase was not reached. VoucherServiceImplTest exposed the retained three-argument compatibility method sampling Clock before locks. Restored legacy post-lock sampling via a shared locked quote helper; explicit four-argument checkout continues to use its supplied event without Clock access. Added both-signature lock-order/event regression cases. After affected checks, resume packaging/integration discovery with only the affected unit selector, preserving prior722passing unit results and avoiding an unnecessary full unit rerun. This remains selective functional verification, not T058.

Affected follow-up `-Dtest=VoucherServiceImplTest,CheckoutConcurrencyIT#checkout_shouldKeepCapturedSaleAndVoucherInstantAcrossInventoryLockWait,CartPreparedPricingIT,VoucherServiceIT test`:24 tests,zero failures/errors/skips. Both quote signatures preserve definition->usage->entitlement lock order; legacy captures Clock after locks and the new checkout entry never samples Clock. All requested expiry-wait/prepared-price/current-voucher checks pass. Resuming verify with affected VoucherServiceImplTest only in Surefire and all functional IT classes in Failsafe; existing722unaffected unit/MVC passes remain evidence on the prior revision, not a fresh unrestricted all-suite pass.

Standalone API document updated for additive option/pricing/range fields, v3-only current item evidence alongside strict v1/v2 replay shapes, review filter parameters, historic options and distribution bins. `python docs/validate_openapi.py`:61operations,55paths,108schemas,550local references,96examples,84DTOshapes,zero errors (standard-library structural/source subset, not external conformance validation). No route/dependency added.


## Final functional discovery and localized follow-up

`verify -Dtest=VoucherServiceImplTest -Dit.test=*IT,!ProductSearchPerformanceIT,!ProductSourceWorkloadPerformanceIT,!CatalogPublicReadPerformanceIT,!SearchQueryPlanIT` completed packaging and 751 integration cases: 735 passed, 13 assertion failures, 3 errors, no skips. Log: validation/functional-verify.log. This failed discovery is retained, not relabeled as passed. The packaged application contains V28/V29/V30 and the Order-owned DirectSaleSnapshot; no stale Checkout-owned snapshot class.

Fifteen failures/errors are affected legacy expectations: two cart fixture sibling dimensions violated V29, repeated response comparisons need a shared explicit instant, eight migration assertions/writers need the additive V30 schema, and three voucher concurrency assertions/spies need the approved captured event and five-argument preview signature. Fixes preserve actual database lock overlap and exact equality assertions; expiry during a wait is evaluated at the captured operation instant, with independent next-request revalidation already covered. The remaining error is environmental: RecommendationHttpIT cannot launch its separate AI-SWE sibling project (CreateProcess error267, missing directory). It includes 1500-product HTTP performance samples and will not be rerun under the user-directed performance deferral. No production dependency or test skip was added to conceal this block.

An additional FR-012 card consumer gap was detected: recommendation hydration displayed the deprecated base-price mirror. Red regression `RecommendationIT#similarCardsShouldExposeTheSamePreparedEffectiveSalePriceAsCatalogCards` failed with expected800000/actual1000000 (validation/recommendation-sale-red.log). Final hydration now calls a bounded Catalog service interface returning immutable per-product ranges from one canonical SQL projection and one fresh microsecond event; original/effective metadata and representative inventory use the same endpoint. Existing publication locks, candidate ranking and inventory policies remain. T029 reopened until functional follow-up succeeds. The follow-up selector is recorded in validation/functional-followup.log and excludes performance tests.


Functional localized follow-up `-Dtest=ProductCatalogServiceImplTest,ProductDetailControllerTest,ProductCatalogConverterTest,Recommendation*Test,RecommendationIT,CartControllerIT,ProductListingApiIT,ProductSearchBaselineRepositoryIT,CartFlowIT,InventoryMigrationIT,VoucherMigrationIT,VoucherConcurrencyIT,CatalogPricingRepositoryIT test`:101 tests,100 passes,1 assertion failure,zero errors/skips. All recommendation cases (12), actual voucher concurrency cases (6), voucher migrations (10), cart flow/API, prepared catalog projection and exact repeat-time comparisons passed. Remaining InventoryMigrationIT historical-row equality compared the old pre-V30 column set against the new additive set. Its expected complete map now includes all six new nullable snapshot columns as null, preserving exact equality of every legacy value and verifying no invented historical evidence. Final `-Dtest=InventoryMigrationIT package` reruns all seven inventory migration cases and packages the final code; results follow. No performance tests in either localized command. T029 closed on the green recommendation sale regression.

API documentation validator rerun:61 operations,55 paths,108 schemas,550 resolved local references,96 examples,84 DTO shapes,zero errors. `git diff --check` passed. No extension hook file is registered at .specify/extensions.yml. The recovered scratch frontend checkout was rechecked clean at its original633aa14542c9373129769d25f16babde501dcfc9 commit. Production-data migration preflight remains a rollout prerequisite; no populated deployment environment was accessed.


Final `-Dtest=InventoryMigrationIT package`:7 tests,zero failures/errors/skips,BUILD SUCCESS (51.930s),including populated legacy-order preservation with all new evidence null. Log:validation/final-functional-package.log. This closes the sole remaining localized assertion. Combined localized follow-ups cover101 distinct cases; six migration cases were repeated, so do not add101+7 as unique tests. No functional application failure remains among available discovered checks and their affected reruns. This is combined revision-scoped evidence, not a fresh unrestricted all-suite pass. Full discovery's environmental RecommendationHttpIT failure remains unresolved.

Completion: T001�T057 and T059 are complete within the user-authorized functional scope. T058 remains unchecked/deferred. No further approval is needed for completed implementation; later unrestricted verification requires resuming performance scope and supplying the separate AI-SWE service environment. Production migration rollout additionally requires running validation/preflight.sql against the intended existing dataset and following the documented old-native-writer cutover; no production data or deployment was touched. All code remains unstaged/uncommitted on draft/version-1. Review artifacts:implementation.diff,changed-files.md,and validation/working-tree-source-manifest.json. No mandatory extension hooks are registered.


## User-authorized publication

After functional completion, the user explicitly requested committing and pushing this work to `feature/product-detail-additional-infor`. Publish application/test/migration changes, OpenAPI, feature specifications/contracts/checklist, this validation summary and the read-only preflight SQL. Local diff exports, raw execution logs/reports, scratch-recovery metadata and unrelated output/ remain local. No additional performance tests or deployment are part of publication. Earlier no-commit statements describe authorization at the time of those runs.
