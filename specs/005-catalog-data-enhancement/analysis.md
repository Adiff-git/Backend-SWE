# Final consistency and convergence review

Reviewed 2026-10-29 against the implementation, tests, architecture, and final existing-pilot scope.

- FR-001, FR-002, FR-005, FR-010: V10 migration, Brand/SourceLink mappings, Variant technical JSONB, and PostgreSQL fresh/upgrade/constraint tests. Existing V1–V9 migration files remain immutable.
- FR-003, FR-006, FR-009: conservative versioned registries, exact pilot source identity selection, raw source traceability, unknown Brand/attribute reports. No unreviewed category remapping or name-based product matching.
- FR-004: public listing/detail DTOs and OpenAPI shapes remain unchanged; Maven Catalog API/security/image regressions passed. Brand/specification data are internal persistence for future separately contracted presentation/filtering.
- FR-007, FR-008: enhancement apply permits only metadata of existing Product/Variant rows; unmatched source identities return UNMATCHED. Read-only preview, approval-bound apply, receipts, conflict detection, bounded commits and replay tests are present. Legacy importer retains its separate prior creation workflow; this feature does not call that mode.
- FR-011, FR-012: full verification and exact runbook commands are recorded in docs/catalog-enhancement/verification.md. The pilot seed has 1,000 Product/Variant pairs; the populated database's current counts/metadata remain unverified and untouched.

The original design document retains full-corpus/new-creation alternatives as historical background; the current spec, plan, tasks, runbook and enhancement apply path consistently restrict operations to existing pilot identities. No remaining spec/implementation contradiction found. Operational migration and actual populated-target enrichment await separate target-specific authorization.

## Pilot normalization review amendment

T012–T016 extend the existing registries without changing the data model or public API. Sixty-three new direct Brand labels have source-field corroboration and distinct canonical IDs; distribution-qualified and generic labels remain unresolved. Four strict typed attribute rules cover display resolution, installed GB storage, battery Wh and battery mAh on applicable source categories. Ambiguous values remain raw/reported. The resulting pilot candidate counts are 824 Brands and 138 Variants with typed specifications, from 1,000 exact source identities. An isolated replay preserved all protected fields and made 1,000 no-op decisions. Final Maven rerun passed 349 tests and full Python discovery passed 101 tests; T016 includes final diff review and the requested local commit.
