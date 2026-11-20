# Voucher validation quickstart

Validation reference; actual execution evidence is in [implementation-validation.md](implementation-validation.md). Follow [API](contracts/api.md), [internal services](contracts/internal-services.md) and [model](data-model.md). Proposed selectors must match meaningful actual classes created during Tasks/Implement; never create empty tests to satisfy a command.

## Prerequisites

Existing Java25/Maven wrapper, Docker Desktop Linux engine, PostgreSQL17 Testcontainers and LocalStack media infrastructure. No dependency/plugin/global pool/tool installation. Commands run from demo/; explicit process-local JAVA_HOME may be needed. Fixtures own disposable databases only. No admin/code-claim provisioning command is supplied: prepare definitions+usage rows+explicit one-use grants in VoucherFixtures, and test stock separately in InventoryFixtures. Initial production inventory/grants are not invented.

## Scope selection and checkpoints

Follow Constitution v1.2.0 Principle XIII and the [plan risk matrix](plan.md). Inspect changed behavior,
callers/dependencies, schema/infrastructure and regression impact; choose the smallest sufficient
compilation/unit/MVC/repository/service/API selection and record the rationale. Calculation changes
exercise exact-money/definition tests; routes/ownership exercise strict/security/API tests; persistence
changes require real commit/rollback; quotas/lock/time require real overlap and post-lock expiry;
codec/fingerprint changes require old/new immutable replay; queries require affected bounds/performance.
No required risk-specific scenario or assertion is removed by narrowing execution scope.

| Checkpoint | Task | Connected regression scope |
| --- | --- | --- |
| Foundation | T016 | Exact calculation and real definition/grant/counter/schema constraints |
| Cart preview | T027 | Cart→Catalog→Inventory→Voucher, all mutations/read/delete cleanup and ownership |
| Atomic purchase | T039 | Checkout→Order→Inventory→Voucher→Cart→receipt, rollback/quota/post-lock expiry/replay |
| History | T044 | Stored Order discounts/sorts and original receipt compatibility |
| Wallet | T052 | Owned wallet paging/eligibility, Cart subtotal context and authenticated access |
| Final evidence | T054–T057 | Complete missing/new/affected races, measurements, upgrades and focused proof |
| Full regression | T058 | At least one unrestricted full `mvn verify` near feature completion, normal discovery and packaging |

The command reference below offers selector examples, not an instruction to rerun all selections
at every checkpoint. Narrow selectors for a change; expand only along affected dependency paths.
Reuse valid results for unchanged code/dependency scope to satisfy overlapping final obligations,
recording the exact run/revision/cases used. Required security, transaction/rollback, quota/concurrency,
discount accuracy, expiry while waiting for Voucher locks, v1/v2 receipt replay and affected performance
coverage must all have actual evidence before fully verified completion.

After an isolated fix, run affected tests first. Do not automatically repeat full verification or unrelated
expensive media/search/benchmark suites. Repeat full verification for broad impact, shared infrastructure
changes or unresolved regression uncertainty; record the trigger. Earlier full results belong to their
original tested revision, not a later revision. A failed full run followed by passing targeted fixes must
be reported as those two distinct outcomes, with residual uncertainty and rerun rationale.

## Environment and evidence reporting

Unavailable Docker/Testcontainers/PostgreSQL/LocalStack blocks dependent real tests. Record exact
command, concrete failure/cause, affected scope and remaining uncertainty; continue feasible independent
checks and retry when prerequisites return. Do not substitute mocks, silently skip required tests, install
new tooling or call a selective run unrestricted. Leave blocked implementation gates incomplete and
disclose limitations; application assertion failures require fixes and are not environmental waivers.

| Status | Required meaning/evidence |
| --- | --- |
| PASS | Executed stated scope successfully; exact command/selectors, working state and current results/counts support this scope only |
| FAIL | Executed check failed; retain failure/exit/report and later fix/rerun separately; expected-red evidence is not a passing implementation gate |
| BLOCKED | A concrete environmental/prerequisite obstacle prevents execution/completion; record cause, attempted command when applicable, affected checks and retry/remaining uncertainty |
| SKIPPED | A check was intentionally omitted or runner reported it skipped; state check/count/reason and mandatory status. Not PASS; required coverage remains outstanding |
| NOT RUN | No execution evidence exists yet; identify the pending scope. Proposed commands and prior-feature passes do not change this status |

Record in implementation-validation.md: exact commands/selectors and working directory; revision or
identified dirty-state scope; risk/scope rationale; environment; time/elapsed; exit result; current-run
report locations/discovered counts/failures/errors/skips; fixes; blockers/unexecuted cases and residual
uncertainty. Label targeted, checkpoint and full-suite results separately. Interrupted runs are not PASS;
classify the observed failure/blocker and identify unfinished checks. Never aggregate stale XML into a
current run or mark pending/blocked/skipped tasks complete. The earlier policy-only documentation edit executed no tests; subsequent implementation evidence is recorded separately.

## Command reference (select by risk, not a repeat-all script)

```powershell
Set-Location demo
.\mvnw.cmd '-Dtest=VoucherCalculatorTest,VoucherValidatorTest,VoucherConverterTest,VoucherServiceImplTest,CartVoucherServiceImplTest,VoucherControllerTest,CartVoucherControllerTest,CheckoutServiceImplTest,CheckoutValidatorTest,CheckoutConverterTest' test
.\mvnw.cmd test-compile failsafe:integration-test failsafe:verify '-Dit.test=VoucherRepositoryIT,VoucherEntitlementRepositoryIT,VoucherRedemptionRepositoryIT,VoucherMigrationIT,VoucherServiceIT,VoucherConcurrencyIT,CartVoucherServiceIT,VoucherControllerIT,CartVoucherControllerIT,CheckoutServiceIT,CheckoutControllerIT,CheckoutConcurrencyIT,VoucherFlowIT'
.\mvnw.cmd test-compile failsafe:integration-test failsafe:verify '-Dit.test=CartServiceIT,CartControllerIT,OrderRepositoryIT,OrderControllerIT,InventoryServiceIT,InventoryConcurrencyIT,CatalogQueryCountIT,ProductListingApiIT,ProductDetailApiIT,AddressServiceIT'
.\mvnw.cmd verify
```

Inspect current-run Surefire/Failsafe XML timestamps, nonzero discovery, failure/error/skip totals and Boot packaging. Exclude stale reports from old packages/selectors; no required skipped cases, optional race profile or prior Inventory green claim. Record commands/environment/counts/elapsed and failures/fixes honestly in implementation-validation.md. No application commands above ran during planning; the implementation register distinguishes later targeted and full execution.

## Acceptance scenarios

1. SC-001: exact approved500,000 fixed/percentage/cap examples,50,000 discount on30,000 caps30,000;10% of101 floors10. Percentage0.01/100 limits, invalid precision/value, huge exact whole VND, optional0 cap, no floating-point/narrowing. Compare original item unit/line values unchanged. Zero subtotal rejects; positive base rounded0 may consume once on successful voucher-bearing checkout.
2. SC-002/008: two users' own wallets, expired/disabled/exhausted current eligibility, foreign/unknown parity and no code/definition-ID application. Three one-use grants with personal limit3: spend2, retain1. Personal limit2 with3 grants rejects third. Unlimited quota still requires grant. Wallet list/detail exposes no other usage and performs no cleanup/stock/usage write.
3. SC-002/003: successful apply/reapply/replace/removal, failed replacement preserving stale prior selection, missing-cart no insertion. All existing add/quantity/variant/delete/individual/bulk mutations use current final subtotal; fall below minimum auto-clears and later eligibility never reselects. External expiry/disable/quota exhaustion read cleanup reports safe reason once; no quota consumption. Explicit item deletion204 still clears invalid selection without S3. Original stock409/price/publication/mutation rollback assertions unchanged.
4. SC-003/004/007: first voucher checkout revalidates ownership/AVAILABLE/time/global/personal/minimum after existing skipped-item rules; invalid captured selection409 without undiscounted fallback or cleanup. Success one order/redemption, exact reserved-only Inventory allocation, one consumed grant, other grants/rows preserved and captured selection cleared. Inject late encoding, cart consume, receipt, affected-count and actual final-commit failures: all counters/grants/order/receipt/reservations/cart rollback; unsuccessful key reusable.
5. SC-005/006: old1 receipt retains exact field set/status201/Location/body and old valid stock skip codes; new2 no-voucher0/null and voucher snapshots exact. Replay after grant removal/expiry/current selection/status changes uses one receipt read/no Voucher/stock/cart work, never clears new selection. Unsupported/corrupt version500, no re-execution. Scoped mapper supports prior131072-digit limit; detached codec uses0 SQL. Existing address mismatch and current permission rules unchanged.
6. SC-006: old/new history totals, stored discounts and item sums remain exact after definition edit/disable/expiry; payable sorting uses committed amount. Snapshot/redemption FK guards reject wrong customer/order/entitlement, missing/extra redemption, altered history, counters not equal committed redemptions and foreign Cart selection at actual commit. No automatic repair, legacy backfill or old receipt rewriting.
7. SC-009: normal VoucherConcurrencyIT and existing CheckoutConcurrencyIT use independent transactions/connections and bounded barriers, capture worker errors and observe real row/advisory waits. Cover final global use across customers; same-customer multiple-grant personal limit; same grant duplicate redemption; selection/replace/remove versus checkout; GET cleanup versus accepted replacement and checkout; receipt commits during acquired/busy fresh lookups; final rollback releasing availability. For expiry during a required Voucher-lock wait, hold the real row lock in another transaction, observe checkout blocked, advance a controlled Clock from before expiry to exactly validUntil or later, then release it. Require409/VOUCHER_EXPIRED and unchanged grant/selection/items/counters with no order/receipt/redemption/reservation. A before-end success control proves every persisted purchase timestamp equals the single post-lock sampled event; no pre-lock caller event or sleeps-only synchronization. Never sleeps-only overlap, sharing a rollback-only transaction or skipped original races. Restore actual timeout policies, no automatic retry; explicit later retry permitted.
8. SC-008: real bearer/current-state permission grant revocation,401/403 before shape details, safe404 ownership,400 strict media/unknown/duplicate inputs,422 owned application,409 invalid checkout and recognized conflicts,415 wrong media, private no-store on all errors. Checkout-only role succeeds/replays but no unauthorized Cart/Order access; no public/code/admin route. VoucherFlowIT combines login→owned wallet→Cart→discounted checkout→history→replay; no cancellation integration workflow.

## Measurement and rollout gates

Wallet pages/count boundary incl empty/offset overflow;100/101/205 entries/cart rows, fixed Voucher observations rather than per-item fetch, prepared no-image wallet/delete reads, original Catalog/Inventory batching, native statement capture and deferred function cost. Preserve prior ceilings where unchanged; justify only measured explicit additions. No invented SLA/capacity, amount narrowing or cart cap.

Clean apply and V25 upgrade preserve all existing carts/order totals/Inventory flags/v1 receipts; grants not fabricated. V26 version rechecked before writing, older migration targets/checksums unchanged. Legacy read0/null fields versus unchanged legacy receipt exact shape. Existing Order cancellation remains baseline only; no quota restoration/retention decision or new lifecycle handler tested/claimed. T058 requires the final unrestricted full verify including media/search/performance and normally discovered races without exclusions or skip flags, subject to the explicit blocked-environment reporting above. Repeat it only under the conditional rerun rules; targeted success never substitutes for or relabels full-suite success. Recheck extensions.yml after_implement hooks and preserve requirements checklist markers; no commit/push/deploy implied.

## Implemented preparation and compatibility details

Wallet pages use an owned AVAILABLE count and one joined projection, with long arithmetic/binding for offset; no per-grant queries. Order read preparation parses stored JSON with the scoped large-number limit before pure conversion. Legacy nonmanaged zero/null orders retain their original payable amounts; new managed and voucher-bearing orders and version2 receipts enforce exact discounted arithmetic. Historical version1 receipts remain unchanged. Status-only Order writes do not run Voucher aggregate reconciliation or change Voucher quotas. Measured query/trigger costs and final gate status belong to implementation-validation.md, not the example commands above.

## Current execution status

All61 tasks complete. The evidence register records unrestricted627 unit/MVC +654 integration tests passing, then33 affected unit/MVC +174 integration tests and current Boot packaging passing after local media/deferred-Cart-lock fixes. Full and affected results refer to different source snapshots; no second full run is claimed. All required scenarios executed with0 required skips/blocks. Future changes should select scope from the same risk matrix rather than rerun all command examples.
