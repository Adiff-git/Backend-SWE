# Implementation Plan: Development and Demo Data Bootstrap

**Branch**: `feature/product-detail-additional-infor` (actual Git branch; unchanged) | **Date**: 2026-11-27 | **Spec**: [spec.md](spec.md)
**Feature directory**: `specs/014-demo-data-bootstrap` (active `.specify/feature.json`)
**Status**: Implementation and Windows/Docker full-profile verification passed. 58/60 tasks closed; T018 and the native cross-checkout portion of T032 remain user-deferred. Frontend journeys are explicitly unexecuted. Source, Java, build and applied migrations remain unchanged; existing secret values are preserved. See tasks.md and private final/summary.json for exact evidence and limitations.

## Summary

Implement one Bash startup entry point with normal and `--fresh` modes for a dedicated local Compose project. Python utilities verify a frozen offline AI-enriched catalog and deterministically resolve a linked commerce recipe. Upload/checkpoint images before a single constrained PostgreSQL business transaction; commit completion metadata with business records only after integrity checks. Preserve later demo activity on normal startup. Fresh reset drops/recreates only `backend_demo`, retaining its PostgreSQL volume and non-owned assets.

The original interview decisions and subsequent approved analysis/R-40 refinements are retained in the specification. Scope includes 1,500 source products, 1,500–9,000 variants, 500 customers (12 logins), 512 addresses, R sampled eligible reviews and C + 3,000 orders with sufficient C completed-order capacity under R-40, 300 populated carts, 3,500 wishlist entries and 360 events. No Java changes, new endpoints, business schema changes, rewritten migrations, disabled constraints or new automated test suites. The single importer operational metadata table is the explicit exception.

## Technical Context

**Language/Version**: Bash 4+; standalone Python 3.12 execution environment for Linux/WSL2. Existing Java 25/Spring Boot application is reused as built.

**Primary Dependencies**: Docker Engine + Compose v2, Git, Python standard library (`json`, `csv`, `hashlib`, `decimal`, `uuid`, `calendar`, `subprocess`), and the exact user-approved standalone lock for `psycopg`, `boto3`, `Pillow`, `bcrypt` and their transitive packages. The concrete approval and hashes are recorded in dependency-proposal.md and tools/demo-bootstrap/requirements.lock. Python 3.12.16 and all dependency imports passed in the non-root helper. No Maven dependency/plugin changes.

**Storage**: Existing PostgreSQL 17 Compose image and Flyway V1–V30; LocalStack 4.14.0/S3 through existing signed gateway; `backend-swe-demo_postgres-data` and `backend-swe-demo_product-checkpoints` under verified Compose naming; immutable local artifacts and private operational reports.

**Testing**: Actual full-profile PostgreSQL/LocalStack operational checks, existing backend API journeys and the existing unrestricted Maven verification (724 unit/slice plus 758 integration tests, zero failures/errors/skips). Private operational probes add no application test suites/classes. Frontend journeys are UNEXECUTED without a supplied frontend. See Verification Strategy and current evidence for remaining checks.

**Target Platform**: Linux and WSL2 with a verified local Docker daemon. Git Bash is not a supported product runtime; it was attempted only for Spec Kit setup in this Windows planning session.

**Project Type**: Development orchestration and offline dataset preparation/import tools; no application service or new public HTTP interface.

**Performance Goals**: Correctness at approved demo scale; record import duration, image bytes and constraint-check duration without asserting an unmeasured SLA. Stream image processing and batch transport, but do not split business commit or disable expensive triggers.

**Constraints**: Exactly two user startup modes; secrets stay in `.env.local`/private process environment; runtime AI prohibited; imported asset provenance/reuse remains subject to F-39. Normal backend must remain stopped until import/asset verification succeeds.

**Scale/Scope**: Up to 9,000 variants; R reviews are determined by uniform per-product draws (bounds 28,950–134,250), with C >= 9,000 completed orders and 3,000 fixed non-completed orders, each with 1–4 items. Freeze actual order/item totals after capacity/popularity feasibility; all dependent graph records derived, never independent aggregate randomization. Model output, sampling recipe, rule versions and source revision are frozen before import.

## Constitution Check

Pre-research assessment and post-design reassessment:

| Gate | Before research | After design and required evidence |
|---|---|---|
| I–III, XVII: approved scope/domain boundaries | Pass: development utilities only | Pass: no production Java/service/repository edits, endpoints or architecture additions |
| II: dependencies | Pass: no dependency introduced | Conditional implementation gate: obtain approval for concrete standalone lockfile; never silently add Maven or Python dependencies |
| VII: PostgreSQL/Flyway/schema | Pass with user-authorized I-27 exception | Pass by design: existing Flyway for application schema; only `bootstrap_import_runs` operational table outside managed application tables; no migration rewriting |
| VI/XI/XVI: secrets, storage and environment | Pass with approved direct-import exception | Pass by design: local-only Compose-owned endpoints, explicit SDK credentials, preserved signature checks; direct final-object upload is an offline import path, not a change to application presigned upload lifecycle |
| IX/X: transactions/concurrency | Pass: atomic business import and whole-operation lock required | Pass by design: READ COMMITTED, immediate triggers retained, deferred checks forced, engine-scoped lock survives DB recreation; S3 rollback not assumed |
| XIII: verification | Documentation-only checks now | Future targeted operational checks, cross-domain checkpoints and unrestricted existing Maven verification retained; no new test classes/suites |
| XV: source control | Pass | No branch switch, commit, push, image push or deployment |
| F-39: restricted educational scope | Scope decision complete: private non-commercial course use/submission, team and instructor only | Public redistribution verification N/A to current scope; preserve provenance, prohibit public/commercial use, permissions UNVERIFIED; verify before future public/commercial use |

Analysis remediation is applied for container execution, traffic/confirmation ordering and extension preflight. The user approved R-40 volume reconciliation: review total and completed-order capacity are derived, not the obsolete fixed totals. Runtime preconditions are gates to prove during implementation, not facts established by a design document.

## Project Structure

### Documentation (this feature)

```text
specs/014-demo-data-bootstrap/
  spec.md
  plan.md
  research.md
  data-model.md
  quickstart.md
  contracts/
    startup.md
    artifact.md
    verification.md
  checklists/requirements.md
  tasks.md                    # next speckit-tasks phase; not created now
```

### Planned source changes (none implemented now)

```text
scripts/start-dev.sh           # only user-facing startup entry point
compose.bootstrap.yaml        # minimal helper overlay; preserve compose.dev.yaml
.env.example                  # nonsecret approved resource/password variable guidance
README.md                     # concise entry-point documentation
tools/demo-bootstrap/
  Dockerfile                  # multi-stage, unconditional non-root runtime; approved pins
  .dockerignore               # narrow helper build context, no secrets/datasets/private data
  requirements.lock
  bootstrap.py                # private orchestration/import/verification dispatcher
  prepare.py                  # maintainer-only offline preparation; not startup mode
  catalog.py                  # source/overlay/options/specification validation
  scenarios.py                # deterministic linked commerce graph
  storage.py                  # signed S3 and checkpoint/ownership integration
  database.py                 # metadata, import transaction, state discovery
  sql/                        # parameterized imports and operational verification
  rules/                      # nonsecret versioned vocabulary/pricing/scenario recipes
  README.md
.private/demo-bootstrap/      # ignored private reports and reviewed artifacts
```

**Structure Decision**: Keep utilities outside `demo/src/main/java` and `demo/src/test`. Existing `docker/localstack` helpers remain the storage contract; emit compatible checkpoint files rather than changing their behavior. Store source-independent enriched output under `.private/demo-bootstrap/artifacts/<version>/`, source copies/AI responses/reviews under its preparation workspace, and reports separately. The Python tool image runs only internal commands invoked by the Bash wrapper; its helper stages are not additional user-facing startup modes.

## Phase 0 — Research Findings and Resolved Choices

[research.md](research.md) records repository evidence, official-source references and alternatives. Critical findings:

1. The existing backend has no dedicated migration-only executable. Run it unpublished and bound to its container loopback only on a verified empty/baseline DB; disable existing optional workers. A compatible completed normal start skips this temporary instance and refuses pending/changed migrations before normal backend startup.
2. V15 requires UTF8, ICU `und-x-icu` version/actual-version 153.136 and `pg_trgm` 1.6/public. A pinned PostgreSQL tag alone does not prove compatibility; probe before reset and again on the recreated database before Flyway.
3. Reservation inserts must start ACTIVE; entitlement inserts AVAILABLE. Terminal history requires valid transitions inside the business transaction. Order pricing/voucher snapshots must be precomputed because updates are forbidden.
4. Database option canonicalization and pricing functions are authoritative. Python Unicode case folding is not substituted for ICU behavior.
5. Existing checkpoint restoration uploads all root checkpoints, not just missing objects. Preflight conflicts before touching live infrastructure and never recreate healthy LocalStack implicitly.
6. Existing filter support is limited to the four registry keys for Laptop/Tablet; extra option dimensions do not add filters. Keep behavior events in the most recent 30 days because retention is 90 days.

## Phase 1 — Runtime Design

### Control, resources and execution boundary

Bash validates arguments, resolves repository paths, fixes Compose project/resource identity and supervises the internal Python tools container. Build/pull prerequisites may be prepared before mutation; missing dependency approval or artifact stops before reset. Helpers inherit no unrestricted shell-sourced environment file: use Compose env-file parsing and pass only allowlisted fields. Database/S3 secrets never appear in command-line arguments, process logs or reports. Do not print unredacted `compose config`.

Use the existing services `postgres`, `localstack`, `image-storage`, `mailpit`, `backend` from `compose.dev.yaml`. The helper overlay joins only required existing networks, mounts source/artifacts read-only and the existing checkpoint volume read/write, and never publishes helper ports. SDK targets are service endpoints in the verified local project; explicitly supply local credentials/region/path style and disable ambient cloud credential/endpoint discovery. Backend signing URL may be local host access, but importing never uses an arbitrary AWS/profile target.

After argument/local-target validation and, in fresh mode, exact typed confirmation, reserve Docker container name `backend-swe-demo-bootstrap-lock` atomically before infrastructure or database mutation, using an already available pinned project image, no host mounts/network. Labels contain invocation token, project/database/bucket, host/process identity and time. The sentinel exists independently of `backend_demo`, so competing checkouts share it. Release only by matching invocation token. Do not automatically reclaim stale/unknown sentinels; establish all prior workers are gone before exact targeted manual removal. Keep this lock while a helper or temporary backend is active; terminate supervised children before release. This is an explicit one-user local guard, not a distributed lock service or defense against an operator manually deleting it.

### Preflight and migrations

1. Validate two-mode CLI, owned resource names, local Docker endpoint/context and safe effective configuration. Inspect checkpoint volume existence/labels first: a read-only mount of an absent named volume can still create it, so never use a helper mount to discover existence. If absent, record a new uninitialized resource and let explicit owned Compose provisioning create it under the lock. If present, read through a read-only helper before LocalStack startup and validate every root checkpoint. If LocalStack is running, compare owned live content before any command that could restore/overwrite it. Reject config drift requiring recreation of stateful services during normal startup; do not quietly `up --force-recreate` or remove volumes.
2. Stop normal backend traffic before any infrastructure startup/restoration, initialization, repair, migration or reset. Then start/retain required infrastructure with service-specific Compose commands and no broad cleanup. Wait for PostgreSQL and signed S3/gateway readiness; validate bucket ownership. Probe ICU/extension and current Flyway history without starting the application on a populated DB.
3. Determine normal state before migration. A matching completed DB requires exact compatible current history and no unapplied migration in the built backend; start normal backend only after asset checks. Foreign/partial unexplained data stops. On an unseeded DB, only known migration baseline roles/permissions may exist.
4. Fresh requires exact `RESET backend_demo`, verified ownership ledger and pre-reset compatibility checks. Preserve a durable intended-reset/asset list in checkpoint control data before dropping the one DB through a maintenance connection; never drop postgres/template DBs or use volume recreation. Cleanup only ledger-proven objects and checkpoint filenames, preserve non-owned entries, then recreate UTF8 backend_demo with the approved owner. Dropping the DB is outside the business import transaction and cannot be rolled back.
5. For fresh/unseeded only, run the existing backend via Compose `run --no-deps` without `--service-ports`, bind `SERVER_ADDRESS=127.0.0.1` inside the container and disable supported optional identity/verification/auth-cleanup/behavior-retention workers. Verify migration history plus internal readiness; stop the temporary app before import. The unconditional legacy image cleanup is safe only because upload/application rows were verified absent. Failure remains a blocker; no Java workaround.

### Offline preparation and immutable artifact

Analyze all source records offline; AI output is a proposal, never authoritative input SQL. Copy exact tracked Git blobs from source revision, validate manifests and permitted checkout newline-only differences. Apply approved CSV/category/publication overlays and retain field-level extracted/synthetic provenance. Use category-aware vocabulary and evidence-based original option mapping; retain single-variant fallback when evidence/conflicts prevent safe enrichment. Preserve product/variant IDs, original SKU/base price, and all 1,500 product identities. Record duplicate-family suggestions only.

Freeze the enriched catalog plus relative-time scenario recipe, prompts/model identity, rules, seed, dependency/runtime identity, required human review and file hashes. Use deterministic UUID namespaces/ASCII SKU suffixes and canonical JSON serialization; decimal arithmetic for money and versioned logarithmic rules, ROUND_HALF_UP to whole VND for synthetic base prices, FLOOR for direct sale discounts. The frozen output's checksums, not a new AI run, establish reproducibility. Validate option equivalence using PostgreSQL's actual canonical functions before publication/import. Preserve source provenance throughout restricted team/instructor educational use and instructor submission. Source permissions/copyright clearance remain UNVERIFIED; no public redistribution or commercial use is allowed, and permission verification plus scope reassessment is required before either future use.

### Business import and storage

[data-model.md](data-model.md) defines graph order, identity maps and final invariants. Resolve one UTC anchor per attempt, reuse it after an interrupted attempt with matching identity, and create a new one only for intentionally fresh generation. Within 12-month history enforce joined/address/grant/placement/completion/review ordering. Place active/failed reservations in a recent period and the 360 events in the last 30 days. Use deterministic within-tier sampling and guarantee zero purchases/reviews for the never-purchased tier; report achieved counts/ratings rather than seed their aggregates.

Write intended ownership durably in `product-checkpoints/.bootstrap/ownership.json` before upload; keep this ledger outside the reset database and outside root checkpoint glob. Verify each object using downloaded bytes/content metadata; skip identical owned bytes, repair missing required owned references, stop on conflicts. Emit existing root checkpoint format with matching key-hash filename and verify readback/permissions for UID 10001. Save verified ownership before database import. Preserve unrelated checkpoints even when corrupt; report startup blockage instead of deleting them. A normal user-deleted DB image is not reinserted or reattached.

Use one READ COMMITTED business transaction. Preallocate stable logical mappings to database identities, prepare immutable snapshots, insert dependencies, perform required initial-to-terminal transitions, calculate stock/counters from final rows, and force all deferred constraints before setting operational completion state. Commit business data and completion atomically. Record attempt/failure via separate short metadata transactions only when completion outcome is known. Re-read metadata after uncertain commit; never overwrite a possibly committed completion as FAILED. S3/checkpoint writes are external and stay tracked for retry, not transaction rollback.

## Verification Strategy and Implementation Gates

| Checkpoint | Affected risks | Required evidence |
|---|---|---|
| Dependency/runtime preparation | Python compatibility, no Java drift | Approved version/hash lock, image identity, actual dependency imports and supported Linux/WSL runtime |
| Source/enrichment preparation | Provenance, corruption, canonical options, pricing/spec contradictions | Exact Git hashes, every-record validators, all flagged cases and >=10% enriched review coverage, frozen artifact digest |
| Infrastructure isolated smoke | Local-only safety, migration side effects, database encoding | Effective target ownership, exact ICU/extension/migration probes, unpublished temporary container, no partial traffic |
| Cross-domain import checkpoint | Rollback, immediate/deferred triggers, inventory/vouchers/reviews | Approved graph loaded with all constraints enabled; exact counts; explicit joined invariants and deferred-check result |
| Recovery/concurrency checkpoint | Cross-checkout contention, crash points, storage durability | Real concurrent starts and interruptions before upload/checkpoint/commit/ack, safe retry and stale-lock behavior, missing/conflicting object checks, LocalStack restart/no resurrection |
| Final integration | Existing application regression and web demo | Existing unrestricted `./mvnw verify` from demo (no skip/selective flags), actual discovery reports; named frontend journeys and nonsecret results |

Targeted operational verification is default after each implementation change. At cross-domain checkpoints run affected existing integration checks; near completion run one unrestricted existing Maven verify when environment permits, without adding suites/classes. Localized fixes rerun affected checks first; repeat broad/full verification only for shared-infrastructure impact, broad regression risk or unresolved uncertainty. Preserve required security/transaction/concurrency/rollback/idempotency evidence. Document exact commands, revision/artifact/anchor, discovery/counts, results, failures, unexecuted checks and environmental blockers. Do not relabel earlier evidence as a later full pass. In this planning turn only document consistency checks run; no Maven/Docker/import check is claimed.

Dependency approval, actual ICU compatibility, temporary-start feasibility, constraint cost at target volume, artifact review are current execution/release gates. F-39 educational scope is complete, public redistribution verification is not applicable to current scope, and permission verification is required before any future public redistribution or commercial use. A failed gate stops the affected stage without broad cleanup, production changes or weakened checks. [quickstart.md](quickstart.md) describes future commands and operational scenarios; [contracts/verification.md](contracts/verification.md) defines output evidence.

## R-40 review update — approved derived-volume design

Apply exact review-count group sizes 300/300/525/300/75 and exact average-rating group sizes 60/120/300/420/300 from spec.md. Use fixed-seed random feasible assignments, uniform inclusive count draws and actual eligible integer review ratings; no intentional count/rating correlation beyond feasibility. Classify persisted averages using existing two-decimal HALF_UP service semantics. No tolerance or additional minimum coverage threshold applies to these review/rating groups.

The user confirmed increased volume. R is the sum of frozen per-product uniform review-count draws (minimum 28,950, maximum 134,250, expectation 81,600; expectation is not an exact target). Choose reproducible feasible completed-item/order totals with C >= 9,000, each order 1–4 distinct variants, enough separate eligible items per product for all reviews and the unreviewed demo journey, and preserved popularity proportions. Keep the 3,000 non-completed orders at exact original status counts. Freeze R, C, total C + 3,000 orders and actual item totals/status breakdown in the manifest. Never fit volume by resampling/clipping counts or relaxing distributions. Extra capacity can exceed ceil(R/4) because low-demand products with many reviews still need their purchases within the approximately 5% tier share. Benchmark actual derived volume; no unmeasured performance claim.

## Complexity Tracking

| Approved exception | Why needed | Simpler alternative rejected because |
|---|---|---|
| I-27 operational `bootstrap_import_runs` table outside Flyway application tables | Reliable transactional completion identity without Java/migration changes | Files alone cannot commit atomically with database business data |
| Offline direct SQL/final-object import | Historical dates, statuses and the R-40 derived coherent order volume with no lifecycle API additions | API-only creation lacks required existing transitions/date control; presigned upload sessions would fabricate application workflow history |

These are explicitly approved exceptions for local tooling, not constitution amendments or permission to alter production upload/transaction behavior. No additional complexity layer, service, cache/aggregate table or domain schema is planned.

## Implementation environment adjustment (2026-11-27)

The user explicitly authorized continuing implementation on Windows without Linux/WSL2. Use Docker-isolated operational checks for compatible components and defer T018 native Bash host verification. This changes implementation sequencing, not the supported runtime or acceptance claims: Linux/WSL2 lifecycle checks remain outstanding and must not be represented as passed by container syntax checks.

## Current execution evidence (2026-11-27)

The helper dependency proposal was approved and installed only in the approved isolated image. Source verification, actual existing-backend V1?V30 migration/internal readiness, ICU/pg_trgm, all candidate canonical option maps, rolled-back empty import SQL, isolated S3 upload/interruption/owned cleanup/archive checks and Bash syntax passed. Full offline graph replay passed without seeding independent aggregates. Unrestricted Maven verification passed 724 unit/slice and 758 integration tests; this is existing Java regression evidence, not persisted bootstrap acceptance.

The user authorized continuing implementation without a Linux/WSL2 host. A stdlib-only host preparer adapter invokes the existing non-root helper and unpublished backend under the same daemon lock, permitting Windows preparation without a circular dependency on final artifact approval. It preserves the supported Bash runtime and keeps native host verification deferred. The live Compose 5.5.1 namespace hash guard was verified against its implementation: https://github.com/docker/compose/blob/v5.5.1/pkg/compose/hash.go . The gateway hash is checked using its exact owned LocalStack container namespace, without service recreation.

The user approved the concrete 711-record generated-row package. Database validation and detached approval binding the unchanged approved payload supplied the release. The first full business profile committed, independent persisted histograms/invariants passed, and actual API activity survived normal startup with identical database digests/sequences/anchor. Confirmed reset recovery, exact resource isolation, checkpoint recreation and actual constraint/unknown-outcome faults passed. Final recovered full import and controlled commit-acknowledgement/report/readiness checks passed. Native host and frontend gaps remain explicit. Planning-stage statements above do not supersede this execution checkpoint.
