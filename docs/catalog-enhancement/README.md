# Catalog Enhancement

See [architecture](architecture.md) for the canonical model and normalization decisions, and the [enrichment runbook](../../tools/catalog-import/ENHANCEMENT_RUNBOOK.md) for exact commands and recovery gates. Feature requirements, plan, tasks and evidence are under [005-catalog-data-enhancement](../../specs/005-catalog-data-enhancement/).

This feature does not implement search endpoints. Existing public DTO fields remain unchanged; Brand and typed Variant metadata are persisted internally. Source provenance is never public. Category aliases and distribution-qualified brand labels require explicit review. No populated database migration or enrichment has been authorized.

## Existing pilot eligibility

The immutable 1,000-product seed and source snapshot match by exact deterministic IDs for all 1,000 Product/Variant pairs; there were zero ambiguous pilot matches. The [measured eligibility summary](pilot-eligibility.json) records 824 approved-brand candidates and 138 typed-specification candidates; 176 records retain unresolved Brand labels, and 998 matched records contain at least one unmapped technical attribute. These are **source candidates**, not confirmed live target updates. The actual populated database was not contacted. Its current Product/Variant counts and existing metadata values require a separately authorized read-only preview before an apply manifest can be approved. The ignored pilot JSONL under `tools/catalog-import/output/enhancement/pilot/` preserves exact source facts for that review.

The [disposable PostgreSQL verification](isolated-enrichment-verification.json) loaded the original 1,000-product seed, enriched 824 Brand relationships and 138 typed specification documents, inserted 1,000 source receipts, preserved all protected fields and Product/Variant counts, and made 1,000 no-op decisions on replay. This proves isolated behavior only.

The earlier [full-source normalization summary](dataset-validation.json) remains as historical data-quality evidence. Full dataset import and full dataset normalization are not required for the current enhancement scope.

## Search and Filter readiness

The existing Product name/description and PUBLISHED status support later keyword matching and visibility checks. Existing Category relationships support direct-category filtering; hierarchy expansion still needs a separate rule. Variant selling prices and option JSONB support later price and selected-option predicates, with same-Variant matching required when combined. Existing zero-based pagination and UUID ordering remain intact. Name/price sorting and all new filter queries are future Search & Filter work.

V10 makes Brand and typed Variant technical specifications persistable and queryable. The reviewed attribute registry marks specific keys as filter eligible, but the **populated database is not yet enriched**. In the immutable pilot seed, 824 of 1,000 source identities have an approved Brand mapping and 138 have at least one parsed technical specification. Source data alone cannot prove current live coverage. The remaining 176 records have unresolved Brand labels, and 998 records have at least one unmapped technical attribute; those values need review or will remain absent from normalized filters. No global electronics attribute taxonomy or variant grouping was inferred.

See the [verification record](verification.md) for exact test commands, counts, and the pending populated-database gate.

The latest [Brand review](brand-review.json) and [technical attribute review](technical-review.json) record the newly approved direct labels, per-attribute pilot coverage, and values deliberately left unresolved.
The [normalization review](normalization-review.md) explains the evidence and limits of each newly added rule.
