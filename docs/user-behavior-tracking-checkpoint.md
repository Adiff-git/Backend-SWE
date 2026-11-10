# User behavior checkpoint
Branch: draft/version-1; approved base b0a22bc (Search & Filter merged). Feature branch features/UserBehavior is preserved.
Phase: COMPLETE — implementation, full verification, local no-ff merge and post-merge regression passed.
Feature commit: 2b089ce. Merge commit: eafdc0b.
Completed: V20 schema/invariants/indexes/cascades; Customer ingestion/history; concurrent idempotency; atomic Wishlist hooks; admin SQL analytics; 90-day cleanup; stable DTOs/OpenAPI; frontend/AI/privacy contract.
Files: behavior main package (13), behavior tests (3), V20, AuthSecurityConfiguration, WishlistServiceImpl, WishlistServiceImplTest, CatalogMigrationsIT, CatalogSearchMigrationIT, application-test.properties, three user-behavior docs (26 total).
PASS: mvn -q -f demo/pom.xml clean verify — 346 unit tests, 384 integration tests; zero failures/errors/skips. Reports: demo/target/surefire-reports and demo/target/failsafe-reports. Execution log: /tmp/behavior-final-verify.log.
PASS: BehaviorServiceTest 4; BehaviorIT 20 (real JWT security, ownership, malformed/strict input, concurrent retries/conflicts, Wishlist rollback/failure, history/analytics/retention, product/user deletion, OpenAPI, 1500 events); BehaviorMigrationIT 1 (V19 upgrade preserving history/data).
PASS: all existing auth/catalog/detail/search/filter/wishlist/migration/performance regression, serial disposable PostgreSQL 17/LocalStack. OpenAPI YAML references and six JSON Schema examples validated.
Earlier failures resolved: account-deletion test fixture ordering; migration count updated to 20; transient recovery fixture moved from V20 to V21. Historical production migrations untouched.
Review: exact 26-file scope; staged diff and whitespace reviewed; secret-pattern scan clean; no env/credential files staged. Existing root docs ignore requires adding only the three exact feature docs explicitly.
PASS post-merge: 13 unit tests + 74 integration tests; zero failures/errors/skips; BUILD SUCCESS. Log: /tmp/behavior-post-merge.log.
Remaining Backend tasks: none. This final checkpoint is committed separately as documentation only.
Exact next action: no further Backend implementation. Frontend instrumentation/service-authentication/AI consumption require separate integration work.
Post-merge command: mvn -f demo/pom.xml -Dtest=BehaviorServiceTest,WishlistServiceImplTest -Dit.test=BehaviorIT,BehaviorMigrationIT,WishlistControllerIT,WishlistServiceIT,ProductSearchControllerIT,ProductDetailApiIT,CatalogMigrationsIT,AuthenticationFlowIT,BearerAuthenticationApiIT verify
Deferred: frontend event instrumentation; service identity and AI-side consumption. Internal cross-user endpoint intentionally absent; secure owner history available. No push/deploy; AI-SWE/Catalog-Data unmodified.
