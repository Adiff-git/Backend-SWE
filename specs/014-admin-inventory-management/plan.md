# Implementation Plan — Admin Inventory Management

**Branch** `features/AdminInventoryManagement` | **Date** 2026-11-24 | **Spec** [Owner-approved UC-037](spec.md)

## Summary

Extend the Inventory domain with private Admin reads and two delta operations. Retain the V25 variant balance as the sole stock authority. Add one append-only administrative journal that doubles as durable replay evidence. No change to existing commerce contracts or reservation writers.

## Technical Context

- Java 25, Spring Boot 4.1.2, Maven, Spring Security resource server, Spring Data JPA, PostgreSQL and Flyway. Use repository JUnit, MockMvc and PostgreSQL Testcontainers conventions.
- One variant per write, exact BIGINT arithmetic, one actor-scoped command per UUID, no external services or new infrastructure.
- Domain read budget at maximum page size: list ≤2 business SELECTs, detail ≤1, history ≤2, including total/existence as applicable. Measure authentication separately and assert total route counts independently. No latency SLA is asserted.
- Repository migration head V28 at inspection. V29 is the candidate for a later additive migration and must be rechecked before authoring.

## Constitution Check

| Area | Pre-design and post-design result |
| --- | --- |
| Domain boundaries and service transactions | Pass. Inventory owns journal and balance. Catalog supplies protected identity/lifecycle. Existing Order and Checkout flows keep their responsibilities. |
| Auth and REST | Pass. Current-state bearer JWT with ROLE_ADMIN across all five routes. Shared problem response conventions and exact quantities. |
| Data and migrations | Pass. Additive journal only, named constraints and index, no existing migration edits or historical backfill. |
| Locking and errors | Pass at design level. READ COMMITTED with an acyclic resource order, safe bounded contention handling, no automatic write retry. |
| Verification | Pass at design level. Disposable upgrade and concurrency tests are required. Existing commerce assertions and budgets remain applicable. |

## Project Structure

- Plans and contracts reside beside the [specification](spec.md). Phase 0 findings are in [research.md](research.md). Phase 1 artifacts are [data-model.md](data-model.md), [Admin API](contracts/admin-api.md), [transaction contract](contracts/transactions.md) and [quickstart.md](quickstart.md).
- Use `demo/src/main/java/com/example/demo/inventory/` for Admin web adapter, validated DTOs, converter, service, journal repository and balance mutation. Catalog retains ownership of Product/Variant read and lock projections. Extend the existing Admin security configuration and shared problem-response support with Inventory-specific mapping.
- Use `demo/src/main/resources/db/migration/` for the future additive migration. Use `demo/src/test/java/com/example/demo/inventory/` and existing commerce test packages for targeted verification. Publish the contract through the existing Backend OpenAPI documentation in implementation.

## Prerequisites

### Environment assessment (2026-11-24)

- **Docker — AVAILABLE with approved execution outside the sandbox.** The approved read-only run passed `id -nG`, `docker version`, `docker info`, and `docker ps`. The process had the `docker` group and reached Docker Engine 29.6.0. Default sandbox execution still denied socket access.
- **PostgreSQL Testcontainers — READY TO TEST (not yet verified).** Run disposable integration tests through an execution path with Docker access and any required approval. The read-only check started no containers and ran no tests or migrations.

- **Implementation** — Keep Feature 1 ancestry, recheck upstream migration numbering and current source and run planning-approved contract/test coverage only in the subsequent implementation phase.
- **Migration verification** — Use disposable PostgreSQL. Test empty bootstrap, populated V28 upgrade, and a disposable V27 → V28 → journal chain with compatible data. A V28 preflight failure is separate from this feature.
- **Target rollout** — Verify actual target schema/version and prerequisites, including V28 compatibility evidence, backups/restore and separately authorized rollout. The earlier V27 demo observation is historical evidence. This plan authorizes no target changes.

## Design review

BR-I08 permits stock writes to Disabled variants under non-Archived parents and requires explicit Archive synchronization. Replay gets current authorization and returns only immutable event-derived data, even after archive. Journal FK validation references the already-protected retained balance and incurs no late live Catalog or User lock. The debit floor, overflow and duplicate protections are independent and tested at commit. Historical reads use the original Variant UUID. The five endpoints stay within UC-037. See [validation matrix](quickstart.md).

## Complexity Tracking

No constitution exceptions required.

## Verification and release separation

Use the [disposable fixture protocol and verification commands](quickstart.md) for development. T030 closes verified development only after T029 and acceptance reconciliation. T031 is separately authorized target preflight and does not perform deployment. Demo deployment needs an independently authorized operation and post-deployment evidence. Production readiness also requires closing inherited [C01](../013-admin-catalog-management/production-release-gates.md). This feature does not alter shared CORS.
