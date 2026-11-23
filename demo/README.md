# Backend-SWE — Admin Catalog Management handoff

Feature013 implements UC-031–UC-036 with30 operations under `/api/admin/catalog`, reusing existing JWT/current-session `ROLE_ADMIN`, catalog, media and commerce modules. Feature scope excludes other Admin roadmap APIs, stock adjustments, frontend and UC-038 import.

- [Implemented request examples and legacy attachment upgrade](../specs/013-admin-catalog-management/quickstart.md)
- [Authoritative Admin API contract](../specs/013-admin-catalog-management/contracts/admin-api.md)
- [Revision/transaction protocol](../specs/013-admin-catalog-management/contracts/consistency.md)
- [Verification evidence](../specs/013-admin-catalog-management/final-verification.md)
- [Local reference/disposable workflow](../specs/013-admin-catalog-management/local-verification-guide.md)
- [Future rollout and post-migration checklist](../specs/013-admin-catalog-management/rollout-readiness.md)

## Build and test

Use Java25, the existing Maven wrapper and isolated PostgreSQL17/Testcontainers/LocalStack. The real Recommendation HTTP test also needs the existing compatible AI-SWE Python/uv runtime. From this directory:

```bash
UV_NO_SYNC=true bash ./mvnw clean verify
```

The preserved final run passed957 Surefire and862 Failsafe tests with no failures/errors/skips and produced `target/demo-0.0.1-SNAPSHOT.jar`. Normal discovery includes real PostgreSQL rollback/concurrency, current JWT filter-chain, LocalStack, genuine1500-candidate AI HTTP, socket commerce flows and existing public query/performance suites. Selected earlier checkpoints are not substituted for that unrestricted result.

## Deployment boundary

The official synthetic demo database remains V27/read-only. The current application maps V28 fields and may run Flyway at startup: **do not start it against that demo or an existing developer database without separate target authorization and G3 readiness**. G1 is verified only for the recorded frozen synthetic demo snapshot; G2 covers the corrected V28 disposable rehearsal/restore. They are not deployment permission. Read the rollout handoff before using Compose/application defaults; do not infer an authorized target from environment variables.

Use current decimal-string Product/taxonomy revisions for every existing-Product/child mutation. Reload on stale409; do not auto-retry. The legacy attachment route now also requires these revisions. Draft may have no variants; Publish requires valid Enabled choices and primary gallery, **not positive stock**. Restore is Archived→Draft; enable does not republish. Optional Brand/description omission, null and empty values have distinct semantics.

Trusted external import/SQL writers need a coordinated compatibility handoff; no import feature is supplied. Historical Orders/receipts and allocation identities remain protected. C01 production CORS remains OPEN, owner UNASSIGNED, production release BLOCKED. No target migration/deployment is performed by this documentation.
