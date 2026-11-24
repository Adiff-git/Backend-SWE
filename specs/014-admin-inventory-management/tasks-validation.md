# Task validation — UC-037

Document validation covered 31 unchecked tasks and 63 direct dependency edges. Checklist shape, contiguous IDs, phase labels, execution classes, and acyclic prerequisite ordering passed. Zero missing prerequisites, cycles, or invalid topological edges. T030 transitively requires every implementation and verification task. T031 has no downstream implementation dependents.

| Phase | Tasks |
| --- | ---: |
| Setup | 3 |
| Foundations | 8 |
| US1 — List and detail (P1) | 3 |
| US2 — Replenish (P1) | 2 |
| US3 — Correct (P2) | 3 |
| US4 — History (P2) | 2 |
| US5 — Out of stock (P3) | 2 |
| Cross-cutting verification and handoff | 8 |

| Execution class | Tasks |
| --- | ---: |
| OFFLINE | 17 |
| DB-INTEGRATION | 13 |
| STORAGE/EXTERNAL | 0 |
| RELEASE/APPROVAL | 1 |

## Review findings

- Migrated-schema route, replay, concurrency and performance tests require T010 and the appropriate implementation tasks. Representative upgrade evidence T011 is required before full verification. T024 uses independent PostgreSQL transactions with committed fixtures and observed lock waits. T025 requires real bearer authentication.
- Parallel labels apply only after prerequisites with isolated edited files and disposable database fixtures. The shared write controller and transaction path are ordered explicitly.
- The traceability matrix covers 13 FRs, 4 NFRs, 26 acceptance scenarios, 13 business rules, 5 stories and 5 Admin operations. IDs describe planned evidence. They establish no runtime result.
- V28 is the repository migration head. V29 is a candidate until T001 rechecks it. T004 authors the migration later. T010 and T011 validate fresh and upgrade paths on disposable environments while T031 governs any separately authorized target rollout. Historical V27 and Feature 1 evidence do not satisfy those gates.
- Independent OFFLINE work has no disposable-service prerequisites until evidence review. Full verification may require the existing disposable CI dependencies. Target access is outside the implementation critical path.

Readiness: ready for Analyze. This Tasks pass performed no SQL, migration, application tests or infrastructure changes.

## Remediation acceptance

The dependency graph permits T022 before T014 despite story presentation order. T023 closes full read acceptance. T024 requires rollback/replay evidence and runs real commit races. T025 requires service replay plus the complete authorized HTTP surface. T027 follows both read acceptance paths. T029 requires the named disposable migration/recovery gates and unrestricted clean verify. T030 closes development and does not depend on external T031. C01 remains an inherited production gate. No runtime evidence was collected in remediation.
