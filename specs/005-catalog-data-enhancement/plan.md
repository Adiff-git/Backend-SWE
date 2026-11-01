# Implementation Plan: Catalog Data Enhancement

Branch: feature/catalog-enhancement. Base: 3bd6c3b. Java 25, Spring Boot 4.1.2, Maven, JPA, PostgreSQL, Flyway.

## Constitution Check
Pre-design: preserve domain ownership, service orchestration, DTO boundaries, explicit imports, constructor injection and existing image-read transaction behavior. Additive migrations only; no dependency additions. No populated database operation, commit or push. Post-design: same gates apply; public response shape unchanged, no speculative variant merging.

## Research and Decisions
Use the approved durable architecture in docs/catalog-enhancement/architecture.md. Dedicated Brand/source mapping identities; typed Variant JSONB plus versioned file registry rather than 770 columns/EAV tables. Unknowns remain review cases. Research tasks dispatched for persistence compatibility, registry semantics and retry reconciliation.

## Implementation Scope
1. V10 additive migration; Brand/source-link entities/repositories; optional Product Brand; separate Variant specifications and validator.
2. tools/catalog-import/enhance_normalize.py and registries: streaming source classification and pilot identity selection, preserved raw observations, no guessed mappings.
3. tools/catalog-import/enhance_import.py: reviewed metadata-only enrichment planning, read-only dry-run, controlled batch apply, receipts and conflict reconciliation. Coordinate legacy importer schema checksum support.
4. PostgreSQL-backed migration/import/enrichment tests, unit tests, pilot source selection and existing Maven verification.
5. Operator runbook, exact statistics and convergence review.

## Validation and Performance
No blanket query/index optimization. Preserve existing CatalogQueryCountIT and performance fixture behavior. Database filtering remains future scope. Do not run populated-target operations. Use disposable Testcontainers PostgreSQL and actual Flyway schema for persistence tests. Record actual commands/results; no passing claim for skipped tests.
