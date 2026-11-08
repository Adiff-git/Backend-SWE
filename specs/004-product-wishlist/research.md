# Research: Personal Product Wishlist

**Date**: 2026-10-30 | **Status**: Complete; no unresolved clarification

## Identifier and baseline

**Decision**: Existing Java 25/Boot 4.1.2/PostgreSQL 17 stack; private generated Long/BIGINT membership ID follows Address. Keep user Long and product UUID.
**Rationale**: No public membership-ID contract or new global ID policy is needed.
**Alternatives considered**: UUID adds no required capability; composite primary key complicates entity mapping.
**Evidence**: demo/pom.xml; demo/src/main/java/com/example/demo/user/entity/Address.java and catalog/entity/Product.java.

## Permission rollout

**Decision**: New migration creates MANAGE_WISHLIST and grants it to existing USER through role_permissions, without changing user_roles/custom roles. Future registration already assigns USER. Extend exact method/path rules in existing bearerChain.
**Rationale**: Existing/future assignments inherit the grant and current-authority checks observe changes on subsequent requests.
**Alternatives considered**: Authentication-only rejected by Q1 B; reusing profile/address grants or assigning USER to custom-role accounts violates scope.
**Evidence**: V6__authorities_and_username_policy.sql; AuthSecurityConfiguration, CurrentStateJwtConverter and UserServiceImpl.

## Atomic duplicate handling

**Decision**: Wishlist-owned targeted INSERT ON CONFLICT DO NOTHING, then a separate scalar SELECT under READ COMMITTED. Affected-row count distinguishes created/duplicate. Native DML shares the service transaction; use an existing EntityManager custom repository fragment only if needed.
**Rationale**: Duplicate insertion does not abort the transaction or change addedAt. The later statement can see a committed winner invisible to the insertion snapshot. This normal duplicate path is successful no-op handling, not recovery from an exception; retain pair uniqueness and introduce no pessimistic locking for addition or duplicate resolution. Existing publication-safe signing locks are a distinct safeguard. Concurrent removal before lookup becomes safe 409, not a fabricated item or retry loop.
**Alternatives considered**: Pre-check alone is racy. Catch/query in a failed transaction is unsafe. DO UPDATE creates unnecessary writes. Same-statement CTE fallback can miss the competing winner. REQUIRES_NEW recovery is unnecessary.
**Primary sources**: PostgreSQL 17 [INSERT](https://www.postgresql.org/docs/17/sql-insert.html) and [READ COMMITTED](https://www.postgresql.org/docs/17/transaction-iso.html). They support this two-statement design; application correctness still needs the required test.

## Catalog integration

**Decision**: Add getPublishedProductEntity(UUID) and getProductSummaries(Collection<UUID>) to ProductCatalogService; they do not exist today. Batch prepares categories, primary images, eligibility and signed URLs, mapping with ProductCatalogConverter. Wishlist wraps prepared summaries with addedAt.
**Rationale**: Existing interface exposes only listProducts/getProduct. Existing batch queries support bounded work without details/variants/gallery.
**Alternatives considered**: Direct ProductRepository access violates domain ownership; per-item getProduct causes excess fetching; catalog-to-wishlist dependency risks cycles.
**Evidence**: catalog/service/ProductCatalogService.java, ProductCatalogServiceImpl.java, repository/ProductRepository.java, ProductImageRepository.java and converter/ProductCatalogConverter.java.

## Pagination and status transitions

**Decision**: Scalar membership page/count use matching owner/PUBLISHED predicates before paging. Fixed addedAt DESC/product UUID ASC. If a selected ID disappears from final catalog eligibility, return safe 409 instead of a post-filtered short success page. No automatic refill/retry.
**Rationale**: Existing catalog late filtering is not proof of exact wishlist pages under overlapping status changes. Stable fixtures require exact totals/full pages; READ COMMITTED separate statements are not one immutable snapshot. No cross-request snapshot is promised.
**Alternatives considered**: Post-load filtering fails visible pagination. Locking all records or escalating isolation adds unjustified scope. Unlimited refill is unnecessary.
**Evidence**: existing catalog listing; PostgreSQL [transaction isolation](https://www.postgresql.org/docs/17/transaction-iso.html).

## Signing and transactions

**Decision**: Q3 A allows non-mutating listing in a write-capable transaction. Reuse prepared signer and sorted FOR SHARE checks for image-bearing products; resolve credentials before locks and sign locally. Final non-image publication revalidation precedes projection.
**Rationale**: Existing safeguards require write-capable transactions. Locks safeguard publication during capability issuance, not duplicate membership. No blanket user/product locks are needed.
**Alternatives considered**: Strict read-only cannot reuse the existing path; weakening eligibility or signing in converters violates requirements.
**Evidence**: ProductCatalogServiceImpl, S3ProductImageStorage, storage/service/impl/ObjectStorageServiceImpl; PostgreSQL 17 [locking](https://www.postgresql.org/docs/17/explicit-locking.html).

## Errors and verification

**Decision**: Reuse common ApiErrorFactory/GlobalExceptionHandler and auth handlers. Translate known FK failures only after rollback, using stable constraint identity internally. Test with installed stack/disposable PostgreSQL, real internal services and mocked external signing.
**Rationale**: Catalog-scoped ApiExceptionHandler differs but does not govern wishlist. Raw persistence failures cannot be recovered by querying an aborted transaction.
**Alternatives considered**: New global advice duplicates infrastructure. Generic integrity-to-duplicate conversion hides unrelated failures. Sequential duplicates do not prove simultaneous behavior.
**Evidence**: common/exception classes; application-test.properties; CatalogQueryCountIT; pom.xml Failsafe configuration. The current pool has two connections; synchronized tests must not hold a coordinator connection while awaiting both workers, or should set a test-local pool size of four.
