# T014 / T088 — Local demo migration rehearsal and G2

**Current-source revalidation (2026-11-23): VERIFIED.** Deferred guard now uses FK-compatible FOR NO KEY UPDATE; Admin retains early FOR UPDATE/revision protection. Affected22 unit +94 PostgreSQL IT pass, including actual gallery insertion race, full checkout concurrency and foundation rollback. The same identified disposable container repeated fresh/representative-upgrade/backup restore with40-relation equality and schema SHA256 `3c8d8fd85f7efec1ce8be8bab959a98051ab21dbaf838bcf76af9b49de4aa1d3`; upgrade342ms/15 lock samples. Earlier results below remain historical; current proofs are in implementation-evidence.md. No demo target migration is authorized.

Reviewed 2026-11-23. Scope: owner-authorized **synthetic local demo dataset** from [G1 evidence](data-preflight-evidence.md), disposable PostgreSQL17 only. Official demo target remains V27/READ ONLY. No target V28 rollout is authorized or performed.

## Resources and source

Owned rehearsal Compose project `admin013-2c3a577f-rehearsal`, container `757e50527a90bbf096c37c62a6fc027006b21e98d2f489b43013b92caa7d9f9e`, PostgreSQL17.11. Reused existing Compose PostgreSQL image/health checks/project volume. `admin013_rehearsal` was restored from the exact G1 reference backup; `admin013_fresh28` and `admin013_restored28` are separate new databases inside this disposable container. Neither the frozen reference nor the demo/audit copy was migrated.

Operator support: `demo/src/test/java/com/example/demo/catalog/audit/CatalogDemoRehearsal.java`. It verifies registry/Compose ownership and the rehearsal suffix before connecting, exposes no demo-target migration action, requires G1 evidence and retains before/after/restore results. Private command inputs/results: `.private/admin013-2c3a577f/`. No historical V1–V27 migration changed; all pre-run source hashes rechecked at resumption.

## T014 execution and corrections

1. Initial V28 migration failed transactionally: expression-index construction uses a restricted search path and the SQL name-key function could not resolve `catalog_name_trim`. V28 now calls **`public.catalog_name_trim(value)`**; immutable/strict/parallel-safe properties, exact whitespace and LOWER semantics are unchanged. Only unapplied-to-target V28 changed. Fresh/upgrade tests and a restricted-search-path regression pass. No Flyway repair or constraint weakening.
2. A successful V27→V28 upgrade was followed by a tooling failure: the allocation script attempted to change isolation after earlier reads. Corrected the rehearsal helper to finish the version-aware read-only transaction before running the approved script's independent REPEATABLE READ/READ ONLY transaction. The disposable rehearsal alone was reset from the immutable backup; official data untouched. Raw failed-run logs retained.
3. Final representative rehearsal `/tmp/admin013-final-rehearsal3.log` succeeded: V27→V28, independent empty V1→V28, upgraded V28 backup→new restored V28 database. Reported upgrade interval347ms;15 actual lock samples, zero observed waiters. These small synthetic measurements are not deployment-size performance evidence or a guarantee of zero contention.
4. Existing migration checkpoint initially failed due to V28 above; rerun after correction had52 tests,51 pass/1 failure, zero errors/skips. The only remaining failure was VoucherMigrationIT's obsolete latest-version27 assertion after successfully reaching28. Updated that expected version to28; all owner/receipt/cart/zero-grant/history assertions retained. Isolated affected rerun passed10/10, zero failures/errors/skips. Other51 successful executions are not relabeled as a new full rerun.

Exact commands from repository root:

```bash
bash demo/mvnw -o -f demo/pom.xml -Dtest=AdminCatalogMigrationSourceTest -Dit.test=AdminCatalogMigrationIT test failsafe:integration-test failsafe:verify
bash demo/mvnw -o -f demo/pom.xml -Dit.test=CatalogMigrationsIT,CatalogEnhancementMigrationIT,CatalogSearchMigrationIT,CartMigrationIT,OrderMigrationIT,CheckoutMigrationIT,InventoryMigrationIT,VoucherMigrationIT,ReviewMigrationIT,CatalogEnhancementMappingsIT,CatalogEntityMappingsIT test-compile failsafe:integration-test failsafe:verify
bash demo/mvnw -o -f demo/pom.xml -Dit.test=VoucherMigrationIT test-compile failsafe:integration-test failsafe:verify
```

Focused first command:3 unit/source tests +9 PostgreSQL migration tests PASS. Second command's corrected-state run `/tmp/admin013-final-checkpoint-a2.log`:52 tests,1 obsolete assertion failure as described. Third command `/tmp/admin013-final-resume-voucher.log`:10 PASS,0/0/0,2026-11-23 01:32:19+07. The checkpoint's **61 unique integration scenarios** across12 classes are green using these attributable incremental results; repeated executions are not summed as distinct tests and no unrestricted full Maven verification is claimed. Risk rationale for affected-only rerun: final fix changes only a test's latest-version oracle; no production code, migration, fixture content or historical assertions changed after the other successful suites.

Negative scenarios include normalized root/brand collision, imageless/invalid-price Published data, category cycles, invalid galleries and real migration transaction rollback with absent new columns/gate and unchanged old rows/history. Legacy search migration successor/recovery/checksum and cart/order/checkout/inventory/voucher/review migration tests remain executed, not excluded. Both mapping suites initialize Hibernate with validate against V28 and pass.

## Backup restoration procedure

Database bytes and image bytes are separate snapshots. G1 private backup checksum manifest pairs `reference-v27.dump`, source manifest/identity and3 validated image checkpoints. Existing checkpoint utility captured3 objects and restored3 into a separate isolated bucket; restored-copy actual HEAD/GET/content audit passed.

Historical receipt CHECK functions depend on schema resolution during restore. Preserve all constraints/triggers: produce SQL using `pg_restore --no-owner --no-acl --file=- <owned-backup>`; verify exactly one session statement `SELECT pg_catalog.set_config('search_path', '', false);`; change **only that statement** to `SELECT pg_catalog.set_config('search_path', 'pg_catalog,public', false);`. Apply the reviewed generated artifact to a **new empty owned database** with `psql -X -v ON_ERROR_STOP=1 --single-transaction`. This changes no function/constraint or migration source. Retain original dump, generated SQL, checksums and exit/log evidence. Never disable triggers, use Flyway repair or accept a partial restore. Restrict this procedure to approved trusted public-schema backups.

The new V28 normalizer's qualified helper also survives index recreation under restricted search paths. Both V27 representative restore and V28 upgraded-backup restore have passed using the explicit session procedure. `CatalogLocalReferenceIT`'s earlier minimal receipt-free snapshot remains a narrower independent proof, not the receipt regression result.

## T088 distinct post-rehearsal integrity review

Private `rehearsal-integrity.json` contains40 pre-existing relation count+MD5 comparisons **beforeV27 == afterV28 == restoredV28**. Only approved additive columns are removed from old-row comparison (Product version/restore provenance; Category/Brand versions; Variant enabled; Image active). Old Flyway entries1–27 remain exact including checksums/history; V28 is checked separately. New singleton is checked separately, never hidden as a data mismatch.

Specific protected relations include4 Products/variants,5 categories/1 brand,2 image associations/1 shared upload/1 source link,1 cart/item,4 Orders/items including historical-zero unmanaged snapshots,2 immutable receipts,4 balances and3 ACTIVE/CONSUMED/RELEASED allocations. Full-row hashes include recipient/item/receipt payloads without publishing them. Specifications/options and every historical source/upload identity remain unchanged. No price/name/status or relationship rewriting occurred.

Fresh/upgrade/backup-restored complete schema signatures match after harmless pg_dump restrict-token/comment and known equivalent cast deparsing canonicalization. Historical pre-lock-correction schema SHA256 `dfe432ccbd498164a0a22879540aad34791192c4540b626f73553aa5c389f795` (superseded by the current-source signature at the top). Validated constraints/FKs retained; default versions0, restore timestampsnull, variantsEnabled, imagesactive, one version0 taxonomy singleton. Version-aware Published readiness query returns0 defects; unchanged allocation supplement returns empty violations on upgraded and restored DBs in separate read-only transactions. The V27-only status query was not misused against V28. Negative migration cases separately prove atomic rollback of data/schema/history.

## Explicit G2 decision

| Required condition | Evidence / decision |
|---|---|
| Actual T009 prerequisite and named representative copy | G1 scoped PASS; exact frozen reference backup and source/copy fingerprint provenance |
| Fresh installation | Empty V1→V28 operator drill and focused migration tests PASS |
| Representative upgrade | Identified restored synthetic V27→V28 PASS; no unrelated-production claim |
| Collision/invalid-data rejection and rollback | Executed AdminCatalogMigrationIT negative schedules PASS; no old schema/data/history leakage |
| Existing migration compatibility | All61 unique scoped checkpoint scenarios green incrementally; actual report/log attribution above |
| Mapping compatibility | Both current Hibernate mapping suites PASS |
| Backup and recovery | Actual V27 representative and V28 upgraded backup restores, matching schemas/rows/history; matching storage checkpoints restored/audited |
| Distinct post-rehearsal integrity | T08840-relation hashes, additive defaults, FKs, snapshots/receipts/allocations and schema equality PASS |
| Historical migration immutability | V1–V27 source hashes unchanged; live old history/checksums preserved |

**G2 VERIFIED for this local synthetic demo scope.** No outstanding G2 criterion identified in the approved task/release contracts. This is independent of G1 and is not G3 or authorization to migrate/deploy the official target. G3 remains BLOCKED pending separate owner rollout approval, fresh target-bound evidence, backup readiness and coordinated compatible reader/writer plan. C01 remains OPEN/UNASSIGNED; production release blocked. Larger real data, other developers' databases, external storage and production remain unverified.


## Final source/artifact binding

Final V28 file SHA256 `ea55e11f01868f6abd1f3d5b8d435099c4c3184ae0c561ac90378ef9b5276fe1` matches source, compiled resources and the tested executable JAR. Recovered current rehearsal dump raw SHA256 `3c8d8fd85f7efec1ce8be8bab959a98051ab21dbaf838bcf76af9b49de4aa1d3` matches rehearsal-integrity.json; all40 before/after/restored old-relation fingerprints are equal and the final helper qualification/NO KEY UPDATE body is present. The final unrestricted run independently passed AdminCatalogMigrationIT9, CatalogConstraintsIT8, AdminCatalogRepositoryIT7 and all remaining migration/consumer suites. This confirms the recorded corrected-source G2 binding without rerunning or touching stopped target resources during finalization. Current V1–V27 have no Git diff; G3 target execution remains unauthorized.
