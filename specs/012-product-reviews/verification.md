# Product review implementation verification

## Environment and baseline — 2026-11-21

- Actual branch: feature/voucher. Existing unrelated changes: .gitignore, docs/README.md and untracked docs/openapi-validation.json, docs/openapi.json, docs/validate_openapi.py. Preserve them.
- Direct installed JDK: C:/Program Files/Java/jdk-25.0.4.1. `java -version` via Oracle javapath stalled; direct executable reports Java 25.0.4.1. Wrapper `mvnw.cmd -version` with JAVA_HOME set reports Maven 3.9.16 and Java 25.
- Docker initially had no Linux engine pipe. Started installed Docker Desktop hidden for Testcontainers verification; engine readiness remains to be verified.
- Existing pom binds Failsafe integration-test/verify. New test classes follow Test/IT discovery conventions; actual reports must be checked.
- Existing Git/Docker ignore files cover target, logs, credentials and environment files; no ignore edits needed.
- Requirements checklist: 16 checked, zero unchecked. No extension hooks installed.

## Verification policy

Record exact commands, selectors, tested working-state scope, fresh report counts and failures. Target changed behavior first, run cross-domain checkpoints and one final unrestricted verify when the environment permits. No unexecuted or failed checks count as passed. This record is updated as work proceeds.

## Foundation evidence

- Red schema check: `mvnw.cmd -Dit.test=ReviewMigrationIT test-compile failsafe:integration-test failsafe:verify` reached PostgreSQL and failed the expected assertion (expected 2 review tables, actual 0).
- Initial foundation: `mvnw.cmd -Dtest=OrderReviewReadServiceImplTest,UserReviewReadServiceImplTest,ReviewImageAccessTest,ReviewConverterTest -Dit.test=ReviewMigrationIT,ProductReviewRepositoryIT test-compile surefire:test failsafe:integration-test failsafe:verify` passed; 2 repository/migration IT tests, plus selected unit reports. This run predates the added shared-service ITs and upgrade scenario.
- Extended checkpoint: selected shared-service ITs ran, but 4 errors occurred during fixture cleanup, not the tested service calls. Existing deferred order-total integrity correctly rejected isolated child deletion. Cleanup now groups review/reference/item/order/user deletion in a single transaction; no production constraints were weakened.
- Affected rerun includes ReviewMigrationIT (including V26 upgrade), OrderReviewReadServiceIT, UserReviewReadServiceIT and ReviewImageAttachmentIT. Result pending; do not count checkpoint A as passed until these complete.

- Cleanup transaction fix verified: the next foundation run executed 9 IT tests; 8 passed, with only the new upgrade-isolation test failing. All order/user/media tests passed, as did persistent review uniqueness, rating and comment bounds.
- Upgrade fixture correction: historical V15 creates public functions, so migration replay in another schema collides with existing functions. The test now uses a separate disposable PostgreSQL database following existing OrderMigrationIT conventions. Applied migrations remain unchanged. Earlier attempt also held a test transaction during Flyway; the upgrade method now explicitly runs outside the test-managed transaction.
- Affected rerun: `mvnw.cmd -Dit.test=ReviewMigrationIT test-compile failsafe:integration-test failsafe:verify`, log target/review-upgrade-fixed.log. Result pending.

Checkpoint A completed: all selected shared-service ITs and repository checks pass, plus the isolated upgrade rerun (2 tests, zero failures/errors). Unit selector reports from review-foundation.log are passing. Upgrade result is attributed to the corrected test state; earlier failed runs remain documented. No full-suite success is claimed.

## Story checks before final integration

- Creation: `mvnw.cmd -Dtest=ReviewValidatorTest,OrderItemReviewControllerTest -Dit.test=ReviewServiceIT,ReviewConcurrencyIT test-compile surefire:test failsafe:integration-test failsafe:verify` passed: 22 unit/MVC tests and 3 IT tests, zero failures/errors. Validates strict JSON, comment Unicode boundaries, private creation response, authorization, persisted uniqueness and competing creates.
- Reads: `mvnw.cmd -Dtest=ProductReviewControllerTest,ReviewStatisticsServiceImplTest -Dit.test=ProductReviewControllerIT,ReviewReadServiceIT test-compile surefire:test failsafe:integration-test failsafe:verify` passed: 9 unit/MVC tests and 3 IT tests. Covers anonymous publication/privacy, summary rounding, 100 seeded reviews with deterministic traversal and <=8 SQL statements at page sizes 1/20/100.
- Updates: `mvnw.cmd -Dtest=PatchReviewValidatorTest,ReviewControllerTest -Dit.test=ReviewUpdateServiceIT,ReviewControllerIT,ReviewConcurrencyIT test-compile surefire:test failsafe:integration-test failsafe:verify` passed: selected unit/MVC and 7 IT tests. Includes disjoint competing edits, no-op timestamps, archived/deleted links, reordered/detached image references and rollback after a deliberately introduced test-only reference constraint fails. The constraint is removed in finally; production constraints remain intact.
- These results precede final integration refinements: author projections now return safe avatar references so review services resolve avatars and review images in one media batch/signer. The final run must validate that working state.
- Checkpoint B command: `mvnw.cmd -Dtest=*Review*Test,OrderControllerTest,ProductListingControllerTest,UserServiceImplTest,UserImageServiceTest -Dit.test=ReviewServiceIT,ReviewConcurrencyIT,ReviewUpdateServiceIT,ReviewControllerIT,ProductReviewControllerIT,ReviewReadServiceIT,ProductReviewReadRepositoryIT,ReviewFlowIT,ReviewHistoryIT,OrderCompletionServiceIT,UserImageApiIT,ImageUploadFlowIT test-compile surefire:test failsafe:integration-test failsafe:verify`. Uses real disposable LocalStack upload/completion/download in ReviewFlowIT and existing ImageUploadFlowIT. Results pending.

## Integration checkpoint and audit

- Checkpoint B: 116 unit/MVC/regression tests passed. IT batch executed 28 tests: 27 passed; one history fixture failed because existing catalog rules restrict product deletion before variants and require at least one variant until product deletion commits. ReviewFlowIT (real LocalStack/S3 HTTP), ImageUploadFlowIT, OrderCompletionServiceIT and UserImageApiIT all passed. Overall command failed and is not represented as a passing full run.
- Follow-up targeted review batch: 30 IT tests, 29 passed, one same history fixture failed because variant removal and product removal were separate commits. Fixed fixture to perform both deletes in one transaction, honoring deferred catalog integrity. No production catalog constraint/migration changed. Affected history test is being rerun.
- New concurrency tests passed (6 in the targeted run): competing creates, disjoint patches, image-order/rating edits, in-flight inactive-state check, concurrent product-reference loss and safe lock-timeout conflict. Fresh rollback tests also passed.
- Source audit: review imports only review repositories; order/user/media services access their own repositories. Converters use supplied values only. Writer/user/order/item/media lock order is consistent; orchestration prepares storage outside write locks and now rejects ambient transactions. Common error mapping recognizes only the exact review uniqueness constraint without adding a common→review dependency. No new dependencies, grants, deletion endpoints, counters or deployment configuration.
- Base revision is f7d1f7e6c9731ba2453bbea5791f764ac4b5a48a; tests cover the uncommitted review working state, not a new commit. Recent final refinements and new acceptance cases require the pending affected rerun and unrestricted verification below.

## Final verification

- Final affected rerun passed: 24 unit tests and 16 IT tests, zero failures/errors (target/review-last-fixes.log). Includes corrected historical catalog removal, 10 ordered images without a comment, persistent reference bounds/uniqueness and the final ambient-transaction guard.
- Unrestricted final command: JAVA_HOME set to installed JDK 25, then ./mvnw.cmd verify from demo/, with no selectors, skips or exclusions. Log: target/review-unrestricted-verify.log. Result pending.
- Source fingerprint for this run: b5e1165afc5ffc39dbba3cb2ae9dbd94c854cdc0e6d5a263cc506a4a13163429 (847 source/resource/test files plus pom.xml). Source must remain unchanged until completion; documentation/task progress updates do not alter this fingerprint.

## Acceptance and requirement reconciliation

| Requirements/outcomes | Implementation and evidence |
| --- | --- |
| FR-001, FR-002, SC-001 | Three controllers expose exactly four approved operations; create service/SQL enforce owned COMPLETED item and persistent uniqueness. ReviewServiceIT covers all five rejected states, quantity>1 fixture, repeated purchases and 10-image optional content; ReviewConcurrencyIT covers duplicate create and active-state/product-link races. No review DELETE mapping. |
| FR-003, FR-006, SC-006 | Strict create/patch DTO binding, normalized Unicode code-point validation, presence flags and shared validators. Unit/MVC boundaries cover 1,999/2,000/2,001 supplementary characters, missing/null/coercion/unknown fields and empty patch. ReviewUpdateServiceIT checks omitted/clear/no-op/timestamps, historical edits and rollback. |
| FR-004, FR-005, SC-002 | Owning media service prepares uploads and verifies objects outside writer locks, rechecks owned COMPLETED metadata inside, and persists ordered references atomically. Media, schema, service and real ReviewFlowIT checks prove readiness, ownership, ordering, max count, duplicate protection and retained S3 objects after detach. |
| FR-007, FR-009, FR-010, SC-005 | Safe author projection and pure converter; inactive states mapped to Former customer without identity/avatar. Anonymous published GETs and unpublished/removed 404 validated through MVC/API/history tests. Unknown/unready/signer-unavailable media retains safe null URL; no per-image existence checks on listing. |
| FR-008, SC-003 | COUNT/AVG from committed review rows, HALF_UP two decimals and zero/null convention; no cache/counters. Repository/statistic unit/API/independent-transaction tests verify rounding, count retention on edit, repeated purchases and exclusion of uncommitted data. |
| FR-011, FR-013, SC-004 | Pageable parent queries and batched references/authors/media, deterministic ID tie-breaker, strict parameter parser, out-of-range empty pages and application page DTO. Read IT checks 100 reviews, complete tie traversal and <=8 SQL statements for 1/20/100 rows; source audit confirms no converter queries. |
| FR-012 | Short writer transaction, user→order→item→review→sorted media locks, no automatic retry or network under writer locks. Six concurrency cases and injected persistence-failure rollback are passing. |

Applicable quickstart acceptance scenarios were exercised through isolated unit/MVC/PostgreSQL/real-LocalStack tests. The interactive Read-Host/Compose smoke script was not run against the developer's local application/data; it remains a reproducible manual guide rather than claimed evidence. Final unrestricted results below govern release readiness.

## Full-suite progress

Unrestricted Surefire phase passed: 690 tests, zero failures/errors/skips. Failsafe integration phase is running. No full-suite success is claimed until it finishes and reports/package are inspected.

Full-suite source snapshot had two migration-test compatibility failures: CatalogMigrationsIT expected 26 applied migrations, and CatalogSearchMigrationIT reserved synthetic V27, now occupied by the real review migration. Updated the count to 27 and made the synthetic recovery version derive from the latest applied production version. Production implementation remains unchanged. These test-only changes were made after the unrestricted run compiled its snapshot; final reports and affected rerun will be recorded separately. No repeated full benchmark run is needed for these localized test-fixture corrections.

Unrestricted run finished: 690 unit/MVC tests passed; 699 integration tests executed with 10 failures, zero errors/skips. Every failure was a stale migration-test assumption introduced by adding V27: CatalogMigrationsIT count, CatalogSearchMigrationIT synthetic V27 collision, seven InventoryMigrationIT cases sharing a max-version assertion, and VoucherMigrationIT max-version assertion. Catalog count and dynamic successor fixes passed an isolated two-test rerun. Updated inventory/voucher expectations to 27; their batch rerun is pending. No production implementation changes were needed after the full run. JAR packaging completed.

## Final outcome — 2026-11-21

- Unrestricted `mvnw.cmd verify` executed normal discovery and packaging: 690 unit/MVC tests passed; 699 IT tests ran with 10 migration-fixture failures, zero errors/skips. The original unrestricted command remains recorded as failed for that pre-fix test snapshot; it is not relabeled a passing full run.
- All ten reported failures were corrected without changing production implementation, schema or integrity rules. CatalogMigrationsIT now expects 27 applied migrations; CatalogSearchMigrationIT chooses its synthetic successor after the latest production version; inventory/voucher migration tests expect version 27.
- Affected reruns passed: 2 selected catalog migration tests, then all 17 inventory/voucher migration tests, zero failures/errors/skips. Fresh reports contain no unresolved failure. Logs: target/review-migration-compatibility.log and target/review-migration-final.log.
- No production source changed after the unrestricted run. Four test compatibility files changed. Full verification was not repeated because these localized test-only fixes have complete targeted PostgreSQL evidence and do not justify repeating unrelated benchmarks (constitution XIII). Original and final source fingerprints remain separate.
- JAR packaging completed at demo/target/demo-0.0.1-SNAPSHOT.jar. No deployment, commit or push was performed. Existing unrelated OpenAPI/documentation edits were preserved.
- FR-001–FR-013 and SC-001–SC-006 are reconciled above; all planned implementation tasks are complete. The interactive developer-environment smoke script remains documented rather than claimed as executed; its applicable acceptance cases are covered by isolated automated API/flow tests.
- No after_implement hooks are installed. Verification-summary.json records commands, scopes, counts and the final source fingerprint.
