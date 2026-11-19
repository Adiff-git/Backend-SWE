# Implementation Plan: Customer Order Management

**Branch**: `draft/version-1` (actual Git branch) | **Date**: 2026-11-17 | **Spec**: [spec.md](spec.md)
**Input**: specs/008-customer-order-management/spec.md
**Status**: Implementation complete on 2026-11-18; see [implementation-validation.md](implementation-validation.md) for executed checks, discovered test counts and full-run/targeted-rerun results. Design decisions and approved U1/I1 remediation are preserved below.

## Summary

Add three authenticated customer operations: paginated/filterable/sortable historical order summaries, owned detail and cancellation. PENDING/CONFIRMED cancel to CANCELLED; valid repeats return 204 without rewriting the original timestamp/reason. Optional reason follows strict input rules. Receipt confirmation is deferred entirely, while all five statuses remain readable/filterable.

Use a new order domain, additive PostgreSQL schema/permission migration, owner-scoped count-first queries, snapshot projections, a catalog-owned best-effort batch image boundary and per-order pessimistic mutation locking. Calculate long offsets and return accurate beyond-last pages after count alone; use normal JPA pagination for nonempty offsets within int range and a custom native SQL bound long OFFSET path above that range. Preserve independent order history, the accepted API page range and existing security/catalog/cart behavior.

## Technical Context

**Language/Version**: Java 25; Spring Boot 4.1.2 from demo/pom.xml.
**Primary Dependencies**: Existing Spring MVC/Security/Data JPA/Validation, Lombok, Flyway, PostgreSQL driver, Jackson and catalog/media interfaces. No additions.
**Storage**: PostgreSQL 17 in current test/local configuration; new orders/order_items; existing S3/LocalStack signed-image capability only.
**Testing**: JUnit 5, Mockito, AssertJ, MockMvc, PostgreSQL Testcontainers JDBC profile; Maven Surefire/Failsafe phases. No tests executed during planning.
**Target Platform**: Existing backend deployment/container target; Windows development through demo/mvnw.cmd. No compute platform change.
**Project Type**: Domain-oriented modular monolith web service in demo/.
**Performance Goals**: No new latency/load SLA. Bound returned list data to 100 orders and 300 preview rows; count-shortcut pages execute exactly one count and no content/preview/catalog query, while nonempty pages execute count then one header path then previews (at most three order statements). No duplicate automatic count. Chunk image batches at 100 IDs. Detail returns all items as required, with no invented cap.
**Constraints**: Exact whole VND, Instant/TIMESTAMPTZ, lazy associations, historical integrity, no collection-fetch pagination or hidden converter queries, current-state bearer permissions, owner-safe 404, atomic/idempotent cancellation, synchronized concurrency verification.
**Scale/Scope**: Three routes, two new tables, one permission; acceptance corpus 125 owned/25 foreign orders. At most three preview rows each; complete detail can be larger and image access is chunked.

## Constitution Check

Pre-research gate: PASS. Approved specification resolves business policy and limits implementation scope. Post-design recheck: PASS for this design; implementation verification remains future work.

| Principles | Pre-research assessment | Post-design evidence |
| --- | --- | --- |
| I–III domains/layers/DTOs | Preserve domain boundaries; no new architecture | OrderService/OrderReadService interfaces+implementations, dedicated validator/converter/request reader; catalog service owns image queries; DTO classes, no entity responses |
| IV–VI HTTP/errors/security | Existing bearer/current-state permission contract reused | Three routes only; MANAGE_ORDER USER rollout, owner-scoped 404; strict input, application-owned page, safe ProblemDetail; explicit 415 media-type and 409 pre-placement clock errors; unchanged 204 repeats and synchronous 204 exception |
| VII–VIII persistence/JPA | Additive schema; preserve ID contracts | BIGINT order/customer, UUID catalog, nullable SET NULL references, customer RESTRICT; lazy entities, projections/window preview query; deferred integrity validation |
| IX–X transactions/concurrency | Protect actual mutation conflicts, no global retries | Read-only seed service then separate catalog transaction; single owned order FOR UPDATE in cancellation; READ COMMITTED/REQUIRED, bounded local timeout, no network in mutation lock |
| XI–XII storage/time | No upload lifecycle change | Catalog-only optional current image URLs, no persisted URLs; Instant/TIMESTAMPTZ and immutable placement; no unnecessary audit columns |
| XIII testing | Existing stack, meaningful responsibility split | PostgreSQL constraints/queries/commit/rollback, security slices/flow, synchronized cancellation and timeout cases; fresh fixtures; actual discovery checked during implementation |
| XIV–XVII tools/git/scope | No new tooling/dependencies/CI/deployment | Documentation only; preserve all dirty cart changes; no commits/pushes; no excluded workflows/placeholders |

No unjustified violation or unresolved technical clarification remains. Existing approved authentication evolution (sessions/email/Google) is preserved, not expanded or reverted. Database aggregate triggers validate stored history; they do not implement order creation. Broader deletion/anonymization and all receipt-confirmation behavior stay excluded.

## Project Structure

### Documentation (this feature)

```text
specs/008-customer-order-management/
  spec.md
  repository-context.md
  checklists/requirements.md
  plan.md
  research.md
  data-model.md
  contracts/api.md
  contracts/internal-services.md
  quickstart.md
```

[tasks.md](tasks.md) contains the generated implementation work and approved U1/I1 remediation; no tasks are executed by this design update.

### Source Code (planned, repository root)

```text
demo/src/main/java/com/example/demo/
  order/
    controller/OrderController.java, OrderRequestReader.java
    service/OrderService.java, OrderReadService.java
    service/impl/OrderServiceImpl.java, OrderReadServiceImpl.java
    repository/OrderRepository.java, OrderItemRepository.java
    repository/OrderPageRepository.java, OrderPageRepositoryImpl.java
    dto/request/CancelOrderRequest.java, OrderListRequest.java
    dto/response/OrderPage.java, OrderSummaryResponse.java, OrderDetailResponse.java
    dto/response/OrderItemPreview.java, OrderItemResponse.java, OrderReadSeed.java
    entity/Order.java, OrderItem.java, OrderStatus.java
    converter/OrderConverter.java
    validator/OrderValidator.java
    exception/InvalidOrderRequestException.java, OrderNotFoundException.java, OrderConflictException.java
  catalog/service/CatalogOrderReadService.java, CatalogOrderReadServiceImpl.java
  catalog/repository/ProductImageRepository.java (bounded scalar primary-image projection)
  auth/config/AuthSecurityConfiguration.java (three permission matchers only)
  common/exception/ApiErrorFactory.java (order-only unsupported-media mapping)
demo/src/main/resources/db/migration/V23__customer_order_management.sql (provisional)
demo/src/test/java/com/example/demo/
  order/controller/OrderControllerTest.java, OrderControllerIT.java
  order/controller/OrderRequestReaderTest.java
  order/service/OrderServiceImplTest.java, OrderReadServiceImplTest.java, OrderServiceIT.java
  order/validator/OrderValidatorTest.java
  order/converter/OrderConverterTest.java
  order/repository/OrderRepositoryIT.java, OrderItemRepositoryIT.java, OrderMigrationIT.java
  order/OrderFlowIT.java
  catalog/service/CatalogOrderReadServiceImplTest.java, CatalogOrderReadServiceIT.java
  testdata/order/OrderTestData.java
  fixture/order/OrderFixtures.java
```

**Structure Decision**: Extend the existing demo module, not top-level global layers or a new service. Pure prepared seed types may be nested inside OrderReadSeed to avoid unnecessary files. Add the Unicode constraint alongside order request DTOs only if needed for the selected Bean Validation implementation. Listed test classes correspond to actual boundaries, not empty scaffolding. Recheck migration numbering before implementation; V22 currently belongs to dirty cart work.

## Phase 0 — Research Outcome

[research.md](research.md) records decisions, rationale and alternatives for IDs/deletion, locks/repeats, fetch plans, image fallback, integrity, parsing/permissions and testing. Local source research plus primary PostgreSQL documentation resolved all technical questions. Git Bash setup failed at Windows initialization; repository PowerShell equivalent succeeded. Setup logical feature branch differs from actual Git branch; no branch was switched.

## Phase 1 — Design and Validation Handoff

1. Implement schema/permission with row and deferred aggregate constraints as described in [data-model.md](data-model.md).
2. Implement projection-based order read seeds, pure mapping and narrow best-effort catalog images per [internal-services.md](contracts/internal-services.md).
3. Implement strict HTTP contracts/current-state security and owner-scoped atomic cancellation per [api.md](contracts/api.md).
4. Validate user stories and failure/concurrency boundaries using [quickstart.md](quickstart.md); record actual test discovery/results during implementation.

Analysis remediation: U1 is resolved by count-first long-offset dispatch and explicit native SQL OFFSET binding in the custom order repository fragment; no accepted page is rejected solely because its offset exceeds Integer.MAX_VALUE. Boundary tests use synthetic counts and direct small-fixture repository binding checks, avoiding billion-row data. I1 is resolved by synchronizing 415 unsupported/missing JSON media-type failures and 409 pre-placement first-cancellation Clock failures across the spec/contracts/tasks. Valid CANCELLED repeats bypass Clock sampling and remain 204. These are documentation decisions, not runtime results.

These are design groups, not generated tasks. Gates were re-evaluated against all artifacts: no creation/admin/receipt/payment/shipping/inventory endpoint, no new dependency, no cascade deleting history, no joined read-only image-lock transaction, and no repeat timestamp/reason update.

Implementation integration note: the existing global cart advice intercepts unsupported-media exceptions and delegates non-cart paths to ApiErrorFactory. The order-only 415 mapping therefore lives at that existing delegation point, rather than adding an ineffective lower-priority general handler. Cart advice and its behavior are unchanged. Existing registration/user exact default-grant assertions were updated only to include the required MANAGE_ORDER permission; their strict set comparisons remain.

## Complexity Tracking

No constitution violations require exemption. Two order service interfaces are justified by a real transaction boundary: seed reads finish before publication-safe optional image preparation. Deferred constraint triggers protect cross-row historical validity that CHECK constraints cannot enforce; no general workflow/creation abstraction is introduced.
