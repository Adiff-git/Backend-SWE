# Checkout Implementation Validation

Started 2026-11-18. Actual execution evidence only; pending work is not a passing gate.

## Baseline — T001

- requirements.md:16 checked,0 unchecked; read-only throughout implementation. No extension hooks configured.
- Existing cart/order/auth/test dirty changes preserved; source hashes and complete tracked diff captured under ignored demo/target/checkout-baseline-* before edits.
- Existing Java/Docker ignore patterns cover targets/classes/jars/logs/environment/editor artifacts; no new build/dependency/CI or ignore setup needed.
- Active feature009-customer-checkout resolved by repository PowerShell prerequisites equivalent; Bash unavailable as previously verified. Actual branch remains draft/version-1.
- At this baseline checkpoint, production checkout and proposed tests were not implemented; later execution evidence follows.

T002: Java25.0.4.1, Maven3.9.16 and Docker29.8.0 verified via approved unsandboxed execution; highest migrationV23, V24 free. No configured static analysis tool added or claimed. T003: fresh receipt values and direct-SQL integrity fixtures added, caller owns transaction/cleanup. T004 red migration/schema tests written before migration/production receipt types.


T004 red executed:2 discovered,2 failures,0errors/skips in checkout-foundation-red.log (missing MANAGE_CHECKOUT/schema constraints as expected), before V24. T005/T006 additive V24 written with success-only scoped receipt, composite owner FK, strict JSON/money/snapshot/deferred integrity and immutable update trigger plus USER grant. Green verification follows.


T007–T010: immutable entity/scoped native repository, safe errors and strict immutable shared DTOs added. First green foundation run:3 tests passed,0failures/errors/skips (checkout-foundation-green.log). Stronger malformed-body/deferred-forgery and independent connection lock-release cases added; follow-up running in checkout-foundation-final.log. Existing source baseline still untouched outside new Checkout files/V24.


Foundation gate T011/T012:3 repository checks passed in checkout-foundation-final.log, including exact lookup/case/owner and independent-connection commit/rollback lock release. Strengthened migration test initially failed only because PostgreSQL omits constraint metadata from printed exception; assertion corrected to verify server constraint metadata and SQLSTATE23514 directly. Affected-class rerun checkout-migration-final.log passed1 test,0failure/error/skip. Four final foundation cases green; no second combined-run claim. Malformed body, forged snapshots at deferred commit, history-preserving upgrade, immutable receipt and USER-only grant verified.


US1 tests-first: checkout-us1-red.log records missing Checkout/cart/catalog/order service symbols before their implementation (no passing/discovery claim). Initial focused green:29 unit/MVC,0failures/errors/skips. Initial real integration green:17 cases across cart/catalog/order/checkout and relocated AddressServiceIT,0failures/errors/skips (checkout-us1-acceptance.log). Existing address class10 tests retained plus1 checkout method, original test bodies verified against tracked baseline after reversing only package/import/new test additions. No duplicate class. Expanded HTTP/history/current-permission and strict input checks now running; code includes receipt-first/acquired/busy rechecks and exact atomic consumption. Additional prepared calculation DTO/repository fragments/config support introduced only for real responsibilities, reconcile paths at final docs task.


US1 expanded gate:34 unit/MVC passed0fail/error/skip (checkout-us1-boundaries.log), including131072-digit receipt roundtrip and unit/line/aggregate bounds. Real domain/API/history rerun25 passed0fail/error/skip (checkout-us1-final.log). Earlier22-case run had one invalid fixture deleting a product's last variant outside product-deletion transaction; corrected fixture respects catalog deferred invariants, no production rule weakened. Current-role checkout-only permission, foreign/unknown404, original201, Location CORS exposure, historical address/catalog deletion, exact consume conflict rollback,100/101/205 distinct batching and order-number collision rollback verified. Final focused clock/independent parent+variant share-lock proof running before T033 checkpoint.


T018/T019/T033 independent US1 complete: checkout-clock-proof.log2 unit tests green; checkout-catalog-lock-proof.log3 real catalog cases green. Additional candidate-discovery/reparent race initially exposed Mockito abstract-method spy invocation, fixed test harness to use repository spy's original delegating Answer (real SQL retained); checkout-catalog-race-final.log4/4 green. Explicit product+variant share locks, current projection/batch behavior and reparent409 proven with independent PostgreSQL writes. No production behavior weakened and no invented red for already-correct cases. Tasks001–033 complete.


US2 T034–T039:38 unit/MVC green in checkout-us2-unit.log,21 PostgreSQL/API/domain green in checkout-us2-acceptance.log. Existing US1 implementation already fulfilled partial selection/classification/ProblemDetail: no artificial red or unnecessary production rewrite. Tests prove exact retained fields, safe reason precedence, no-eligible/reusable key, quantity1/999 at price0, NaN/Infinity/fractional/negative price representations via rollback-only corruption fixture (constraint/value restored), actual catalog SQL42P01 abort, item/receipt write failures and deferred23514 commit rollback. T040 final encoding-failure spy executes real order/cart persistence then verifies rollback; running checkout-us2-final.log. Tests contain no excluded stock/voucher implementation.


T040/T041 complete: checkout-us2-final.log13 actual service cases passed0failure/error/skip including receipt encoding failure after real order/cart writes, SQL write failures, query failure and deferred commit failure. All temporary corruption/DDL constraints rolled back/restored and successful key reuse verified. US2 complete; tasks001–041 marked from implementation/execution evidence, reviewer checklist unchanged.


T042–T045: checkout-us3-replay-unit.log11 unit/codec cases green; checkout-us3-key-races.log14 real integration cases green. Existing CheckoutConcurrencyIT5 cases prove simultaneous completed replays(no fence), acquired-fence fresh committed hit, busy-fence fresh committed hit, genuinely uncommitted original409 then201, different-key exactly-once consumption/later addition. Real repository/JDBC fresh visibility on independent connections and real-bearer lost-response/address-delete/cancellation replay passed. Test-only synchronization wraps original repository delegate; no production callbacks or excluded stock/voucher stub/mutation introduced. Current source unchanged for these already-correct behaviors.


T046: checkout-us3-cart-concurrency.log21/21 green, including16 combinations of ADD/quantity/variant/remove/individual true+false/bulk true+false vs Checkout in both lock orders. Each observes pg_stat_activity wait_event_type=Lock on carts before releasing holder. Actual CartService writers participate, exact remaining fields/quantities/variants checked; re-added rows retain new identity/quantity; stale consumed-row mutations safely reject. Five keyed cases rerun green in same class. No production mutation fixes required.


T047: initial35-case domain run34 passed and one variant-price overlap raised actual40P01: existing V3 deferred variant invariant obtains parent FOR NO KEY UPDATE, while test SQL writer had variant-first order. Cooperative controlled catalog writer corrected to acquire parent first, matching documented lock hierarchy; no production lock/order change or applied migration edit. Targeted checkout-us3-domain-final.log14/14 real address/catalog cases pass in both orders, preserving snapshots or safe404/422 and history across valid catalog deletion. Valid deletion respects existing restrictive cart FK by serializing cart-reference removal before child/product deletion; no cascade policy invented. Existing CatalogCheckoutServiceIT retains real reparent-discovery race proof. Explicit variant-first conflict regression carried into T048 to prove rollback409 rather than ignore original conflict.


T048/T049: checkout-us3-config-unit.log14/14 green (configuration bounds and canonical fingerprint included); checkout-us3-concurrency-final.log41/41 green. Real55P03 held-cart/product/variant timeouts map409 CHECKOUT_CONFLICT, restore all state and permit explicit same-key retry. User3000ms wait survives beyond checkout1000ms; current_setting at cart boundary proves checkout1s reapplied after user policy. Deliberately variant-first V3 trigger deadlock40P01 retained as separate regression: exactly one loser, safe409, no partial receipt/order/cart loss, explicit retry when checkout loses; no application automatic retry. Four I1 race scenarios plus16 cart and14 address/catalog permutations all normal-gate tests. No production fixes needed for these accepted conflict behaviors.


T050–T052: checkout-us3-compat-unit.log13/13 green, including corrupt body/unsupported version rejection without fallback checkout and wide-number receipt codec. checkout-us3-flow.log12/12 green:6 real-bearer API cases with actual logout/revoked-token/new-session recovery,1 strengthened migration,4 repository visibility/lock cases,1 real local HTTP full account/address/cart/partial-checkout/history/cancel/session flow. Receipt immutable after address/catalog/current-status changes. Physical C1 relocation retains10 old methods/assertions plus new snapshot test; full current class11 cases previously passed. No duplicate task/test classes or stock/voucher workflow introduced.


T053–T055: checkout-us3-feature-unit.log48/48 unit/MVC and checkout-us3-feature-verify.log89/89 integration cases passed, zero failures/errors/skips. Includes all41 synchronized concurrency cases and11 relocated address tests. New failed deferred-commit retry changes owned address before success; successful key then rejects original command. Same key across customers and case-distinct successful keys remain independent.


T056: checkout-regressions.log43/43 integration tests passed, zero failures/errors/skips. Only latest migration assertions advance to24, isolated disposable recovery successor25; exact USER grants add MANAGE_CHECKOUT. Historical migration assertions and all previous grants preserved.


T057: checkout-resource-proof.log23/23 integration cases passed, zero failures/errors/skips. Hibernate prepared statements for100/101/205 included rows:118/124/233 (overhead18/23/28 after per-item inserts). Catalog candidate/parent/child/final queries and exact consumption batch100. No entity fetch/image query; detached encode/decode0 additional SQL; each replay exactly1 receipt lookup. These are client Hibernate statement observations, not server trigger statement counts, a row cap or latency/load SLA. Existing scoped codec131072-digit tests leave public Long parsing unchanged.
T058: full .\\mvnw.cmd verify started; target/checkout-full-verify.log is the actual full-gate output. Completion/counts pending.


## Success-criterion evidence and review

| Criterion | Executed focused evidence |
| --- | --- |
| SC-001 selected-only/partial checkout | CheckoutServiceIT, CartCheckoutServiceIT, real HTTP CheckoutFlowIT: one order, exact included quantities, skipped/unselected fields preserved. |
| SC-002 exact current VND | Calculator/validator/converter boundaries, current-price domain cases, zero-price quantities1/999, values above Long,131072/131073-digit bounds. |
| SC-003 permanent recovery | Receipt-first unit sequencing, independent committed visibility, four synchronized I1 races, case/customer scope, real logout/new-session and changed/deleted address/catalog/current order replay. |
| SC-004 access/ownership | Strict MVC and real bearer tests, checkout-only role, grant revocation/inactive account/expired session, indistinguishable foreign/missing address404 and independent order permission. |
| SC-005 atomicity/concurrency | Actual deferred commit and item/receipt/query/encoding failures;41 synchronized races including16 cart and14 address/catalog combinations, held-lock55P03 and deadlock40P01; reusable failed key and no duplicate order/consumption. |
| SC-006 historical order compatibility | Real HTTP flow through list/PENDING/detail/cancel; persisted historical address/item snapshots survive later edits/deletions; replay remains original PENDING. |
| SC-007 scope | Sole new route POST /api/me/cart/checkout; no excluded stock/voucher/payment/shipping/image/network dependency or stub, no new build/dependency/CI change. Full regression gate recorded separately. |

Query observations measure Hibernate client statements in disposable PostgreSQL fixtures; database trigger internals and production latency/load/storage capacity are not claimed. Successful receipts have lifetime retention, so storage grows with orders. Existing shared wildcard CORS origins remain a pre-existing configuration issue; only Location exposure is changed here. No deployed/shared migration, commit, push or deployment was performed.

Final scoped review (while full gate runs): SHA256 baseline comparison found only the intended11 existing production files,5 regression test files and the removed old address-test path changed since checkout began; all other captured files preserved. Independent exact-body comparison verified all10 original AddressServiceIT tests unchanged, one relocated class, eleven current cases. No old migration/dependency/build/CI changes, excluded route or placeholder; markdown relative links and git diff --check pass. Prepared DTOs, owning-domain fragments, pure converter and acyclic checkout -> user/cart/catalog/order boundaries reviewed. No configured static-analysis gate exists; none invented. Post-implement extensions.yml is absent.


## Final gate — T058–T060

Full command: from demo/, .\mvnw.cmd verify > target/checkout-full-verify.log 2>&1. BUILD SUCCESS,2026-11-18 15:15:39 Asia/Saigon,20:10 elapsed. One complete full verification run; no broad rerun or skip flags.

- Surefire:534 tests across100 current-run XML suites;0 failures,0 errors,0 skipped.
- Failsafe:529 tests across109 current-run XML suites;0 failures,0 errors,0 skipped.
- Total1063 executed checks, including all41 normally discovered CheckoutConcurrencyIT cases, CheckoutServiceIT18 and relocated AddressServiceIT11. Current-run XML aggregates independently match the Maven log; no stale report aggregate used.
- Full existing cart/order/auth/user/catalog/migration/media/wishlist/behavior/flow regressions and performance/query-plan tests passed. Maven jar/repackage and packaged-application integration checks passed.
- Feature-focused current coverage:48 unit/MVC plus92 integration cases including11 relocated address cases,140 checks (130 net new relative to the ten preserved address tests). Earlier combined focused IT run89 preceded the three resource-instrumentation cases; those ran in the23-case resource gate and the final full gate. No claim of a second92-case focused command.
- T059 documents reconciled: V24 schema, actual helper/fragments/support paths, immutable native order creation, safe error properties, scoped wide-number codec, parent-before-child catalog coordination, actual commands/counts and SC-001–SC-007 evidence. Business/API policy unchanged.
- T060 review passed: all60 task markers complete; quality checklist remains16/16 unchanged. Relative Markdown links and git diff --check pass; C1 ten original test bodies exactly preserved, one class. Baseline dirty work preserved; no prior migration/build/dependency/CI change or excluded workflow. Post-implement .specify/extensions.yml absent, no hooks to dispatch. No commit/push/deployment.

No remaining consistency or Constitution alignment finding identified in the scoped final review. Limits remain those stated above: disposable fixtures and measured queries establish behavior, not a production capacity/deployment claim. No application source/test change followed the successful full gate; only documentation/task completion records changed.
