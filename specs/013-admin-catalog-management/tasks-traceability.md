# Tasks Traceability — Admin Catalog Management

**Runtime closeout2026-11-23:** original design responsibilities below remain unchanged. Actual implemented sources, discovered tests and business oracles are reconciled in [implementation-acceptance.md](implementation-acceptance.md), with per-class counts in [verification-test-inventory.tsv](verification-test-inventory.tsv) and one final unrestricted run in [final-verification.md](final-verification.md). G1/G2 are scope-bound verified evidence; G3 and C01 remain blocked.

**Updated**: 2026-11-22, confirmed-finding remediation. Rows map actual deliverables and proof responsibilities; they do not claim runtime PASS. [spec.md](spec.md) remains authoritative for every subcase. [remediation-record.md](remediation-record.md) records the additive preflight amendment; the original API and consistency contracts are unchanged.

## Functional requirements

| Requirement | Implementation / safety responsibility | Verification |
| --- | --- | --- |
| FR-001 | T021, T027, T032, T038, T048, T052, T056 | T040, T069 |
| FR-002 | T020, T024, T025, T026, T027 | T023, T028 |
| FR-003 | T015, T019, T025, T027 | T018, T023, T028 |
| FR-004 | T019, T020, T025, T026 | T023, T028, T041 |
| FR-005 | T081, T017, T019, T026, T030, T031, T037, T046, T055 | T018, T028, T033, T039, T049, T057 |
| FR-006 | T026, T058, T059, T060, T061, T062 | T028, T063, T064, T066, T067, T072, T074 |
| FR-007 | T026, T058, T059, T060, T061, T062 | T028, T063, T065, T066, T067, T074 |
| FR-008 | T019, T025, T030, T031, T036, T037, T046, T047, T055 | T018, T028, T033, T039, T043, T049, T057, T070, T085 |
| FR-009 | T045, T046, T048 | T044, T049, T066, T070 |
| FR-010 | T046, T047, T048 | T044, T049, T070 |
| FR-011 | T051, T052 | T050, T053, T071 |
| FR-012 | T081, T017, T045, T046, T047, T051, T060 | T044, T049, T053, T066, T070, T071 |
| FR-013 | T015, T019, T030, T032 | T029, T033, T057 |
| FR-014 | T011, T012, T019, T030 | T029, T033, T071 |
| FR-015 | T020, T019, T030, T059 | T029, T033, T064, T065, T072, T084 |
| FR-016 | T019, T031, T058, T059, T060, T061 | T029, T033, T063, T064, T065, T067, T072 |
| FR-017 | T015, T030, T031 | T033, T065, T072 |
| FR-018 | T035, T036, T038 | T034, T039, T068 |
| FR-019 | T036, T037, T038 | T034, T039, T068 |
| FR-020 | T012, T019, T036, T037 | T018, T034, T039, T043 |
| FR-021 | T012, T016, T037, T062 | T034, T039, T042, T043, T068 |
| FR-022 | T081, T017, T019, T030, T055, T056 | T018, T054, T057, T085, T086 |
| FR-023 | T081, T017, T030, T055, T060 | T054, T057, T066 |
| FR-024 | T019, T025, T030, T055 | T018, T023, T028, T054, T057 |
| FR-025 | T020, T025, T030, T038, T048, T055, T056 | T023, T028, T029, T033, T044, T049, T054, T057, T069, T085 |
| FR-026 | T026, T030, T031, T037, T059, T062 | T013, T065, T072, T074, T083, T084, T088 |
| FR-027 | T058, T059 | T063, T064, T072, T074, T084 |
| FR-028 | T015, T026, T030, T031, T059 | T004, T007, T065, T072, T074, T086, T088 |
| FR-029 | T016, T025, T026, T030, T031, T036, T037, T046, T047, T051, T055 | T022, T041, T042, T043, T070, T071, T072, T085 |
| FR-030 | T020, T021, T027, T032, T038, T048, T052, T056 | T023, T034, T040, T041, T044, T050, T069, T085 |
| FR-031 | T011, T012, T026, T030, T031, T037, T047, T051, T062 | T013, T033, T039, T049, T053, T065, T068, T071, T088 |
| FR-032 | T015, T026 | T023, T028, T065, T074 |
| FR-033 | T019, T031, T058, T059, T060, T061 | T029, T033, T063, T064, T065, T067, T074 |
| FR-034 | T011, T012, T045, T046, T047 | T005, T013, T044, T049, T070 |
| FR-035 | T011, T012, T051 | T006, T013, T050, T053, T071 |
| FR-036 | T015, T026, T027 | T023, T028, T041, T074 |

FR-008 now explicitly joins the ONE validator and product, variant/price, gallery, category/create/delete applicability and specification mutations. FR-025 includes Product Brand/Description omission/null semantics, variant document replacement and explicit collection commands. FR-027 includes Cart T058/T063 as well as Checkout T059/T064 and renewed eligibility. Setup/audit rows represent safety obligations, never implementations of unrelated Admin inventory/order features.

## Acceptance scenarios

| Scenario | Implementation | Verification |
| --- | --- | --- |
| AC-01 | T025, T027 | T023, T028 |
| AC-02 | T020, T025 | T023, T028 |
| AC-03 | T019, T026 | T018, T028, T033 |
| AC-04 | T019, T026 | T018, T028 |
| AC-05 | T019, T026 | T018, T028 |
| AC-06 | T026, T058, T059, T060, T061 | T028, T063, T064, T066, T067, T072, T074 |
| AC-07 | T026, T058, T059, T060, T062 | T028, T063, T065, T074 |
| AC-08 | T020, T024, T027 | T023, T028 |
| AC-09 | T019, T025, T030, T031, T037, T046, T055 | T018, T028, T033, T039, T049, T057, T085 |
| AC-10 | T019, T020, T025, T030 | T018, T028, T029, T033 |
| AC-11 | T030, T058, T059 | T029, T033, T064, T065, T072, T084 |
| AC-12 | T031, T058, T059 | T033, T063, T064, T065, T072 |
| AC-13 | T011, T012, T030 | T029, T033, T071 |
| AC-14 | T019, T031 | T018, T029, T033, T042 |
| AC-15 | T035, T036, T038 | T034, T039, T068 |
| AC-16 | T037, T038, T060 | T034, T039, T066 |
| AC-17 | T037, T038 | T034, T039, T042 |
| AC-18 | T037, T038, T062 | T034, T039, T068 |
| AC-19 | T019, T037 | T034, T039, T043 |
| AC-20 | T035, T036, T038 | T034, T039, T068 |
| AC-21 | T045, T046, T048 | T044, T049, T070 |
| AC-22 | T047, T048 | T044, T049, T070 |
| AC-23 | T047, T048 | T044, T049, T070 |
| AC-24 | T017, T045, T046, T060 | T044, T049, T066, T070 |
| AC-25 | T051, T052 | T050, T053, T071 |
| AC-26 | T051, T052 | T050, T053, T071 |
| AC-27 | T020, T025 | T023, T028, T053 |
| AC-28 | T030, T055, T056, T060 | T054, T057, T066, T085 |
| AC-29 | T081, T017, T019, T055 | T018, T054, T057, T085, T086 |
| AC-30 | T019, T025, T030, T055 | T018, T023, T028, T054, T057 |
| AC-31 | T021, T027, T032, T038, T048, T052, T056 | T040, T069 |
| AC-32 | T016, T025, T030, T031, T036, T037, T046, T051, T055 | T022, T041, T042, T070, T071, T085 |
| AC-33 | T026, T031, T058, T059 | T063, T064, T065, T072, T074 |
| AC-34 | T026, T030, T031, T037, T046, T051, T055 | T065, T072, T074, T088 |
| AC-35 | T012, T016, T021, T035, T036, T037 | T022, T039, T041, T043, T069, T085 |
| AC-36 | T015, T026 | T023, T028, T065, T074 |
| AC-37 | T019, T031 | T029, T033, T063, T064, T065, T074 |
| AC-38 | T019, T026, T030, T031 | T018, T028, T029, T033 |
| AC-39 | T037, T038 | T034, T039 |
| AC-40 | T011, T012, T045, T046 | T005, T013, T044, T049, T070 |
| AC-41 | T011, T012, T051 | T006, T013, T050, T053, T071 |
| AC-42 | T025, T030 | T028, T029, T033, T071 |
| AC-43 | T081, T017, T019, T045, T046 | T044, T049, T066, T070 |

## Non-functional requirements

| Requirement | Implementation / evidence preparation | Verification |
| --- | --- | --- |
| NFR-001 | T021, T027, T032, T038, T048, T052, T056 | T040, T069 |
| NFR-002 | T012, T016, T026, T031, T037, T047, T051 | T013, T043, T065, T068, T072, T085, T088 |
| NFR-003 | T016, T046, T051 | T022, T042, T070, T071, T072, T085 |
| NFR-004 | T019, T020, T021 | T023, T029, T034, T041, T044, T050, T054, T069, T085 |
| NFR-005 | T058, T059, T060, T061, T062 | T063, T064, T065, T066, T067, T068, T072, T074, T075, T084 |
| NFR-006 | T001, T002, T078, T079, T080, T082, T087 | T010, T014, T073, T076, T077, T086, T088 |

## Approved business rules

Approved BR→FR associations are retained exactly; task unions below are recomputed from the substantive FR mappings above.

| Rule | Requirement path | Implementation | Verification |
| --- | --- | --- | --- |
| BR-01 | FR-002, FR-003, FR-004, FR-005, FR-006, FR-007, FR-008, FR-036 | T015, T017, T019, T020, T024, T025, T026, T027, T030, T031, T036, T037, T046, T047, T055, T058, T059, T060, T061, T062, T081 | T018, T023, T028, T033, T039, T041, T043, T049, T057, T063, T064, T065, T066, T067, T070, T072, T074, T085 |
| BR-02 | FR-007, FR-026, FR-031 | T011, T012, T026, T030, T031, T037, T047, T051, T058, T059, T060, T061, T062 | T013, T028, T033, T039, T049, T053, T063, T065, T066, T067, T068, T071, T072, T074, T083, T084, T088 |
| BR-03 | FR-010, FR-011 | T046, T047, T048, T051, T052 | T044, T049, T050, T053, T070, T071 |
| BR-04 | FR-015, FR-026, FR-027 | T019, T020, T026, T030, T031, T037, T058, T059, T062 | T013, T029, T033, T063, T064, T065, T072, T074, T083, T084, T088 |
| BR-05 | FR-017, FR-028 | T015, T026, T030, T031, T059 | T004, T007, T033, T065, T072, T074, T086, T088 |
| BR-06 | FR-003, FR-004, FR-024 | T015, T019, T020, T025, T026, T027, T030, T055 | T018, T023, T028, T041, T054, T057 |
| BR-07 | FR-005, FR-015, FR-020, FR-024 | T012, T017, T019, T020, T025, T026, T030, T031, T036, T037, T046, T055, T059, T081 | T018, T023, T028, T029, T033, T034, T039, T043, T049, T054, T057, T064, T065, T072, T084 |
| BR-08 | FR-018, FR-019, FR-020, FR-021 | T012, T016, T019, T035, T036, T037, T038, T062 | T018, T034, T039, T042, T043, T068 |
| BR-09 | FR-013, FR-014, FR-015, FR-016, FR-017, FR-026, FR-027, FR-028 | T011, T012, T015, T019, T020, T026, T030, T031, T032, T037, T058, T059, T060, T061, T062 | T004, T007, T013, T029, T033, T057, T063, T064, T065, T067, T071, T072, T074, T083, T084, T086, T088 |
| BR-10 | FR-004, FR-008, FR-015, FR-020 | T012, T019, T020, T025, T026, T030, T031, T036, T037, T046, T047, T055, T059 | T018, T023, T028, T029, T033, T034, T039, T041, T043, T049, T057, T064, T065, T070, T072, T084, T085 |
| BR-11 | FR-032, FR-036 | T015, T026, T027 | T023, T028, T041, T065, T074 |
| BR-12 | FR-033 | T019, T031, T058, T059, T060, T061 | T029, T033, T063, T064, T065, T067, T074 |
| BR-13 | FR-020, FR-021 | T012, T016, T019, T036, T037, T062 | T018, T034, T039, T042, T043, T068 |
| BR-14 | FR-003, FR-013, FR-015 | T015, T019, T020, T025, T027, T030, T032, T059 | T018, T023, T028, T029, T033, T057, T064, T065, T072, T084 |
| BR-15 | FR-029 | T016, T025, T026, T030, T031, T036, T037, T046, T047, T051, T055 | T022, T041, T042, T043, T070, T071, T072, T085 |
| BR-16 | FR-009, FR-012 | T017, T045, T046, T047, T048, T051, T060, T081 | T044, T049, T053, T066, T070, T071 |
| BR-17 | FR-005, FR-008, FR-013, FR-016 | T015, T017, T019, T025, T026, T030, T031, T032, T036, T037, T046, T047, T055, T058, T059, T060, T061, T081 | T018, T028, T029, T033, T039, T043, T049, T057, T063, T064, T065, T067, T070, T072, T085 |
| BR-18 | FR-026, FR-027, FR-028 | T015, T026, T030, T031, T037, T058, T059, T062 | T004, T007, T013, T063, T064, T065, T072, T074, T083, T084, T086, T088 |
| BR-19 | FR-012, FR-014, FR-034, FR-035 | T011, T012, T017, T019, T030, T045, T046, T047, T051, T060, T081 | T005, T006, T013, T029, T033, T044, T049, T050, T053, T066, T070, T071 |
| BR-20 | FR-002, FR-006, FR-007, FR-008, FR-032, FR-036 | T015, T019, T020, T024, T025, T026, T027, T030, T031, T036, T037, T046, T047, T055, T058, T059, T060, T061, T062 | T018, T023, T028, T033, T039, T041, T043, T049, T057, T063, T064, T065, T066, T067, T070, T072, T074, T085 |

## All 30 Admin API operations

Paths are relative to `/api/admin/catalog`. Every row requires strict DTO rules T020, compiling support/security T021 and final real30-route error/security matrix T069; its controller-owning task binds only concrete existing types. T040/T041 are early18-operation proof ONLY; they are not prerequisites claiming verification of later category/brand/spec endpoints. A24 GET has no mutation revision claim; A25 PUT needs its own HTTP stale/rollback proof T085 after T056. T042 is shared-service concurrency proof and is not mapped as REST A24/A25 proof.

| ID | Method / path | Owner | Implementation | Verification |
| --- | --- | --- | --- | --- |
| A01 | GET `/products` | US1 | T027 | T023, T028, T040, T041, T069 |
| A02 | GET `/products/{productId}` | US1 | T027 | T023, T028, T040, T041, T069 |
| A03 | POST `/products` | US1 | T027 | T023, T028, T040, T041, T069 |
| A04 | PATCH `/products/{productId}` | US1 | T027 | T023, T028, T040, T041, T069 |
| A05 | POST `/products/{productId}/publish` | US1 | T027 | T023, T028, T040, T041, T069 |
| A06 | POST `/products/{productId}/unpublish` | US1 | T027 | T023, T028, T040, T041, T069 |
| A07 | POST `/products/{productId}/archive` | US1 | T027 | T023, T028, T040, T041, T069 |
| A08 | POST `/products/{productId}/restore` | US1 | T027 | T023, T028, T040, T041, T069 |
| A09 | GET `/categories` | US5 | T048 | T044, T049, T069 |
| A10 | GET `/categories/{categoryId}` | US5 | T048 | T044, T049, T069 |
| A11 | POST `/categories` | US5 | T048 | T044, T049, T069 |
| A12 | PATCH `/categories/{categoryId}` | US5 | T048 | T044, T049, T069 |
| A13 | DELETE `/categories/{categoryId}` | US5 | T048 | T044, T049, T069 |
| A14 | GET `/brands` | US6 | T052 | T050, T053, T069 |
| A15 | GET `/brands/{brandId}` | US6 | T052 | T050, T053, T069 |
| A16 | POST `/brands` | US6 | T052 | T050, T053, T069 |
| A17 | PATCH `/brands/{brandId}` | US6 | T052 | T050, T053, T069 |
| A18 | DELETE `/brands/{brandId}` | US6 | T052 | T050, T053, T069 |
| A19 | GET `/products/{productId}/variants` | US2 | T032 | T029, T033, T040, T041, T069 |
| A20 | POST `/products/{productId}/variants` | US2 | T032 | T029, T033, T040, T041, T069 |
| A21 | PATCH `/products/{productId}/variants/{variantId}` | US2 | T032 | T029, T033, T040, T041, T069 |
| A22 | POST `/products/{productId}/variants/{variantId}/enable` | US2 | T032 | T029, T033, T040, T041, T069 |
| A23 | POST `/products/{productId}/variants/{variantId}/disable` | US2 | T032 | T029, T033, T040, T041, T069 |
| A24 | GET `/products/{productId}/variants/{variantId}/specifications` | US7 | T056 | T054, T085, T069 |
| A25 | PUT `/products/{productId}/variants/{variantId}/specifications` | US7 | T056 | T054, T085, T069 |
| A26 | GET `/products/{productId}/images` | US3 | T038 | T034, T039, T040, T041, T069 |
| A27 | POST `/products/{productId}/images` | US3 | T038 | T034, T039, T040, T041, T069 |
| A28 | PUT `/products/{productId}/images/primary` | US3 | T038 | T034, T039, T040, T041, T069 |
| A29 | PUT `/products/{productId}/images/order` | US3 | T038 | T034, T039, T040, T041, T069 |
| A30 | DELETE `/products/{productId}/images/{imageId}` | US3 | T038 | T034, T039, T040, T041, T069 |

Legacy POST `/api/products/{id}/images`: T035/T036/T038/T039/T040/T041/T042/T069; new and legacy routes use the same parent revision command, missing tokens400 and stale replay409. Existing three upload routes are reused/regressed, not new Admin operations. No extra price, stock or destructive Product/Variant endpoint is added.

## Stories and completion gates

| Story | Priority / UC | Direct tasks | Additional required completion evidence |
| --- | --- | --- | --- |
| US1 | P1 / UC-031 | T023, T024, T025, T026, T027, T028 | T073, T076, T077, T080 |
| US2 | P1 / UC-034 | T029, T030, T031, T032, T033 | T073, T076, T077, T080 |
| US3 | P1 / UC-035 | T034, T035, T036, T037, T038, T039 | T073, T076, T077, T080 |
| US4 | P1 / all six UCs | T040, T041, T042, T043 | T073, T076, T077, T080 |
| US5 | P2 / UC-032 | T044, T045, T046, T047, T048, T049 | T073, T076, T077, T080 |
| US6 | P2 / UC-033 | T050, T051, T052, T053 | T073, T076, T077, T080 |
| US7 | P2 / UC-036 | T054, T055, T056, T057, T085 | T073, T076, T077, T080 |

## Success criteria

| Criterion | Verification |
| --- | --- |
| SC-001 | T074, T069 |
| SC-002 | T028, T063 |
| SC-003 | T028, T033 |
| SC-004 | T065, T072, T088 |
| SC-005 | T049, T053, T070, T071 |
| SC-006 | T039, T068 |
| SC-007 | T057, T066, T085 |
| SC-008 | T069 |
| SC-009 | T042, T070, T071, T072, T085 |
| SC-010 | T074, T041, T069, T080 |

## Supporting task responsibilities and gates

| Tasks | Requirement-linked deliverable / proof |
| --- | --- |
| T001/T002 | NFR-006 baseline, migration numbering, tool readiness and honest blocked evidence |
| T003–T010, T081/T086 | FR-005/012/014/018/022/026/028/034/035 and NFR-002/006: exact V27 read-only compatibility, typed applicability, bounded objects and complete order/allocation audit before G1 |
| T011–T016, T022/T088 | FR-003/005/014/020/029/031/034/035: additive data integrity, claimed revisions, deferred commit, preserved identity/history and post-rehearsal proof |
| T082/T083/T084 | NFR-005/006, FR-005/015/026/028: explicitly classified fixture/assertion compatibility before checkpoints and cross-module regressions |
| T073–T077 | NFR-005/006: distinct mutation/API/media and consumer checkpoints, flow, existing query/performance budgets and unrestricted full verify |
| T078–T080, T087 | NFR-001/006 and constitution VI: client handoff, exact DB rollout/post-execution gates, explicit unresolved external production CORS issue; no other Admin roadmap implementation |

G1 requires T004 both SQL scripts, T007 complete allocation review, T081/T008/T086 safe validator/auditor and T009 content/applicability proof; T010 closes only after all pass or specifically approved remediation and full recheck. G2 requires T014 and T088. T079 prepares G3 but executes no target change; target migration and subsequent integrity verification stay separately authorized external operations. C01 is OPEN outside Feature1 code scope until production owner/remediation/runtime evidence exists. No requirement/AC/SC count, roadmap update or document checkbox grants deployment permission.


## Implemented evidence reconciliation — T080

The original36FR/6NFR/43AC/20BR/7story/30API responsibility rows above now resolve through the actual source/test/oracle index and individual ID rows in [implementation-acceptance.md](implementation-acceptance.md). Contract method/path tuples were independently compared (exact30/30), with the legacy alias separate. Actual final normal discovery is316 XML suites/1819 unique test identities across315 concrete annotated source files, no missing source class; [verification-test-inventory.tsv](verification-test-inventory.tsv) contains every class/result. Shared-service concurrency is not substituted for dedicated PUT HTTP proof, local signing is not S3 proof, and genuine AI HTTP is not replaced by fallback. All88 task statuses/dependencies are independently recorded in [task-inventory.md](task-inventory.md). Original BR→FR associations, story priorities and API scope are unchanged.

This is tested development/integration acceptance at the pinned uncommitted source. G1's synthetic snapshot and G2's representative disposable/recovery scope do not establish target rollout; the stopped demo has no V28 target/post-migration result. G3 requires separate approval/freshness/coordinated compatibility, and C01 stays OPEN/UNASSIGNED with production release BLOCKED.
