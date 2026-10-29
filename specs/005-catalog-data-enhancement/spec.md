# Feature Specification: Catalog Data Enhancement

**Feature Branch**: `feature/catalog-enhancement`
**Created**: 2026-10-29
**Status**: Code complete; populated-database migration and enrichment pending separate authorization
**Input**: Enhance existing Catalog with Brand, typed configuration specifications, normalization and safe enrichment; preserve identities and existing public behavior.

## User Scenarios & Testing

### User Story 1 — Preserve useful product metadata (Priority: P1)
As a catalog consumer I need reliable brand and technical configuration facts available without losing existing product identities.
**Independent Test**: Persist existing products before upgrade; upgrade; enrich only approved metadata; verify identities, prices, categories and media unchanged.
**Acceptance Scenarios**:
1. Given a legacy Product without Brand, when upgraded, then it remains valid and public responses retain their existing fields.
2. Given approved brand aliases, when normalized, then they resolve deterministically to one canonical identity; uncertain aliases remain unresolved.
3. Given valid numeric/text/boolean configuration facts, when stored, then they remain separate from selectable options and belong to the same Variant.
4. Given malformed technical documents or conflicting source identities, when persisted, then validation/integrity protection rejects them.

### User Story 2 — Normalize source metadata for the existing pilot (Priority: P1)
As a data reviewer I need every crawled record classified with traceable normalization outcomes.
**Independent Test**: Select only source identities matching the existing pilot; report metadata eligibility and keep all other source records out of the enrichment manifest.
**Acceptance Scenarios**:
1. Given unsupported or ambiguous attributes, when normalized, then originals and issues are retained without guessed values.
2. Given zero price, aggregate-only price or placeholder SKU, metadata may still be enriched for an exactly matched existing Variant; no Product or Variant is created.
3. Given missing optional metadata, when independently valid fields normalize, then partial progress and missing values are reported.

### User Story 3 — Enrich safely and retry (Priority: P1)
As an operator I need controlled metadata enrichment with read-only previews, bounded transactions and replay reconciliation.
**Independent Test**: Isolated PostgreSQL tests cover dry-run, apply, replay, conflict and rollback.
**Acceptance Scenarios**:
1. Given exact legacy identities, when metadata enrichment applies, then all existing core fields and images remain unchanged.
2. Given equal accepted metadata, when replayed, then no duplicate Product/Variant/source mapping is created.
3. Given conflicting nonempty metadata or ambiguous mappings, when processed, then no silent overwrite occurs.
4. Given failed batches or uncertain commit outcomes, when resumed, then committed state is reconciled before additional writes.

### Edge Cases
Unknown brand is null; missing normalized facts are absent rather than zero/false. False is a real boolean. Source prices do not gate exact-match metadata enrichment. Category mappings require explicit approval. Similar names never establish variant groups. Missing source facts never clear existing metadata.

## Requirements

### Functional Requirements
- **FR-001**: Provide stable canonical Brand identity/code/name and optional Product reference.
- **FR-002**: Store versioned typed technical specifications on Variant, independently of SKU/price/options.
- **FR-003**: Govern technical aliases, categories, types, units and filter eligibility with reviewed versioned registries; retain unmapped raw information.
- **FR-004**: Preserve existing Product/Variant/Category/media IDs and public listing/detail shapes, visibility, pagination and signed-image checks.
- **FR-005**: Persist unique source identity mapping and accepted source/normalization/manifest receipts; conflicting source ownership must fail.
- **FR-006**: Select and normalize only source facts for exact pilot Product/Variant identities; report unmatched and ambiguous identities separately.
- **FR-007**: The enhancement apply path only enriches existing Product and Variant metadata; unmatched source records never create Product or Variant rows.
- **FR-008**: Preview enrichment without database writes; apply requires explicit target/approval/recovery prerequisites, bounded transactions, conflict checks and idempotent reconciliation.
- **FR-009**: Keep uncertain brand/category relationships unresolved; do not merge products or automatically recategorize existing products.
- **FR-010**: Use additive migrations with fresh/upgrade PostgreSQL tests and preserve old migration checksums.
- **FR-011**: Verify normalization, persistence, imports, enrichment and Catalog regressions using automated tests; report skipped/unavailable infrastructure honestly.
- **FR-012**: Provide durable architecture/registry decisions and exact operator commands; populated-target migration/import remains separately authorized.

### Key Entities
Product, Variant and Category retain their contracts. Brand owns canonical identity. Source Link owns source-to-Variant mapping and accepted receipts. A technical document describes one configuration; it does not define a variant family.

## Success Criteria
- **SC-001**: Every pilot identity has a matched, unmatched, or ambiguous source outcome; report exact measured counts.
- **SC-002**: Exact replay changes zero Product or Variant counts and creates zero duplicate source identities.
- **SC-003**: Enrichment changes zero protected legacy fields or media relationships in isolated tests.
- **SC-004**: Existing Catalog regression and added PostgreSQL integration tests execute without unreported failures/skips.
- **SC-005**: All unresolved values have explicit review outcomes, with no fabricated mandatory identity/price fields.

## Assumptions and Clarifications
Baseline: docs/catalog-enhancement/architecture.md. No approved uncertain brand aliases or category remaps are assumed. Public DTOs remain unchanged in this increment; metadata persistence is available for future separately contracted presentation. No latency SLA is invented. No populated target is authorized. No Search & Filter endpoints or queries are included.
