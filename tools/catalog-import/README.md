# Catalog normalization (review only)

This standalone Python 3 tool reads `../../../electronics-crawler/data/catalogue.sqlite` by default. It opens the crawler database with SQLite `mode=ro` and `PRAGMA query_only=ON`. It never connects to PostgreSQL, runs Flyway, calls S3, or modifies Product Catalog application code.

From `Backend-SWE`:

```bash
python3 tools/catalog-import/normalize.py --dry-run
python3 tools/catalog-import/normalize.py
python3 -m unittest discover -s tools/catalog-import -p 'test_*.py' -v
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

Before any database import, review retailer-price authority, target selling prices, source SKU suitability and uniqueness against the target catalog, product variant boundaries, category mapping, zero-price policy, and which products may be published. This tool intentionally contains no database writer.
