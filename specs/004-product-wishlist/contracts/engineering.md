# Wishlist Engineering Constraints and Decision Record

**Relocated**: 2026-10-31, at the user's request to separate business requirements from implementation detail.

All constraints below remain binding. No feature policy, dependency approval, test requirement or scope exclusion is waived by relocation. Business requirements remain in [spec.md](../spec.md); exact interfaces are in [HTTP contract](http.md), integration mechanisms in [domain integration](domain-integration.md), schema in [data model](../data-model.md), and execution in [tasks.md](../tasks.md). Historical generation statements describe their original drafting phase, not current implementation evidence.

**Branch context**: feature/wishlist was the existing branch when the original specification was drafted; no branch operation is authorized here.

## Request, Response and Error Contracts — Explicit User Requirements

These requested compatibility constraints supplement the business requirements; no implementation is delivered.

| Operation | Input | Success |
| --- | --- | --- |
| GET /api/me/wishlist | Optional page and size only | 200 {items: WishlistItem[], page, size, totalItems, totalPages} |
| POST /api/me/wishlist/{productId} | Canonical lowercase hyphenated UUID; no body required | 201 WishlistItem on creation; 200 existing WishlistItem on duplicate |
| DELETE /api/me/wishlist/{productId} | Same UUID convention; no body required | 204 without body, including absent membership |

WishlistItem is `{addedAt: UTC ISO-8601 timestamp, product: {id: UUID, name: string, category: {id: UUID, name: string}, primaryImageUrl: string|null}}`. POST returns one item, never the list or a JPA entity. Membership ID is private. Use existing public `totalItems`; the request's `totalElements` denotes the visible count, not a second response field. totalPages is ceiling(totalItems/size), zero for an empty wishlist.

Omitted pagination selects defaults. Empty, text null, signed, whitespace-padded, fractional, overflowing, duplicate or noncanonical decimal values return 400. Unknown query fields, including userId and sort under fixed ordering, return 400. Proposed bodyless default: reject supplied mutation JSON, including null/empty objects and owner claims, with 400. No request body is needed and none can select an owner. Preserve current authentication/authorization evaluation before business access.

| Condition | Status and effect |
| --- | --- |
| Anonymous/invalid credential or ineligible current account/session | 401, no protected data/change |
| Missing MANAGE_WISHLIST permission | 403, no change |
| Invalid UUID/query/body | 400, no change |
| Missing/nonpublic product on add | 404, indistinguishable safe error |
| Duplicate addition | 200 existing item, unchanged addedAt |
| Missing owned entry on removal | 204 without body, no foreign disclosure |
| Product physically deleted after eligibility check | 404, rollback and no orphan |
| Current user physically deleted before insert | 401, rollback and no orphan |
| Other recognized concurrent state conflict | 409 safe conflict, caller decides retry |
| Image URL preparation/signing unavailable | 503, no fabricated success/new committed membership |
| Unexpected persistence/infrastructure failure | 500 safe error, rollback uncommitted writes |

Use current ApiError/ProblemDetail and timestamp/message/path/fieldErrors extensions, security handlers, common application exception hierarchy and central handling. No new global handler. Private/capability responses use Cache-Control: no-store. Never persist signed URLs. A network-lost response does not prove rollback.

Recheck publication eligibility before returning duplicate metadata: an already-saved hidden product still produces 404 on add. Use PostgreSQL INSERT ... ON CONFLICT DO NOTHING targeted at UNIQUE(user_id, product_id). A duplicate is a successful no-op: it returns 200 with the existing item/original addedAt without triggering transaction failure or requiring rollback. Retrieve the winner with a separate statement in the same valid transaction. Do not introduce pessimistic locking to resolve duplicate additions. Actual persistence exceptions are a separate failure path: roll back before safe error translation, never query an aborted transaction or mistake unrelated errors for duplicate success. Preserve the database unique constraint; pre-insert checking alone is insufficient. Existing approved publication-safe image-signing safeguards remain unchanged.


## Persistence and Domain Integration — Explicit User Requirements

- Use a domain-owned PostgreSQL association with private technical ID, required existing Long user/UUID product references, immutable Java Instant addedAt stored as TIMESTAMPTZ, NOT NULL/FK protection and UNIQUE(user_id, product_id). Follow plural snake_case and constitution constraint/index naming. Do not duplicate user/product data.
- No global identifier policy is introduced. Private technical membership ID follows an existing repository convention chosen and documented during planning; mixed Long/UUID conventions do not establish a universal type.
- Physical-parent cleanup is required; FK dependent deletion is appropriate. Never cascade ORM persist/remove from membership to User/Product. Status changes are not physical deletion.
- Use a new Flyway migration after the current chain without changing applied files. No migration is produced now.
- Prefer com.example.demo.wishlist with applicable controller, service/interface and service/impl, repository, entity, dto/response, converter, validator and meaningful exceptions. No empty layers, unnecessary request DTOs or abstractions.
- Controllers delegate; services orchestrate transactions and access only wishlist repositories; validators enforce applicable business rules. Cross-domain operations use user/catalog service interfaces with acyclic dependencies. Keep associations LAZY and converters pure, including nested mapping through the owning converter.
- Smallest catalog extension: a publication-eligible product-reference operation for addition, plus bounded batch summary preparation for requested UUIDs including category and primary-image access. These are NEW integration needs; ProductCatalogService currently has neither. Entity-returning retrieval methods follow get...Entity naming. No public HTTP extension or per-item getProduct call is needed.
- Wishlist repository may filter/count through its product association before pagination; this does not authorize wishlist service calls to catalog repositories or duplication of catalog eligibility policy. Catalog owns product business eligibility and summary preparation.
- Batch preparation preserves membership ordering and excludes hidden data. Do not load full product details, variants, all images or unrelated user fields. Reuse ProductCatalogConverter and ProductImageStorage/PreparedReadSigner backed by shared storage; converters never query or call S3.
- Listing MUST NOT mutate business data. A write-capable service transaction is permitted solely to preserve existing catalog publication-safe signing safeguards; strict read-only transaction annotation is not required for this path. Service-owned write transactions cover complete add/remove operations with REQUIRED/default READ COMMITTED. Do not introduce pessimistic locking for addition or duplicate resolution; the approved existing catalog signing safeguards are distinct. No blanket pessimistic locks, isolation escalation or automatic retries.
- Fetch plans must not use collection fetch joins that corrupt pagination or duplicate rows. Use suitable Pageable and application DTOs without creating a global pagination framework. Responses must serialize after service transactions close without lazy fetching.
- Related product/category/primary-image query counts remain bounded independently of page size; compare pages of 1 and 100. Prepare signing in batches where supported. Concurrent status transitions must not authorize fresh hidden metadata/URLs; exact consistency/fencing design belongs to planning within the approved signing safeguard exception.


## Acceptance Verification Requirements

These requirements govern later implementation; no tests are written or run now.

| Category | Required coverage |
| --- | --- |
| Service/validator/converter unit | First add, duplicate 200, missing/hidden eligibility, owner isolation, hidden removal, missing removal 204, re-add time, pure allowlisted mapping/null image and business-failure rollback |
| MVC/security | Status/body contracts, UUID/pagination boundaries including 0/1/100/101, duplicates/unknown inputs, anonymous/current-session/account denial, MANAGE_WISHLIST permission denial, owner injection rejection and unchanged token handling |
| PostgreSQL Testcontainers persistence/query | Unique/FK/NOT NULL constraints, physical parent cleanup, no parent deletion, filter-before-page visible totals, empty/out-of-range pages, timestamp ties, hidden/restore lifecycle |
| Real-service/API integration with disposable PostgreSQL | Add/list/remove across login sessions, rollback at real transaction boundaries, safe persistence conflicts, response serialization after service close, bounded fetching and unchanged catalog contracts |
| Simultaneous-add integration | Required: synchronize competing additions and verify one 201, remaining responses 200 with the same item/original addedAt, exactly one row and no unsafe errors; hold the published product stable during this test |

Use existing JUnit 5/Mockito/AssertJ, MockMvc, domain fixtures and Test/IT discovery conventions. API integration uses real application services; mock external storage/signing only as appropriate. No real AWS/developer database. Do not hide transaction behavior with test-level transactions. Dependency/build-plugin additions require constitution approval. The simultaneous-add integration test is required; database uniqueness or sequential duplicates do not substitute for it. Concurrent behavior is not claimed verified until that test executes.


## Repository Evidence and Compatibility

Reviewed constitution and existing specifications 001-product-catalog, 001-user-profiles-addresses, 002-auth-session-tokens and 003-image-storage, relevant auth contracts, actual catalog controller/service/repository/converter/entities/DTOs/storage adapter, user service/entities, identity adapter, current-state JWT/security configuration, ApiError/handlers, migration inventory and test conventions.

Source confirms UUID products, Long users, DRAFT/PUBLISHED/ARCHIVED, published-only browsing, ProductSummary fields id/name/category/primaryImageUrl and defaults 0/20/max 100. ProductPage uses totalItems, not totalElements. ProductCatalogService exposes only listProducts and getProduct; user service already provides getUserEntity. CurrentUserIdProvider has a real SpringSecurityCurrentUserIdProvider despite outdated interface comments.

Auth specification explicitly authorizes refresh sessions; these are not an unapproved assumption under constitution VI. Current authentication checks active user, valid owned session and current authorities. bearerChain denies unlisted routes, requiring future explicit wishlist rules within existing configuration. Neither existing profile/address permission authorizes wishlist access by default.

ApiError extends ProblemDetail, but catalog's scoped ApiExceptionHandler returns plain ProblemDetail without its extensions. Wishlist follows the requested existing ApiError convention without changing catalog errors. Older profile/auth specification delivery statements claiming no implementation are stale against current code and later documents; they are not new business requirements. Constitution ratification is explicitly unverified; this work does not amend it.

No extensions.yml, template overrides, preset or extension template exists. The core .specify/templates/spec-template.md is the resolved template. Host script policy prevented executing the PowerShell resolver; its resolution stack was inspected and applied directly. Existing README changes are preserved. No branch created and no production implementation claimed.


## Resolved Integration Conflict

ProductCatalogServiceImpl uses write-capable transactions for published-image signing because ProductRepository.lockStatusesForShare executes PostgreSQL FOR SHARE. The user approved Q3 A: wishlist listing may use a write-capable transaction solely to preserve existing publication-safe signing safeguards. Listing remains a non-mutating business operation. This supersedes the initial strict read-only transaction proposal for this path, without approving blanket locks, image-access weakening or public catalog HTTP changes.


## Confirmed Decisions (2026-10-30)

The user confirmed Q1 B, Q2 A and Q3 A, resolving all seven original policy questions.

1. **Q1 B - Authorization**: All operations require MANAGE_WISHLIST. Grant it to default USER for existing and future assignments. Existing users assigned USER gain access without reassignment. Custom roles lacking it are denied until explicitly granted. Current database authorities apply on subsequent requests; no token reissue is required. Profile/address permissions never substitute.
2. **Q2 A - Repeated operations**: Duplicate addition returns 200 with the existing saved item/original addedAt, including recognized unique-constraint losers. Missing-entry removal returns 204 without a body. Neither changes another user's membership.
3. **Q3 A - Visibility, integration and verification**: Retain hidden membership and restore it with original addedAt on republishing. Fix ordering to addedAt DESC/product UUID ASC. Preserve public catalog HTTP contracts. Include deterministic simultaneous-add integration testing. Permit a write-capable listing transaction solely for existing publication-safe signing safeguards.


## Remediation Decisions (2026-10-30)

I1: conflict-ignore duplicate insertion is normal success, not exception recovery; retain the pair unique constraint and add no duplicate-resolution pessimistic locks. U1: repository/migration checks remain separate from real registration acceptance. A1: re-add samples a fresh timestamp without a strict-increase guarantee; controlled-clock tests also verify equal-time ordering. I2: constitution XIII governs domain/layer packages, responsibility-based classes, fixture placement and test categories; no new testing convention is introduced.


## Original Technical Functional Constraints

- **FR-003**: Require MANAGE_WISHLIST for all wishlist operations while preserving account/session eligibility and Bearer authentication. Grant it to default USER for existing and future assignments. Custom roles require an explicit grant; profile/address permissions never substitute.
- **FR-007**: List only caller-owned currently PUBLISHED products, filtering before pagination/counting. Retain hidden membership, use fixed newest-first ordering, preserve public catalog HTTP contracts and require simultaneous-add integration testing. Permit a write-capable listing transaction solely for existing publication-safe image-signing safeguards.
- **FR-010**: Order addedAt descending then product UUID ascending, a unique tie-breaker within a wishlist; no additional client sorts.
- **FR-013**: Re-addition after removal creates new membership with a fresh server timestamp sample if the product is eligible; strict timestamp increase is not required. Equal timestamps use the fixed product UUID tie-breaker.
- **FR-014**: Physical user/product deletion atomically cleans their dependent memberships; soft product status changes retain membership under FR-008. No orphan or membership-to-parent cascade is allowed.
- **FR-015**: Make writes atomic with safe uniqueness/FK/conflict outcomes, rollback on business failure and no SQL, constraint, credential or stack-trace disclosure.
- **FR-016**: Prepare current catalog summaries/images without per-item related-data queries and materialize responses before service completion.
- **FR-017**: Preserve existing public catalog HTTP contracts; do not add wishlist fields.

## Preserved Technical Exclusions


Single-vendor only: no shops, sellers or ProjectCV policies. No reviews/ratings/counts/statistics/placeholders; cart/checkout/payment; inventory reservation/mutation; prices/status mutation; purchases; option/quantity selection; shared/public or multiple named wishlists; notifications/recommendations/alerts; product administration; anonymous persistence/merging; frontend implementation. No new SecurityFilterChain, token changes, global pagination framework or unrelated refactoring.


## Evidence relocation map

| Former spec evidence | Current technical authority |
| --- | --- |
| Routes, status codes, request parsing and wire fields | http.md and Request, Response and Error Contracts above |
| Atomic duplicate insertion, uniqueness, exception rollback | domain-integration.md Addition; research.md Atomic duplicate handling; tasks T013/T016/T018/T019 |
| Non-mutating write-capable listing and signing locks | domain-integration.md Listing; plan.md; Resolved Integration Conflict above |
| Bounded queries, types, schema, migrations and domain boundaries | data-model.md, domain-integration.md, plan.md and Persistence and Domain Integration above |
| Framework test categories, registration-versus-migration evidence, fixtures | constitution XIII, tasks T003/T021 and Acceptance Verification Requirements above |
| Timestamp sampling and deterministic ties | data-model.md Lifecycle, domain-integration.md Timestamp sampling and tasks T036 |

No new implementation, test execution or release approval is claimed. The four approved remediations I1/U1/A1/I2 remain intact. No existing task checkbox changes as a result of this relocation.
