# Implementation Plan: Customer Checkout

**Branch**: `draft/version-1` (unchanged) | **Date**: 2026-11-18 | **Spec**: [spec.md](spec.md)
**Input**: `specs/009-customer-checkout/spec.md`; Q1:A, Q2:A, Q3:A explicitly approved.
**Status**: Implementation and verification complete; all60 tasks finished. Actual execution evidence is in [implementation-validation.md](implementation-validation.md).

## Summary

Add POST `/api/me/cart/checkout` for selected eligible rows and an owned address. A checkout domain orchestrates existing domain service boundaries in one REQUIRED READ_COMMITTED transaction. It stores a permanent immutable purchase receipt with one PENDING order, historical items and deletion of precisely the consumed cart rows. A committed-receipt lookup precedes the nonblocking customer/key advisory fence. On a miss, both acquisition success and busy failure lead to a fresh committed-receipt recheck; matching receipt hits return the original201 before any mutable-domain work. Only the holder with no receipt executes checkout; busy plus no visible committed receipt returns processing409. Current MANAGE_CHECKOUT authorization applies to first requests and replay.

## Technical Context

**Language/Version**: Java25, Spring Boot4.1.2, verified in demo/pom.xml.
**Primary Dependencies**: Existing Spring MVC/Security/Data JPA/Validation, Jackson3, Lombok, Flyway; no new dependency/plugin.
**Storage**: PostgreSQL; one new checkout_receipts table and permission grant; V24__customer_checkout.sql, verified next free migration after V23.
**Testing**: Existing JUnit5/Mockito/AssertJ, WebMvcTest/MockMvc, PostgreSQL17 Testcontainers, Surefire and normal Failsafe integration-test/verify. Real synchronized tests run in the normal gate, including concurrent completed replays, commit-during-fence races, busy rechecks and genuinely in-progress originals; no stock/voucher interactions or duplicate execution.
**Target Platform**: Existing backend runtime; Docker for disposable tests. No deployment added.
**Project Type**: Domain-oriented modular-monolith web service in demo/.
**Performance Goals**: No invented latency/load SLA. No S3/network calls; catalog queries batch100 distinct IDs, no per-item fetching. Work/receipt size proportional to selected rows. Existing cart has no approved row cap; introduce none silently.
**Constraints**: Quantity1–999, exact whole VND with BigInteger/NUMERIC; no long narrowing. User-row wait retains existing user.lock-timeout-ms; positive checkout.lock-timeout-ms default5000 is installed transaction-locally immediately after user locking and before subsequent cart/catalog blocking locks. No automatic retries, SERIALIZABLE or REQUIRES_NEW.
**Scale/Scope**: One customer/cart/address/order per success; existing Long commerce/customer/address IDs and UUID catalog IDs. Lifetime receipt storage grows with successful orders, no TTL/reuse.

## Constitution Check

Pre-research: PASS. Reassessed post-remediation design gate: PASS; C1 is resolved in the design by relocating the existing address-service test responsibility to the owning service package, not grandfathering its old layout. I1 is resolved by receipt-first/fresh-recheck flow and synchronized race coverage. The physical relocation preserves all ten original tests; eleven address-service cases and all 41 synchronized concurrency cases passed the focused gate. No constitution exception needed. Feature-local idempotency, permission and synchronized test policies are explicitly approved.

| Gate | Design evidence |
| --- | --- |
| I–III architecture/DTOs | Checkout owns receipt repository only; cart/catalog/address/order service interfaces own their repositories. No reverse checkout dependencies. Validators own rules, one calculator owns authoritative totals, converters use prepared immutable data. |
| IV–VI HTTP/errors/security | Explicit201 first/replay, strict input/presence, ProblemDetail errors and current MANAGE_CHECKOUT/ownership checks. Existing authentication stays enabled. |
| VII–VIII schema/JPA | Additive migration, restrictive FKs, named constraints/indexes, TIMESTAMPTZ, existing IDs; safe Lombok/lazy associations/projections, no entity HTTP bodies. |
| IX–X transactions/locks | Public proxied REQUIRED READ_COMMITTED transaction; stable order, explicit child locks, timeout/conflicts without retries. Commit including deferred constraints precedes201. |
| XI–XII storage/time | No S3/image signing; no temporary URL in durable receipt. Clock/Instant/TIMESTAMPTZ for placedAt. |
| XIII testing | Meaningful units/MVC/PostgreSQL/service/API/flow; synchronized independent transactions and nonzero discovery in normal verify. |
| Remaining conventions/scope | No dependencies, framework, excluded workflows or unrelated refactor. Preserve dirty files. Only add Location to CORS exposure; existing wildcard-origin issue is not a checkout redesign. |

## Project Structure

### Documentation (this feature)

specs/009-customer-checkout contains spec.md, plan.md, [research.md](research.md), [data-model.md](data-model.md), [contracts/api.md](contracts/api.md), [contracts/internal-services.md](contracts/internal-services.md), [quickstart.md](quickstart.md) and checklists/requirements.md. [tasks.md](tasks.md) tracks implementation; [implementation-validation.md](implementation-validation.md) records executed gates.

### Source Code (repository root; implemented changes)

All following Java paths are under demo/src/main/java/com/example/demo/:

```text
checkout/
  controller/CheckoutController.java, CheckoutRequestReader.java
  service/CheckoutService.java, impl/CheckoutServiceImpl.java
  repository/CheckoutReceiptRepository.java
  entity/CheckoutReceipt.java
  dto/request/CheckoutRequest.java
  dto/response/CheckoutReceiptResponse.java, CheckoutOrderReceipt.java,
               CheckoutItemReceipt.java, SkippedCheckoutItem.java, CheckoutCalculation.java
  validator/CheckoutValidator.java
  calculator/CheckoutCalculator.java
  converter/CheckoutConverter.java
  exception/InvalidCheckoutRequestException.java, CheckoutInProgressException.java,
            CheckoutKeyConflictException.java, CheckoutConflictException.java,
            NoEligibleCheckoutItemsException.java, CheckoutAmountLimitException.java
cart/service/CartCheckoutService.java, impl/CartCheckoutServiceImpl.java
cart/dto/response/CheckoutCartSeed.java
cart/repository/CartCheckoutRepository.java, CartCheckoutRepositoryImpl.java
catalog/service/CatalogCheckoutService.java, CatalogCheckoutServiceImpl.java
catalog/dto/response/CheckoutVariantState.java
user/service/AddressService.java, impl/AddressServiceImpl.java
user/dto/response/CheckoutAddressSnapshot.java
order/service/OrderCreationService.java, impl/OrderCreationServiceImpl.java
order/dto/request/CreateOrderCommand.java
order/dto/response/CreatedOrderSeed.java
order/validator/OrderValidator.java
order/repository/OrderCreationRepository.java, OrderCreationRepositoryImpl.java
```

Extend owning cart/catalog repositories for locking/projections/conditional deletion, order repositories for creation; add exact checkout route in auth/config/AuthSecurityConfiguration.java, scoped errors in common/exception/ApiErrorFactory.java and Location exposure in common/security/SharedCorsConfiguration.java. Existing AddressRepository/UserService support user serialization and owned address retrieval. ApplicationException supplies safe error extensions; config/CheckoutIntegrationSupport.java centralizes committed test fixtures and cleanup.

Implemented migration: demo/src/main/resources/db/migration/V24__customer_checkout.sql. Do not edit V22/V23.

Tests mirror responsibilities under demo/src/test/java/com/example/demo/: checkout/controller/CheckoutControllerTest and CheckoutControllerIT; checkout/service/CheckoutServiceImplTest, CheckoutServiceIT and CheckoutConcurrencyIT; checkout/repository/CheckoutReceiptRepositoryIT and CheckoutMigrationIT; checkout/validator/CheckoutValidatorTest; checkout/calculator/CheckoutCalculatorTest; checkout/converter/CheckoutConverterTest; catalog/service/CatalogCheckoutServiceIT; cart/service/CartCheckoutServiceIT; user/service/AddressServiceIT (move existing user/integration/AddressServiceIT.java, preserve all assertions/coverage, no duplicate; explicitly import existing public user.integration.PostgresTestSupport or retain equivalent @ActiveProfiles without creating another support class); order/service/OrderCreationServiceIT; e2e/CheckoutFlowIT. Add isolated reader tests only for parsing behavior not meaningfully isolated in MVC. Reusable fresh objects in testdata/checkout, persistent fixture helpers in fixture/checkout when actually shared; no mechanical accessor/delegation tests.

**Structure Decision**: A small checkout orchestration domain preserves acyclic boundaries and persists only recovery receipts, not a copied multi-order Checkout aggregate.

## Phase 0 — Research

Read-only research agent dispatched per skill; recommendations consolidated in research.md. Resolved keyed processing fence, catalog child locks, user/cart ordering, stored immutable receipts, money limits and HTTP/migration compatibility. Official PostgreSQL/Spring sources checked; decisions are designs requiring tests, not existing functionality claims.

## Phase 1 — Design and Contracts

Data model defines receipt fields, invariants and migration. API defines strict input, frozen receipt and errors. Internal contract specifies service graph, transaction order, locks and rollback. Quickstart supplies reproducible validation commands and proof obligations. No unresolved clarification remains.

### Integration risks carried into tasks

Existing global cart advice intercepts checkout media errors; retain its415 behavior, avoid misleading generic cart codes for checkout. Add Location to exposed CORS headers while preserving Authorization. Current shared CORS wildcard origins are an unrelated pre-existing issue; report rather than silently change deployment policy. Update only affected latest-version migration assertions and exact USER permission sets. Existing disposable V24 search-recovery successor must advance to V25 in its test; no applied migration edit. Existing dirty cart/order/auth code is baseline, never reset.

## Complexity Tracking

No constitution exception. Transaction advisory fencing is a checkout-specific persistence primitive for processing detection before a successful receipt exists, not a distributed framework or durable processing-state subsystem.

## Workflow execution

Required Bash setup could not run because bash is unavailable. Repository PowerShell setup-plan equivalent succeeded, resolving feature via .specify/feature.json. Its BRANCH output is feature alias009-customer-checkout; actual git branch remains draft/version-1. No branch switch. Pre/post extensions.yml checks found no hooks. Planning changes documentation only, no test/build/runtime success claimed.

## Accepted remediation verification

C1: T016 moves the existing class rather than extending a new copy in the old package; preserve every existing assertion, test discovery and public PostgresTestSupport integration. I1: T027 uses receipt-first lookup and fresh SQL rechecks on acquired/busy fence paths; T042/T044/T045 prove all races in existing test classes. No new task IDs, duplicate classes, production changes or executed-test claim in this remediation step.


## Voucher integration follow-up — feature011

The earlier first-execution Voucher exclusion is superseded by feature011: captured Cart grant is independently validated after definition→usage→grant locks, then one authoritative event timestamps order, Inventory reservation, grant/redemption and receipt. Selected grant/quota/discount/order/Inventory/Cart/version2 receipt commit atomically. Receipt-first/fresh rechecks and address-only fingerprint remain; old version1 bodies are unchanged and every replay bypasses Voucher and Inventory mutations. Verified targeted deltas and the separately tracked unrestricted completion gate are recorded in [Voucher execution evidence](../011-voucher-support/implementation-validation.md). Earlier completed tasks and their historical execution records are preserved; this follow-up adds no admin/code/public-discovery/cancellation-quota contract.
