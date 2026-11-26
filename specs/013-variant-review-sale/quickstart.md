# Implementation Validation Guide

Feature013 is implemented in the working tree. Targeted and story-checkpoint results are recorded in [implementation-validation.md](implementation-validation.md); performance and unrestricted verification remain separate gates. Do not run sale fixture writes or migrations on a populated/shared database. Use disposable PostgreSQL17 Testcontainers and existing local infrastructure only.

## Prerequisites and setup

Java25, Docker, existing Maven wrapper and current repository dependencies are required. PostgreSQL must provide UTF8/NFC and deterministic ICU root-lowercase support; fail capability preflight if absent, with no fallback locale. Local development uses `compose.dev.yaml`; do not change its images or supply/print credentials as part of validation. Existing media ITs provision their established isolated LocalStack when needed.

From repository root in PowerShell:

```powershell
& "$env:JAVA_HOME/bin/java.exe" -version
docker version
Set-Location demo
./mvnw.cmd -version
```

The setup script for planning has a PowerShell equivalent because bash is unavailable in the current session. Wrapper paths below are Windows commands; on an existing POSIX environment use `./mvnw` from `demo/`.

## Targeted verification commands

These selectors exercise implemented components. Inspect actual test discovery; a command that finds no meaningful tests is not acceptance.

```powershell
./mvnw.cmd '-Dtest=CatalogPricingRepositoryIT,CatalogSaleMigrationIT,VariantOptionIntegrityIT,CheckoutFoundationCompatibilityIT' test
./mvnw.cmd '-Dtest=ProductListingControllerTest,ProductDetailApiIT,ProductSearchRepositoryIT,CartPreparedPricingIT,CartVoucherServiceIT,OrderSaleSnapshotIT,CheckoutConcurrencyIT,VoucherFlowIT' test
./mvnw.cmd '-Dtest=ProductReviewControllerTest,ProductReviewReadRepositoryIT,ReviewReadServiceIT,ReviewHistoryIT,OrderReviewReadServiceIT,ReviewFlowIT' test
```

Selective `verify` still executes its normal earlier phases; these are targeted integration invocations, not unrestricted full-gate evidence. For isolated fixes prefer component-specific selectors and do not repeatedly run the entire groups without affected risk. Extend controller slice/API selectors for new parameter binding/security and price responses as implementation lands. Required feature-specific function, migration, concurrency and performance classes must also execute; existing selectors alone do not establish newly designed behavior.

## Runnable scenario matrix

Use existing domain fixtures and test clock injection, never production endpoints for sale administration. Fixture setup supplies explicit base/configuration, inventory and owned vouchers/orders. Keep transaction setup separate from the operation being verified.

| Scenario | Action / expected outcome |
| --- | --- |
| Default variant | Fetch detail with one empty-map variant; empty dimensions and one authoritative UUID. |
| Option combinations | Seed Red/M, Blue/M unavailable, no Red/L; classify complete/incomplete/absent/unavailable and add only the resolved UUID. |
| Identity integrity | Submit composed/decomposed accents, case, NBSP and duplicate canonical keys through disposable SQL/JPA; canonical duplicates and sibling dimension mismatches fail at commit. Test two simultaneous writers using barriers. |
| Cart semantics | Add 2+3, test 999/1000 boundary, absent-target reset and present-target preservation, cross-product rejection and stock sufficiency. |
| Sale formula | Base101/12.50% yields unit89 and quantity3 line267; 1,000,000/20% yields800,000; zero/100% remains zero; invalid precision/range rejected. |
| Time | Fixed common instant at start applies, at end does not; disabled/future/expired use base. Force later voucher/inventory waits across expiry without changing the captured result. |
| Variants/search | Bases1m/1.5m at20% yield800k/1.2m; paired extrema retain own originals. Verify price+spec matches one variant, sorting/count and orphan null compatibility. |
| Voucher | Eligible10% uncapped voucher on800k after-sale subtotal discounts80k, payable720k; minimum-spend evaluated after sale; zero-subtotal/invalid selection retain existing rules. |
| Checkout/replay | Checkout, change base/sale/options, replay identical key/command; stored v3 unchanged and no new order/reservation/redemption. Preserve v1/v2 original shapes too. |
| History/migration | Upgrade prefeature fixtures; old amounts/options/receipt bodies unchanged, new original evidence unknown on old orders. Collision audit fails without mutation; no fabricated bases for historic orders. |
| Reviews | Traverse exact rating × omitted/true/false image flag × omitted/true/false comment flag under four sorts. Check filtered counts, out-of-range pages, ties, multiple images, whitespace-only comments and safe author fields. |
| Summary/options | Filters never alter product average/count/distribution; bins1–5 sum to count. Rename/delete current variant and confirm purchased option labels remain historic with no private order information. |
| Security/rollback | Unpublished products stay hidden, foreign items/addresses stay safe; force post-reservation failure and assert all effects roll back. |

Public disposable-app read probes (set IDs/port from the local fixture; curl.exe avoids PowerShell aliases):

```powershell
$featureBaseUrl = 'http://localhost:8080'
$featureProductId = '<published fixture UUID>'
curl.exe "$featureBaseUrl/api/products/$featureProductId"
curl.exe "$featureBaseUrl/api/products/$featureProductId/reviews?rating=5&hasImages=true&hasComment=true&sort=rating,desc&page=0&size=20"
curl.exe "$featureBaseUrl/api/products/$featureProductId/rating-summary"
```

Expected shapes/validation are in [API contracts](contracts/api.md). Checkout/authenticated mutation probes should use existing test harness credentials rather than exposing bearer tokens in logs. There is no sale-write endpoint to call.

## Checkpoints and performance

Follow CP1/CP2/CP3 in [plan.md](plan.md), selecting connected-domain regression at each integration point. Preserve existing catalog007 workload/threshold/query-budget protocol in `specs/007-product-search-filter/evidence/performance-policy.md` and its quickstart; execute `ProductSearchPerformanceIT` and any relevant source-workload checks with their documented fixtures/configuration. Record two independent serial JVM runs, each scenario separately, and compare correctness to exhaustive qualifying-variant expectations. Do not weaken existing SQL-count or performance assertions for new pricing. EXPLAIN uses disposable SELECT fixtures only.

## Final unrestricted gate and evidence

Near implementation completion, from `demo/` run without skip flags, selectors or profile exclusions:

```powershell
./mvnw.cmd verify
```

Inspect fresh `target/surefire-reports` and `target/failsafe-reports`, packaging output and actual Test/IT counts/failures/skips. Record exact command/selectors, revision/working-state identity, JDK/PostgreSQL/Docker versions, environment limits, outcomes, failures/fixes and unexecuted checks in a feature implementation-validation record created during implementation. Preserve security/transaction/concurrency/performance tests even if additional selectors are required outside normal discovery.

After localized fixes rerun affected checks first; repeat full verification only for broad/shared changes or unresolved uncertainty, documenting the trigger. An earlier passing run remains attached to its tested revision. Missing Docker/JDK/network or failing assertions do not become a passed gate; disclose blocked scope and continue independent checks. No application verification was executed during planning.

## Approved Remediation Acceptance (G1/I1/U1)

- **Foundation migration order**: Install V28 -> V29 -> V30 before stories. New `CheckoutFoundationCompatibilityIT` must run old no-sale catalog/cart/voucher/order behavior and native v2 checkout/receipt replay after each stage and all together. V30 nullable expansion permits old all-null v1/v2 evidence, rejects partial evidence and requires complete evidence for v3. V28 base mappings/fixture writers and V29 normalization preflight are Foundation prerequisites. Never enable Flyway out-of-order. Report actual data/writer/capability incompatibility before continuing; no real dataset or production deployment is exercised by this guide.
- **Stored snapshot proof**: New `OrderSaleSnapshotIT` must commit the real native writer/checkout, clear persistence context or read in another transaction, and query PostgreSQL order-item snapshots. Verify all original/effective/discount/active percentage/bounds/time values against the v3 receipt and constraints, including no-sale and zero-base/100% cases. An in-memory command/seed is insufficient.
- **Prepared operation proof**: New `CartPreparedPricingIT` synchronizes base and separate sale configuration edits at former duplicate-read points. Assert one coherent input/pricing statement per operation, consistent eligibility/discount/subtotal/response, no redundant pricing read, fresh next-request values and fresh checkout/voucher validation. Preserve default isolation and existing inventory/quota/fence concurrency. These implemented classes are exercised by the selectors above; consult the evidence register for exact tested revisions and results.

Preserve the targeted/checkpoint/unrestricted-run protocol. Documentation planning/remediation did not itself run application tests; implementation results are recorded separately.

## Reviewing the changes

The local desktop review exports `implementation.diff` and `changed-files.md` describe the changes against baseline `86512d7ab1e0bb262f783c05c0051d6be46b1983`; these generated exports are kept local. The user subsequently authorized publication to `feature/product-detail-additional-infor`. Review the committed branch diff for the published sources. Validation limitations remain recorded in implementation-validation.md. Existing unrelated output/ is retained; no deployment is performed.

## Current user override

Further performance tests are deferred at the user's request. The two completed dedicated runs remain evidence, but do not start more benchmark/plan tests now. Functional verification uses `./mvnw.cmd -B -ntp verify '-Dit.test=*IT,!ProductSearchPerformanceIT,!ProductSourceWorkloadPerformanceIT,!CatalogPublicReadPerformanceIT,!SearchQueryPlanIT,!RecommendationHttpIT'`. This explicitly selected run is not the unrestricted T058 gate. Leave T058 open until the user resumes verification that includes those tests. RecommendationHttpIT also combines a separate AI-SWE service with HTTP performance samples; it is currently blocked by the missing sibling project and is excluded from further runs under this override.
