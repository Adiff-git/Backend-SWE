# Portable Catalog seed verification report

Version: `catalog-dev-v1`. Files are under Git-ignored `tools/catalog-import/output/catalog-dev-v1/`, with a separate `catalog-dev-v1.tar.gz` archive in `output/`. No dataset or credentials were staged.

- Catalog JSON SHA-256: `3b16fd9311d1d7e2c64ead75726123f173c8c05d075acf2508a4114c201764a8`.
- JSON data: 710,299 bytes; archive: 124,244 bytes.
- Manifest contains version, data SHA-256, expected counts, local demo policy and checksums for all nine unchanged migrations. `SHA256SUMS` also covers the manifest. Checksums detect file changes; distribution approval and artifact authenticity remain a maintainer responsibility.
- Only explicit Catalog fields were exported: category IDs/names/parents, source identities, Product/variant IDs, names, descriptions, categories, statuses, SKUs, prices and empty options. No Users, Addresses, Authentication, Media records, credentials or S3 keys are exported.
- Fresh disposable PostgreSQL 17: real Flyway V1–V9 applied; first load created 31 categories, 1,000 products, 1,000 variants; 5 PUBLISHED / 995 DRAFT; 0 ProductImages. Every Product/variant matched the seed.
- Actual second load on disposable PostgreSQL: 1,000 unchanged products; zero duplicates. Conflicting name change was rejected and rolled back.
- Spring Boot local profile started against the disposable database. Listing returned exactly five PUBLISHED products with correct pagination; all five detail responses matched category, variant and price. Ten DRAFT sample requests returned 404.
- Persistent pilot database was accessed only in read-only transactions. All 1,000 products/variants and all 31 categories still match the exported seed; zero images, original five PUBLISHED and 995 DRAFT.

## Tests

- Seed: 7 passed, including checksum, UUID, SKU, negative price, publication split, category cycle, fresh PostgreSQL load/reload/conflict/API.
- Importer: 19 passed.
- Normalizer: 12 passed.
- surefire: {'tests': 174, 'failures': 0, 'errors': 0, 'skipped': 0}
- failsafe: {'tests': 171, 'failures': 0, 'errors': 0, 'skipped': 0}
- `mvn -B -ntp clean verify`: BUILD SUCCESS.

## Review and distribution

Commit candidates are the two new Python seed tools, their test, README link, frontend guide, reviewed local setup and execution documentation, and the exact 45-product pagination proposal. Existing staged work was preserved. No commit or push was performed.

Ignored artifacts include private environment configuration, Java build output, Python caches, dataset JSON/archive, executable approval manifests, database backup, normalized crawler outputs and historical selection/snapshot scripts. The source data remains local until separately reviewed for distribution; no download URL or external publication was created.

The 45-product list is in `pagination-45-publication-proposal.md`. It contains exact UUIDs, names, category IDs/names and approved demo prices, but grants no publication approval.
