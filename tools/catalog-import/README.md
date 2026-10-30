# Catalog normalization and approval-gated PostgreSQL import

The existing private local pilot now has all 1,000 products PUBLISHED for Frontend testing. See [local publication and status-only rollback](LOCAL_PUBLICATION.md), [its execution report](review/1000-products-local-publication.md), and the [root onboarding README](../../README.md). Immutable seed v1 and v2 fixture packages retain their original five-PUBLISHED state; do not reload them to reset the published pilot.

The standalone Python 3 normalizer reads `../../../electronics-crawler/data/catalogue.sqlite` by default. It opens the crawler database with SQLite `mode=ro` and `PRAGMA query_only=ON`. It never connects to PostgreSQL, runs Flyway, calls S3, or modifies Product Catalog application code.

From `Backend-SWE`:

```bash
python3 tools/catalog-import/normalize.py --dry-run
python3 tools/catalog-import/normalize.py
python3 -m unittest discover -s tools/catalog-import -p 'test_normalize.py' -v
```

For a fixed copied snapshot, specify `--input-db /absolute/path/catalogue.sqlite`. Use `--output-dir /absolute/path/output` to choose another output directory. `--dry-run` reads and validates every row, prints a JSON summary, and creates no files. The default output is `tools/catalog-import/output/`, which is ignored by Git. The normal run writes exactly five files there:

| File | Contents |
|---|---|
| `normalized_categories.csv` | Source and original category path, proposed path and deterministic reference, count, confidence, manual approval flag, example source ID and URL. Multiple original paths can propose the same reference; neither names nor references are database categories yet. |
| `normalized_product_candidates.jsonl` | One proposed DRAFT product per structural candidate, with source identity, UUID, category reference, source variant evidence, review flags, external image URL metadata, and reason codes. |
| `pending_or_rejected_records.jsonl` | All other source rows with the same trace fields, primary outcome, and all applicable reasons. |
| `validation_summary.json` | Reconciled outcome totals and reason counts. |
| `normalization_report.md` | Human-readable deterministic summary. |

## Mapping rules

The source SQLite `products` columns are `id`, `site`, `url`, `updated_at`, and `payload`. The tool reads `id`, `site`, `url`, and JSON `payload`; it checks payload `id/source/source_url` against the SQLite row. Only the configured CellphoneS and TGDD source/host pairs pass. The payload's `name`, `description`, `sku`, `offers`, `category`, `breadcrumbs`, and `images` provide the candidate mapping. Source identifiers and URLs remain in every record.

Product UUIDs are UUIDv5 with namespace `8f776a17-4fe9-4b13-82af-3e134ba6e55d` and identity string `source|source_url`. This deliberately survives a source SKU change. URL changes need an explicit identity reconciliation before import; do not silently treat them as an update. Source URLs are not normalized or stripped by this tool. A duplicate source identity is `INVALID`. Category references are UUIDv5 from namespace, source, and the exact proposed path JSON. These are review keys, not inserted database IDs. Names are normalized for Unicode NFC, HTML entity decoding, tag removal, and whitespace. Repeated names are flagged without merging records.

CellphoneS category hierarchy comes from breadcrumbs after the known site root and before the matching product name. A missing or mismatched final breadcrumb is flagged. TGDD uses its explicit `category` as a single-level proposal because its breadcrumb labels include promotional text; the original breadcrumb path remains visible in the category CSV. The tool never merges similar names across sources or paths. Every category row requires manual approval, including hierarchy and the choice of a target category. The target schema permits duplicate category names, so a future importer must match by an approved identity map, not name alone.

Only `Offer.price` in `VND` with a finite, nonnegative, integral decimal representation is an exact source price. `AggregateOffer.low_price` and `high_price` never become prices. Zero is held with `ZERO_PRICE_REVIEW`; the current catalog specification does not forbid zero by itself. Source SKU is preserved exactly after display whitespace normalization and is **not** asserted to be a manufacturer SKU or approved catalog SKU. Placeholder values are held. Case-insensitive SKU collisions are detected over nonplaceholder source SKUs. No source record supplies an approved selling price or approved target SKU. For a single priced source `Product` with no variant group, a proposed empty options map is only a review proposal; it is never an approved variant. No record is marked `PUBLISHED`.

Primary outcomes are exclusive: malformed identity/payload/name or duplicate source identity is `INVALID`; range-only records are `CATEGORY_OR_FAMILY_ONLY`; other blockers such as zero price, placeholder SKU, or category problems are `NEEDS_ENRICHMENT`; otherwise records are `STRUCTURAL_CANDIDATE`. Approval reason codes remain even on structural candidates. An outcome is a normalization decision, **not** permission to insert into PostgreSQL. All variants need a real SKU and approved selling price before even a DRAFT product can satisfy Flyway V1/V3. Images are only external URL trace metadata; no `ProductImage` rows, object keys, signed URLs, or image transfers are produced. The teammate's Media/S3 workflow must provide verified private object keys and metadata later.

## Current snapshot reconciliation

The inspected SQLite snapshot has 20,658 records: 15,236 structural candidates, 2,747 category/family-only, 2,675 needing enrichment, and zero invalid. The earlier 15,236 screen is reproduced. There are 17,911 exact price records, including 2,510 zero-price records; 139 records carry placeholder SKU `0`. There is one case-insensitive SKU collision group if placeholder `0` is counted (138 extra rows), and no collision group among nonplaceholder SKUs. There are 1,467 distinct proposed normalized category paths represented by 1,616 source-path mapping rows. These numbers belong to this inspected snapshot and should be regenerated after a crawl update.

Before any database import, review retailer-price authority, target selling prices, source SKU suitability and uniqueness against the target catalog, product variant boundaries, category mapping, zero-price policy, and which products may be published. The normalizer intentionally contains no database writer; the separate approval-gated importer is described below.

## Fifty-product pilot review

The pilot is a reproducible review sample from `output/normalized_product_candidates.jsonl`:

```bash
python3 tools/catalog-import/prepare_pilot.py
python3 tools/catalog-import/import_postgres.py --dry-run \
  --report tools/catalog-import/output/pilot-dry-run.json
```

`prepare_pilot.py` selects five candidates from each of ten retailer/category groups. It requires a product name, description, source identity, positive exact Offer price, structurally unique nonplaceholder source SKU, clear proposed category path, and a proposed single empty options map. Selection by source ID and URL is deterministic. It refuses to overwrite an existing review file; choose a new `--output` path for another sample. The committed review sheet is `review/demo-products-approval.csv`.

The CSV keeps crawled facts separate from decisions. `exact_source_price_vnd` is a source observation. `approved_selling_price_vnd` starts blank and must be entered by a reviewer; it may differ from the retailer price. `proposed_catalog_sku` initially copies the source SKU as a *proposal*, not as an approved manufacturer or catalog SKU. `proposed_category` is a JSON path, and `proposed_category_reference` is the normalizer's deterministic review identity. Every approval field starts `NOT APPROVED`.

For a row to be written, a reviewer must set **each** of `import_approval`, `sku_approval`, `selling_price_approval`, `category_approval`, and `variant_mapping_approval` to `APPROVED`, enter a positive whole-VND `approved_selling_price_vnd`, and review the proposed catalog SKU, category and single-variant options. `publication_approval` is independent: `NOT APPROVED` imports a DRAFT product; only `APPROVED` imports PUBLISHED. The importer does not infer approval from matching source and selling prices. Unapproved rows remain pending. An approved but incomplete or inconsistent row is rejected with reason codes.

The importer compares every CSV source fact with the normalized JSONL candidate. It uses `source|source_url` for product UUIDv5, then UUIDv5 of that product UUID and `variant|single` for its one variant. For a reviewed proposed category path, each prefix receives a deterministic UUIDv5 from retailer plus path. This preserves hierarchy without matching by name. If a reviewer instead enters `approved_category_uuid`, the importer requires that exact category to exist and creates no path for that row. No database column stores source identity, so a changed source URL requires manual reconciliation before an import. Different names are never used to merge products.

## Importer and database safety

Install the Python database driver in an isolated environment:

```bash
python3 -m venv /tmp/backend_catalog_import_venv
/tmp/backend_catalog_import_venv/bin/pip install -r tools/catalog-import/requirements-import.txt
```

`--dry-run` is the default and does not connect to PostgreSQL. Its concise stdout gives counts; `--report PATH` writes detailed JSON with pending/rejected rows and reasons, duplicate source identities and proposed SKUs, plus proposed categories, products and variants. An offline dry run cannot know conflicts already in a database. `--check-db` adds a **read-only** target check and existing-record conflict inspection. Neither dry-run mode writes database rows.

`--execute` is the only write mode. It requires an externally supplied `CATALOG_IMPORT_DSN` and all target arguments below. The DSN must be a PostgreSQL URI with an explicit loopback host and port. The supplied database name, host, port, and `--confirm-target` must match the URI, and the connection must report PostgreSQL 17 in schema `public`. The `local-development` target is restricted to database `backend`; `disposable` names must end in `_test`. The importer verifies exactly the successful unified Flyway V1–V9 scripts and checksums before any write. It does not create a database, run Flyway, alter migration history, or accept remote hosts. Review any local tunnel carefully: a loopback address alone does not prove the server is disposable.

For a **future** local development import, first inspect that database's history, data and backup, complete the review sheet, and supply the DSN through a private environment mechanism. With the default local Compose database on `127.0.0.1:5432/backend`, use:

```bash
/tmp/backend_catalog_import_venv/bin/python tools/catalog-import/import_postgres.py \
  --check-db --target-kind local-development \
  --expect-database backend --expect-host 127.0.0.1 --expect-port 5432 \
  --confirm-target backend@127.0.0.1:5432 \
  --report tools/catalog-import/output/local-preview.json

/tmp/backend_catalog_import_venv/bin/python tools/catalog-import/import_postgres.py \
  --execute --target-kind local-development \
  --expect-database backend --expect-host 127.0.0.1 --expect-port 5432 \
  --confirm-target backend@127.0.0.1:5432 \
  --report tools/catalog-import/output/local-import.json
```

These commands are **instructions for a later reviewed import**; this task did not connect to a persistent database. Use `--manifest` and `--candidates` together for a different reviewed dataset. Do not run against an existing teammate database with its original V1–V6 history: it fails the unified-history check. The importer refuses history with missing, failed, additional, renamed or checksum-different migrations.

All approved rows execute in one PostgreSQL transaction. The importer creates category ancestors before products, inserts each product and its required variant in the same transaction, and commits only after deferred Catalog constraints pass. Existing deterministic IDs are compared field by field: exact matches are skipped on rerun; differences cause an error and full rollback. A case-insensitive SKU owned by another variant also aborts the transaction. It never silently updates an existing product, category, SKU, or selling price, and never deletes rows. Database constraint failures return a sanitized constraint/SQLSTATE error without credentials.

External crawler image URLs remain source metadata only. The importer inserts no `product_images` or S3 object keys and does no download or upload. Product-specific image upload and its acceptance tasks remain separate work.

## Verification

For importer integration tests, install Python Testcontainers in the isolated environment and build the Java test bootstrap's Flyway classpath:

```bash
/tmp/backend_catalog_import_venv/bin/pip install 'testcontainers[postgres]==4.15.0'
mvn -B -ntp -f demo/pom.xml dependency:build-classpath \
  -Dmdep.outputFile=/tmp/backend_catalog_import_classpath.txt
/tmp/backend_catalog_import_venv/bin/python tools/catalog-import/test_import_postgres.py
python3 -m unittest discover -s tools/catalog-import -p 'test_normalize.py' -v
cd demo && mvn -B -ntp clean verify
```

The importer test suite starts a disposable PostgreSQL 17 Testcontainers instance, applies the repository's Flyway V1–V9 SQL with Flyway, and uses **synthetic, explicitly approved** rows for write tests. It checks real FK and deferred constraints, history checksums, read-only preview, transactional rollback, DRAFT defaults, explicit publication, conflicts and idempotency. The crawler-derived pilot remains unapproved.

Known limits: one approved variant per source product; no product-image import; no automatic reconciliation of changed source URLs, source IDs, prices, category decisions or previously imported product fields; no production target; no automatic approval. The source dataset and normalization summary may change after another crawl, so revalidate a new snapshot before preparing another review sheet.

## Portable frontend seed

See [FRONTEND_SEED.md](FRONTEND_SEED.md) for the versioned Catalog-only snapshot, fresh Docker setup, checksum validation, transactional loading and seed regression tests. The original crawler approval manifest remains unchanged. The generated seed remains under ignored `output/` pending distribution review.

## Product images

For image-bearing portable datasets, see [PORTABLE_SEED_V2.md](PORTABLE_SEED_V2.md). The v2 tool bundles Catalog metadata, ProductImage relationships and checksummed object payloads; its tests restore 1,096 generated authorized images onto the [isolated storage topology](../../docker/localstack/gateway/README.md). Real retailer-image packaging requires separate redistribution permission and remains blocked.

See [PRODUCT_IMAGES.md](PRODUCT_IMAGES.md) for the authenticated Product-specific image API, local SigV4 gateway/durability checkpoints, auditable media-permission gate, bounded API-only bulk import and authorized-fixture tests. Retailer-media execution remains blocked without documented permission.
