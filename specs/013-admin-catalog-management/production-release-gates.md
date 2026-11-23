# Production Release Gates — Feature 013

**Current handoff2026-11-23:** development/integration unrestricted verification PASSED (957 Surefire+862 Failsafe,0/0/0); G1 VERIFIED only for the frozen synthetic demo snapshot; G2 VERIFIED for pinned corrected-source rehearsal. G3 remains BLOCKED and C01 remains OPEN/UNASSIGNED, production release BLOCKED. Dated unexecuted tables below describe their original planning stage, not current test results. See [rollout-readiness.md](rollout-readiness.md).

**Recorded**: 2026-11-22. This is an evidence/ownership boundary, not deployment authorization or a security implementation task. No production configuration was read or changed by this remediation.

## C01 — OPEN inherited production CORS compliance issue

Source `demo/src/main/java/com/example/demo/common/security/SharedCorsConfiguration.java:12-20` supplies wildcard origins/methods/headers without a production-specific origin restriction in that class. Constitution VI (`.specify/memory/constitution.md:155-158`) requires explicit production origins. `plan.md:39` already records the discrepancy; this record does not invent a new defect or claim a current production exploit. Effective deployed configuration is UNVERIFIED.

| Responsibility | Owner / current state |
| --- | --- |
| Assign accountable production security/deployment owner | Project owner; named assignee UNASSIGNED |
| Approve the actual production origin allowlist and separately scope remediation | Deployment/Security owner; UNASSIGNED, external to Feature 1 implementation |
| Implement and review approved production CORS changes | Separate authorized production/security work; NOT a Feature013 code task |
| Validate effective production configuration and origin/authorization behavior | Assigned Deployment/Security owner; NOT RUN |
| Carry blocker into Feature013 release report | Feature013 implementer through T087/T079/T080; document OPEN even if feature tests pass |

Required closure evidence: named accountable owner; separately authorized remediation/change reference and reviewed exact revision; owner-approved explicit production allowlist; effective deployed revision/profile and redacted configuration proving that wildcard origins are not active in production; real preflight and actual-response checks for each allowed origin and rejected unapproved origins; existing anonymous/non-Admin401/403 and current Admin JWT checks remain effective; no new cookie/auth/CORS-header architecture or customer contract weakening. Record commands, environment, tested revision, observed headers/statuses and review approval without secrets. Local MockMvc tests, an unexecuted checklist or document remediation are insufficient production evidence.

**Gate C01**: OPEN/BLOCKED until the owner is assigned, approved remediation is actually applied and all required production evidence is reviewed. No waiver or automatic closure is introduced. T087 can complete recording the outstanding issue while C01 remains OPEN; it cannot mark the runtime defect fixed. T079/T080 must state separate results: Feature013 implementation/test readiness, database migration readiness and production compliance/release readiness. Missing C01 evidence always yields production BLOCKED, irrespective of Maven/preflight success.

Safe OFFLINE Feature013 work need not wait for external CORS remediation. No SharedCorsConfiguration, production profile, frontend, RBAC or broader Admin roadmap functionality is modified or added by this backlog amendment.

### T087 tracking completion — Controlled Batch 2, 2026-11-22

Re-inspected the current `SharedCorsConfiguration` bean, its wildcard `setAllowedOrigins(List.of("*"))`, and Constitution VI's explicit-production-origin requirement. The source issue remains present; this review does not establish the effective deployed configuration. No CORS/application configuration was changed and no production request was sent.

T087's documentation deliverable is complete when the source/requirement, unresolved responsibility, required external remediation and evidence, and non-waivable release condition are recorded. It does **not** require claiming that unavailable evidence exists. The accountable assignment remains with the project owner; the Deployment/Security assignee is **UNASSIGNED**. No owner or deployment approval is inferred from implementation authorization.

| Closure evidence | Current state | Required accountable action |
| --- | --- | --- |
| Named Deployment/Security owner | UNASSIGNED | Project owner assigns the accountable individual/team and records acceptance. |
| Approved explicit production origins | NOT PROVIDED | Owner approves actual origins; no guessed allowlist or wildcard substitution. |
| Separately authorized CORS remediation | NOT PROVIDED / NOT IMPLEMENTED | Reference reviewed change and exact source revision; scope remains external to Feature 1. |
| Effective production deployment/configuration | UNVERIFIED | Record deployed revision/profile and redacted effective origin configuration, including overrides. |
| Allowed and denied Origin behavior | NOT RUN | Real OPTIONS preflight and actual-response checks for approved and unapproved origins; record statuses/headers and ensure denied origins receive no permissive CORS response. Do not demand a particular error status when absence of CORS permission is the relevant oracle. |
| Authentication/authorization preservation | NOT RUN | Anonymous/non-Admin401/403, valid current Admin bearer access, expired/revoked sessions denied; no cookie/auth-policy changes or security disabling. |
| Security/deployment review and release decision | BLOCKED | Assigned owner reviews environment, commands, tested revision and all evidence without secrets; no closure from MockMvc/Maven/documents alone. |

**Current release decision: BLOCKED by C01.** T087 may be checked complete for this tracking record while C01 stays OPEN. T079/T080 must carry this unresolved evidence table into their later reports. Independent OFFLINE development remains permitted; no task or permission for a CORS refactor, production deploy or another Admin feature is introduced.

## Database execution gates (all unexecuted)

| Gate | Evidence required | Current status |
| --- | --- | --- |
| G1 exact-target compatibility | Identified authorized target, PostgreSQL/collation, verified schema/Flyway checksums; both read-only SQL scripts; complete order/allocation integrity; V27-safe shared typed-fact/applicability and bounded object audit; controlled results and copy provenance | UNVERIFIED; no live audit performed |
| Remediation decision | Every incompatibility has affected identities/dependencies and a specific explicit approval before any repair; complete preflight recheck after any separately performed remediation | No findings/data assumed clean; no repair authorized here |
| G2 rehearsal/recovery | T014 fresh-install + representative V27 upgrade, negative transactional failure proof, backup restoration, and T088 post-rehearsal schema/FK/row/history/order/receipt/allocation comparison | NOT RUN |
| G3 target migration execution | Fresh G1 + completed G2 + approved remediation/recheck when needed + verified target-bound backup/restore + stopped old readers/writers + compatible application/external writer handoff + separate operator authorization | BLOCKED; outside implementation backlog execution |
| Target post-migration verification | Actual new schema/history/constraint/index checks, valid new states/flags/revisions/active gallery, preserved identities/source/upload FKs, recipient/item/receipt/stock/allocation comparisons against controlled pre-execution evidence | NOT RUN; cannot be implied by preparing T079 |
| Production release | Database rollout/post-execution proofs AND C01 production compliance evidence plus feature verification gates | BLOCKED |

T014 is disposable migration EXECUTION; T088 is separate POST-REHEARSAL verification. T079 is OFFLINE documentation of the future target execution/post-migration steps, not permission to execute them. The original V27 preflight rejects post-V27 states intentionally; post-migration checks must use the new version-aware invariants rather than treating a blind rerun of the old status query as authoritative. No Flyway repair, applied-migration edit, destructive downgrade, silent data rewrite or mixed old/new binary rollout is permitted.


## Current implementation evidence — 2026-11-23

Earlier UNVERIFIED/NOT RUN environment statements above describe their dated preparation stage. Current exact identities and synthetic-only provenance are recorded in [data-preflight-evidence.md](data-preflight-evidence.md): **G1 VERIFIED only for the frozen designated synthetic local demo V27 snapshot**, not another dataset or deployment. [migration-rehearsal.md](migration-rehearsal.md) records **G2 VERIFIED for the current V28 source** after fresh/representative upgrade, negative rollback and independent post-rehearsal/restored integrity verification. The late deferred parent-lock correction was separately re-rehearsed; old success was not carried forward without revalidation.

**G3 remains BLOCKED**: exact target freshness, rollout coordination and explicit migration authorization remain required. The official demo, reference and immutable audit copy remain V27/read-only; only positively identified disposable rehearsal/test databases used V28. C01 remains OPEN, owner UNASSIGNED, production release BLOCKED. No target migration or deployment is authorized by these completed evidence tasks. Final full Maven and acceptance gates remain pending; see implementation-evidence.md for incremental test attribution.


## Final gate reconciliation — after T080

This final status supersedes earlier pending full-test/acceptance wording in dated checkpoints above. Feature1 development/documentation88/88 and unrestricted957+862 verification are COMPLETE; no remaining mandatory Feature1 test is marked skipped or pending. G1 remains VERIFIED only for its recorded frozen synthetic demo snapshot and G2 for the pinned corrected-source rehearsal/recovery. Named containers are currently stopped; live target/storage freshness and target post-migration verification were not performed during finalization. **G3 BLOCKED, target V28 migration/deployment NOT AUTHORIZED/NOT EXECUTED. C01 OPEN, owner UNASSIGNED, production release BLOCKED.** Actual production origin/allowlist/remediation/deployment/auth evidence remains outstanding and cannot be waived by88 task markers.
