# Internal Service and Persistence Contracts

**Inventory follow-up (2026-11-20):** [Inventory Management](../../010-inventory-management/spec.md) supersedes the earlier Inventory exclusions only for the implemented deltas below. See its [validation record](../../010-inventory-management/implementation-validation.md). Existing routes, strict inputs, authorization, money and immutable historical receipts remain unchanged. Admin supply, public receipt confirmation, FAILED processing, payment and expiry producers remain deferred.

Implemented boundaries, with Inventory follow-up as documented below. All paths are under demo/src/main/java/com/example/demo unless stated otherwise.

## OrderController and OrderRequestReader

Controller derives Long ownerId from common.identity.CurrentUserIdProvider, parses strict input and delegates to OrderService. Never read repositories. Reader validates duplicate/unknown fields, token types, canonical Long IDs, query single-occurrence/ranges/sort allowlist and absent-body semantics. CancelOrderRequest is a Lombok request class with applicable Jakarta validation; ensure normalized code-point validation matches the HTTP contract. Ordinary binding alone is insufficient. Map only approved sort fields to Pageable values. Reject any body on GET routes, and any query on detail/cancel. Controller success responses add no-store.

## OrderService / service.impl.OrderServiceImpl

- listOrders(Long ownerId, OrderListRequest request) -> OrderPage.
- getOrder(Long ownerId, Long orderId) -> OrderDetailResponse.
- cancelOrder(Long ownerId, Long orderId, CancelOrderRequest request) -> void; a validated absent body is represented as no reason.

Read facade methods have no encompassing transaction: call OrderReadService, collect distinct non-null product IDs from only the returned preview/detail rows, call CatalogOrderReadService, then invoke OrderConverter using immutable snapshots and prepared URLs. No catalog metadata rewrites, entity lazy loads or data access inside converter. Ownership-scoped seeds must be obtained before asking catalog for images. No lazy entity survives the read boundary.

Public cancelOrder is @Transactional REQUIRED/READ_COMMITTED, activated by controller/service proxy (not self-invocation). Validate input before locking; set transaction-local order lock timeout, fetch owned row FOR UPDATE, validate state. PENDING/CONFIRMED: sample one microsecond UTC event, validate it, prepare owned item seeds, release exact managed ACTIVE allocations and set CANCELLED/reason atomically. Explicit legacy skips Inventory. CANCELLED: no-op return before Clock or Inventory. SHIPPING/COMPLETED/FAILED: OrderConflictException. Unknown/foreign: OrderNotFoundException. Commit failure propagates before controller returns 204. No response-signing/network call or catalog lookup. Managed first cancellation uses one owned item projection for the prepared resolution command; legacy/repeat performs no allocation/item work. No generic updateStatus method or receipt-confirmation stub.

Use injected existing Clock bean and validate configured order.lock-timeout-ms>0. Cancellation timestamps must meet placedAt constraint; fixtures use a controlled clock after placement. Classify CANCELLED as idempotent before sampling Clock; a valid repeat returns 204 even if the clock now precedes placement. For an eligible PENDING/CONFIRMED first cancellation, sample once; if that sample precedes placement, return 409 OrderConflictException/ORDER_CONFLICT without changing anything. Domain OrderValidator owns this persisted-state decision.

## OrderReadService / service.impl.OrderReadServiceImpl

- getOrderPageSeed(Long ownerId, OrderListRequest request) -> immutable OrderReadSeed.Page.
- getOrderSeed(Long ownerId, Long orderId) -> immutable OrderReadSeed.Detail.

Each is @Transactional(readOnly=true), REQUIRED/READ_COMMITTED, no write lock. Repository projection values are copied/prepared before return. Owner/header/status/count metadata come from orders; items are loaded only for page-selected order IDs or an already-owned detail parent. Read seeds contain all data needed for conversion without JPA traversal. Count/content consistency guaranteed for unchanged data, not a multi-request or concurrent snapshot. No customer/profile/catalog read is required for historical text/amounts.

getOrderPageSeed first executes the matching owned/status count. Compute offset=(long)page*size, widening before multiplication. If offset >= totalElements, produce the empty prepared page with accurate requested page/size and filtered totals; do not call either content method, preview lookup or catalog image preparation. Otherwise dispatch by offset range: <=Integer.MAX_VALUE to the standard JPA content query; >Integer.MAX_VALUE to the explicit custom native SQL OFFSET path. Count once, never let a returned Page implementation infer/replace the authoritative count, never cap/wrap/narrow offsets, and skip preview/catalog work if concurrent changes leave content empty. Existing metadata overflow safeguards remain in force.

## OrderRepository / OrderItemRepository

- Explicit countOwnedOrders(ownerId,status) runs first. Both content paths use the same owner/status predicates and allowlisted sort plus id ASC; Pageable remains the internal page/size/sort representation, not the public response.
- Normal header content query returns a List of prepared projections with Pageable only when its long offset <=Integer.MAX_VALUE; it must not issue another count query.
- Add the OrderPageRepository custom fragment and OrderPageRepositoryImpl in order/repository. Its long-offset header query uses EntityManager native SQL with bound long OFFSET and size LIMIT parameters in the SQL text itself, not Query.setFirstResult/setMaxResults pagination for the offset. Map the header projection explicitly and choose SQL ORDER BY only from the four trusted allowlisted templates. No new JDBC dependency, unrestricted property interpolation, invalid JPA offset or cross-domain repository access. The custom fragment is part of OrderRepository. Test its direct long-parameter binding against a small PostgreSQL fixture and synthetic service counts, not billions of rows.
- Owned detail header by customer_id+id; return absent for unknown/foreign.
- Owned row lock by customer_id+id using PESSIMISTIC_WRITE/FOR UPDATE. No order-item join on mutation lock query, avoiding locking independent catalog/account rows.
- Transaction-local set_config('lock_timeout', configuredDuration, true) follows current cart convention.
- Preview scalar query restricts input to page order IDs, computes row_number/count per order, filters rank<=3 in an outer query, returns historical preview fields, product ID and total row count. It must not rank after globally limiting all rows, and it must not filter catalog visibility. At most 300 application rows at page size 100.
- Detail scalar query fetches all historical items in one query, order by id ASC.

Expected order-data query envelope: count-shortcut list uses exactly one count statement and no content/preview/catalog queries; nonempty list uses count first + one selected header content path + one preview query (at most 3 statements), never duplicate counts; detail uses one header plus one items query. If concurrent changes empty a previously nonempty page, return empty content with count-time totals and skip preview. No query per order/item. Window counting may read all item rows for selected orders internally. Catalog queries/signatures are measured separately by bounded chunks, not mislabeled as zero query overhead.

## OrderValidator and OrderConverter

Validator owns owner/state/transition/clock checks and repeat classification, with applicable request normalization shared once rather than duplicated across layers. Structural validation remains controller/request concerns. Converter maps prepared seeds to dedicated summary/detail DTOs, wraps exact amounts using existing SellingPrice conventions, maps item previews separately, and calculates remainingItemCount from known row count. It must not recompute historical totals or access repositories/services. Units/line totals are stored inputs; fixture/domain validation owns arithmetic integrity.

## CatalogOrderReadService / CatalogOrderReadServiceImpl

getPrimaryImageUrls(Collection<UUID> productIds) -> Map<UUID,String>, missing entry means null image. Empty input returns empty map without signer/database access. Deduplicate IDs, filter nulls and chunk at 100; full detail can exceed 100 products.

Own catalog repositories only. Reuse ProductRepository.lockStatusesForShare and add a neutral scalar primary-image projection to ProductImageRepository as needed; do not call cart-specific service or expose object keys. Normal REQUIRED read-write transaction for publication locks; caller facade is not in an order read-only transaction. Prepare ProductImageStorage.PreparedReadSigner before locking. Lock candidates in globally ascending PostgreSQL UUID order, reread current primary image after locks and sign only PUBLISHED. No current product name/price/options/purchasability is returned. Share-lock timeout/deadlock remains a real database conflict rather than silent image absence.

Handle expected CatalogStorageUnavailableException from prepare/sign/close inside this method; prepare failure yields empty map, per-product signing failure omits that URL, close failure is safe image degradation. Never catch arbitrary database exceptions and return an empty map. Since signing wrapper methods are local storage boundaries rather than transactional proxies, caught expected image failures must not leave rollback-only state; verify this at a real service boundary. No S3 upload/delete or persisted URL. Never hold order mutation locks while preparing images.

## Errors and authorization integration

Order custom exceptions extend common.exception.ApplicationException with 400/404/409 and safe domain codes. Existing ApiErrorFactory handles them and already recognizes PostgreSQL lock states 55P03/40P01. Unsupported-media exceptions intercepted by the existing global cart advice delegate to ApiErrorFactory; its order-path-only mapping returns HTTP 415 without changing cart advice or weakening other error handling.

AuthSecurityConfiguration adds exact MANAGE_ORDER matchers for GET /api/me/orders, GET /api/me/orders/*, POST /api/me/orders/*/cancel. Do not use an unrestricted order-path matcher that would grant future mutations. Permission migration grants USER through role_permissions; no ADMIN bypass. CurrentStateJwtConverter re-reads grants per request; test revocation with the same token. No auth flow changes.

## Required verification boundaries

- Pure validator/converter/read/facade unit tests: state matrix, timestamp/reason preservation, exact money wrapping, strict preview mapping, prepared data, optional images. No Spring context.
- MVC slices: all input shapes/media types, query rules, UUID refs/Long order IDs, no-store, authentication/authorization-first outcomes and exact 200/204/400/401/403/404/409/415 contracts.
- PostgreSQL repository/migration IT: filters/counts/window preview/ties, FKs/deletion, money/JSON/state/deferred aggregate constraints and USER grant. Read output remains usable after transactions close; no N+1.
- Service IT: committed cancellation and rollback, sequential repeats with different reasons, synchronized competitors and lock timeout, missing/foreign parity, snapshot immutability and image-failure nonfatal behavior. Separate fixture transactions and fresh verification reads; no test-level @Transactional.
- Catalog image unit/IT: chunks/duplicates, absent/unpublished/deleted images, prepare/sign/close failures, ordered locks/publication race, no hidden URLs or exposed keys; isolated LocalStack only where actual signed access is tested.
- OrderFlowIT: real bearer/current-state permissions -> list/detail -> first/repeat cancellation -> later-session read; no receipt-confirmation capability. Add fresh order helpers in testdata.order/fixture.order, using existing account/catalog factories without putting order logic in them.

## Guarded internal completion (feature010)

OrderCompletionService/Impl has no controller or customer grant. Lock the owned order with its existing timeout; only first SHIPPING may complete, COMPLETED repeats return before Clock/Inventory, and all other states (including FAILED) conflict. Sample/validate one microsecond event, prepare exact owned item seeds, consume managed allocations by decrementing both stock and reserved, then set COMPLETED/completedAt in the same transaction. Explicit legacy skips Inventory. Existing-order actions lock Order then balances sorted by original UUID then allocations; no network under these locks. Unknown/foreign remains the same safe404. The OrderCacheFilter applies private,no-store to success and security/media/business errors. Reads and pagination are unchanged.


## Voucher integration follow-up — feature011

The earlier Voucher exclusion is superseded by feature011: summary/detail add stored original voucherDiscount and nullable immutable snapshots; payable sorting and historical items remain unchanged. Legacy zero/null history retains original payable amounts. Cancellation/completion contain no Voucher policy calls or status-driven quota reconciliation. Verified targeted deltas and the separately tracked unrestricted completion gate are recorded in [Voucher execution evidence](../../011-voucher-support/implementation-validation.md). Earlier completed tasks and their historical execution records are preserved; this follow-up adds no admin/code/public-discovery/cancellation-quota contract.
