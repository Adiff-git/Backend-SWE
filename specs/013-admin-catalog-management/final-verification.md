# Final unrestricted verification — Feature013

Recovered/revalidated2026-11-23, branch `features/AdminCatalogManagement`, HEAD `86512d7ab1e0bb262f783c05c0051d6be46b1983` plus tested uncommitted source. Actual finalization starting progress **85/88**: T077 was already completed, not merely printed. No Maven/Failsafe/coordinator remains running. No suite rerun was necessary for documentation-only finalization.

## One final run, not combined checkpoints

From `demo/`:

```bash
UV_NO_SYNC=true bash ./mvnw clean verify
```

Started2026-11-23T02:48:14+07; finished03:06:49+07;18:34. **Exit0 / BUILD SUCCESS**: coordinator checks subprocess returncode before recording T077 PASS; the successful branch, preserved final log and fresh XML/package agree. The original live exit object no longer exists; no separate raw exit-code file is invented. `UV_NO_SYNC` avoids AI environment synchronization and is not a test-skip flag. No selectors, skips, exclusions or offline shortcut were used.

| Normal lifecycle phase | XML suites | Tests | Failures | Errors | Skips |
|---|---:|---:|---:|---:|---:|
| Surefire (unit/MVC and context smoke) |159|957|0|0|0|
| Failsafe integration |157|862|0|0|0|
| Unique test identities |316 suites|1819|0|0|0|

Independent source discovery reconciliation:315 concrete annotated normal Test/Tests/IT source classes, no missing class (nested suites explain why suite/source counts need not match). Current `demo/target/surefire-reports/TEST-*.xml` and `demo/target/failsafe-reports/TEST-*.xml` are newer than this run's start and byte-identical to protected final copies. All main/test source timestamps precede the run; current source hashes are preserved. Successful clean compilation and JAR generation belong to this command, not a new compilation claim made during documentation finalization.

The final protected log is `.private/admin013-2c3a577f/verification/resumed-final/admin013-final-full-verify.log`; command, per-class `full-summary.json`, `full-surefire-reports/`, `full-failsafe-reports/` and independent `finalization-verification.json` are beside it. Transient `/tmp` logs no longer exist; protected evidence survives. Do not publish private registry/credentials or copy arbitrary report properties into public documents.

## Artifact and G2 source binding

- Executable `demo/target/demo-0.0.1-SNAPSHOT.jar`,80415796 bytes; SHA256 `3b9d3bb6d5e9de0d2535744a82341b8b8dc8567f839d12d4422ce7db9f47cfaf`.
- Application migration V28 SHA256 `ea55e11f01868f6abd1f3d5b8d435099c4c3184ae0c561ac90378ef9b5276fe1`; source, `target/classes` and the actual JAR entry are byte-identical.
- Rehearsed schema dump SHA256 `3c8d8fd85f7efec1ce8be8bab959a98051ab21dbaf838bcf76af9b49de4aa1d3`; matches recorded current-source G2 integrity artifact. Both qualified `public.catalog_name_trim` and FK-compatible `FOR NO KEY UPDATE` corrections are present. Earlier schema hash in historical rehearsal paragraphs describes the earlier guard revision, not this result.
-40 controlled old-relation fingerprints match V27→V28→restoredV28; old history/default/FK/order/receipt/allocation checks are in [migration-rehearsal.md](migration-rehearsal.md). V1–V27 are unchanged; application migration history reaches28 on disposable environments only. Existing temporary successor/negative recovery fixtures are not new application migrations or a demo rollout.

## Evidence layers and concrete oracles

| Boundary | Actual final-run evidence | Scope/limit |
|---|---|---|
| Unit/MVC |957 Surefire tests; strict readers, centralized publication/basic/fact validators, service and controller slices | Mocks/slices alone are not DB/storage/production proof |
| Repository/mapping/commit | AdminCatalogRepositoryIT7; AdminCatalogMigrationIT9; CatalogConstraintsIT8; current mappings and legacy migrations | Real disposable PostgreSQL17; conditional claims, missing gate, refresh and deferred rollback |
| Product/variant/taxonomy services | AdminProductServiceIT6; AdminVariantServiceIT5; AdminTaxonomyServiceIT7 | Committed lifecycle, variantless Draft, stock0 Publish, prices/SKU, optional fields, category applicability/dependencies and Brand identity |
| Aggregate/taxonomy/identity concurrency | AdminCatalogConcurrencyIT36 | Real transactions and observed PostgreSQL waiters; barrier spies call real SQL, no substitute locks/commit or automatic stale retry |
| Actual Admin HTTP/filter chain | AdminCatalogApiIT14 | Actual login bearer/session/account/filter chain via full-context MockMvc plus real PostgreSQL/LocalStack;31 routes (30 Admin + alias),217 identity-route observations; dedicated PUT races/late rollback. This is not a claim of315 separate network HTTP cases |
| Dedicated specifications | Four PUT-vs-price/disable forced HTTP schedules plus stale-token/deferred-failure/reload case | Endpoint-level proof separate from shared-service/PATCH tests |
| Storage/media | AdminGalleryServiceIT6; ProductImageAttachmentIT2; ImageCompletionConsistencyIT8; ImageObjectReplacementIT4; ImageUploadFlowIT3; ReviewImageAttachmentIT1; PackagedImageFlowIT1; UserImageApiIT5 | Real LocalStack-backed object/ownership/type/byte/copy-failure/reference safety; local signers in other suites do not replace these |
| Public interleavings | AdminCatalogPublicEligibilityIT2; ProductSearchRepositoryIT23; other catalog API/read suites | Disabled/withdrawn exclusions, same-variant filters, detached gallery not signed after credential-preparation pause |
| Commerce | CheckoutConcurrencyIT67; Cart/Checkout/Order/Inventory/Wishlist/Review owning suites | Authoritative price, idempotency, exact snapshots/reservations, quantity bounds, rollback; no stock-adjustment API |
| End-to-end | AdminCatalogFlowIT1; AdminCatalogConsumerCompatibilityIT4; CartFlowIT2; CheckoutFlowIT1 | Lifecycle/commerce retention plus genuine socket HTTP/session/quantity/checkout replay; local signing remains explicitly narrower than real object checks |
| AI service HTTP | RecommendationHttpIT1: `actualTwoServices1500ProductsAndOutage`; RecommendationIT12 | Genuine local two-service1500-candidate request and separately asserted outage/fallback; no replacement mock or fallback-as-success claim |
| Auditor/preflights | CatalogCompatibilityAuditIT, CatalogAllocationPreflightIT, CatalogLocalReferenceIT | Actual V27-only PostgreSQL, SELECT-only role/read-only transactions, shared applicability projection, unchanged data/schema, allocation failures and repeatable restore |

These owning counts are overlapping subsets of862 IT, not extra tests added to1819.

## Current full-run performance

All five existing query/performance suites and the Admin observation test ran normally. Public list≤6/search≤8/detail≤5 and original workload/latency budgets were not relaxed. Native JDBC counting is calibrated; actual EXPLAIN/BUFFERS plans and raw samples are retained in `demo/target/` and protected `full-performance/`, separately from earlier T075 measurements.

| Current full run | Measured p95 ms / statements | Approved boundary |
|---|---|---|
| Synthetic1500 browse / fuzzy / phrase / exact SKU / missing / combined |24.31 /334.48 /367.58 /274.80 /280.74 /228.30;≤6 data statements observed | Existing serial≤1000ms scenarios;20 warmups/100 samples each |
| Frozen1500 source browse / broad / fuzzy / selective / brand-category / combined / price-spec |59.09 /751.95 /791.97 /416.29 /9.37 /137.93 /25.44;≤6 data statements | Existing scenario budgets500/1500/1500/1000/500/500/500ms |
|10-reader synthetic diagnostic |2496.61ms | Diagnostic only, no invented concurrent SLA; not averaged with prior3627.95ms run |
| Query plans/skew | SearchQueryPlanIT2 PASS, including10000 Products and1/5/20-variant skew | Existing exhaustive membership/ranking oracle retained |
| Admin reads at1/20 Products | list1/detail12 statements at both sizes | Observation only, no new Admin SLA |

## Failure history and boundaries

The first unrestricted attempt was ABORTED/FAILED after four additional legacy fixture scopes produced24 errors (partial XML562 IT,0 failures/skips), despite957 passing Surefire tests. BehaviorIT/CartFlowIT/CheckoutFlowIT positive Published fixtures needed valid galleries; ReviewHistoryIT's physical-delete drill needed its own image metadata removed first. All assertions remained; owning25 IT passed, closure markers were revalidated, and **this fresh full run passed all four scopes again**. Initial test-authoring compile errors and earlier migration failures remain historical, never combined/relabelled as this PASS. Expected SQL/Flyway errors in negative rollback tests are asserted successful test cases, not unexplained suite errors.

**G1 VERIFIED only for the recorded frozen synthetic demo snapshot. G2 VERIFIED for the pinned corrected source/rehearsal. G3 BLOCKED.** No official demo V28 migration, target post-migration proof or deployment occurred. During finalization the six preserved named local containers are stopped; live target version was not re-queried or restarted. Its last verified state is V27/read-only. C01 OPEN, owner UNASSIGNED, production release BLOCKED. Final documentation changes do not alter tested application/test/migration/configuration source.
