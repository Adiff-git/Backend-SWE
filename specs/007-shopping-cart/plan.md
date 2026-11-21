# Implementation Plan: Cart business-rule revision

2026-11-17. Java 25 / Spring Boot / PostgreSQL / Flyway; no new dependencies or plugins. Current requirements are in [spec.md](spec.md).

## Architecture and contracts

Keep the domain-oriented cart module, Lombok DTOs under dto/request and dto/response, CartValidator business checks, pure CartConverter and exact CartCalculator. Preserve CartReadSeed, PreparedCart and AddCartResult as immutable Lombok @Value classes with defensive collections. Controllers derive owner from CurrentUserIdProvider; cross-domain operations use UserService, CatalogCartReadService and ImageUrlResolver, never other domains' repositories.

Request classes: AddCartItemRequest(productVariantId,quantity), UpdateCartQuantityRequest(quantity), ChangeCartVariantRequest(productVariantId), CartSelectionRequest(selected). Remove the ambiguous UpdateCartItemRequest and combined PUT. CartResponse adds totalItemCount, independent of selectedItemCount/selectedTotalQuantity. POST add is an explicitly approved business-action 200 for both creation and increment, rather than a resource-creation status split.

Facade methods: getCart, addItem, updateQuantity, changeVariant, removeItem, selectItem, selectAll. CartTransactionService has corresponding methods and a final nullable PreparedImageUrls on response-bearing operations. AddCartResult remains internal; HTTP serializes only its CartResponse.

## Persistence and upgrade

Leave applied V1..V21 unchanged. V22__cart_quantity_limit_999.sql replaces ck_cart_items_quantity with CHECK BETWEEN 1 AND 999. Existing invalid legacy rows make migration fail atomically with a review message; do not cap, delete or silently transform them. Review quantities before upgrade and retry after an owner-approved correction. Unique/FK/cascade/order-index rules stay unchanged. The unrelated disposable search recovery fixture moves to unused V23 so it does not collide with real V22.

## Transaction and image boundaries

Retain REQUIRED/READ_COMMITTED on the separate transaction service. Set positive transaction-local cart.lock-timeout-ms (default 5000) before the first cart PESSIMISTIC_WRITE lookup; missing-cart add coordinates UserService first, rechecks cart and reapplies cart timeout. Read children after parent coordination; do not take User locks after Cart locks. Every writer uses this protocol.

Validate quantity replacement against its retained current variant. Variant change validates owned source, eligible same-product target and then either does nothing, replaces source with quantity 1, or deletes source while leaving an existing target untouched. Flush and finish presentation/calculation before commit. Signing/final eligibility failure rolls back changes, including new cart creation and source deletion. No broad retries.

Prepare resource-free image URL capabilities outside authoritative cart transactions. Catalog presentation batches 100, takes globally PostgreSQL-ordered image publication share locks and signs locally. Missing/new images may yield null without a prepared capability; real signing failures remain 503. Image-bearing GET stays write-capable but business-nonmutating for FOR SHARE, the previously documented constitution readOnly SHOULD exception.

## Validation

Update boundary/controller/service/real database/flow tests for new semantics rather than retaining old expectations. Verify 999/1000, 998+2 rejection, explicit variant-only requests, same-variant no-op, absent target reset, existing target entire-row preservation, counts, rollback, ownership and synchronized operations. Migration tests must prove legacy 1000 is not capped and retry after a reviewed 999 value preserves data. Run full Maven verify and record actual discovery/results in quickstart.md. No commits or deployment are part of this revision request.


## Voucher integration follow-up — feature011

The earlier Voucher exclusion is superseded by feature011: owned entitlement selection, exact order-level estimated discount and invalid-selection cleanup are implemented. Item unit/line prices and Inventory eligibility remain unchanged; finalTotal is selectedSubtotal minus voucherDiscount. Wallet browsing prepares current subtotal without images, insertion or cleanup. Verified targeted deltas and the separately tracked unrestricted completion gate are recorded in [Voucher execution evidence](../011-voucher-support/implementation-validation.md). Earlier completed tasks and their historical execution records are preserved; this follow-up adds no admin/code/public-discovery/cancellation-quota contract.
