# Voucher HTTP contracts

Binding contract for [spec.md](../spec.md); implementation evidence is in [implementation-validation.md](../implementation-validation.md). Existing Cart/Checkout/Order contracts remain except the explicit extensions here. No public /api/vouchers, code redemption, admin, grant creation or cancellation endpoint.

## Routes and access

| Method/path | Access | Input | Success |
| --- | --- | --- | --- |
| GET /api/me/vouchers | Current authenticated eligible identity | page,size only |200 owned wallet page |
| GET /api/me/vouchers/{walletEntitlementId} | Same; own grant only | No query/body |200 own grant detail |
| PUT /api/me/cart/voucher | MANAGE_CART | Exactly {walletEntitlementId} |200 complete CartResponse |
| DELETE /api/me/cart/voucher | MANAGE_CART | No query/body |204 even absent selection/cart |

Exact route matchers precede denyAll; authenticated wallet route does not imply any admin bypass. Ownership resolved only from CurrentUserIdProvider. Existing MANAGE_CHECKOUT alone permits first checkout/replay, without wallet/Cart read permission; MANAGE_ORDER still protects Order routes. All personalized success/errors private,no-store, including filter failures. No new permission/role grant is required for authenticated own-wallet reads.

IDs are positive Long1..9223372036854775807. PUT requires exactly one nonnull JSON integer walletEntitlementId, no strings/fractions/exponents/overflow/duplicates/unknown keys/trailing JSON. code/voucherId/userId/cartId/prices/discounts are not aliases. Query forbidden. Nonempty body requires JSON or compatible application/*+json; missing body400, unsupported media415. DELETE/GET reject any supplied nonempty body. Path IDs use existing strict canonical positive-Long conventions.

Wallet list page default0>=0, size default20 in1..100; literal integral query values once, no unknown parameters. Fixed normalized code ASC then entitlement id ASC. Application page DTO: items,page,size,totalElements,totalPages,first,last; authoritative owned AVAILABLE count, not public definition count. Widen offset arithmetic before multiplication; empty count shortcut, explicit bound long native offset for nonempty >Integer.MAX_VALUE. No PageImpl JSON contract. Concurrent changes are observational, no snapshot across requests.

## Wallet representation

Each item/detail: walletEntitlementId,voucherId,code,title,description,discountType,discountValue,minimumSubtotal,maximumDiscount,validFrom,validUntil,enabled,grantedAt,applicable,ineligibilityReason. Nullable description/cap/bounds are always present null. Fixed monetary values are exact VND amounts; discountValue is fixed VND integer for FIXED_AMOUNT or exact percentage decimal for PERCENTAGE (discriminator documented). Monetary subtotal/cap fields use existing {amount,currency:VND}; percentage is not a money object. No owner IDs, global/customer usage counts, other grants or administrator fields. Detail additionally entitlementState and consumedAt nullable; retained own consumed detail reports false/ENTITLEMENT_UNAVAILABLE, active list excludes it. Available expired/disabled/exhausted grants stay in active wallet but are inapplicable. Multiple grants remain distinct entries; no implied free reissue.

Cart-owned wallet facade obtains current eligible selected subtotal observationally (no image preparation/signing and no cart insertion/cleanup) and asks Voucher to prepare page/detail applicability. Empty cart base0 makes grants inapplicable, not absent. Wallet browse never applies a grant or reserves usage; views do not guarantee checkout.

## Cart response additions

Preserve existing items/counts/selectedSubtotal. Add voucherDiscount money(always present), voucher nullable, voucherSelectionClearedReason nullable. finalTotal becomes estimated payable. voucher contains walletEntitlementId,voucherId,code,title,discountType,discountValue,maximumDiscount and calculationBase/appliedDiscount money; only a currently applicable selection is returned. No selection: voucher=null, discount0, finalTotal=selectedSubtotal. An eligible voucher whose rounded discount is0 may still be selected. No eligible subtotal0 means no application; auto-clear previous selection and all totals0. Item unitPrice/subtotal/count eligibility stays governed by Inventory/Cart; no per-item discount fields.

On successful Cart view/mutation, invalid selection is cleared and safe voucherSelectionClearedReason reported. The reason is response-local, not durable state or private terms from a foreign grant. Subsequent read normally has null reason. DELETE item204 still performs cleanup even though it cannot return the reason. DELETE voucher204 intentionally clears; subsequent read has null reason. Failed apply/replace leaves previous selection intact; no cleanup commits as a rejection side effect. External invalidity during checkout rejects409 without clearing selection; later explicit Cart evaluation can clear it. No automatic reselection when eligibility returns.

## Checkout and history

Request/key contract unchanged: addressId only and existing Idempotency-Key, no voucher input/fingerprint of live selection. First execution captures cart entitlement and selection revision under lock, calculates included base after skips, acquires all required Voucher locks, then samples the authoritative event and revalidates owned grant/quota/validity before calculating discount. A wait across validUntil rejects409 VOUCHER_NOT_APPLICABLE with reason VOUCHER_EXPIRED; exactly validUntil is expired. Order, Inventory reservation, grant consumption, redemption and receipt timestamps share the same post-lock event. Any invalid captured grant409 VOUCHER_NOT_APPLICABLE, no undiscounted fallback. Empty included set retains existing422. Success201/Location, new receipt version2 (internal version, not client selector), original PENDING header plus existing items/totals/skips and voucherDiscount/voucher. voucher is full immutable order snapshot defined in [data model](../data-model.md); absent voucher null, discount0. Existing items retain exact old fields/values. Selected entitlement consumed, other grants retained, included rows removed and selection cleared only on same successful commit.

Version1 replay returns original exact old field set without added zero/null discount fields. Version2 replay returns its exact original discount explanation. Neither queries current wallet/cart/stock or mutates them. Address mismatch remains409 CHECKOUT_KEY_CONFLICT; busy fresh-receipt miss409 CHECKOUT_IN_PROGRESS. Expiry, selection changes/clear, consumption, logout and later status never invalidate committed replay.

Owned Order summary adds voucherDiscount money and voucher nullable compact snapshot (voucherId,code,title); detail adds full voucher snapshot. Existing merchandise/payable totals, previews and sorts remain; payableTotal reflects committed discounted amount. Legacy summaries/details have0/null additive fields, while legacy receipt bodies remain unchanged. No live voucher metadata is fetched to explain history.

## Errors and precedence

400 malformed shape/query/IDs;401 identity;403 permission;404 same foreign/unknown entitlement;422 owned apply/reapply ineligible (VOUCHER_NOT_APPLICABLE);409 independent checkout invalidity/recognized quota/conditional conflicts;415 unsupported media;500 unknown faults. Safe ProblemDetail code/reason only; no SQL/usage counts/foreign metadata. Existing unavailable catalog/stock Cart errors retain precedence; an otherwise valid mutation auto-clears its voucher instead of rejecting for voucher ineligibility.

After ownership, reasons: ENTITLEMENT_UNAVAILABLE, VOUCHER_DISABLED, VOUCHER_NOT_STARTED, VOUCHER_EXPIRED, GLOBAL_USAGE_EXHAUSTED, CUSTOMER_USAGE_EXHAUSTED, NO_ELIGIBLE_ITEMS, MINIMUM_SUBTOTAL_NOT_MET. Missing ownership always safe404 on apply/detail; invalid persisted cart reference may clear as VOUCHER_UNAVAILABLE without foreign details. Infrastructure failure is not business ineligibility. Checkout maps recognized integrity/lock failures to existing CHECKOUT_CONFLICT; Cart selection races use safe CART_CONFLICT; exact Voucher constraint allowlist only, no blanket SQLSTATE conversion. Failed transaction rolls back all work.

Implementation notes: CartPreviewService and CartWalletReadService provide the Cart-owned preparation boundary; VoucherCacheFilter covers personalized success/filter errors. Stored Order JSON is materialized by the Order read service before conversion. Legacy nonmanaged zero/null history retains its prior payable amount; new managed/voucher-bearing purchases and version2 receipts enforce exact discounted arithmetic. Voucher aggregate validation does not run for Order status-only updates. No cancellation/confirmation/payment policy was added.
