# Research: Customer Order Management

Date: 2026-11-17. All specification decisions resolved. This research selects implementation mechanisms; it does not implement or verify them. A research agent independently inspected catalog/media, errors and test infrastructure; findings were checked against local sources.

## 1. Persistence identifiers and historical references

**Decision**: Order and order-item IDs use generated BIGINT/Java Long, consistent with customer-owned cart aggregates; customer_id remains BIGINT and product_id/product_variant_id remain nullable UUID. Keep scalar foreign-key IDs for independent aggregates, with no catalog association traversal during order mapping. Order-to-item association is lazy; no cross-aggregate cascades.

**Rationale**: Preserves actual Backend-SWE types and avoids importing ProjectCV Long catalog IDs. ON DELETE SET NULL catalog references preserve immutable snapshots; customer deletion is RESTRICT. This is a local order decision, not a new global ID policy.

**Alternatives considered**: UUID orders are viable but add no required behavior. RESTRICT catalog references would prevent otherwise valid catalog deletion; cascade would erase history.

## 2. Cancellation and concurrent repeats

**Decision**: Service-layer REQUIRED/READ COMMITTED transaction, an owner-scoped SELECT FOR UPDATE on orders, and transaction-local lock_timeout configured by order.lock-timeout-ms (positive, default 5000). Lock only the single target order. Validator classifies PENDING/CONFIRMED as cancellable, CANCELLED as idempotent and SHIPPING/COMPLETED as conflict. Sample injected Clock only for the first successful transition; set status, cancelledAt and normalized reason together. Repeat does not update any column. No automatic retries.

**Rationale**: A waiting cancellation observes the winner and can return 204 preserving its timestamp/reason. No catalog/network calls or child locks occur during cancellation. Existing CartRepository demonstrates local timeout and pessimistic locking. PostgreSQL documents that conflicting row locks wait until the other transaction ends; consistent lock order limits deadlocks. [PostgreSQL 17 locking](https://www.postgresql.org/docs/17/explicit-locking.html).

**Alternatives considered**: Optimistic locking yields an ordinary conflict instead of the natural repeat outcome; conditional update plus re-read is possible but complicates classification for this small aggregate. Raising isolation/global locking/retries is unnecessary.

## 3. Bounded summaries and complete detail

**Decision**: OrderReadService provides fully materialized read seeds under readOnly transactions. Count owner/status-filtered orders first and compute offset=(long)page*size before selecting content. If offset >= totalElements, return accurate empty-page metadata after that single count, with no content/preview/catalog work. Otherwise use Pageable for the normal List-returning JPA header content path at offset <=Integer.MAX_VALUE, or OrderPageRepository/OrderPageRepositoryImpl custom native SQL with bound long OFFSET and LIMIT at larger nonempty offsets. The native path must not call JPA setFirstResult with a narrowed offset; use four trusted SQL sort templates with identical predicates/id ASC. Preserve count-time totals rather than running a second automatic Page count. A native window query over just selected page order IDs returns the first three items per order using ROW_NUMBER ordered by item ID and COUNT(*) OVER partitioned by order ID. No pageable collection fetch. Merge by header order, never IN-query order. Empty content skips preview/image access. Detail uses owned header plus all item projections ordered by ID. No new item-count cache.

**Rationale**: At size 100 the application materializes at most 300 preview rows; row counts remain exact without loading every item. SQL may scan all items of selected orders to calculate counts, but returned preview rows are bounded. Catalog deletion never determines order eligibility.

**Alternatives considered**: Fetching every item for previews wastes memory; one query per order is N+1; paginating a collection fetch is unsafe. Stored counts add unused write-maintenance obligations. Merely widening arithmetic still leaves JPA's int offset boundary; narrowing/clamping or rejecting valid large pages would violate the accepted API contract. Count-first skips empty pages and a bound native SQL offset handles the remaining range without new dependencies. Boundary tests use synthetic large counts and small-fixture SQL binding rather than billion-row setup.

## 4. Image integration and transaction boundary

**Decision**: OrderService is a nontransactional facade: call OrderReadService to obtain immutable prepared seeds; then call a new catalog-owned CatalogOrderReadService.getPrimaryImageUrls(Collection<UUID>); then use pure OrderConverter. Cancellation remains transactional in OrderServiceImpl's public cancelOrder method (called through the service proxy). New catalog image implementation uses a normal read-write REQUIRED transaction because PostgreSQL publication share locks cannot join a read-only transaction. No order read transaction spans this call.

Catalog service deduplicates non-null IDs, chunks at 100 and prepares ProductImageStorage.PreparedReadSigner before taking any row lock. For candidate IDs, use ProductRepository.lockStatusesForShare in ascending PostgreSQL UUID order (canonical string ordering, not Java UUID signed comparison), then re-read primary-image scalar projections and sign only still-PUBLISHED products. Acquire chunk locks in globally ascending order. Close signer resources. Catch CatalogStorageUnavailableException locally around prepare/sign/close: preparation failure gives empty map; individual failure omits that product. A close failure must also degrade to safe optional image results rather than fail historical viewing. Database failures are not image absence and propagate. Set the same bounded transaction-local lock timeout to prevent unbounded publication waiting.

**Rationale**: Existing ProductCatalogService.getProductSummaries caps inputs and maps current catalog values; CatalogCartReadService mixes variant eligibility and failure behavior. A narrow image-only service keeps historical content independent, prevents hidden URLs and catches expected signing failures before a transaction interceptor can mark a caller rollback-only. Best-effort null images are explicitly required here. Share-lock behavior follows existing CatalogCartReadServiceImpl. Temporary URLs retain existing expiry/revocation limitations.

**Alternatives considered**: Current-summary reuse violates history/failure semantics. Querying catalog repositories from order service violates domain boundaries. Catch-all query exceptions could hide database failure. Adding REQUIRES_NEW is unnecessary with the facade boundary.

## 5. Database integrity

**Decision**: Add new migration, provisionally V23 after current uncommitted V22; re-scan before implementation. Use unconstrained NUMERIC plus checks for finite nonnegative integral values (avoid NUMERIC(p,0) silently rounding fractional input), JSONB option map, TIMESTAMPTZ, row-level terminal-state/reason/quantity/line-total constraints, and deferred aggregate constraint triggers verifying at least one item and merchandiseTotal=sum(lineTotal) at transaction end. Triggers check only affected order IDs, lock parent rows in ascending order when multiple IDs are involved, and tolerate a parent that has been removed by explicit fixture cleanup. No trigger recalculates or rewrites snapshots.

**Rationale**: Row checks cannot enforce cross-row sums or nonempty child collections. Deferred checks permit fixtures to insert parent/items in one transaction while requiring valid committed history. This is integrity validation, not an order-creation workflow. PostgreSQL describes CHECK's row-local limitations and recommends suitable relational constraints/triggers for other cases. [PostgreSQL 17 constraints](https://www.postgresql.org/docs/17/ddl-constraints.html).

**Alternatives considered**: Ordinary CHECK for aggregate totals is unsupported; fixtures-only validation leaves final integrity unprotected. No payableTotal=merchandiseTotal assumption is added because stored payableTotal is independently authoritative.

## 6. HTTP, permission and reason parsing

**Decision**: MANAGE_ORDER assigned to existing USER through the additive migration; future USER assignments inherit it by existing role_permission lookup. Explicit security matchers for only the three approved routes. Strict single query parameters and allowlisted sort mapped to known persistence fields. Order IDs accept canonical positive base-10 Long only. Cancellation accepts optional JSON reason with the exact defaults in spec.md, strict duplicate/unknown/type detection and Unicode code-point length. An applicable Bean Validation custom constraint may enforce normalized code-point length; do not substitute UTF-16 @Size for this contract. Empty body is accepted; nonempty body requires JSON media type (otherwise 415). Existing compatible application/*+json accepted. No confirm-receipt route.

**Rationale**: Mirrors current-state security, strict CartRequestReader, ProblemDetail and owner-safe not-found conventions. 204 synchronous action is the explicit feature exception to the constitution's normal 200/202 preference.

**Alternatives considered**: Generic Jackson binding can accept duplicate/unknown fields and coerce types. A bodyless-only action would contradict optional reason. Generic status input is out of scope.

## 7. Tests and environment

**Decision**: Use existing Maven/JUnit/Mockito/AssertJ/MVC/Testcontainers stack; test profile JDBC URL creates PostgreSQL 17 and Flyway validates schema. No new dependency or plugin. Concurrency service IT overrides pool to at least 4 and uses bounded two-thread independent committed operations, plus a held-row-lock/second-connection observation to prove overlap rather than relying only on a start latch. Include lock-timeout rollback separately. Unit tests run without Spring; API and real bearer flow tests retain security. No test-level transaction hides service commit/rollback. Use fixture.order/testdata.order and existing fresh account/catalog helpers.

**Rationale**: Current test profile has a pool of 2; Failsafe binds integration-test/verify. Runtime discovery must later be checked in actual Surefire/Failsafe reports, not inferred from configuration. No live payment, shipping, checkout or inventory services are needed. Image-signing tests can mock only the storage boundary; use isolated LocalStack for actual URL behavior if exercised, never AWS.

**Alternatives considered**: In-memory database cannot prove PostgreSQL locks/constraints/window queries. Sequential duplicate tests cannot prove concurrency. New tooling is unnecessary.

## Setup outcome

Analysis remediation confirmed on 2026-11-17: count-first pagination and explicit nonempty long-offset support are required. HTTP 415 covers nonempty cancellation bodies with unsupported/missing JSON media type. An eligible first cancellation whose Clock sample precedes placedAt returns 409 unchanged; already-CANCELLED repeats return 204 before any Clock sampling. All associated design/acceptance tasks are documentation only at this stage.

Git Bash exists but failed with Windows NtCreateDirectoryObject access denied before running setup-plan.sh. Equivalent repository setup-plan.ps1 -Json succeeded and copied the active plan template. It reported logical BRANCH=008-customer-order-management from feature metadata; git branch --show-current confirms actual branch draft/version-1. No branch was changed. No extension file exists.
