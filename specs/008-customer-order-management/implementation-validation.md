# Customer Order Management — Implementation Evidence

Started 2026-11-17; completed 2026-11-18. This document records actual work and checks, not assumed completion.

## Final results

Implemented the three approved routes: private list/filter/sort/pagination, complete owned historical detail and eligible cancellation. PENDING/CONFIRMED cancel atomically; valid CANCELLED repeats return 204 preserving the winning timestamp/reason. Receipt confirmation and all excluded creation/payment/shipping/inventory workflows remain absent.

| Gate | Actual result |
| --- | --- |
| Feature unit/MVC checks | 64 tests, 0 failures/errors/skips; seven test classes discovered |
| Feature PostgreSQL/API/flow checks | 23 tests, 0 failures/errors/skips; seven integration classes discovered |
| Full Maven unit/MVC phase | 486 tests, 0 failures/errors/skips, from the full-run Maven summary |
| Original full Maven integration phase | 447 tests executed; 444 passed and 3 failed solely on obsolete exact default-permission expectations |
| Corrected permission regressions | All 18 tests in RegistrationLoginApiIT, user.integration.UserServiceIT and user.repository.UserRepositoryIT passed a targeted Failsafe rerun |
| Final integration XML report set | 447 tests across 100 classes, 0 failures/errors/skips, combining the full run and affected-class rerun |
| Existing catalog performance benchmarks | Both benchmark classes passed during the full run; no policy or timing thresholds changed |
| Packaging | demo/target/demo-0.0.1-SNAPSHOT.jar generated during the full run |

**Run distinction**: `mvnw.cmd verify` ran once and exited with FAILURE because those three legacy assertions omitted the newly required MANAGE_ORDER. Only their exact expected sets were updated, retaining containsExactlyInAnyOrder and every existing permission. All three affected classes were rerun successfully. The 19:23 full command was not repeated after these test-only corrections; no production code changed after that full run. This is not a claim of a second all-green full command.

Primary logs: demo/target/order-full-verify.log and demo/target/order-permission-regressions.log. Surefire/Failsafe feature XML reports were inspected for real, nonzero discovery and zero failures. Full-phase unit counts use Maven's actual summary rather than summing persistent report directories from earlier runs.

### Reproduction commands (PowerShell from demo/)

```powershell
.\mvnw.cmd '-Dtest=OrderValidatorTest,OrderConverterTest,OrderServiceImplTest,OrderReadServiceImplTest,OrderControllerTest,OrderRequestReaderTest,CatalogOrderReadServiceImplTest' test
.\mvnw.cmd test-compile failsafe:integration-test failsafe:verify '-Dit.test=OrderRepositoryIT,OrderItemRepositoryIT,OrderMigrationIT,OrderServiceIT,OrderControllerIT,OrderFlowIT,CatalogOrderReadServiceIT'
.\mvnw.cmd verify
.\mvnw.cmd test-compile failsafe:integration-test failsafe:verify '-Dit.test=RegistrationLoginApiIT,UserServiceIT,UserRepositoryIT'
```

Feature commands above also describe the reproducible combined selections; execution during development used the focused subsets recorded in the chronological notes. The full and permission-rerun commands were executed exactly as shown.

### Success criteria evidence

| Criterion | Executed proof |
| --- | --- |
| SC-001 | Real-bearer OrderControllerIT traverses 125 owned/25 foreign orders, all statuses/four sorts/sizes1/20/100; unit/repository tests verify count-first boundaries, exact long arithmetic and native long-offset binding without billion-row fixtures |
| SC-002 | MVC/security/real-session cases enforce current permissions, custom-role denial and inactive/revoked identity; unknown/foreign detail and cancellation share safe ORDER_NOT_FOUND |
| SC-003 | OrderServiceIT commits address/catalog name/SKU/options/price changes and valid catalog deletion; every historical snapshot/amount stays intact while deleted references become null |
| SC-004 | Eligibility/clock matrix, unchanged rejected/repeated fields, observed pg_stat_activity lock overlap from independent connections, exactly one Hibernate update and safe timeout rollback; original cancellation reason/time never refreshed |
| SC-005 | Dedicated three-row previews/counts exclude address/full prices; complete five-row and105-row detail retains all rows; optional image prepare/sign/close failures degrade safely |
| SC-006 | Real-session flow uses fixtures directly; exactly three order routes registered; attempted customer completion changes nothing; no creation/checkout/inventory/payment/shipping services required |

Measured order-data statements: empty count shortcut exactly1, populated list exactly3 at sizes1/20/100 with <=3 previews/order, detached detail exactly2 after catalog references disappear. Catalog/authentication statements are separate from this order-data envelope. No additional order latency/load SLA is claimed.

### Scope and review

- V23 is additive, no demo order inserts, no prior migration edits. Default USER receives MANAGE_ORDER without reassignment; current grants apply on the next request.
- Read seeds finish their read-only transaction before catalog image locking/signing. Mutation locks only an owned order and performs no image/network/excluded side effect.
- Existing global cart advice intercepts media exceptions; the order-only 415 mapping is placed at its existing ApiErrorFactory delegation point. Cart advice is unchanged.
- Only latest-version expectations and the disposable V24 recovery successor were adjusted in the already-dirty catalog migration tests; deliberate cart V21/V22 upgrade targets remain unchanged. Three registration/user expected-grant sets were updated as described above.
- Baseline auth/cart diff comparison passed: original cart route changes retained, with only the approved order matcher additions. Other pre-existing cart source/test edits were not modified by this work.
- No dependencies/build plugins/CI, deployments, commits or pushes added. No new static-analysis tooling was installed or claimed as executed. Reviewer-owned checklist markers were not changed.
- No unresolved test failure remains in the final report set. No receipt-confirmation implementation or placeholder was introduced.

## Baseline (T001–T002)

- requirements.md: 16 checked, 0 unchecked; reviewer-owned gate unchanged. No extension hooks exist.
- Existing dirty cart production/test files and V22 are retained. AuthSecurityConfiguration, CatalogMigrationsIT and CatalogSearchMigrationIT already contain cart edits; order integration must add to their current contents.
- Existing ignore files cover target/classes/jars/logs/env/editor/OS artifacts; no new dependency/build/CI or ignore change is needed.
- Java 25.0.4.1 and Docker 29.8.0 verified outside sandbox. Sandboxed Java initialization hung; subsequent Java/Maven/Docker checks run through approved elevated execution.
- Existing Maven Failsafe binds integration-test/verify. Test profile uses disposable PostgreSQL17 Testcontainers JDBC, Flyway and ddl-auto=validate. Actual discovery/results will be recorded below.
- V23__customer_order_management.sql is available after existing V22. No shared database was queried; isolated migration tests will verify clean/upgrade history.

## Chronological execution notes

The entries below record intermediate work, including failures and subsequent passing checks. References to a check being pending describe that point in the sequence; the final results above govern completion status.


T003–T008: migration test first failed on missing orders; V23 then passed OrderMigrationIT (1 test, no failures) through Failsafe after correcting a message assertion. Mapped immutable historical entities and added fresh order factories/fixtures. Logs: demo/target/order-migration-red.log and order-migration-green.log. Command: mvnw.cmd test-compile failsafe:integration-test failsafe:verify -Dit.test=OrderMigrationIT.


T009–T013 foundation gate passed: 6 catalog image unit tests, 2 catalog PostgreSQL IT cases including competing unpublish, and the migration IT. Storage prepare/sign/close fallback and database error propagation covered; context/entity schema validation succeeded. Corrected throwing-mock restubbing and used valid catalog child-first deletion, without weakening catalog FKs. Focused logs: demo/target/order-foundation.log and order-catalog-it.log. Added safe domain exceptions, exact MANAGE_ORDER matchers and immutable prepared seeds.


T014–T022 passed: 23 unit/MVC cases and 2 PostgreSQL repository cases in order-us1-focused.log. Custom native long-offset SQL, ordinary JPA projections and window previews verified. T023 service acceptance: 2 cases pass, including exactly one count statement for shortcut and exactly three order statements at page sizes1/20/100. HTTP corpus first run correctly rejected its accidentally blank filter; corrected the harness to omit status for unfiltered retrieval, rerun pending in order-us1-http.log.


US1 checkpoint T023–T024 passed: order-us1-http.log has 2 real-bearer HTTP cases (all five status filters plus unfiltered; four sorts; sizes1/20/100; exact traversal and metadata; next-request same-token grant changes). OrderServiceIT has 2 passing query-bound cases. List-only MVP verified directly from fixtures, no creation/detail/cancellation endpoint used.


US2 T025–T033 complete. order-us2-red.log established missing detail contract; order-us2-focused.log passed32 unit/MVC +4 repository cases; order-us2-acceptance.log passed7 full-stack cases including prior list traversal, safe detail404, committed profile/catalog edits/deletion, signer failure,105-item detail and2-query detached detail. No current catalog values replace snapshots; nullable FK cleanup verified without weakening catalog constraints.


US3 integration adjustment: existing global CartRequestBodyAdvice catches unsupported-media exceptions for every domain and delegates non-cart paths to ApiErrorFactory. A GlobalExceptionHandler-only415 mapping was bypassed. Added a narrowly order-path-specific mapping in ApiErrorFactory instead, preserving existing cart advice/behavior; T042 target updated accordingly. First focused cancellation run:57 of58 checks passed;415 integration exposed this issue and is being rerun.


T034–T042 passing focused run:58 unit/MVC cases,9 full-stack list/detail/cancel checks in order-us3-focused.log. Strict JSON/media and500-code-point Unicode tests pass. Eligible clock-before-placement409 and repeated204 without Clock sampling verified at unit/committed-service boundaries. Mutation uses the owned row lock and REQUIRED/READ_COMMITTED service proxy. T043 competing-lock and HTTP matrix verification is in progress; no concurrency claim yet.


US3 T043–T044 passed13 full-stack acceptance cases (order-us3-acceptance.log). Real pg_stat_activity Lock observations proved2 competing independent cancellation transactions overlap, both eligible initial statuses yield2 successes with exactly1 Hibernate entity update, original winner snapshot/reason retained, and bounded held-lock timeout maps409 with zero losing fields committed. Real HTTP status matrix, malformed repeats and clock409/repeated204 behavior pass. Pool4, lock timeout5000ms; worker futures bounded and all held connections cleaned.


T045: OrderFlowIT3 cases pass (real sessions/logout, custom-role grant/account state, exactly3 routes and no completion). Strengthened migration test passes including pre-existing user roles/cart preservation and500/501 Unicode database reason checks (order-migration-final.log). Its customer-deletion assertion clears unrelated fixture role/cart references first to isolate the order FK. T046: only latest migration expectations changed22->23 and disposable recovery successor23->24 in already-dirty catalog tests; deliberate cart V21/V22 upgrade targets unchanged. New code uses explicit types/no wildcard imports.

## Final review (T049)

All 49 sequential task IDs are complete. Relative Markdown links resolve; requirements checklist remains 16 checked/0 unchecked. Scoped route, pagination, migration, cross-domain image boundary and regression evidence reviewed against the approved artifacts. git diff --check passes. Temporary auth baseline comparison artifact removed after its preservation check. No .specify/extensions.yml exists at the mandatory post-implementation hook check; no hooks to dispatch. No further application change or broad rerun followed the successful permission-class rerun.
