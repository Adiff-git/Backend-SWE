# Feature Specification: Persistent Shopping Cart

**Inventory follow-up (2026-11-20):** [Inventory Management](../010-inventory-management/spec.md) supersedes the earlier Inventory exclusions only for the implemented deltas below. See its [validation record](../010-inventory-management/implementation-validation.md). Existing routes, strict inputs, authorization, money and immutable historical receipts remain unchanged. Admin supply, public receipt confirmation, FAILED processing, payment and expiry producers remain deferred.

Updated 2026-11-17 from the user's confirmed replacement brief. Current branch: draft/version-1. This document defines the current rules; earlier 1,000-unit and request-quantity variant switching policies no longer apply.

## Scope and ownership

Authenticated, single-vendor cart management only: view, add, replace quantity, change variant, remove and individual/bulk selection. Cart now observes Inventory availability and validates full resulting quantities through feature010 without reserving or mutating stock. Order/Checkout workflows are owned by features008/009/010; payments/shipping, vouchers/discounts, shop grouping and guest carts/merging remain excluded. No placeholder implementations for those domains are introduced.

At most one persistent cart per user and one item per variant. Owner comes only from authenticated identity; clients cannot supply owner overrides or authoritative prices/totals. Every item operation returns the same safe CART_ITEM_NOT_FOUND for foreign and unknown items. Create a cart only on a successful first add. Missing-cart GET and bulk selection return empty items and zero totals without insertion. Logout retains the cart; last removal retains an empty cart.

## Confirmed business rules

- Quantity is a strict integer from 1 to 999 inclusive. Missing/null, zero, negative, string, fractional, exponent and excessive quantities are rejected. Add increments an existing variant, checking the widened final sum before mutation. Existing 2 + add 3 becomes 5; 998 + add 2 fails unchanged. Quantity update replaces the value and preserves variant/selection.
- Variant change accepts only productVariantId, with no quantity. The target must exist, be purchasable and belong to the source product. If absent from the cart, replace the source variant, reset quantity to exactly 1 and preserve source identity/selection/addedAt. If a distinct target row exists, delete only the source and leave every target field unchanged, including quantity, selection, snapshots and addedAt. Existing target takes precedence over reset-to-1. Submitting the current variant is a no-op and preserves quantity.
- New rows start selected. Add/replacement/variant change preserve the applicable stored selection. Explicitly selecting unavailable rows is rejected, even if already selected. Deselect/removal remain allowed. Bulk true selects eligible rows only, preserving unavailable preferences; bulk false clears all.
- Eligibility uses the current PUBLISHED product and valid nonnegative whole-VND selling price, then sufficient observed stock-reserved for the full row quantity. Availability is observational and never guarantees a later purchase. Unavailable rows remain visible and are excluded from eligible selected totals. Hidden descriptions/images are withheld; use prior-public cart snapshots. GET never changes stored selections or snapshots.
- Current line subtotal = unit price times quantity. Selected subtotal sums selected purchasable rows. finalTotal equals selectedSubtotal, with no fees or discounts. Money is exact VND integer arithmetic and aggregate quantities use checked Long arithmetic. Adding an item never locks its future price.

## User stories and acceptance

### US1: Save/revisit a private cart

A missing cart reads empty without insertion. Adding quantity 2 then 3 yields one selected row of 5. Re-add preserves selection. Later valid sessions recover the cart with current prices. Quantities 1 and 999 succeed; 1000 and a final addition above 999 fail unchanged. Concurrent accepted increments cannot be lost.

### US2: Edit quantities and variants explicitly

Quantity 2 updated to 3 becomes 3. Source A quantity 8 unselected changed to absent B becomes B quantity 1 unselected with the same source identity/time. Source A quantity 8 selected changed to existing B quantity 4 unselected removes A and leaves B entirely unchanged. Submitting B again preserves quantity 4. Cross-product, hidden/missing target, invalid price or response-signing failures leave state unchanged. Foreign and missing sources are indistinguishable. Removal accepts unavailable rows and leaves an empty cart after the last item.

### US3: Select and calculate

Selected eligible rows 2x100 and 3x50 produce selectedItemCount=2, selectedTotalQuantity=5 and total=350. Deselecting or making the first unavailable produces eligible selected total=150, while totalItemCount still includes both rows. Bulk true skips unavailable rows; bulk false clears all. Selection changes never overwrite quantity or variant changes.

## Functional requirements

- FR-001: Seven authenticated cart operations through the established bearer/session and MANAGE_CART policy.
- FR-002: Persistent owner-scoped uniqueness and safe lifecycle described above.
- FR-003: Add requires productVariantId and quantity; no trusted owner/price input.
- FR-004: Quantity replacement and variant change are separate operations with the confirmed semantics above.
- FR-005: Current catalog eligibility, retained unavailable rows and safe snapshot/image rendering.
- FR-006: Reuse Catalog-to-Inventory availability reads under feature010. Cart never creates reservations or mutates either Inventory counter; other excluded capabilities remain absent.
- FR-007: Request and final added quantities remain in 1..999 without capping or partial mutation.
- FR-008: Stored selection is independent of eligibility and never silently changed by reads.
- FR-009: Exact current VND totals, checked aggregate quantity and separate total/eligible selected row counts.
- FR-010: Complete CartResponse fields and ordering defined in contracts/api.md.
- FR-011: Follow the specified empty, repeated-add, same-variant, removal and unavailable policies.
- FR-012: Common writer coordination; atomic variant/source changes and rollback on response failure; no lost accepted increments.
- FR-013: Strict local JSON validation, ProblemDetail, authentication/authorization-first errors and safe ownership failures.
- FR-014: Preserve domain boundaries and all explicit exclusions.

## Measurable acceptance

SC-001: All seven routes return the documented resulting state/status. SC-002: Exact 350/150 examples and totalItemCount independence. SC-003: No cross-user access or owner override. SC-004: Failed validation, storage response or lock conflict commits no partial mutation. SC-005: Logout/later login preserve the cart. SC-006: Synchronized independent transactions preserve uniqueness, 999 bounds and serializable observable outcomes for the supported operations.

## Dependencies and references

Existing catalog/user/auth/storage service boundaries; current PostgreSQL/Flyway schema and established currency rules. See [plan.md](plan.md), [data-model.md](data-model.md), [contracts/api.md](contracts/api.md), [contracts/internal-services.md](contracts/internal-services.md), [tasks.md](tasks.md), [quickstart.md](quickstart.md). No new dependencies are approved or needed.

## Subsequent checkout integration

The orders/checkout exclusion above bounds this cart-management increment. [Customer Checkout](../009-customer-checkout/spec.md) separately specifies atomic conversion of eligible selected rows into one order, preserving skipped/unselected rows and coordinating existing cart mutations. Checkout is implemented with receipt-first replay and, through feature010, atomic exact order/item reservation. It introduces no payment, voucher or shipping workflow and changes no Cart route.


## Voucher integration follow-up — feature011

The earlier Voucher exclusion is superseded by feature011: owned entitlement selection, exact order-level estimated discount and invalid-selection cleanup are implemented. Item unit/line prices and Inventory eligibility remain unchanged; finalTotal is selectedSubtotal minus voucherDiscount. Wallet browsing prepares current subtotal without images, insertion or cleanup. Verified targeted deltas and the separately tracked unrestricted completion gate are recorded in [Voucher execution evidence](../011-voucher-support/implementation-validation.md). Earlier completed tasks and their historical execution records are preserved; this follow-up adds no admin/code/public-discovery/cancellation-quota contract.
