# Verification guide — UC-037

On `draft/version-1`, Admin Catalog is V31 and Admin Inventory is V32. The recorded V28/V29 results below refer to the original admin branch.

Use the focused commands for diagnosis and the unrestricted command for development acceptance.

## Prerequisites and commands

Use Java 25 and the Maven wrapper under `demo/`. Run future integration tests on isolated disposable PostgreSQL Testcontainers using the repository CI baseline. Check that no local/shared JDBC override can redirect tests. Seed committed fixtures through existing test helpers and use separate connections for worker transactions. Confirm that the test reports contain the selected tests and that their disposable target was used.

After implementation, run from `demo/`:

```sh
UV_NO_SYNC=true bash ./mvnw -Dtest=AdminInventoryValidatorTest,AdminInventoryStockStatusConverterTest test
UV_NO_SYNC=true bash ./mvnw -Dit.test=AdminInventoryMigrationIT,AdminInventoryUpgradeIT,AdminInventoryConcurrencyIT,AdminInventorySecurityIT,AdminInventoryQueryBudgetIT verify
UV_NO_SYNC=true bash ./mvnw clean verify
```

Include the actual Cart, public Catalog and Order integration tests from test discovery in the regression checkpoint. The final command is the full gate after targeted verification. Preserve existing budgets, assertions and integration-test discovery.

## Acceptance matrix

| Area | Fixture or schedule | Expected evidence |
| --- | --- | --- |
| Migration (FR-12, NFR-04, AC-14/17/26) | Clean bootstrap, populated V28 → new migration, representative V27 → V28 → new migration | Flyway validation and an empty new journal. Before/after protected-row checks preserve existing records and quantities on V28 upgrade, plus old-to-new compatibility assertions for V28 changes on V27 upgrade. V25 trigger/constraint probes still fail on invalid mutations. Journal FK/unique/amount checks hold at commit. History survives live detachment. |
| Authorization (FR-01, NFR-03, AC-16/25) | Every route through security chain using guest, Customer, invalid/expired JWT, revoked/expired session, disabled actor, active Admin; replay after revoke | Correct 401/403 and no effects for invalid callers. Authenticated actor comes from current state. No cross-actor original response leakage when UUIDs match. |
| List/detail/history (FR-02/03/04/10/11, AC-01/02/03/13/15/20/22/26) | Archived/Disabled default, SKU siblings, literal wildcard chars, state filter, missing/zero/positive stock, unknown/detached UUID, tied ordering and empty pages | Deterministic results and exact quantity precision. History by original UUID. No fake zero or audit events. Verify query counts on page sizes 1, 20, 100, including empty/out-of-range and history existence; list ≤2, detail ≤1, history ≤2 domain SELECTs. Separate total measurements include current auth. Inspect disposable query plans with representative stock/journal sizes and record workload and EXPLAIN evidence before claiming performance. |
| Validation (FR-05/06/07/08, AC-04/05/07/08/18/21/24) | Positive replenish; positive and negative correction; floor equality/crossing; BIGINT max and overflow; decimals, invalid sign/zero, blank and overlong reasons; all six Enabled/Disabled by writable parent states and both Archived states | Correct 200/400/404/409/422 classification, exact arithmetic, scoped floor details, one journal per accepted write, no changes on rejection. |
| Atomicity and replay (FR-08/09/13, NFR-02, AC-06/12/19/24/25) | Inject failure after stock update or on journal insert and at commit. Lose response after commit. Race independent sessions on identical and conflicting payloads, including distinct target variants/types, matching UUID across actors and canonical optional reasons | Neither effect survives failed commit. Same actor/key has one mutation/event; exact original result is returned on retry after stock or Archive changes. Rolled-back key can succeed. Direct duplicate insert fails via named unique constraint without surviving stock change. |
| Concurrency (NFR-01, AC-09/10/11/19/23) | Hold locks in independent transactions with latches and observed database waits, then run competing Admins, Checkout, cancellation, completion, and Archive/Restore or Enable/Disable in both meaningful orderings | Serial-equivalent counters and exact ACTIVE ownership, no duplicate effects, stable lifecycle, safe lock timeout and rollback. Archive-first rejects a fresh write. Disable-first remains writable. Existing receipt replay and terminal actions survive later Admin changes. |
| Consumer compatibility (FR-12, NFR-04, AC-17/23/26) | Existing customer Inventory, Cart, Checkout, Order, Catalog and recommendation availability paths | Cart remains non-reserving, Checkout reserves only, cancellation releases only, completion consumes. Existing response shapes, receipt snapshots, query budgets and protected history remain intact. |

No shared target migration is part of this guide. Earlier demo V27 verification neither establishes today's target state nor grants migration permission.


## Disposable fixture and recovery protocol

- T003 defines versioned fixtures UC037-SYN-V28-01 and UC037-SYN-V27-01 in test source. Capture source revision, fixture code/hash, generation inputs and row coverage, PostgreSQL version, exact disposable target identity and restore provenance. These synthetic fixtures are in scope for disposable development. An optional actual snapshot requires separate approval and sanitized provenance; it cannot be substituted silently or inferred from synthetic success.
- V28 fixture covers Draft, Published, Unpublished and Archived Products, Enabled and Disabled Variants, zero and positive stock, ACTIVE/RELEASED/CONSUMED allocations and corresponding Orders, legacy unmanaged Orders and receipts, and retained historical references. V27 fixture covers the states and records representable before V28; assert V28 defaults, lifecycle/publication preconditions, and enablement on upgrade. Establish Disabled/Unpublished fixture states through V28-valid operations before capturing the V29-only baseline. Include incompatible V28 fixture as a separate negative case.
- T010 verifies empty V1-to-head and populated V28-to-journal; T011 rehearses V27-to-V28-to-journal. Record Flyway versions/checksums and deterministic protected-row fingerprints at each step. Validate allowed V28 transformations independently; V29 must preserve protected rows and start an empty journal. Check V25/V28 integrity and journal constraints.
- On separate disposable clones create and restore a pre-upgrade backup, verify protected fingerprints/Flyway history, inject a transactional migration failure and inspect rollback from a new connection. Rehearse retry from a verified clean or restored baseline. Record tool/fixture identities and outcomes. Never run these commands against a real demo or existing developer target. The receipt-aware restore uses the [Feature 1 restore-session procedure](../013-admin-catalog-management/migration-rehearsal.md#backup-restoration-procedure).
- T029 requires unrestricted clean verify, final Surefire and Failsafe XML counts by test identity, no excluded required tests and JAR packaging evidence. Inspect final reports for expected negative SQL probes, failures, errors and skips. Targeted runs are diagnostic.


## Implementation files

Created source and test files in this branch:

- [CatalogInventoryTargetService.java](../../demo/src/main/java/com/example/demo/catalog/service/CatalogInventoryTargetService.java)
- [AdminInventoryMutationController.java](../../demo/src/main/java/com/example/demo/inventory/controller/admin/AdminInventoryMutationController.java)
- [AdminInventoryReadController.java](../../demo/src/main/java/com/example/demo/inventory/controller/admin/AdminInventoryReadController.java)
- [AdminInventoryStockStatusConverter.java](../../demo/src/main/java/com/example/demo/inventory/converter/AdminInventoryStockStatusConverter.java)
- [AdminInventoryCommand.java](../../demo/src/main/java/com/example/demo/inventory/dto/request/AdminInventoryCommand.java)
- [AdminInventoryOperation.java](../../demo/src/main/java/com/example/demo/inventory/dto/response/AdminInventoryOperation.java)
- [AdminInventoryPage.java](../../demo/src/main/java/com/example/demo/inventory/dto/response/AdminInventoryPage.java)
- [AdminInventoryVariant.java](../../demo/src/main/java/com/example/demo/inventory/dto/response/AdminInventoryVariant.java)
- [AdminInventoryException.java](../../demo/src/main/java/com/example/demo/inventory/exception/AdminInventoryException.java)
- [AdminInventoryOperationRepository.java](../../demo/src/main/java/com/example/demo/inventory/repository/AdminInventoryOperationRepository.java)
- [AdminInventoryReadRepository.java](../../demo/src/main/java/com/example/demo/inventory/repository/AdminInventoryReadRepository.java)
- [AdminInventoryMutationService.java](../../demo/src/main/java/com/example/demo/inventory/service/AdminInventoryMutationService.java)
- [AdminInventoryReadService.java](../../demo/src/main/java/com/example/demo/inventory/service/AdminInventoryReadService.java)
- [AdminInventoryValidator.java](../../demo/src/main/java/com/example/demo/inventory/validator/AdminInventoryValidator.java)
- [V32__admin_inventory_journal.sql](../../demo/src/main/resources/db/migration/V32__admin_inventory_journal.sql)
- [AdminInventoryFixtures.java](../../demo/src/test/java/com/example/demo/fixture/inventory/AdminInventoryFixtures.java)
- [AdminInventorySecurityIT.java](../../demo/src/test/java/com/example/demo/inventory/controller/admin/AdminInventorySecurityIT.java)
- [AdminInventoryStockStatusConverterTest.java](../../demo/src/test/java/com/example/demo/inventory/converter/AdminInventoryStockStatusConverterTest.java)
- [AdminInventoryMigrationIT.java](../../demo/src/test/java/com/example/demo/inventory/repository/AdminInventoryMigrationIT.java)
- [AdminInventoryQueryBudgetIT.java](../../demo/src/test/java/com/example/demo/inventory/repository/AdminInventoryQueryBudgetIT.java)
- [AdminInventoryUpgradeIT.java](../../demo/src/test/java/com/example/demo/inventory/repository/AdminInventoryUpgradeIT.java)
- [AdminInventoryConcurrencyIT.java](../../demo/src/test/java/com/example/demo/inventory/service/AdminInventoryConcurrencyIT.java)
- [AdminInventoryValidatorTest.java](../../demo/src/test/java/com/example/demo/inventory/validator/AdminInventoryValidatorTest.java)

Extended the existing [Catalog filter chain](../../demo/src/main/java/com/example/demo/common/security/CatalogSecurityConfiguration.java) and [OpenAPI file](../../docs/openapi.json). Updated the expected migration head in [VoucherMigrationIT.java](../../demo/src/test/java/com/example/demo/voucher/repository/VoucherMigrationIT.java). Its data-preservation assertions are unchanged. Updated the Feature 2 data model, verification guide, backlog and traceability. The owner-approved specification and decisions, Feature 1 migrations and SharedCorsConfiguration were preserved.

## Verified development closeout — 2026-11-24

- **T001–T030 VERIFIED DEVELOPMENT COMPLETE.** T031 remains unchecked and outside development acceptance.
- Focused Feature 2 verification passed 12 unit and 29 integration tests. The disposable Catalog/Cart/Recommendation consumer probe and PostgreSQL EXPLAIN probe also passed. V29 was exercised only on disposable PostgreSQL.
- The first completed unrestricted run found one outdated head assertion in VoucherMigrationIT. Updated the expected version from V28 to V29 and preserved the Voucher, Order and receipt assertions. The focused Voucher rerun passed 10/10.
- Final `UV_NO_SYNC=true bash ./mvnw clean verify` from `Backend-SWE/demo` succeeded. Surefire ran 969 tests across 161 XML suites and Failsafe ran 891 tests across 162 XML suites, with 0 failures, 0 errors and 0 skips. The packaged JAR contains V29 and both inventory controllers.
- Feature 2 integration breakdown: real bearer HTTP 1, concurrency and transaction cases 23, clean/staged migrations 3, backup/restore and injected migration rollback 1, and query budgets 1. Both existing Catalog performance suites and the real RecommendationHttpIT passed.
- Evidence: `demo/target/uc037-evidence/full-clean-verify.log`, focused and supplemental logs in the same directory, and the final `demo/target/surefire-reports` and `demo/target/failsafe-reports`.
- Demo/production rollout remains separate. Confirm runtime database role privileges at preflight. C01 remains open and shared CORS was unchanged.
