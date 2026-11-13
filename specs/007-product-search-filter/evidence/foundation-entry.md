# T010 — scoped G0 foundation entry

Status: PASS for G0 only. Captured UTC:2026-11-08T19:10:40.116944+00:00. Branch:features/SearchAndFilter;HEAD:c5bd4e7eeb39d9b06a0c7e36af9a65635058613f;existing dirty Catalog/Brand/Specifications work preserved. No migration/dependency/ignore/application wiring change is authorized by this gate.

## Actual prerequisites

- T001 checked;[baseline.md](baseline.md) records source/tool/security/pagination/consumer/D7 evidence,existing work and migration reservations. SHA-256:338ec40a895ab4ded90dc9908a6ac6980a0a7fc2fc311d4d03867e709cde6ca4.
- T009 checked;[verification-protocol.md](verification-protocol.md) defines all6 SC,edge-case responsibilities,run identity,JDBC/latency measurement,isolation,selection-union evidence and final acceptance. SHA-256:b8a518efc8e194005ca95430dfc7fab61175a750e85b36c6cf38a3a4db99ddfa.
- Both prerequisite artifacts are present;their scoped evidence does not purport to approve the feature. No unmerged paths/hooks found;checklist markers remain requirements16/16 and custom design0/40,unchanged. User explicitly authorized only T007/T010/T011 with known scoped pending gates.

## Separate gates and actual consumers

| Gate | Current status | Required consumers |
| --- | --- | --- |
| G0/T010 baseline+protocol | PASS | Scoped entry record;not a global Phase1 barrier |
| G-N/T013 SQL/Java parity and immutable primitives | NOT_VERIFIED;not executed | T015 support migration/index use;T014 SQL prototype depends on its proof |
| G-ID/T004 alphabetic/model scope | BLOCKED;no sign-off | T005 corpus,T014 calibration,T024 matcher/model promotion;not T007 structural helpers |
| G-F/T014 reviewed core fuzzy calibration | NOT_VERIFIED;not executed | T024 fuzzy implementation,T029 core evaluation |
| G-S/T003 approved registry/category/type/unit matrix | BLOCKED;no approval | T008 registry,T030 typed keyword,T039/T040/T042/T043 specification work,per-key index support;not DTO/parser/normalization |
| T006 semantic OpenAPI evidence | BLOCKED;validator missing | T018 exposed baseline wiring;not DTO/parser authoring |
| G1/T019 core foundation after T018/T015 | NOT_VERIFIED;not executed | Core story implementation and early repository test authoring |
| G-C/T029 core keyword increment | NOT_VERIFIED;not executed | US1.core acceptance only;nine core RK cases,not US3 RK07 |
| G-US1/T031 core+approved specification contributor | NOT_VERIFIED;not executed | Full US1 acceptance;cannot omit FR-004 approved technical branch |
| G-L/T065 full local evidence | NOT_VERIFIED;not executed | Full20FR/6SC,complete corpus/RK01–RK10,registry/runtime,contract,regression AND benchmarks |
| G-D/T063 intended target | NOT_VERIFIED;target unspecified | Deployment only;missing evidence does not block a truthful local handoff,but prohibits rollout |

T011/T012/T007/T017 use their own prerequisites. Missing registry/model/OpenAPI evidence must not become a blanket blocker for independent DTO/parser/structural work. G0 does not close CHK005/009/020/026/028/034/036/037,accept benchmarks,approve a vocabulary,or authorize target writes. T002 disposable infrastructure evidence is not target proof. No downstream task is executed by recording G0.
