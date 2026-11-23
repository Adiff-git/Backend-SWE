# G1 — Authorized local demo compatibility evidence

Decision: **PASS for the exact synthetic local demo snapshot below**, 2026-11-22. No real-world production/team dataset compatibility or V28 rollout authorization is implied. Source branch `features/AdminCatalogManagement`, HEAD `86512d7ab1e0bb262f783c05c0051d6be46b1983` plus the preserved working tree; preparation source is `catalog/audit/CatalogDemoPreparation.java` under test sources.

## Authorization, identities and provenance

The owner's Final Implementation instruction explicitly designates a **new separate local demo target**, authorizes its preparation/read-only verification and isolated rehearsal, and permits representative synthetic fixtures when approved real data is unavailable. No existing developer database or archive was selected/imported. The implementer authored the labeled synthetic fixture under that authorization. It is the entire current demo dataset, not a sample claimed to represent an unrelated populated deployment.

Run/project prefix: `admin013-2c3a577f`. PostgreSQL **17.11**, UTF8, `en_US.utf8` collation/ctype, public schema, 344 columns and311 public constraints in each V27 database. Existing `compose.dev.yaml` PostgreSQL17 pinned image, health checks and dedicated project networks/volumes were reused without modification.

| Purpose | Database / Compose project | Exact container ID |
|---|---|---|
| Frozen V27 reference | `admin013_reference` / `admin013-2c3a577f-reference` | `3b87a0cb64a3e36b8b0d14ebdabc806d026609803db48a602598c6f1e440f396` |
| **Official local demo target** | `admin013_demo` / `admin013-2c3a577f-demo` | `52bf5d7c42fec0d9c129f6d8e51e113de2a9ddfad464a01e7c3f782bb6bd06b1` |
| Immutable restored audit copy | `admin013_copy` / `admin013-2c3a577f-copy` | `2a8ad9f715db02fdab0cc8694f5953395dcda46f27576b0c29296f7429557e83` |

Storage project `admin013-2c3a577f-storage`: actual LocalStack service container `31bc9d2beb2bf2d5de5c95670d44635a585447745f329f8c167686bc0805b789`; signed gateway `5dc1a74588bf0b347d3dc9079c225c7365a6b2a9f8980538fc58bd091f1a210f`. The initial recovery checkpoint listed those two service IDs in reversed roles; this inspected service-label mapping corrects that description. Only these new isolated buckets were used.

Private registry, full identities, source/reference/copy schema dumps, complete SQL result sets, JSONL audits, checksums and backups: ignored `.private/admin013-2c3a577f/`. Do not publish its credentials. Credentials are newly generated local values, never copied from application configuration. Container labels/IDs are verified before preparation. No Spring/JPA process, AI service, application scheduler or external object storage was started for G1.

## T003 — Identity, schema and history

Reference explicitly initialized with Flyway target27; demo and audit copy restored from its frozen custom-format backup. All 27 successful V1–V27 SQL history entries and checksums match packaged resources via the existing auditor manifest check. No V28 fields/table exist. Full schema dump comparisons between source/demo/copy are equal after removal of pg_dump's randomized restrict tokens/comments and narrow canonicalization of equivalent varchar-array-to-text CHECK expressions. Functions, triggers, indexes, constraints and table definitions remain present; none was disabled or repaired. Schema dumps and empty comparison reports retained privately.

## Dataset and backup

Representative **synthetic** fixture:5 categories (including equal child names under different parents),1 brand,4 products (2 Published,1 Draft,1 Archived),4 variants,2 valid gallery PNG references (1 legacy null source,1 completed shared upload),1 source link,1 customer/cart/item,4 orders/items (pending, completed, cancelled, historical unmanaged zero-price),2 immutable historical receipt rows,4 inventory balances and3 reservations (ACTIVE/CONSUMED/RELEASED). Draft preparation price0 is deliberately allowed; Published prices1000/2000 are positive. No required universal specification fact was invented. Real PNG bytes were uploaded through signed requests, not metadata-only images. An additional unreferenced synthetic object is retained, never deleted.

Reference backup SHA-256: `fe995100504f873385e0dac337760bfbb8fd0fb4b8e6b8b8544cb92a3b412bf2`. Reference→dump→demo/copy row/history fingerprints match. Three image checkpoints were captured using the existing restoration utility, checksummed with the DB backup and restored into a separate new bucket. The copy's auditor used that restored bucket; both referenced objects passed real HEAD/conditional bounded GET, metadata/decode/dimension and SHA-256 checks.

Initial restoration exposed a historical search-path dependency: receipt CHECK functions return invalid when called with pg_restore's empty search_path. The successful restore uses pg_restore's SQL output with the **single session-level** `set_config('search_path', '', false)` changed to `pg_catalog,public`, then executes using `psql -X -v ON_ERROR_STOP=1 --single-transaction` on a new empty owned database. No function/migration/constraint is changed or disabled. The failed initial demo preparation was recreated before any application use; only new synthetic setup was affected. Raw failures and successful restore logs retained. Repeat this reviewed session setting for this backup; never use trigger disabling or unchecked partial restore. Final schema equality and data fingerprints verify the result.

## T004–T007 — Complete read-only SQL and substantive review

Both approved scripts executed on reference, demo and copy using non-superuser `admin013_reader`, public USAGE and SELECT only. Inspected privileges: schema CREATE=false, products SELECT=true, INSERT=false. Scripts explicitly use REPEATABLE READ/READ ONLY,60s statement/2s lock limits and ROLLBACK. All27 baseline result sets and7 supplement result sets retained, not truncated. Before/after full row/history/column fingerprints unchanged.

- T005: normalized sibling/root collision/empty-key and cycle result sets empty. Equal `Portable` names under distinct parents are valid. Laptop/Tablet each resolve uniquely. Direct product-category assignments and existing parent references intact; no taxonomy data was changed during review.
- T006: global normalized Brand/name/code conflicts absent; optional product brand is null on one Archived record; all other references resolve. No merge/rename/delete or eligibility rule was introduced.
- T007: unknown lifecycle, zero-child legacy aggregates, SKU collision/basic invalidity, duplicate/invalid options, malformed fact envelope, Published sale/gallery defects, duplicate positions and orphan result sets empty. One null-source legacy image is expected informational output. All six allocation supplement violation result sets empty: exact item quantity/attribution, lifecycle/times, live ACTIVE references, cardinality and reserved counters. Historical zero-price snapshots are retained while current Published prices are positive. Row counts and order/receipt/allocation fingerprints retained; no remediation necessary.

## T009 — Typed applicability and actual object proof

Plain standalone auditor, no Flyway/JPA/application bootstrap. Each audit used reviewed27-line manifest, exact database/server identity, SELECT-only credentials and explicit read-only transaction/rollback. All phases finished within operational budgets:4 variants,2 images, zero findings, final COMPLETE and process exit0. The restored copy read matching restored image objects. No network write occurs in the audit; fixture uploads/checkpoint restoration occurred beforehand. Storage Community LocalStack is not an IAM enforcement boundary; evidence proves real signed local HEAD/GET/content behavior, not production IAM policy. Existing auditor exposes no write capability.

| Dataset | Final audit run ID | Result |
|---|---|---|
| Reference | `ef8e8e08-a39f-4a2f-9857-4e2861014927` | COMPLETE /0 violations |
| Official demo | `9ad3f34a-f066-43ac-a201-6aa35e64626a` | COMPLETE /0 violations |
| Restored copy | `1a00c071-71af-44d9-8279-6e3022894347` | COMPLETE /0 violations |

## T010 — Formal G1 decision and freshness

**PASS scoped exclusively to this owner-authorized synthetic local demo snapshot**: exact identity/schema/history/collation, both SQL scripts, complete allocation review, shared typed/applicability validation, bounded object content, restored-copy/storage provenance and unchanged fingerprints all verified. No incompatibility/remediation decision is outstanding. Reference/demo/copy are now frozen with database default transaction read-only; no application writer has connected. Re-run G1 after any data/schema/object change or target replacement; do not combine new target data with this snapshot's proof. Separate owner authorization is still required for V28 demo rollout. G2 is not implied; G3 and production release remain blocked. C01 stays OPEN/UNASSIGNED.
