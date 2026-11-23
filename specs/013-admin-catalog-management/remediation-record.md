# Confirmed Analyze Finding Remediation

**Date/branch**: 2026-11-22, `features/AdminCatalogManagement`. Application baseline `86512d7ab1e0bb262f783c05c0051d6be46b1983`. This is artifact remediation explicitly requested after Analyze; it is not a new Analyze/Implement invocation. No application code/tests/configuration/Flyway migration, database or production environment was changed. All implementation tasks remain unchecked.

## Findings reproduced before editing

| Finding | Artifact defect and source evidence | Resolution in this task design | Remaining execution state |
| --- | --- | --- | --- |
| H01 HIGH | Original tasks T008 depended only on T001; T009/T010 could close before T017. TechnicalSpecificationValidator.java:11-40 validates generic shape; SpecificationFilterRegistry.java:67-83 validates search operands, not persisted applicable facts. application.properties:5-7 enables migration discovery and JPA schema validation at normal startup. | T081 shared schema-independent validator/applicability projection precedes T008 plain JDBC CLI; T086 proves V27/read-only/no-startup compatibility before T004/T009; T017 integrates the SAME core later. audit-preflight-design.md defines exact entry/boundary/proofs. | Design resolved; auditor/validator/safety tests NOT IMPLEMENTED, real target audit NOT RUN/environment dependent. |
| H02 HIGH | Original T013/T014 lacked fixture retrofit. CatalogEnhancementMigrationIT.java:25-31 seeds imageless Published then migrates to latest. CheckoutServiceIT.java:238-243 asserts zero-current-price checkout success. CatalogFixtures.java:56-62 persists Published without adding an image. | T082 classifies positive/negative/historical cases; T083 adapts foundation/migration fixtures before T013/T014; T084 adapts cross-consumer assertions before regression closure, preserving quantity1/999, history, reservation and rollback oracles. | Design resolved; existing tests unchanged and future retrofit/runtime checks NOT RUN. |
| H03 HIGH | Original T021 required typed advice binding to controllers only created by T027/T032/T038/T048/T052. Existing ApiExceptionHandler.java:14 uses a broad package selector; only ProductCatalogController and ProductImageController currently exist. | T021 introduces compiling shared support plus existing legacy-controller-only Admin binding and narrows existing public advice. Each controller task extends exact assignableTypes only AFTER creating its concrete type; short shared-list edits are ordered. | Design resolved; advice/support/controllers NOT IMPLEMENTED; actual precedence/security verification pending. |
| M01 MEDIUM | Original traceability FR-008 omitted child/category/specification validation; FR-025 mapped only fact tasks; FR-027 omitted T058/T063 Cart behavior. Related grouped FR/AC/BR rows contained unrelated references. | Individual substantive FR/AC/NFR mappings rebuilt, BR unions recomputed with unchanged approved BR→FR associations, all30 exact operations retained. FR-008/025/027 now show the relevant multiple responsibilities, not a repeated generic group. | Documentation resolved; task paths still need actual implementation/test evidence at T080. |
| M02 MEDIUM | Original data-preflight.sql:149-162 did not detect managed items missing allocations, quantity/state/time mismatch or all retained/live variant attribution. V25__inventory_management.sql:103-127 and owning Inventory/Order resolution define the missing persisted invariants. | Add mandatory SELECT-only allocation supplement; T004 runs both, T007 reviews all violations, T086 proves SQL behavior later, T010 cannot close without allocation integrity; T088 preserves pre/post evidence. | Design/SQL artifact resolved; SQL was NOT parsed/executed by PostgreSQL, corruption/cleanliness UNKNOWN, data remediation prohibited. |
| M03 MEDIUM | Original T040 promised every route before P2/spec endpoints; T042 was incorrectly mapped as A24/A25 HTTP proof before T056. | T040/T041 cover the available18 operations + alias only; T042 is shared-service/PATCH A21 race proof; T085 adds dedicated A24/A25 HTTP revision/ownership/late-rollback proof after T056; T069 depends on it and all later endpoint suites. | Design resolved; real HTTP/session/race/rollback proof NOT RUN. |
| C01 CRITICAL inherited | SharedCorsConfiguration.java:14-16 has wildcard origins/methods/headers; constitution VI:155-158 requires explicit production origins; plan.md:39 already records this external issue. | production-release-gates.md records OPEN status, Deployment/Security owner UNASSIGNED, required separately authorized remediation + effective-production verification evidence; T087/T079/T080 cannot ignore this production blocker. No security refactor task added. | NOT fixed; external production compliance issue. Safe offline feature work allowed, production release BLOCKED pending assigned owner/actual remediation/runtime evidence. |

Spring conventions were checked in current ApiExceptionHandler/GlobalExceptionHandler and by `javap` on the locally cached spring-web7.0.10 RestControllerAdvice/ControllerAdvice annotations: assignableTypes is Class<?>[], so missing controller references are compile-time dependencies. This is API/source inspection, not proof of an application build or effective deployed Spring version. No package selector or new unscoped high-precedence advice is introduced to bypass this dependency.

## Exact change and amendment boundary

Modified: [tasks.md](tasks.md), [tasks-traceability.md](tasks-traceability.md), [tasks-validation.md](tasks-validation.md).

Added supporting artifacts: [audit-preflight-design.md](audit-preflight-design.md), [contracts/data-preflight-allocation-integrity.sql](contracts/data-preflight-allocation-integrity.sql), [production-release-gates.md](production-release-gates.md), this record.

**Amendment A1, additive preflight contract**: the new allocation supplement extends the approved baseline SQL's section7 with missing V25 relational proof. Existing contracts/data-preflight.sql remains byte-identical. The SQL adds no schema, business policy or repair operation; target/copy identity and READ ONLY/ROLLBACK behavior remain mandatory. Review impact: later T086 must execute valid/invalid snapshot cases, verify allowed retained null references and actual PostgreSQL17 syntax, and T004/T007/T010 must include the new result sets. Its statement count is separate from the historical31-statement baseline validation; neither count establishes runtime validity.

**Clarification A2, auditor execution boundary**: audit-preflight-design.md makes the already required model read-only runner concrete as a non-Spring V27 JDBC CLI sharing pure validation/projection with Admin services. No new endpoint, framework, schema or dependency is introduced; G1 does not depend on JPA startup or future columns. Review impact: T081/T008/T086 must prove no startup/migration/write side effects and parity with current search applicability.

**Sequencing correction A3, advice/tests**: final API/error/lifecycle/revision decisions remain exactly those of approved admin-api.md and consistency.md. The backlog now reaches that design incrementally using existing controller types only and separates early/shared-service from final endpoint proof. No approved response/status or auth rule changes.

spec.md, plan.md, research.md, data-model.md, contracts/admin-api.md, contracts/consistency.md, contracts/data-preflight.sql, testing-strategy.md, quickstart.md, planning-validation.md, approved checklists and constitution remain unchanged. Their original stage-validation statements remain historical, not silently rewritten as current runtime proof. No approved business rule, requirement ID, acceptance oracle, user-story priority or one of the30 API operations changed. Other Admin roadmap features remain separate; stock adjustments/order/customer/sales/settings functionality is not added.

## Backlog delta and execution semantics

Baseline:80 tasks,13 phases,159 declared dependency edges. Remediated:88 tasks,13 phases,199 declared edges;52 OFFLINE and36 DB. Existing T001–T080 retained; no task removed/renumbered. T015 moved ahead of foundation fixture conversion. Added IDs are inserted at their topological execution positions; numeric IDs are stable labels, not scheduling order.

| Added task | Purpose | Phase / execution |
| --- | --- | --- |
| T081 | Shared V27-safe persisted-fact validation/applicability core | Setup / OFFLINE |
| T082 | Inventory/classify legacy positive, negative and historical tests | Foundation / OFFLINE |
| T083 | Adapt foundation/migration fixtures before checkpoint A | Foundation / OFFLINE |
| T084 | Adapt changed consumer assertions/fixtures before regressions | Cross-module / OFFLINE |
| T085 | Dedicated specification HTTP stale revision and rollback proof | US7 / DB |
| T086 | Disposable V27 auditor/SQL safety and allocation cases | Setup / DB |
| T087 | Record external C01 production owner/evidence/blocker | Setup / OFFLINE |
| T088 | Separate post-rehearsal integrity closure | Foundation / DB |

Modified existing task descriptions/paths/dependencies: T004, T007–T010, T013–T014, T017, T021–T022, T027, T032, T038, T040–T042, T047–T048, T052, T057–T059, T063, T066, T068–T070, T072, T077, T079–T080. T058/T059 name the actual Cart/Checkout state and validator/converter touchpoints to prevent a new enabled field from being mistaken for enforced eligibility. Later checks still run all relevant suites and the unrestricted full Maven workflow.

Critical paths and parallel windows are explicit in tasks.md. Audit: T081→T008→T086→both SQL/reviews→T009→T010. Persistence: new migration/entity + classified fixtures→T013→T014→T088→T022. Feature: shared revisions/validation/errors→concrete story endpoints→T085/full security + cross-domain races/consumers→checkpoints B/C→unrestricted verify→separate implementation/DB/production reports. T087 records the external issue, not its runtime fix. T029/T034/T044/T050 retain only isolated test-authoring [P] eligibility after stable shared support; shared advice edits are explicitly ordered; shared test-class changes require an ownership handoff, with T085 explicitly waiting for T041/T042.

## Remaining risks and stop boundary

Real data/schema/history/collisions, object content, allocation integrity, migration duration/locks, backup/restore, API advice precedence, concurrent commit/rollback, real AI HTTP compatibility and existing performance budgets remain unverified. psql/Docker/PostgreSQL/LocalStack/AI availability must be established during separately authorized execution; absent infrastructure is BLOCKED, never PASS. No current dataset cleanliness or production deployment state is inferred.

This remediation makes the confirmed task-design corrections reviewable; it does not certify a fresh Analyze result or unrestricted migration/production readiness. No automatic `$speckit-analyze` or `$speckit-implement` followed. Runtime implementation, audits, data remediation, migration execution, production CORS changes, commit and push all remain outside this invocation.
