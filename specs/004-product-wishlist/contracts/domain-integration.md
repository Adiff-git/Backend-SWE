# Domain Integration and Transactions

## Boundaries

Proposed new contracts, not currently implemented capabilities:

| Owner | Contract | Behavior |
| --- | --- | --- |
| WishlistService | listWishlist(Long ownerId, int page, int size) -> WishlistPage | Trusted controller identity only; visible scalar page/count plus prepared summaries |
| WishlistService | addProduct(Long ownerId, UUID productId) -> AddWishlistResult | created boolean selects 201/200; one response item |
| WishlistService | removeProduct(Long ownerId, UUID productId) -> void | Owned deletion; absent success |
| ProductCatalogService | getPublishedProductEntity(UUID id) -> Product | Eligible reference; missing/nonpublic ProductNotFoundException; no detail fetch |
| ProductCatalogService | getProductSummaries(Collection<UUID> ids) -> Map<UUID, ProductSummary> | <=100 unique IDs; empty -> empty; prepared summaries for still-public IDs only |
| UserService (existing) | getUserEntity(Long id) -> User | Existing trusted reference/existence boundary |

Controller uses CurrentUserIdProvider; services do not parse JWT. Wishlist services access only WishlistRepository. Catalog owns eligibility/category/image fetching/signing; its converter maps prepared entities/URLs. WishlistConverter wraps already-prepared ProductSummary and addedAt without storage/query calls. Domain validator checks identity presence, paging and applicable business preconditions without duplicating catalog eligibility. Dependencies remain wishlist -> catalog/user/common, no reverse service dependencies.

WishlistRepository owns scalar page/count/lookup, owner/product deletion and targeted atomic insertion. Native DML must share the service/JPA transaction. Small custom repository fragment using existing EntityManager facilities is allowed if necessary; no new dependency. Visibility joins/count use identical owner/PUBLISHED predicates before limit/offset, with Pageable; no collection fetch joins or per-item relationship reads.

## Addition

1. Service-owned REQUIRED/READ COMMITTED read-write transaction. Validate trusted identity and resolve user through UserService; a now-missing current user is an identity failure.
2. Catalog getPublishedProductEntity establishes eligibility without fetching details/variants/gallery.
3. Insert membership with generated identity/server Clock time, targeted ON CONFLICT DO NOTHING. Row count 1 means created, 0 duplicate; duplicate is a successful no-op without transaction failure. Preserve UNIQUE(user_id, product_id), never update addedAt, and do not introduce pessimistic locking for addition/duplicate resolution. Existing catalog image-signing safeguards serve a separate publication-visibility invariant.
4. Separate scalar SELECT obtains saved time, including committed concurrent winner. Concurrent removal leaving no row becomes safe 409, without automatic retries.
5. Prepare one catalog summary through the batch boundary. Missing eligibility -> 404; signing failure -> 503. Either rolls back new membership. Convert and return created flag/item; commit must complete before HTTP success.

Conflict-ignore insertion does not abort the transaction. A real FK failure does: allow rollback before safe translation using SQLSTATE 23503 and exact named FK identity. Known user FK ->401; product FK ->404; no query inside the failed transaction. Existing central ApiErrorFactory may translate these targeted failures after transactional invocation unwinds. Unknown integrity failures stay safe 500; never convert all integrity failures into duplicate success. No self-invoked transaction boundary or REQUIRES_NEW recovery.

## Listing

Use REQUIRED write-capable transaction solely for approved signing safeguards; no business writes. Retrieve bounded scalar page and matching visible count. Catalog batch fetches categories/primary-image metadata only, prepares credentials before sorted UUID FOR SHARE image-product checks, then signs locally. No network call while holding those locks. Non-image products receive final eligibility revalidation before mapping; no blanket parent/user locks.

If any selected ID lacks a final eligible summary, return safe 409, not a filtered short page. Stable fixtures require accurate totals/full pages. Separate READ COMMITTED page/count statements can observe intervening committed changes; no stronger snapshot promise is made. Combine prepared summaries in original membership order. Serialize DTOs after transaction closure with no lazy queries (existing OSIV=false).

Plan <=6 feature SQL statements per nonempty imaged listing independently of page size, excluding authentication: page/count plus bounded catalog batch queries. Empty/no-image paths may use fewer. Compare 1/100 item pages using existing Hibernate statistics pattern; no linear category/image queries.

## Removal

REQUIRED read-write transaction deletes by trusted owner and UUID. Zero rows is success. No public product lookup; hidden and foreign-only membership are undisclosed 204. No parent cascade.

## Timestamp sampling

Re-addition after removal samples the injected Clock again; do not compare with a deleted timestamp or force a strictly increasing value. Tests advance a controlled clock for deterministic fresh-sample assertions, and test equal-time addedAt DESC/product UUID ASC ordering separately.

## Testing boundaries

Constitution XIII is authoritative. Repository integration uses @DataJpaTest with PostgreSQL Testcontainers for Flyway/JPA/query/constraint behavior. Real registration and HTTP permission acceptance uses @SpringBootTest/MockMvc with real services in WishlistControllerIT, not the repository slice. Mocked service/validator/converter unit tests load no Spring context; MVC slices use @WebMvcTest. Packages mirror domain/layer; classes follow the constitution responsibility names. No category-based integration/api packages or duplicate classes for the same component.

## Security/error integration

Extend existing bearerChain with exact GET /api/me/wishlist and POST/DELETE /api/me/wishlist/* permission rules before denyAll. Do not broaden profile rules, change catalog-chain scope or alter token/CORS/CSRF handling. Use existing GlobalExceptionHandler/ApiErrorFactory and authentication/access-denied handlers; add only meaningful invalid-input/concurrent-state domain exceptions, not a feature global handler.

## Verification boundaries

Required synchronized adds use independent real transactions/connections, stable published product and same owner: one 201, other 200, same addedAt and one row. Coordinate overlap with latches/barriers and bounded waits, not sleeps. No outer test transaction; cleanup committed fixtures. Existing pool size is 2: do not hold coordinator connection while awaiting both workers, or set test-local pool size 4.

Verify newly inserted membership rolls back on signing failure; named FK translation occurs after rollback; visibility restoration and late-eligibility conflict; response mapping after service close and bounded SQL. API tests retain real application services and mock only external signing/storage. All execution belongs to implementation, not this plan.
