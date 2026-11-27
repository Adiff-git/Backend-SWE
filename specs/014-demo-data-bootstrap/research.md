# Research: Demo Data Bootstrap

**Date**: 2026-11-27. Repository/source inspection and official-document research only. No imports, resets, AI enrichment, dependency installation or runtime provisioning executed.

## R1 — Feature setup and template

**Decision**: Use active feature `specs/014-demo-data-bootstrap` from `.specify/feature.json`; retain actual Git branch `feature/product-detail-additional-infor`.

**Rationale**: Feature directory and branch are independent. `setup-plan.sh --json` was attempted through installed Git Bash but failed at MSYS `NtCreateDirectoryObject` with access denied before script execution. The repository's equivalent `.specify/scripts/powershell/setup-plan.ps1 -Json` succeeded, resolved the active plan template and created plan.md. Its returned BRANCH `014-demo-data-bootstrap` is a feature-derived logical name, not evidence of a Git checkout; `git branch --show-current` verified the actual branch. No extension configuration/preset/override directory was present; core plan template applies.

**Alternatives considered**: Retry with escalation or change Bash installation was unnecessary; the existing native setup script preserves template resolution without infrastructure changes. Product support remains Linux/WSL2; this fallback changes no product requirement.

## R2 — Existing-backend migration path

**Decision**: On fresh/unseeded verified baseline only, use a temporary existing backend container with no published ports, loopback container bind and supported workers disabled. Completed compatible datasets skip this instance and refuse changed/pending migrations.

**Rationale**: `demo/Dockerfile.demo` runs Java JAR directly; no migration-only command exists. `IdentityCleanupScheduler` and `VerificationMailWorker` act at application readiness when enabled; `LegacyProductImageUploadCleanup` is unconditional scheduled cleanup and can mutate upload rows/S3/checkpoints. Empty upload/business tables make that cleanup a no-op. Servlet security configuration means non-web startup cannot be assumed. Compose run does not publish service ports by default; loopback bind also restricts access from peer containers. See [Docker Compose run](https://docs.docker.com/reference/cli/docker/compose/run/) and repository `demo/src/main/java/com/example/demo/auth/config/`, `catalog/service/LegacyProductImageUploadCleanup.java`.

Temporary overrides use existing properties: `security.identity.enabled=false`, `security.verification.enabled=false`, `security.verification.mail-worker-enabled=false`, `security.auth.cleanup.enabled=false`, `behavior.retention.enabled=false`; implementation must verify exact effective property mappings against current source. No new global scheduler-disabling feature is assumed. Stop the instance after internal readiness and verified Flyway history, before import. Normal backend final startup keeps ordinary application behavior.

**Alternatives considered**: New Java migrator/startup seeder violates scope. Dedicated Flyway runner was not the approved choice. Running temporary app on completed/foreign data risks cleanup side effects; refuse it.

## R3 — Exact migration compatibility

**Decision**: Freeze relative migration paths, SHA-256 file hashes and actual Flyway checksum/history expectations with importer compatibility identity; validate live history as well as prerequisite capabilities.

**Rationale**: Source manifest V1–V10 is stale; repository has V1–V30. V15 demands UTF8, deterministic `pg_catalog.und-x-icu` recorded and actual version 153.136, pg_trgm 1.6/public and appropriate CREATE privilege; V28 also creates ICU-based option functions. Probe the pinned runtime image before reset/import; fail without rewriting migrations. Before V15, pg_trgm 1.6 must be available for installation and the role must have CREATE privilege if absent; absence of an installed extension is allowed. An already-installed incompatible version/schema is rejected; after migration require installed pg_trgm 1.6/public. File digests are not interchangeable with Flyway's checksum values; record both. Backend's ordinary Flyway validate must see no pending/unexpected migration on a completed normal start.

**Alternatives considered**: Only checking maximum version misses checksum drift and failed histories. Changing migration prerequisites or database constraints is out of scope. A pinned image is necessary evidence of identity, not proof that capability probes pass.

## R4 — Whole-resource locking

**Decision**: Reserve fixed Docker-engine container name `backend-swe-demo-bootstrap-lock` before infrastructure changes; token-label ownership and exact cleanup, no automatic stale takeover.

**Rationale**: Different checkouts sharing a local daemon coordinate on one daemon resource. A stopped sentinel survives DB deletion and host process crash. [Docker container create](https://docs.docker.com/reference/cli/docker/container/create/) supplies named container reservation without starting its executable. The chosen design must be demonstrated with two real invocations. The sentinel is not a timer lease: absence of heartbeat alone never authorizes deletion. Verify prior host/helper/migrator processes are gone before manual exact removal. If daemon ownership is ambiguous, stop.

**Alternatives considered**: Repository-local flock fails across checkouts. Advisory lock in backend_demo is lost/unavailable during reset; maintenance-DB session lock does not cover infrastructure creation before PostgreSQL starts. PostgreSQL session locks differ from transaction locks and release on session termination; see [PostgreSQL locking](https://www.postgresql.org/docs/17/explicit-locking.html). No second coordination service is justified for this project.

## R5 — Storage durability and conflict ordering

**Decision**: Reuse existing checkpoint format; control ledger is `.bootstrap/ownership.json` inside the existing checkpoint volume, outside root `*.json`. Preflight checkpoints before LocalStack startup and inspect live owned objects before any potential restore/recreation.

**Rationale**: `compose.dev.yaml` has LocalStack `PERSISTENCE=0`. `docker/localstack/init/10-init-s3.sh` restores root checkpoints; `restore_product_images.py` validates the batch then unconditionally PUTs every root checkpoint. `LocalImageCheckpoint` writes atomic, fsynced JSON with key-derived filename and UID 10001 compatibility. Startup restore is not missing-only repair and can mask conflicts if allowed to run first. Normal infrastructure startup must retain healthy services and reject config drift requiring unexpected recreation. Missing required objects may be restored, existing conflicting objects stop.

Ledger records key/hash/checkpoint/product/artifact/attempt and intended/verified/cleanup state before effects. Preserve ledger across reset; save reset intention before deleting DB metadata. No key-prefix-only ownership, bucket-wide delete or removal of unrelated corrupt checkpoints. Such corrupt checkpoints may block readiness and must be reported. Do not store ledger root JSON because the existing loader would treat it as an image checkpoint.

**Alternatives considered**: Database-only ownership disappears on reset; source prefix alone does not prove ownership. Enabling LocalStack persistence or rewriting Java checkpoint behavior is not needed. S3 writes are not reversed by PostgreSQL rollback.

## R6 — Atomic direct import and valid transition sequence

**Decision**: Precompute graph/snapshots and import in one READ COMMITTED business transaction; transport batches are allowed, independent commits are not.

**Rationale**: V25 rejects reservation INSERT unless ACTIVE/unresolved, then allows one terminal transition. V26 requires entitlement INSERT AVAILABLE and makes voucher order snapshots immutable. Insert final order pricing/voucher values, ACTIVE reservations and AVAILABLE grants, then transition allocations and grants, insert redemptions and derive counters. `SET CONSTRAINTS ALL IMMEDIATE` retroactively checks deferred constraints while immediate constraints/triggers remain active; see [PostgreSQL SET CONSTRAINTS](https://www.postgresql.org/docs/17/sql-set-constraints.html). Psycopg transaction contexts support explicit commit/rollback; see [Psycopg transactions](https://www.psycopg.org/psycopg3/docs/basic/transactions.html).

Use existing SQL canonicalization/pricing/snapshot functions where available, not a divergent money/Unicode implementation. Preallocate identities before immutable snapshots; advance all affected identity sequences before backend startup. Unknown commit response requires authoritative reread, not a blind repeat or failure-marker overwrite.

**Alternatives considered**: API-only has no arbitrary lifecycle/history endpoints. Independent randomized stock/voucher counters fail constraints. Terminal reservation/grant INSERTs fail immediate guards. Disabling triggers or session_replication_role is forbidden.

## R7 — Historical orders, receipts, reviews and derived statistics

**Decision**: Seed no checkout_receipts or auth workflow artifacts; verify review purchase eligibility explicitly and derive all sales/ratings from rows.

**Rationale**: No reverse FK requires every order to own a receipt. Historical direct SQL orders were not actual API checkout/idempotency operations; future normal checkout creates its existing receipt. V30 requires valid immutable direct-sale snapshots when populated; populate coherent full current snapshots for generated orders. V27 basic FKs/rating/uniqueness do not enforce the entire owned COMPLETED purchase relationship; verify author/customer/item/product/status/time joins explicitly. V25 FAILED allocations remain ACTIVE as approved. `ProductSearchSort` supports relevance/price/name only; review repository already computes COUNT/AVG/histogram. New sales/rating sort or aggregate tables are not implied.

**Alternatives considered**: Fake receipts/sessions add unsupported invented workflows and unnecessary constraints. Seeding rating counters conflicts with derived-statistics requirements.

## R8 — Specification and option applicability

**Decision**: Preserve arbitrary valid typed technical facts/options but promise filters only for existing registry definitions. Validate option identity with actual PostgreSQL ICU functions.

**Rationale**: `TechnicalSpecificationValidator` accepts schema version 1, canonical keys and typed scalar/quantity values. `SpecificationFilterRegistry` restricts filters to physical_ram, installed_storage_gb, display_diagonal, display_resolution: Laptop supports all four; Tablet display_diagonal only. Source sample `internal_storage` is not automatically the filter key. Add a derived installed_storage_gb value only from equivalent evidence and applicable category, updating its value for synthetic storage variants with clear provenance. Never invent CPU/display/compatibility facts to fill a filter.

**Alternatives considered**: Global Color×Storage or freeform dimension merging ignores product specificity. Python casefold is not guaranteed equal to database NFC/ICU lower/trim; SQL functions are authoritative. Adding registry definitions is out of scope.

## R9 — Time and distributions

**Decision**: Frozen relative-time recipe plus one saved UTC attempt anchor; fixed seed 20261126 and explicit stable sampling algorithm/version. Current events are last 30 days; completed purchases span prior twelve calendar months.

**Rationale**: Behavior retention is 90 days, so year-old events could be cleaned immediately. Customer joins precede purchases; address/grants precede their use, completion precedes review and active sale windows use end-exclusive semantics. Calendar-month construction needs explicit clamping for invalid month days, preserving event order. Normal startup never updates dates or enforces original row counts after user activity. Freeze resulting choices/hashes rather than assuming a future model/PRNG run is identical.

**Alternatives considered**: Fixed absolute dates expire; runtime AI violates scope; independently moving related timestamps breaks lifecycle evidence. Unbounded full option Cartesian expansion exceeds approved scope.

## R10 — Standalone runtime and evidence gates

**Decision**: Plan a Python 3.12 helper image with a reviewed exact dependency/hash lock, multi-stage build, unconditional non-root runtime and a dedicated tools/demo-bootstrap/.dockerignore in its narrow build context; resolve checkpoint permissions without a root fallback, with no new Java dependencies. Package selection is psycopg 3, boto3, Pillow and bcrypt plus standard library. Concrete pins require developer approval before introduction.

**Rationale**: Existing source validator uses Pillow and restoration uses boto3, but that does not prove these packages exist in a standalone importer environment. Use explicit local SDK config to avoid ambient AWS credential/endpoint discovery; see [Boto3 configuration](https://docs.aws.amazon.com/boto3/latest/guide/configuration.html). Decode/read image content rather than trust extension/manifest; see [Pillow Image API](https://pillow.readthedocs.io/en/stable/reference/Image.html). Generate twelve compatible BCrypt cost-10 hashes without committing secrets; verify normal Java login using the supplied secret, observing BCrypt's byte-length constraints; see [bcrypt project](https://github.com/pyca/bcrypt).

**Alternatives considered**: Host tools without a lock vary across Linux/WSL. Running imports in the production JRE image is infeasible without adding tools to it. Vendoring dependencies or dynamically installing them on every startup weakens reproducibility.

All design unknowns are resolved to decisions or explicit implementation/release gates: dependency lock approval, actual ICU/migration/startup proof, record/artifact human review. F-39 now records a completed private non-commercial educational scope with team/instructor access and private instructor submission; public redistribution verification is not applicable to current scope, permissions remain unverified, and permission verification is required before future public redistribution or commercial use. No gate is labeled passed on inspection alone.
