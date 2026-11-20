# Cart revision verification

Current rules and API: [spec.md](spec.md) and [contracts/api.md](contracts/api.md), updated 2026-11-17.

## Migration preparation

V22 follows applied V21. It refuses existing quantities outside 1..999 and never silently caps or deletes rows. Before deploying, review `SELECT id, cart_id, quantity FROM cart_items WHERE quantity > 999 OR quantity < 1` and explicitly correct incompatible rows according to owner/business decisions, then retry Flyway. No developer/local production database is changed by this implementation task; integration tests use disposable databases.

## Commands

From repository root with Java 25 and Docker Linux engine:

```powershell
.\demo\mvnw.cmd -f .\demo\pom.xml test
.\demo\mvnw.cmd -f .\demo\pom.xml verify
```

On this Windows environment, use the installed Mockito JAR as a startup javaagent and a workspace-local jdk.net.unixdomain.tmpdir if the existing attach/socket restrictions occur. No dependency/plugin changes are needed. Inspect actual Surefire/Failsafe class reports, not stale targeted-run artifacts.

## Acceptance walkthrough

Use a real authenticated account/session and published same-product variants A/B. Add with {productVariantId,quantity} returns 200. Quantity PUT /items/{id}/quantity accepts {quantity}; variant PUT /items/{id}/variant accepts only {productVariantId}. Check 1/999 accepted, 1000 rejected, 998+2 rejected unchanged, A=8 to absent B gives B=1 preserving source selection/time, A=8 to existing B=4 leaves B entirely unchanged, and resubmitting current variant keeps quantity. Check private/no-store, ownership, empty count zero, totalItemCount independent of selectedItemCount, unavailable rows, exact totals, atomic response failure and later-session persistence.

## Observed revision results

Verified 2026-11-17 against the revised source on draft/version-1:

- Focused acceptance: 116 tests, zero failures/errors/skips (cart, authenticated HTTP flow and catalog migration regressions).
- Full Maven verify: BUILD SUCCESS, 422 unit/controller tests plus 424 integration tests, zero failures/errors/skips; completed at 19:37:58 Asia/Saigon. The additional real-session variant HTTP regression is included in this full run (7 CartControllerIT tests).
- Actual discovered suites were cross-checked against Surefire/Failsafe XML, rather than summing stale targeted-run files. Disposable PostgreSQL 17 and LocalStack were used; no application database was upgraded.
- Covered reset-to-1, existing target entire-row preservation, same-variant no-op, strict split request bodies, 999 bounds and overflow, independent row/selected counts, ownership, rollback, lock contention/concurrency, and V21-to-V22 rejection/retry without silent capping.
- Full repository regressions include catalog performance, migration recovery, authentication, image storage, user and wishlist flows. Raw logs are local generated artifacts: demo/target/cart-new-rules-acceptance.log and demo/target/cart-new-rules-full-verify.log.

Red phase first reproduced the old final-quantity-1000 acceptance and existing-target quantity mutation. Both are now rejected/corrected by the revised tests. Deployment still requires review of any legacy quantity-1000 rows before V22 can succeed.

Historical verification on 2026-11-12 passed 342 unit/controller and 337 integration tests for the old contract. Those counts are not reused for the current rules.
