# Catalog enhancement import and enrichment

Run from the repository root. No command below is permission to alter a populated database. The feature tests only disposable PostgreSQL. Obtain separate authorization for a populated development/shared target. Do not use old seed restore to replace existing products.

## Prerequisites

Java 25/Maven, PostgreSQL 17, Docker for isolated tests, Python environment with existing `requirements-import.txt` (and existing testcontainers test dependency). Raw source remains at sibling `electronics-crawler/data/catalogue.sqlite`. Registries live under `tools/catalog-import/registries`.

Apply new Flyway V10 using normal application deployment only after reviewing target/history. V1–V9 are immutable. The legacy importer now verifies the exact V1–V10 checksums; it does not bypass history validation. Immutable portable seed manifests pinned to the complete V1–V9 checksum set remain accepted; the target database must still have the exact V1–V10 history.

## Prepare existing pilot metadata (offline, read-only source)

```bash
python tools/catalog-import/prepare_enrichment.py --seed tools/catalog-import/output/catalog-dev-v1/catalog.json --input-db ../electronics-crawler/data/catalogue.sqlite --output-dir tools/catalog-import/output/enhancement/pilot
```

Review every unresolved brand and value report. The category registry is not needed for metadata-only enrichment; existing Product category IDs remain unchanged. Keep raw_source_json and source hashes in the output for traceability. Preserve output/manifests/backups durably outside Git; do not rely on `/tmp`.

The selector reads only source rows with exact pilot source identities. It does not require normalizing the full corpus. This is an offline source/seed comparison, not a live target count. The immutable seed may differ from current publication/media state. Do not approve its output without a current target preview.

## Target dry run

Use a disposable PostgreSQL first. The target URL must be loopback with explicit port and database; disposable names end `_test`, local-development target must be `backend`. `CATALOG_IMPORT_DSN` is read from the environment and never logged. Example target arguments below must be replaced with the verified target values; no secret DSN is committed.

```bash
python tools/catalog-import/enhance_import.py \
  --records tools/catalog-import/output/enhancement/pilot/pilot-enrichment-candidates.jsonl \
  --target-kind disposable --expect-host 127.0.0.1 --expect-port 55433 \
  --expect-database catalog_enhancement_test \
  --confirm-target catalog_enhancement_test@127.0.0.1:55433 \
  --report tools/catalog-import/output/enhancement/target-dry-run.json
```

Default mode starts a database READ ONLY transaction and rolls it back. No Product/Variant/Brand/source-link writes occur. Every normalized row is regenerated from preserved raw JSON and the current registry, and identity/hash consistency is checked before database access. `--registry-dir` selects the same reviewed custom registry directory used for normalization.

Review item operations: ENRICH, UNCHANGED, UNMATCHED, CONFLICT. ENRICH never changes name/category/status/SKU/price/options/media. Optional missing values never clear existing values; a field conflict prevents the item write. Source price and SKU quality do not change existing prices or SKUs. Unmatched source identities never create Products or Variants.

## Approval and recovery prerequisites

Make a JSON array containing ONLY approved dry-run items. Retain `source`, `source_key`, `source_payload_sha256`, `normalized_payload_sha256`, `operation`, and `expected_before_sha256` unchanged. Do not approve CONFLICT or UNMATCHED items. Approval binds actual normalized content, not a product name. Any source/registry/target drift requires a new dry run and review. No matching by product name is implemented.

Before approving an apply, identify the exact target host, port and database, inspect its successful Flyway history, and record `SELECT count(*) FROM products` and `SELECT count(*) FROM product_variants` from that target. Compare the target dry-run `ENRICH`, `UNCHANGED`, `UNMATCHED` and `CONFLICT` counts with the source eligibility report; the source-side 151 Brand and 102 specification candidates may differ from the target's actual missing metadata. Approve only exact `ENRICH` items after inspecting intended before/after values. There must be no Product or Variant count change after apply. Capture the before counts, IDs, SKUs, prices, category IDs, statuses, option documents and image relationships for reconciliation.

Before apply, obtain and independently verify a database backup with `pg_dump --format=custom` using the confirmed DSN. Keep a recovery document identifying target, schema revision, backup hash, before-values, reviewer and restoration/compensation procedure. The CLI checks backup file SHA-256 but cannot establish that its contents represent a recoverable database; an operator must verify `pg_restore --list` and a disposable restore. Do not restore a full snapshot over concurrent unrelated changes.

## Controlled apply (only after target-specific authorization)

```bash
python tools/catalog-import/enhance_import.py \
  --records tools/catalog-import/output/enhancement/records.jsonl \
  --approvals /secure/reviewed-approvals.json \
  --target-kind disposable --expect-host 127.0.0.1 --expect-port 55433 \
  --expect-database catalog_enhancement_test \
  --confirm-target catalog_enhancement_test@127.0.0.1:55433 \
  --apply --confirm-apply catalog_enhancement_test@127.0.0.1:55433 \
  --backup /secure/verified-before.dump --backup-sha256 REVIEWED_SHA256 \
  --batch-size 100 --report /secure/enrichment-receipt.json
```

Batch size is bounded to 1–1000. Whole-manifest source/duplicate/approval/target preflight runs before the first write. Each product item rechecks target state under row locks. Product groups are never split. Each batch commits metadata and source receipts together. Brand/source identity conflicts fail; metadata is not overwritten.

## Reconciliation and retry

Persist reports after each successful batch. On failure, the report retains earlier committed item counts and an error; the failing batch is atomic. A connection error during commit can leave outcome uncertain: query source receipts and target metadata before retry. Re-executing the same approved manifest recognizes equal completed items as UNCHANGED, after a prior committed enrichment. Receipt ownership uniqueness prevents one source identity silently moving to another variant.

Check before/after Product/Variant IDs, name, category, status, SKU, price, options and image IDs/keys, plus added brand/specification/source links. Equal metadata is a no-op; changed nonempty metadata is a conflict. No automatic retry loop is implemented. Operator retries replay reviewed work after reconciliation.

After apply, run a fresh read-only target preview and compare the counts and protected-field snapshots. Confirm every approved source identity has one source receipt owned by the expected Variant, and confirm Product and Variant row counts equal the recorded pre-apply counts. Keep the report, approved manifest hash, backup verification, Flyway history and reconciliation evidence together. If a batch failed, inspect committed receipts before retrying; do not blindly replay a stale expected-before manifest.

Any compensation must compare current values against the recorded after-values and preserve later edits. Do not delete/recreate Products or Variants. This tool performs Brand/specification enrichment only; it never corrects categories or prices.

## Isolated test commands

```bash
python -m unittest discover -s tools/catalog-import -p test_enhance_normalize.py -v
python -m unittest discover -s tools/catalog-import -p test_enhance_import.py -v
python -m unittest discover -s tools/catalog-import -p test_import_postgres.py -v
```

PostgreSQL tests require the Maven dependency classpath at `/tmp/backend_catalog_import_classpath.txt` as established by the existing catalog-import README and Docker access. They create synthetic products/media only in disposable containers. They do not import or enrich the populated pilot.

## Disposable 1,000-product verification

After generating the exact pilot candidate file and Maven classpath, run:

```bash
python tools/catalog-import/verify_pilot_enrichment.py --seed-dir tools/catalog-import/output/catalog-dev-v1 --records tools/catalog-import/output/enhancement/pilot/pilot-enrichment-candidates.jsonl --classpath /tmp/backend_catalog_import_classpath.txt --report tools/catalog-import/output/enhancement/pilot/isolated-enrichment-verification.json
```

This command creates a disposable PostgreSQL 17 container, applies Flyway V1–V10, loads the immutable pilot seed, enriches only matched existing records, checks protected core fields, and replays the metadata. It never opens the populated database. The seed has 1,000 Product/Variant pairs; current live counts and metadata are not inferred from it. The measured result is preserved in `docs/catalog-enhancement/isolated-enrichment-verification.json`.
