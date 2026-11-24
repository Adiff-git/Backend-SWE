# Task traceability — UC-037

Planned evidence only. A unit dash means the relevant guarantee is verified through a migrated-schema or HTTP integration test. Tasks T029 and T030 aggregate executed evidence.

| Requirement | Implementation | Unit | Integration | Database | Acceptance scenarios |
| --- | --- | --- | --- | --- | --- |
| FR-01 | T005, T013, T015, T017, T020 | — | T025 | T025 | AC-16, AC-25 |
| FR-02 | T012, T013 | — | T014 | T014 | AC-01, AC-02, AC-20 |
| FR-03 | T012, T013 | — | T014 | T027 | AC-01, AC-22 |
| FR-04 | T012, T013, T022 | T022 | T014, T023 | T023 | AC-02, AC-03, AC-15, AC-20 |
| FR-05 | T007, T009, T015 | T007 | T016 | T016, T024 | AC-04, AC-09, AC-18 |
| FR-06 | T006, T007, T009, T013, T017 | T007 | T014, T016, T018, T021 | T010, T016, T018 | AC-05, AC-07, AC-08, AC-18, AC-20, AC-21, AC-26 |
| FR-07 | T007, T009, T017 | T007 | T018, T019 | T018, T024 | AC-07, AC-08, AC-23, AC-24 |
| FR-08 | T004, T008, T009 | — | T016, T018, T024 | T010, T016, T024 | AC-04, AC-06, AC-07, AC-09, AC-10, AC-19, AC-23 |
| FR-09 | T004, T008, T009 | T007 | T016, T019, T021 | T010, T016, T019 | AC-04, AC-06, AC-09, AC-13, AC-19, AC-24 |
| FR-10 | T008, T020 | — | T021 | T010, T021, T027 | AC-13, AC-14, AC-22, AC-26 |
| FR-11 | T022 | T022 | T023 | T023 | AC-15 |
| FR-12 | T004, T006, T009, T020 | — | T021, T024, T026 | T010, T011, T024, T026 | AC-10, AC-11, AC-14, AC-17, AC-21, AC-23, AC-26 |
| FR-13 | T004, T007, T008, T009, T015, T017 | T007 | T019, T025 | T010, T019 | AC-12, AC-19, AC-24, AC-25 |
| NFR-01 | T006, T009 | — | T019, T024 | T019, T024 | AC-09, AC-10, AC-11, AC-19, AC-23 |
| NFR-02 | T004, T008, T009, T017 | — | T016, T018, T019 | T010, T016, T018, T024 | AC-06, AC-07, AC-19, AC-23 |
| NFR-03 | T005, T013, T015, T017, T020 | — | T025 | T025 | AC-16, AC-25 |
| NFR-04 | T004, T006, T009, T020 | — | T021, T026 | T010, T011, T026 | AC-14, AC-17, AC-26 |

## Acceptance evidence

| Scenario | Verification task |
| --- | --- |
| AC-01 | T014 |
| AC-02 | T014 |
| AC-03 | T014, T023 |
| AC-04 | T016 |
| AC-05 | T007, T016 |
| AC-06 | T016 |
| AC-07 | T018 |
| AC-08 | T007, T018 |
| AC-09 | T024 |
| AC-10 | T024 |
| AC-11 | T024 |
| AC-12 | T019, T025 |
| AC-13 | T021 |
| AC-14 | T010, T011, T021 |
| AC-15 | T023 |
| AC-16 | T025 |
| AC-17 | T024, T026 |
| AC-18 | T007, T016 |
| AC-19 | T016, T019, T025 |
| AC-20 | T014, T016, T021 |
| AC-21 | T018 |
| AC-22 | T014, T021, T027 |
| AC-23 | T024 |
| AC-24 | T007, T018, T019 |
| AC-25 | T019, T025 |
| AC-26 | T010, T021 |

## Business rules

| Rule | Implementation and verification tasks |
| --- | --- |
| BR-I01 | T012, T013, T014 |
| BR-I02 | T007, T009, T015, T016 |
| BR-I03 | T007, T009, T017, T018 |
| BR-I04 | T004, T009, T017, T018, T024 |
| BR-I05 | T007, T015, T017, T018, T019 |
| BR-I06 | T004, T008, T009, T010, T020, T021 |
| BR-I07 | T022, T023 |
| BR-I08 | T006, T009, T018, T024, T026 |
| BR-I09 | T006, T009, T019, T024 |
| BR-I10 | T004, T007, T008, T009, T010, T019, T025 |
| BR-I11 | T005, T025 |
| BR-I12 | T004, T010, T011, T021, T026 |
| BR-I13 | T012, T013, T014, T020, T021, T027 |

## Stories and API operations

| Story | Phase tasks | Operation coverage |
| --- | --- | --- |
| US1 | T012–T014 | GET list and GET detail. T022 wires stockStatus and T023 closes acceptance after T014. T025 security and T027 budget. |
| US2 | T015–T016 | POST replenishments. T019 replay and T025 security. |
| US3 | T017–T019 | POST corrections. T024 concurrency and T025 security. |
| US4 | T020–T021 | GET history. T025 security and T027 budget. |
| US5 | T022–T023 | Stock status on existing list/detail. No additional route. |

T029 collects unrestricted Surefire/Failsafe and JAR evidence per [verification guide](quickstart.md). T030 closes verified development. T031 is external authorization/preflight.

## Development verification

- T003/T010/T011 use isolated PostgreSQL 17 fixtures with clean and staged V29 migration, V27 allocation and receipt history, a populated V28 baseline, restored-copy consistency and an injected V29 rollback. Existing migration checksums and protected rows were compared.
- T014–T025 evidence is consolidated in AdminInventorySecurityIT and AdminInventoryConcurrencyIT. The first uses a real HTTP server and bearer filter chain. The second observes PostgreSQL lock waits, verifies both winner orders, and asserts committed stock, allocation and journal state. No authorization or write transaction is mocked.
- T026's supplementary disposable probe used committed Admin corrections and replenishments, then checked the public Catalog, Cart and recommendation availability projections against the same variant. Only the image signer was stubbed. The repository's real two-service recommendation check passed in unrestricted verification.
- T027 counted actual PostgreSQL query calls and separately observed Hibernate authentication statements on live HTTP. The domain projections used one statement each for list, detail and history within the 2/1/2 limits. Generic plans for all three were captured separately by a disposable probe.
- Focused and supplemental logs and PostgreSQL query plans are under `demo/target/uc037-evidence/`. T031 remains outside this execution.

T029 and T030 are closed with the unrestricted 969-unit / 891-integration run and packaged JAR inspection. The [verified closeout](quickstart.md#verified-development-closeout--2026-11-24) records evidence and the T031 boundary.
