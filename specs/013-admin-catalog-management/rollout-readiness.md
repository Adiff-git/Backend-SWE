# Future demo rollout and post-migration handoff — T079

**Decision: G3 BLOCKED. No target migration or deployment has occurred.** This OFFLINE deliverable describes two separately approved operations: target migration execution, followed by target post-migration verification. Completion of T079 does not execute either operation. C01 remains OPEN/UNASSIGNED; production release is BLOCKED independently of local demo readiness.

## Pinned target and tested artifacts

| Item | Recorded identity / evidence |
|---|---|
| Branch / baseline HEAD | `features/AdminCatalogManagement` / `86512d7ab1e0bb262f783c05c0051d6be46b1983` plus tested uncommitted implementation |
| Only designated demo target | PostgreSQL17.11, database `admin013_demo`, schema `public`, Compose project `admin013-2c3a577f-demo` |
| Exact demo container | `52bf5d7c42fec0d9c129f6d8e51e113de2a9ddfad464a01e7c3f782bb6bd06b1` |
| Recorded current schema / state | V27, frozen database default transaction read-only; no V28 rollout authorized |
| Dataset scope | Entire identified synthetic local demo snapshot, not another developer/team/production dataset |
| V28 source / packaged-resource SHA256 | `ea55e11f01868f6abd1f3d5b8d435099c4c3184ae0c561ac90378ef9b5276fe1` |
| Tested executable JAR SHA256 | `3b9d3bb6d5e9de0d2535744a82341b8b8dc8567f839d12d4422ce7db9f47cfaf` |
| Current rehearsed schema signature | `3c8d8fd85f7efec1ce8be8bab959a98051ab21dbaf838bcf76af9b49de4aa1d3` |
| Frozen representative V27 backup SHA256 | `fe995100504f873385e0dac337760bfbb8fd0fb4b8e6b8b8544cb92a3b412bf2` |

The reference (`admin013_reference`/project suffix-reference), audit copy (`admin013_copy`/suffix-copy), disposable rehearsal and demo are different resources. Preserve their identities/volumes. Never substitute a Testcontainers database, an application datasource default or an existing developer database for the designated target. Historical identities/provenance and protected backup/object manifests are in [G1 evidence](data-preflight-evidence.md); do not publish the private registry or credentials.

## Required approvals and pre-execution checklist

- [ ] Project owner names the rollout operator, reviewer, maintenance window and exact demo container/database/schema; all must acknowledge the synthetic-only scope. Ownership of the operator role is not currently assigned by this document.
- [ ] Owner separately authorizes **target V28 migration**, the necessary temporary lifting of the demo's read-only freeze and narrowly scoped DDL/Flyway permissions, compatible binary startup and any expected application writes. No implicit authorization follows from designation or 88 task markers.
- [ ] Owner separately authorizes subsequent bounded read-only **target post-migration verification**, approved storage HEAD/GET and its evidence destination. No external storage access or data repair is implied.
- [ ] Recheck live target version/identity, encoding/collation and all V1–V27 history/checksums against the pinned resources. Unexpected identity/history/schema/checksum is STOP, not Flyway repair/baseline.
- [ ] Fresh **complete G1**: both approved V27 SQL scripts with SELECT-only credentials/explicit read-only transactions; typed persisted specification/category applicability and bounded authorized object content audit on an identified restored copy; complete inventory/order allocation integrity and all findings reviewed. Reuse prior G1 only after proving the frozen snapshot/objects unchanged; any changed data/schema/target/storage requires complete renewed evidence.
- [ ] Any incompatible records have a separate explicit remediation decision with exact IDs/dependencies, a reviewed procedure and full recheck. No automatic rename/merge/reprice/Unpublish/delete/rewrite is allowed.
- [ ] Current **G2** is verified against exactly this V28/JAR pair: T014 fresh install/representative upgrade/negative rollback/backup restore plus T088 independent schema/row/FK/history/receipt/allocation comparisons. A changed migration or incompatible binary requires risk-based re-rehearsal; old success cannot silently carry forward.
- [ ] Capture a fresh target-bound custom-format DB backup and matching object checkpoints/manifests, checksums, identity and time. The historical reference backup is recovery evidence, not automatically a fresh target backup.
- [ ] Restore that backup and paired image bytes into a new isolated owned environment; verify schema, histories, all controlled row fingerprints and object bytes. Preserve historical receipt constraints using the session-safe procedure below. Record recovery operator/time and result.
- [ ] Quiesce all old application readers/writers, jobs and external SQL/import/media writers; stop traffic/background mutations before backup/migration. Inventory and coordinate verification-mail/identity/behavior/auth scheduled jobs as well as API services; do not assume binding HTTP to loopback stops jobs.
- [ ] Clients and trusted writers acknowledge V28 fields, Product aggregate revision/lock protocol, taxonomy/Brand versions, publication checks, active gallery/source retention, and legacy attachment's required revision strings. No mixed old/new writer or importer is allowed. External importer changes belong to its owner, not UC-038 implementation here.
- [ ] Validate the migration-only invocation, pinned project Flyway version/locations, secure private connection configuration, target28, no out-of-order/baseline/repair/clean shortcuts, and audit logging without secrets. Repository rehearsal helpers deliberately refuse the demo target; do not bypass their ownership guard.
- [ ] Record explicit GO/NO-GO signed approval after reviewing every item. Any missing item leaves G3 BLOCKED.

Credentials must be provisioned through an approved private runtime mechanism, never pasted into chat or committed. Read-only audit credentials cannot perform rollout; temporary privileged migration credentials must not be reused as routine application/auditor credentials.

## Authorized future migration execution procedure — NOT EXECUTED

1. Freeze the approved artifact set and maintenance evidence; verify exact target and V27 history again immediately before execution. Preserve the reference/copy and all unrelated containers/volumes.
2. Use the existing pinned Flyway runtime and `classpath:db/migration`, validate all installed history, then migrate **to28 only** in the approved maintenance context. The migration adds columns/gate/functions/indexes/constraints; it must not rewrite business records. Retain the complete Flyway output, return code, start/end and lock/wait observations. Rehearsal342ms on tiny synthetic data is not a target execution-time guarantee.
3. The invocation must be migration-only or explicitly account for and suppress/quiesce application startup jobs until verification is approved. The normal Spring Boot startup automatically invokes Flyway and then application logic; it is not a read-only inspection tool. The operator must review/approve the actual invocation before G3 GO.
4. If an approved operator uses a Flyway CLI, the conceptual steps are `flyway -configFiles=<approved-private-config> -target=28 validate` then the corresponding `migrate`, using the project's compatible Flyway version. CLI availability/configuration has **not** been verified; these are future operator recipes, not commands executed here or an instruction to install another infrastructure stack. Existing runtime API `validate()`/`migrate()` with target28 is equivalent when securely invoked through the approved migration-only runner.
5. Any error, checksum mismatch, timeout, lock conflict or unexpected row change is STOP. Record transaction/history outcome before deciding recovery. Do not retry blindly, repair metadata, disable constraints or hand-edit an applied migration.
6. On successful migration keep ordinary traffic/writers closed. Obtain the separately authorized post-migration verification evidence before compatible binary/clients are released. No automatic target rollout is part of this feature backlog.

## Separately authorized target post-migration verification — NOT EXECUTED

Use bounded read-only transactions and a recorded operator/account. The V27 baseline status query intentionally rejects new lifecycle values; **do not treat a blind V27-only preflight rerun as the V28 oracle**. Reuse/review the version-aware checks from `CatalogDemoRehearsal` for this exact schema; adaptation is an operator-controlled future step.

| Required check | Evidence / acceptance condition |
|---|---|
| Identity and Flyway | Same authorized target; V1–V27 entries/checksums unchanged; one successful V28 with expected script/checksum, no unexpected successor/failure |
| Schema/functions/indexes/constraints | Approved additive fields/defaults, one taxonomy singleton, nonnegative revisions, four lifecycle states, authoritative normalized-name indexes, active ordering/primary indexes and deferred guards present/validated; qualified trim helper and FK-compatible `FOR NO KEY UPDATE` guard match approved source |
| Fresh state defaults | Existing versions0, restore provenance null, Enabled/active defaults valid; distinguish these expected additive changes from old business-row data |
| Full controlled data comparison | Old identities, source links, categories/Brands, variants/SKU/options/prices/specifications, image/source/upload associations and references unchanged; compare full sorted-row fingerprints and counts, not counts alone |
| Historical commerce | Order recipients/items/names/SKU/options/prices/totals, immutable checkout receipt payloads and customer ownership unchanged; no prior order rewritten |
| Inventory/order allocation | Balances/stock/reserved and original variant attribution unchanged; correct order/item/variant association, quantities, lifecycle/state/time and exact active reservation counters; no missing managed allocations |
| Publication and applicability | Every Published Product valid for Enabled choices/positive whole price/active primary gallery and applicable typed facts; no stock-positive publication requirement; Draft/Unpublished/Archived stay private |
| Storage | Paired references still resolve through approved HEAD/conditional bounded GET/content checks; shared/unreferenced retained objects not deleted; DB backup alone never proves image bytes |
| Mapping and compatibility | Pinned V28 binary starts against the verified migrated target only after approved startup conditions; mappings validate, Admin revision/error protocol and legacy client upgrade acknowledged, old writers remain stopped |
| Safe smoke plan | Owner separately approves any test mutations/transactions, test identities and cleanup. Read-only authorization does not permit creating Products/orders or altering stock. Verify Admin/Customer/Guest/current-session behavior and public eligibility without exposing secrets |
| Release decision | Operator/reviewer sign captured schema/history/FK/count/fingerprint/object/application checks; only then permit traffic under the compatible writer plan. This is not production CORS closure |

## Backup, failure and recovery

Use [the verified backup/restore procedure](migration-rehearsal.md#backup-restoration-procedure): original custom dump→reviewed SQL via `pg_restore --no-owner --no-acl --file=-`; assert one restore-session `set_config('search_path', '', false)` and change **only it** to `pg_catalog,public`; restore with `psql -X -v ON_ERROR_STOP=1 --single-transaction` to a new empty identified owned recovery database. No triggers/constraints may be disabled. Restrict this to approved trusted public-schema backups, retain generated/original checksums, and independently restore/validate paired image checkpoints.

If V28 fails transactionally, verify old schema/history/data remain exact before any decision to reopen the old compatible service. If it committed but post-checks fail, keep maintenance mode and escalate; do not attempt a destructive down migration or start the V27 binary against V28. Restore to a separately approved recovery environment, prove its data/object integrity, and obtain explicit authorization for cutover/replacement. Preserve failed resources/logs for review; never overwrite the frozen reference/demo or delete unrelated resources to force a retry. Repairing data or promoting the recovery database requires separate approval.

## Sign-off and outstanding gates

| Decision | Current state |
|---|---|
| T079 OFFLINE handoff | Documented; does not execute target migration/post-checks |
| G1 | VERIFIED for recorded synthetic frozen demo snapshot only; freshness must be re-established for rollout |
| G2 | VERIFIED for pinned corrected V28 source and representative synthetic recovery scope |
| G3 target migration / post-check / demo traffic authorization | **BLOCKED / NOT GRANTED / NOT EXECUTED** |
| Demo schema | Recorded V27/read-only; no V28 target execution |
| Production CORS C01 | **OPEN; accountable remediation owner UNASSIGNED** |
| Production release | **BLOCKED** until separately approved CORS remediation, explicit production origins, effective deployed config, real allowed/denied-origin and auth evidence, and accountable review; see [production-release-gates.md](production-release-gates.md) |

Project owner must assign rollout/review responsibilities and separately approve the target procedure. Deployment/Security ownership, production origin allowlist/remediation and real production verification remain unresolved; no invented assignee, implicit waiver or production-readiness claim is supplied.


## Finalization environment observation

All six named reference/demo/copy/storage/rehearsal containers remain present but stopped; no restart, database connection or storage access was performed during finalization. The recorded demo schema is **last verified V27**, not a new live query result. LocalStack uses the existing nonpersistent runtime model: a stopped process does not establish that image bytes will exist on restart. Use the preserved paired checkpoints through the approved isolated restoration procedure and repeat authorized object/content/G1 freshness checks before rollout. No current object availability or production persistence is inferred from historical PASS. These are operational startup/authorization conditions, not unexecuted Feature1 implementation tasks.
