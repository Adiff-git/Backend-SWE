# User behavior checkpoint
Branch: features/UserBehavior; approved base draft/version-1 b0a22bc (Search & Filter merged).
Phase: implementation and pre-merge verification COMPLETE; local integration pending.
Completed: V20 schema/invariants/indexes/cascades; Customer ingestion/history; concurrent idempotency; atomic Wishlist hooks; admin SQL analytics; 90-day cleanup; stable DTOs/OpenAPI; frontend/AI/privacy contract.
Files: behavior main package (13), behavior tests (3), V20, AuthSecurityConfiguration, WishlistServiceImpl, WishlistServiceImplTest, CatalogMigrationsIT, CatalogSearchMigrationIT, application-test.properties, three user-behavior docs (26 total).
PASS: mvn -q -f demo/pom.xml clean verify — 346 unit tests, 384 integration tests; zero failures/errors/skips. Reports: demo/target/surefire-reports and demo/target/failsafe-reports. Execution log: /tmp/behavior-final-verify.log.
PASS: BehaviorServiceTest 4; BehaviorIT 20 (real JWT security, ownership, malformed/strict input, concurrent retries/conflicts, Wishlist rollback/failure, history/analytics/retention, product/user deletion, OpenAPI, 1500 events); BehaviorMigrationIT 1 (V19 upgrade preserving history/data).
PASS: all existing auth/catalog/detail/search/filter/wishlist/migration/performance regression, serial disposable PostgreSQL 17/LocalStack. OpenAPI YAML references and six JSON Schema examples validated.
Earlier failures resolved: account-deletion test fixture ordering; migration count updated to 20; transient recovery fixture moved from V20 to V21. Historical production migrations untouched.
Review: exact 26-file scope; staged diff and whitespace reviewed; secret-pattern scan clean; no env/credential files staged. Existing root docs ignore requires adding only the three exact feature docs explicitly.
Remaining: commit feature, no-ff local merge, targeted post-merge regression, record results and commit checkpoint.
Exact next action: git commit -m 'feat(behavior): implement customer behavior tracking'; git switch draft/version-1; git merge --no-ff features/UserBehavior -m 'merge: integrate user behavior tracking'.
Deferred: frontend event instrumentation; service identity and AI-side consumption. Internal cross-user endpoint intentionally absent; secure owner history available. No push/deploy; AI-SWE/Catalog-Data unmodified.
