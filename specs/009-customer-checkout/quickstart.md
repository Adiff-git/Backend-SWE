# Checkout Validation Quickstart

Validation guide for the implemented Checkout endpoint. Actual commands, results and limits are recorded in [implementation-validation.md](implementation-validation.md). Follow [API](contracts/api.md), [internal services](contracts/internal-services.md) and [data model](data-model.md).

## Prerequisites

Java25, repository Maven wrapper, running Docker and existing test dependencies. Run from repository root, then enter demo/. Test profile uses disposable PostgreSQL17 Testcontainers/Flyway with ddl-auto=validate. Concurrency class overrides pool6 locally. Checkout requires no S3/LocalStack/network calls; existing full-suite media tests may still require their normal infrastructure. Do not use shared databases or seed demo orders via migrations.

## Commands

PowerShell:

```powershell
Set-Location demo
.\mvnw.cmd '-Dtest=CheckoutServiceImplTest,CheckoutValidatorTest,CheckoutCalculatorTest,CheckoutConverterTest,CheckoutControllerTest' test
.\mvnw.cmd test-compile failsafe:integration-test failsafe:verify '-Dit.test=CheckoutReceiptRepositoryIT,CheckoutMigrationIT,CartCheckoutServiceIT,CatalogCheckoutServiceIT,OrderCreationServiceIT,CheckoutServiceIT,CheckoutControllerIT,CheckoutConcurrencyIT,CheckoutFlowIT,AddressServiceIT'
.\mvnw.cmd test-compile failsafe:integration-test failsafe:verify '-Dit.test=CatalogMigrationsIT,CatalogSearchMigrationIT,RegistrationLoginApiIT,UserServiceIT,UserRepositoryIT'
.\mvnw.cmd verify
```

Use actual generated class names; no empty classes just to satisfy this list. Relocated user/service/AddressServiceIT (move the existing user/integration class, preserve every assertion/profile and remove the old copy) and its additions and affected exact-permission/migration regressions must also run. Full verify includes required CheckoutConcurrencyIT normally; do not add skips/optional profile. Inspect target/surefire-reports and target/failsafe-reports for actual nonzero discovery and failures/errors/skips. Record commands, JDK/Docker versions, counts and constraints at implementation time. A command not executed is not a passing gate.

## End-to-end scenarios

Prepare real owned user/address/catalog/cart data through existing valid fixtures, not a new order-creation backdoor. Obtain bearer credentials through existing authentication test flow.

1. Select eligible variants with quantities1/999, preserve one unselected row. POST checkout with unique16–128-character key and owned numeric addressId. Expect201/Location/private,no-store; complete original PENDING receipt, exact current VND totals and only included rows removed.
2. Mix eligible selection with unavailable/hidden/bad-price/bad-quantity states via justified isolated fixtures. Expect order includes eligible only; safe skipped IDs/reasons; skipped/unselected states unchanged. All-ineligible/no-selection/missing-cart422 creates no order/receipt/cart.
3. Change current price before checkout; exact new price wins. Later edit/delete address, names/SKU/options/prices or unpublish/delete catalog: current order history remains and original receipt unchanged.
4. Repeat original key/address after changing/emptying cart: exact original201/Location/body, no reprice or extra deletion. Cancel order, replay again: receipt PENDING/current detail CANCELLED. Original address deletion must not break replay.
5. Same key/different address after success409; same key across customers independent. After rejected/rolled-back initial attempt, reuse key successfully against corrected current command. No age-based expiry.
6. Real current grants: custom checkout-only role succeeds checkout/replay, cannot read detail without MANAGE_ORDER; grant/revoke affects next request; missing/expired/revoked/inactive identity401, no grant403; foreign/unknown address same404. No foreign result lookup.
7. Input matrix: key lengths15/16/128/129, case sensitivity, whitespace/non-ASCII/repeated header; integer address bounds; omitted/null/string/fraction/exponent/unknown/duplicate/trailing body; required empty body400, unsupported nonempty media415, extra query400. Every failure unchanged/no key reservation.
8. Inject failures before item persistence, after exact cart deletion and at actual deferred commit; all original cart rows preserved and no order/items/success receipt. Stop HTTP delivery after commit, recover by same key.

## Required real overlap matrix (normal integration gate)

Run independent transactions/connections with deterministic barriers and verified lock overlap. Prepare committed fixtures outside worker transactions; pool6 permits two workers, holder and observer. Bound all futures/barriers and release holders in finally blocks; no arbitrary sleep-only concurrency claims.

- Same key, completed outcome: synchronize two replay requests against an already committed receipt; both return original201/Location/body without acquiring a fence or calling mutable domains.
- Same key, commit during acquisition: hold a retry after its initial miss, commit the original, then let the retry acquire the fence and freshly read the receipt; original201, no new order or cart consume.
- Same key, busy recheck race: hold retry after failed fence attempt, commit original before its recheck, then observe201 from the fresh lookup rather than processing409.
- Same key, genuinely in progress: hold original fence/uncommitted receipt, let competitor miss initial/recheck reads and return409 CHECKOUT_IN_PROGRESS; release winner to commit exactly one order/receipt, then matching retry201 and different command409. Rollback leaves key reusable.
- Different keys: contend for same selected rows; no duplicate consumption; losing no-eligible/conflict leaves no receipt; later addition may be ordered independently without changing first receipt.
- Checkout versus add, quantity replacement, variant change, removal, individual selection and bulk true/false selection: exercise both meaningful serialization orders, assert no accepted state lost unless represented in ordered row/quantity/variant.
- Address update/delete: coherent old/new recipient/address or first-attempt404, never mixed fields; no replay dependency on live address.
- Product publication/unpublication/deletion and variant repricing/options/deletion/reparenting: final protected view includes/skips/conflicts as contracted; never stale hidden metadata or mismatched price/options. Verify parent locking alone is not the asserted proof.
- Use distinct configured user/checkout timeout values to prove user wait uses existing user policy and cart/catalog waits use the reapplied checkout policy. Hold blocking row past its configured timeout: safe409, full rollback and reusable key; later client-directed retry succeeds. Verify actual independent commit, not flush-only deferred integrity.

## Additional proof and regression checks

CheckoutServiceIT records CHECKOUT_QUERY_BOUND for100/101/205 selected rows. The focused execution measured118/124/233 prepared statements, including one native item insert per row; detached receipt encoding/decoding issued0 SQL and each completed replay issued1 receipt lookup. CatalogCheckoutServiceIT separately proves batch boundaries and explicit product/variant locks. Counts describe these fixtures and exclude database-trigger internals, authentication and production load capacity.

- Catalog selected sets100/101/205: distinct-ID batching, no per-item queries or image access; prepared receipt serializes detached without lazy SQL. Replay bypasses cart/address/catalog/order reads and all mutation/excluded stock/voucher operations. Completed fast-path query is one scoped receipt lookup/no fence; initial miss adds try-fence and a fresh receipt query on acquired or busy branches. Assert no duplicate orders/repeated cart consumption in every race without introducing stock/voucher implementations or fake persisted mutation records.
- Exact amounts including0, values above Long.MAX_VALUE, bound validator131072/131073 digits, line/sum overflow, and receipt-scoped JSON integer decoding; no rounding or global parser relaxation. Avoid expensive giant database fixtures where a focused real mapper/validator boundary test proves behavior.
- Migration clean apply/upgrade fromV23 preserves cart/history, grants only USER/default policy and enforces scoped key/order uniqueness, foreign-owner rejection, receipt immutability and deferred consistency. Do not weaken existing V23 order integrity.
- Only new route is checkout; no preview/receipt-confirmation/payment/inventory/voucher/shipping placeholders. Current order list/PENDING/detail/cancel tests remain valid.
- Extend exact USER permission sets with MANAGE_CHECKOUT; advance latest migration assertions and isolated successor toV25 only where required. Preserve unrelated dirty cart/order work and no applied migration changes.
- Browser contract exposes Location while retaining Authorization and existing security/CORS behavior. Document existing wildcard-origin issue separately; no invented production-origin configuration.

## Evidence required before completion

Create implementation-validation.md during implementation with actual test discovery/results, real overlap proof, atomic rollback at commit, arithmetic/receipt consistency, query/batch observations and any unexecuted checks. Do not mark reviewer-owned quality checklist items from implementation progress. No commit/push/deploy follows automatically from this guide.

## C1/I1 preservation checks

At implementation compare all pre-existing AddressServiceIT test methods/assertions before/after moving to user/service; update package/profile-support imports and selectors, remove old class and verify discovery without duplicate execution. Keep race cases within existing CheckoutConcurrencyIT/CheckoutReceiptRepositoryIT/CheckoutServiceImplTest responsibilities and normal verify. See implementation-validation.md for executed gates; these commands remain reproducible checks.
