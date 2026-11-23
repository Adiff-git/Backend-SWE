# Local verification workflow

**Current operator entry point (2026-11-23):** preserve and reuse the existing `admin013-2c3a577f-reference`, `-copy`, `-demo` and `-rehearsal` projects; do not run the creation examples below against them. Exact container/database identities and frozen synthetic provenance are in [data-preflight-evidence.md](data-preflight-evidence.md). G1/G2 are scoped VERIFIED; the official demo remains V27/read-only and G3 requires separate rollout approval. The original minimal-reference examples below are retained as dated reproducibility instructions for a new independently owned namespace, not instructions to recreate the designated environments.

## Scope and environment identities

The team currently uses individual Docker databases; no shared or production target is confirmed. An existing developer container, volume, application URL or imported archive is **not** an authorized target. No target was designated or accessed for this workflow. Synthetic evidence below does not close G1 or G2.

| Logical environment | Data and purpose | Execution boundary |
|---|---|---|
| Reference V27 | Deterministic synthetic category, Draft product and variant; V1–V27 only | New Testcontainers PostgreSQL 17; read-only auditor and SQL checks; export a frozen snapshot |
| Disposable migration database | A new empty database restored from a designated snapshot; destructive negative fixtures permitted only here | Never reuse a developer volume. Synthetic restore checks are available now; V28 rehearsal remains gated by T009/T014 |
| Development database | A separately owned new Compose project/volume, eventually V28 | Do not start the backend or upgrade an existing database before the applicable rehearsal, mapping and authorization prerequisites are satisfied |

## Reuse existing infrastructure

`compose.dev.yaml` already provides pinned PostgreSQL 17-alpine, health checks, loopback port bindings, project-scoped persistent storage, Mailpit and LocalStack with a signed storage gateway. No second Compose stack is needed. The backend uses `.env.local` and automatically discovers current Flyway migrations, including V28: **never start the full stack to create a V27 reference**.

The standalone `V27AuditDatabase` Testcontainers helper creates its own non-reused PostgreSQL container with ephemeral data and explicitly sets Flyway target `27`. It never reads an application datasource URL. It supplies JDBC connectivity, a SELECT-only audit role and transaction-level read-only checks without Spring/JPA startup. The new local-reference scenario uses this same helper, not a second database implementation.

## Verified synthetic initialization and restoration

From the repository root, with Java 25, the Maven wrapper dependencies cached, Docker available and PostgreSQL 17/Testcontainers images available:

```bash
bash demo/mvnw -o -f demo/pom.xml \
  -Dtest=CatalogCompatibilityAuditTest,AuditImageReaderTest,CatalogApplicabilitySqlTest \
  -Dit.test=CatalogLocalReferenceIT,CatalogCompatibilityAuditIT,CatalogAllocationPreflightIT \
  test failsafe:integration-test failsafe:verify
```

Remove `-o` only when dependency downloads are intended and permitted. Do not substitute ordinary Spring integration suites: their current profile discovers V28.

`CatalogLocalReferenceIT` initializes V1–V27, checks history, `pg_trgm`, schema/constraints/indexes/triggers/functions and sequence state, then makes a custom-format `pg_dump`. Two separately created databases are restored with `pg_restore --exit-on-error`. Row/history fingerprints, structural signatures, auditor results and both SQL preflight results are compared. Identity/time result columns are expected to differ. PostgreSQL's equivalent varchar-array cast deparsing is narrowly canonicalized for structural comparison. A second fresh migration initialization is compared independently. A destructive synthetic edit in one copy must not change the reference or other copy.

The test prints `ADMIN013_LOCAL_REFERENCE <path>` and retains these generated files under `demo/target/admin013-local-reference/<unique-id>/`:

- `reference-v27.dump`: **synthetic**, not a representative customer/team dataset.
- `v27.tsv`: all 27 migration names/checksums.
- `identity.json`: PostgreSQL version and actual container/image identity, with synthetic-only scope.
- `SHA256SUMS`: integrity checks for those three files.

Verify the selected artifact before reuse:

```bash
# Set this to the exact artifact directory printed by your successful run.
cd "$referenceArtifact"
sha256sum -c SHA256SUMS
```

The source and copies live only in the new test-owned container. Testcontainers closes its resources on completion; exported files remain until explicitly removed. No host `psql` is required: dump/restore tools run inside that container.

## Optional persistent local reference using existing Compose

This is an operator procedure, not a claim that a persistent Compose stack was started in this run. Choose a **new unique project**, unused loopback port and private environment file with local-only values. Respect Compose's existing `.env.local` backend-file requirement; do not overwrite an existing file or print secrets. Check that no container/volume already has the selected `com.docker.compose.project` label; a collision means choose another name, never delete the colliding resources.

```bash
# refProject, privateEnv and referenceArtifact are operator-selected paths/names.
docker compose -p "$refProject" --env-file "$privateEnv" -f compose.dev.yaml \
  up -d --wait --no-deps postgres
```

**Receipt-free minimal test backup only:** after proving this is a newly created empty owned database, the original minimal synthetic fixture can use the following direct restore. **Do not use this command for the current representative receipt-containing backup.** For that backup, use the verified session-safe procedure immediately below.

```bash
docker compose -p "$refProject" --env-file "$privateEnv" -f compose.dev.yaml \
  exec -T postgres sh -c 'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --no-owner --no-acl --exit-on-error' \
  < "$referenceArtifact/reference-v27.dump"
```

### Representative backups with historical receipts

Historical receipt CHECK functions require public-schema helper resolution. The final verified procedure is in [migration-rehearsal.md](migration-rehearsal.md#backup-restoration-procedure) and implemented by `CatalogDemoRehearsal`: retain the original custom dump, generate SQL with `pg_restore --no-owner --no-acl --file=-`, assert exactly one `SELECT pg_catalog.set_config('search_path', '', false);`, and change **only that restore-session statement** to `SELECT pg_catalog.set_config('search_path', 'pg_catalog,public', false);`. Apply the reviewed generated file with `psql -X -v ON_ERROR_STOP=1 --single-transaction` to a **new empty, identified, owned disposable database**. Tools may run inside its existing PostgreSQL17 container; no host psql or second infrastructure implementation is required.

Retain original/generated checksums and full logs; compare schema, all historical rows/receipts, Flyway history and allocation integrity after restore. Both representative V27 and upgraded V28 restoration have passed with this procedure. It does not disable any trigger/constraint, alter historical migration SQL, authorize a partial restore or permit restoring over the frozen reference/demo. The original minimal receipt-free test remains a narrower proof. Image bytes must still be restored and verified separately from their paired checkpoint.

A restore failure is not a clean reference: retain evidence, stop, and recreate only the new owned disposable resource. Provision a separate SELECT-only audit role before applying `auditor-operations.md`; restoring with `--no-acl` intentionally does not transfer grants or roles. Preserve exact identity/history evidence. Repeat with another **new** project/database for a disposable copy. Do not run Flyway or the backend as part of V27 restoration.

Stop/remove only the explicitly recorded project resources after inspecting their labels. Preserve volumes by default; delete a volume only if it is recorded as newly created disposable storage for this run. Never use global prune commands, the default Compose project, or cleanup against an unknown volume. For development V28 startup, use another new project only after the relevant approved migration prerequisites and explicit startup decision; this guide does not authorize conversion of an existing developer database.

## Data and image bytes

Historical `tools/catalog-import/output/` artifacts exist locally, but this checkout has no executable importer source outside those outputs. Their existence is not approval, provenance or representative-data evidence. No archive was imported. A project owner must designate an approved snapshot and matching objects before representative rehearsal. UC-038 import remains outside this feature.

Database dumps contain image references, **not image bytes**. Existing LocalStack uses private S3, signature validation, `PERSISTENCE=0` and the `product-checkpoints` volume. Its existing `restore_product_images.py` validates checkpoint keys, content types, sizes and SHA-256 before upload and verifies stored bytes on restore. Pair a database snapshot with the corresponding checkpoint/object manifest, keys and checksums. Preserve shared objects and historical references; never infer that an unreferenced-looking object can be deleted.

Use a new isolated LocalStack project/bucket for synthetic media tests. Do not mount or capture an existing developer checkpoint volume without approval. Existing media Spring integration support also initializes the latest database schema, so its execution remains subject to that schema's gates. This run's reference fixture has **no images**; its successful audit is not storage integrity evidence. Offline checkpoint tests can run separately:

```bash
python3 -m unittest discover -s docker/localstack/init -p 'test_restore_images.py'
```

These use a fake storage client and do not establish live LocalStack compatibility.

## Remaining gates and smallest safe sequence

See `database-gate-readiness.md` for the full permissions/evidence matrix. No credentials should be pasted into chat or committed.

| Tasks | Local infrastructure can provide | Still required before task closure |
|---|---|---|
| T003 | PostgreSQL 17 and inspection tooling | Owner-designated exact authorized target, schema/collation/history/checksum evidence; new synthetic reference is not implicitly that target |
| T004 | T086-proven SQL execution boundary | T003 plus both complete read-only SQL results on the identified target/copy |
| T005/T006/T007 | Collision/allocation test fixtures | Reviewed actual target findings, identities and dependencies; no automatic remediation |
| T009 | V27 auditor, dump/restore tooling | T005–T007; identified operator-provided restored copy, provenance and authorized bounded storage HEAD/GET/content evidence |
| T010 | Evidence format | T004/T009 and explicit G1 compatibility/remediation decision; rerun after any separately approved repair |
| T014 | Isolated PostgreSQL, existing migration tests | T013/T009/T083/T086; fresh and representative upgrades, negative rollback and backup restoration evidence. Synthetic V27 restore alone does not satisfy this |
| T088 | Post-rehearsal comparison tooling | T014 plus version-aware post-upgrade integrity, fingerprints and restore closure for G2 |
| T022 | Existing runtime test support | T016/T019/T020/T088 and successful mapping/transaction integration checkpoint |

Actual graph: `T003 → T004 → T005/T006/T007 → T009 → T014 → T088 → T022`; T010 separately records G1 after T004/T009. T014 also requires T013/T083/T086. T022 additionally requires T016/T019/T020. Do not erase these edges to execute synthetic V28 tests early.

Next owner decision: designate an exact local verification target and an approved representative restored copy, authorize read-only identity/history/preflight access, and separately authorize object HEAD/GET. Provide backup/restore provenance and isolated rehearsal ownership. If only synthetic data will ever be used, obtain an explicit planning decision about the representative-data requirement rather than silently substituting it. Subsequent target migration/rollout requires its own authorization.

**Current status:** 49/88 tasks complete; no newly completed task from this infrastructure preparation. T086 remains previously complete. G1 and G2 are unpassed; G3 blocked. C01 remains OPEN, owner UNASSIGNED, production release blocked. No target migration is authorized. Once T022 closes, T028/T033/T039/T049/T057 become candidate integration paths subject to their remaining prerequisites; do not start them from this guide alone.


## Current implementation evidence — 2026-11-23

Earlier UNVERIFIED/NOT RUN environment statements above describe their dated preparation stage. Current exact identities and synthetic-only provenance are recorded in [data-preflight-evidence.md](data-preflight-evidence.md): **G1 VERIFIED only for the frozen designated synthetic local demo V27 snapshot**, not another dataset or deployment. [migration-rehearsal.md](migration-rehearsal.md) records **G2 VERIFIED for the current V28 source** after fresh/representative upgrade, negative rollback and independent post-rehearsal/restored integrity verification. The late deferred parent-lock correction was separately re-rehearsed; old success was not carried forward without revalidation.

**G3 remains BLOCKED**: exact target freshness, rollout coordination and explicit migration authorization remain required. The official demo, reference and immutable audit copy remain V27/read-only; only positively identified disposable rehearsal/test databases used V28. C01 remains OPEN, owner UNASSIGNED, production release BLOCKED. No target migration or deployment is authorized by these completed evidence tasks. Final full Maven and acceptance gates remain pending; see implementation-evidence.md for incremental test attribution.


## Final tested handoff

Unrestricted `UV_NO_SYNC=true bash ./mvnw clean verify` passed957 Surefire+862 Failsafe tests,0 failures/errors/skips; [final-verification.md](final-verification.md) records one actual run and pinned artifacts. During finalization the six designated containers are present/stopped, not re-created or restarted. Preserve them. Live target/storage freshness is not claimed; restore paired object checkpoints as needed under explicit authorization and perform the [rollout checklist](rollout-readiness.md). G3 and C01 remain blocked even after implementation handoff completion.
