# Local development expansion to 1,000 products

Target: `backend@127.0.0.1:55432`; Compose `catalog-pilot-20261029-01`; container `catalog-pilot-20261029-01-postgres-1`; volume `catalog-pilot-20261029-01_postgres-data`. PostgreSQL 17 and exact successful Flyway V1–V9 verified through the importer before and after writing.

One transactional execution inserted 971 DRAFT Products and 971 variants. No existing Product was updated or deleted. Final counts: 1,000 Products, 1,000 variants, 31 categories, 0 images; 5 PUBLISHED and 995 DRAFT. The original 29 products, their variants, categories and image rows match the before-import snapshot. All new rows match approved UUIDs, SKUs, whole-VND prices, empty options and category mappings. No case-insensitive SKU duplicates, duplicate approved source identities, orphan products or missing variants were found.

## Selection

All 15,236 normalized structural candidates were considered deterministically by retailer, source product ID and URL. Rules reconcile source identity, name, description, category, SKU and exact price with the read-only crawler SQLite snapshot; require a single InStock Offer, positive whole-VND price and single empty-options proposal; exclude repeated names, used/service listings and potentially conflicting capacity text. No missing facts were invented. Current retailer prices and availability were not checked.

Categories use the longest matching existing retailer/path ancestor through `approved_category_uuid`. This deliberately retains a sometimes broad existing category rather than creating 446 proposed source hierarchy nodes. No new categories were inserted. More detailed taxonomy is future work.

Selection statistics:

- suitable: 3501
- already_imported: 29
- not_single_instock_offer: 8863
- no_existing_taxonomy_ancestor: 1855
- used_or_service: 783
- structural_or_repeated_name: 183
- weak_text: 5
- configuration_text_uncertain: 17

Selected: 971; suitable but outside this batch: 2530. All 971 new records are DRAFT with source-backed SKUs and historical source prices explicitly approved as LOCAL DEMO prices.

## Backup and local artifacts

The backup `tools/catalog-import/output/catalog-before-1000.dump` was produced with PostgreSQL `pg_dump -Fc` before writing and its archive directory was read with `pg_restore -l`. It was not restored into another database. It and the executable manifest are Git ignored and mode 0600.

Local artifacts under `tools/catalog-import/output/`:

- `expand_local_1000.py`: deterministic selection and source reconciliation performed for this batch; requires the 29-product pre-import state.
- `local-development-additional-971.csv`: exact approved source identities and development decisions, separate from all previous manifests.
- `additional-971-selection.json`: selection statistics.
- `before-1000-rows.json`: original rows used for preservation checks.
- `additional-971-dry-run.json`, `additional-971-preview.json`, `additional-971-execute.json`: validation and execution evidence.
- `additional-971-idempotency.json`, `verify_1000.py`, `final-1000-verification.json`: post-import evidence.

Offline dry-run: 971 eligible, 0 pending, 0 rejected, no duplicate proposed SKUs. Read-only preview passed exact target/history/schema and record conflict checks. Read-only repeat preview reported 971 unchanged products and zero new rows; no write-mode rerun was performed.

API at `http://127.0.0.1:18080`: anonymous listing 200 with exactly the original five PUBLISHED IDs and page=0, size=20, totalItems=5, totalPages=1. All five detail responses matched names, categories, variants and VND demo prices. Original 24 DRAFT detail requests and ten distributed new DRAFT samples returned 404. API implementation and contracts were unchanged.

Database size measured with `pg_database_size(current_database())`: 9,647,795 bytes (size may change with normal PostgreSQL maintenance).

Manifest SHA-256: `b50d5fddbd79c102b6c16d23f6f033aea573cf596004380c3fb58749104cdfaf`.

## Automated verification

- surefire: {'tests': 174, 'failures': 0, 'errors': 0, 'skipped': 0}
- failsafe: {'tests': 171, 'failures': 0, 'errors': 0, 'skipped': 0}
- Normalizer: 12 tests, all passed.
- Importer PostgreSQL integration suite: 19 tests, all passed, using a separate PostgreSQL 17 Testcontainers database and real Flyway bootstrap.
- `mvn -B -ntp clean verify`: BUILD SUCCESS, including PostgreSQL/Flyway integration tests. No tests were disabled.
- Logs: `/tmp/catalog-1000-maven.log`, `/tmp/catalog-1000-normalizer.log`, `/tmp/catalog-1000-importer-tests.log`.
