# Feature Specification: Personal Product Wishlist

**Created**: 2026-10-30
**Revised**: 2026-10-31
**Status**: Implemented and verified on 2026-10-31; [implementation evidence](implementation-validation.md). Engineering details remain maintained separately.
**Input**: Personal product wishlist for Backend-SWE. The original specification captured requirements and acceptance criteria; implementation results are recorded separately in the evidence linked above.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Save a product for later (Priority: P1)

As an eligible signed-in customer, I save a published product so I can find it after signing in again.

**Why this priority**: Persistent saving is the feature's core value.
**Independent Test**: Add a published product to an empty wishlist, inspect the saved item, sign out and sign in again.

**Acceptance Scenarios**:

1. **Given** an eligible customer and a PUBLISHED product, **When** first added, **Then** exactly one owned membership is created and creation succeeds and returns the saved item with immutable addedAt.
2. **Given** saved membership, **When** the customer logs out and signs in again, **Then** membership and original addedAt persist.
3. **Given** an unknown, DRAFT or ARCHIVED product, **When** added, **Then** an indistinguishable not-found failure reveals no metadata or image URLs and creates nothing.
4. **Given** existing membership, **When** addition repeats, **Then** success returns the existing saved item; no duplicate or addedAt change occurs.
5. **Given** simultaneous additions for one user/product pair, **When** requests complete, **Then** exactly one membership exists and competing repeated saves succeed with the existing item and original addedAt without internal diagnostic disclosure.
6. **Given** a business failure, **When** the operation ends, **Then** membership changes are undone and no user, product, stock, price or purchase changes.

### User Story 2 - Browse my saved products (Priority: P1)

As a customer, I browse a bounded page of my visible saved products without seeing another user's choices.

**Why this priority**: Retrieval makes saved products useful.
**Independent Test**: Seed memberships for two users with mixed product statuses and equal/different timestamps; retrieve pages independently of addition.

**Acceptance Scenarios**:

1. **Given** visible owned memberships, **When** pagination is omitted, **Then** page 0 contains at most 20 items with accurate visible totals and approved ordering.
2. **Given** no visible membership, **When** listed, **Then** success returns items [] and zero totals; a beyond-last page returns [] with unchanged totals.
3. **Given** hidden memberships interspersed among visible ones, **When** paged, **Then** hidden entries are excluded before counting and page boundaries; they do not consume page positions.
4. **Given** equal addedAt values and unchanged data, **When** pages are traversed repeatedly, **Then** the stable unique tie-breaker prevents omissions, duplicates and reordering.
5. **Given** a product without images, **When** its saved item is returned, **Then** primaryImageUrl is null and no rating/review placeholder appears.
6. **Given** a saved product, **When** a product becomes DRAFT or ARCHIVED, **Then** its membership remains but metadata/URLs disappear from listing and totals; republishing restores it with original addedAt.
7. **Given** users A and B, **When** A lists their wishlist, **Then** no B-owned membership appears, including when both saved the same product.

### User Story 3 - Remove a saved product (Priority: P2)

As a customer, I remove unwanted saved products, including products no longer public.

**Why this priority**: Customers need control over saved choices.
**Independent Test**: Seed visible and hidden memberships for two users; remove owned entries without using public product lookup.

**Acceptance Scenarios**:

1. **Given** an owned visible or hidden membership, **When** removed, **Then** removal succeeds without additional content and only that membership is deleted; the user, product and other users' memberships remain.
2. **Given** no owned membership, **When** removal is requested, **Then** removal succeeds without additional content; foreign memberships remain undisclosed and unchanged.
3. **Given** successful removal and a currently published product, **When** added again, **Then** a new membership receives a fresh addedAt sample; it need not be strictly greater than the removed membership's timestamp. Order by addedAt descending then product identifier ascending, including timestamp ties.

### Edge Cases

- Anonymous, expired/revoked-session, missing-user and inactive-account requests fail existing authentication; valid callers lacking MANAGE_WISHLIST are denied access.
- Invalid product identifiers, empty/duplicate/nonnumeric/negative/overflowing pagination, unknown fields and unsupported sorting fail safely.
- Existing signed image capabilities may remain valid until their existing five-minute expiry after unpublishing; no immediate revocation is promised.
- Physical parent deletion cleans dependent membership without deleting independent aggregates through wishlist operations.
- Stable ordering applies to unchanged data; concurrent changes may shift offset pages. No cross-request snapshot is promised.
- Image-access preparation failure never creates fabricated URLs or successful partial writes. Returned information remains usable after the operation finishes.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Provide one private persisted wishlist per eligible account with add, paginated view and remove, surviving logout/login.
- **FR-002**: Derive ownership exclusively from trusted immutable current identity; never accept a supplied user identity to select another owner. Enforce isolation for reads, writes and error disclosures.
- **FR-003**: Require MANAGE_WISHLIST for every wishlist operation, alongside current account/session eligibility and authentication. Grant it to default USER for existing and future assignments. Custom roles require an explicit grant; profile/address permissions never substitute.
- **FR-004**: Newly add only existing PUBLISHED products. Missing, DRAFT and ARCHIVED products produce indistinguishable not-found failures with no unpublished information disclosed.
- **FR-005**: Membership identifies a whole product, independent of variants/options, with at most one entry per account/product pair even under simultaneous additions.
- **FR-006**: Repeated addition succeeds with the existing saved item and immutable original addedAt, without duplication or reordering. Removal of an absent entry also succeeds without additional content.
- **FR-007**: List only caller-owned currently PUBLISHED products, excluding hidden entries before counting and forming pages. Retain hidden membership, use fixed newest-first ordering and preserve existing public catalog behavior. Browsing must not change business data or weaken publication-safe image access.
- **FR-008**: Retain hidden membership and restore it on publication with original addedAt; expose no hidden metadata or newly issued image access.
- **FR-009**: Default page and size to 0 and 20; accept page >= 0 and size from 1 through 100. Return correct visible totals. Empty lists and beyond-last pages succeed.
- **FR-010**: Order by addedAt descending, then product identifier ascending as a unique tie-breaker within a wishlist. No customer-selected alternative sort is supported.
- **FR-011**: Return addedAt and a product summary containing only id, name, category {id,name} and primaryImageUrl. Missing images are represented by null. Exclude rating/review information, private membership/owner identifiers and internal storage details.
- **FR-012**: Remove only owned membership, including hidden products, without requiring the product to be publicly discoverable. Never delete users, products or other independent data through wishlist removal.
- **FR-013**: Re-addition after removal creates a new membership with a fresh server saving-time sample if the product is eligible. Strict timestamp increase is not required; equal samples use the fixed product identifier tie-breaker.
- **FR-014**: Physical deletion of an account or product removes its dependent memberships as one complete change, leaving no orphans. Soft product status changes retain membership. Removing membership never deletes its account or product; unrelated parent-deletion restrictions remain in force.
- **FR-015**: Apply each change completely or leave the previous state intact on failure. Preserve uniqueness and valid account/product references under competing operations. Fail safely without exposing credentials, private data or internal diagnostics.
- **FR-016**: Provide current product/category/image summaries through bounded retrieval work that does not grow per item in the page, with complete returned information usable after the operation ends. Technical measurement criteria are maintained in the engineering contract.
- **FR-017**: Preserve existing public catalog behavior and returned information; do not add wishlist fields to catalog results.

### Key Entities *(include if feature involves data)*

- **Membership**: One account's saved product reference and immutable saving time; not a product snapshot. Its internal identity is private.
- **Account**: Existing immutable identity, current eligibility and permissions; owns memberships.
- **Product**: Existing stable identity and DRAFT/PUBLISHED/ARCHIVED lifecycle; governs addition/list visibility.

### Request, Response and Error Contracts

These are observable information and failure requirements; exact transport details are maintained in [interface contract](contracts/http.md).

| Operation | Required behavior |
| --- | --- |
| Browse | Accept only optional page and size; return saved items plus page, size, totalItems and totalPages |
| Save | Select one valid product; distinguish a newly saved item from an already saved item while returning one item in either case |
| Remove | Select one valid product; confirm success without extra content, including when no owned membership exists |

Each item contains addedAt and the exact product allowlist in FR-011. Saving times are absolute UTC times. totalItems counts visible owned memberships only; totalPages is the ceiling of totalItems divided by size, or zero when empty. An empty page contains an empty item list. A beyond-last page retains the actual visible totals.

Omitted page/size selects defaults. Empty, explicit-null, signed, whitespace-padded, fractional, overflowing, repeated and noncanonical decimal inputs are invalid. Unknown selectors, including another owner and alternative sorting, are rejected. Save/remove need no additional payload; supplied extra payload, including an explicit null or empty object, is rejected. Authentication and permission checks precede business access.

| Failure category | Required result |
| --- | --- |
| Missing/invalid authentication or ineligible account/session | No protected information or change |
| Missing MANAGE_WISHLIST | Deny access without changes |
| Invalid identifier, paging or extra input | Reject without changes |
| Missing/nonpublic product on save | Indistinguishable not-found failure, including previously saved hidden products |
| Product physically disappears during saving | Not-found failure, no new membership or orphan |
| Account physically disappears during saving | Authentication failure, no new membership or orphan |
| Recognized competing-state conflict | Safe conflict; customer decides whether to retry |
| Image-access preparation unavailable | Safe service-unavailable failure, no fabricated image access or newly saved membership |
| Unexpected failure | Safe failure with all incomplete changes undone |

Repeated save is ordinary success, not a failure that changes the saving time. Actual persistence failures remain separate; never claim duplicate success for an unrelated failure. Private results must not be reused from a shared cache or expose stored temporary image access. Use the established consistent safe error information. Loss of a response does not prove the operation was undone.

### Persistence and Domain Integration

Persist membership across sessions while preserving existing accounts, catalog data and image lifecycle. Product eligibility, summaries and image access remain governed by the catalog; the wishlist adds no product state or image workflow. Internal integration and data-preservation constraints are maintained in [engineering contract](contracts/engineering.md).

Listing is a non-mutating business operation, including any safeguards needed to issue images only while publication permits it. Exclude hidden entries before paging/counting. If a selected item loses visibility before safe response preparation, report a conflict rather than silently return a shortened success page or hidden data. Separate page requests are not a single fixed snapshot; exact traversal guarantees apply to unchanged data.

### Acceptance Verification Requirements

Verify each story independently and together: first/repeated save; sign-out/sign-in retention; owner isolation and permission changes; hidden/not-found nondisclosure; exact visible pages, counts and timestamp ties; missing image; hidden removal; re-addition; physical-parent cleanup; image-access failure; complete failure recovery and unchanged catalog behavior.

Simultaneous-add verification is mandatory and distinct from sequential duplicate checks. With a stable published product, synchronize two saves for one owner: exactly one is new, the other reports the same existing item and original saving time, and only one membership remains. Do not claim concurrency verified before this scenario executes.

Verify completed results remain usable after the operation and that retrieval remains bounded for pages of 1 and 100 items. Technical test categories, measurement boundaries and isolated environment rules live in [engineering contract](contracts/engineering.md) and [plan.md](plan.md); these documents are requirements, not passing runtime evidence.

### Explicit Exclusions

Single-vendor only. Exclude shops/sellers, reviews/ratings/statistics/placeholders, cart/checkout/payment, inventory reservation/mutation, price/status mutation, purchases, variant/quantity selection, shared/public or named wishlists, notifications/recommendations/alerts, product administration, anonymous persistence/merging and frontend implementation. No unrelated authentication changes, image lifecycle changes or refactoring.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Every first-save/logout/login acceptance case retains exactly one saved product and its original saving time until removal or physical parent deletion.
- **SC-002**: All foreign-owner, anonymous, ineligible-account and nonpublic-add cases disclose zero protected entries or unpublished product details.
- **SC-003**: Unchanged fixtures with 120 visible and 30 hidden saved products traverse correctly at page sizes 1, 20 and 100: every visible product appears exactly once, no hidden product appears, and totals/order are exact.
- **SC-004**: Every successful removal affects one owned membership, or zero for idempotent missing-entry removal, with zero changes to other users, products or inventory.
- **SC-005**: Every duplicate/failure acceptance case preserves uniqueness, original saving time and atomicity. The required simultaneous-add acceptance test must satisfy these outcomes; execution and verification belong to implementation.
- **SC-006**: All save/find/remove walkthroughs complete without variant selection, checkout, another user's identity or unrelated profile/address permissions, and every item contains only the specified summary fields.

## Assumptions

### Repository Evidence and Compatibility

Existing accounts, current-state authentication, public catalog summaries and temporary image access are reusable dependencies. Wishlist-specific persistence, permission rollout and internal catalog integration are planned work, not delivered by this specification. Technical source inventory and compatibility requirements are preserved in [engineering contract](contracts/engineering.md). Older delivery statements in other features do not establish the current implementation state.

### Resolved Integration Conflict

The approved listing approach preserves existing publication-safe image access while browsing remains non-mutating. The implementation exception and its narrow boundaries are recorded in [engineering contract](contracts/engineering.md) and [plan.md](plan.md); the business requirement does not choose a transaction or locking mechanism.

### Confirmed Decisions (2026-10-30)

1. **Q1 B - Authorization**: Every wishlist operation requires MANAGE_WISHLIST. Existing and future USER assignments inherit it without role reassignment. Custom roles lacking it are denied until explicitly granted. Current permissions apply on the next request without signing in again or receiving replacement credentials. Profile/address permissions never substitute.
2. **Q2 A - Repeated operations**: Duplicate saves succeed with the existing item and original addedAt. Missing-entry removal succeeds without additional content. Neither changes another account's membership.
3. **Q3 A - Visibility, integration and verification**: Retain hidden membership and restore its original saving time on republishing. Order by addedAt descending then product identifier ascending. Preserve public catalog behavior. Require synchronized simultaneous-add verification. Keep browsing non-mutating and publication-safe.

### Remediation Decisions (2026-10-30)

I1: Duplicate save is normal no-op success; preserve pair uniqueness and distinguish unrelated failures. U1: Data-integrity verification and acceptance through real account registration are distinct responsibilities. A1: Re-addition samples saving time again without a strict-increase guarantee; equal timestamps retain deterministic ordering. I2: Existing project testing conventions remain authoritative. The technical mechanisms and task assignments for these four decisions are preserved in [engineering contract](contracts/engineering.md), [plan.md](plan.md) and [tasks.md](tasks.md).

### Additional Authorization and Integration Acceptance

1. **Given** existing and newly registered users assigned default USER, **When** wishlist permission rollout occurs, **Then** both receive MANAGE_WISHLIST without role reassignment or changes to unrelated permissions.
2. **Given** an eligible custom-role user lacking MANAGE_WISHLIST, **When** any wishlist operation is attempted, **Then** access is denied even with profile/address permissions, without membership changes.
3. **Given** a completed wishlist permission grant/revocation, **When** the same otherwise-valid credentials are used on the next request, **Then** current permissions allow or deny the operation accordingly.
4. **Given** a wishlist page containing published images, **When** browsing completes, **Then** publication-safe image access succeeds, no business records change, and returned information is complete and usable without further data retrieval.

No capacity limit, membership expiry, additional sort, stronger snapshot or load/latency promise is introduced. Input-validation and private-response protections above remain approved defaults. No new frontend is required. Separating engineering details does not change these decisions or declare implementation complete.
