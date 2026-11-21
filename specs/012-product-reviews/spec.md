# Feature Specification: Product Reviews and Ratings with Optional Images

**Feature Branch**: Existing `feature/voucher`; no branch hook is installed and no branch was created.

**Created**: 2026-11-20

**Status**: Ready for implementation — Q1–Q3 resolved; all proposals explicitly approved on 2026-11-21

**Input**: Specify verified-purchase product reviews, rating summaries, owned edits and optional uploaded images for Backend-SWE, using ProjectCV as a functional reference.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Review a completed purchase (Priority: P1)

As a customer, I can rate an item I purchased and optionally describe or illustrate my experience so other shoppers receive verified feedback.

**Why this priority**: Verified authorship is the integrity boundary for all feedback.

**Independent Test**: Create a review for an owned completed order item and verify its purchase association, content and contribution to the product summary.

**Acceptance Scenarios**:

1. **Given** an owned COMPLETED order item with no review, **When** the customer submits integer rating 1 or 5, **Then** one review is created with the authenticated author, derived product and original order item.
2. **Given** an order in PENDING, CONFIRMED, SHIPPING, CANCELLED or FAILED, **When** a review is attempted, **Then** it is rejected without changing reviews or statistics.
3. **Given** another customer's order item or a mismatched order/item pair, **When** creation is attempted, **Then** no private purchase details are disclosed and no review is created.
4. **Given** quantity greater than one, **When** duplicate or simultaneous requests target that item, **Then** exactly one succeeds and subsequent attempts report a conflict. A different completed purchase of the same product remains separately eligible under approved policy P-01.
5. **Given** missing, null, fractional, textual, zero or six rating, **When** submitted, **Then** input is rejected.

6. **Given** a valid rating, **When** submitted without comment or with images and no comment, **Then** the review is accepted; missing comments are stored as null.
7. **Given** comments at 1,999, 2,000 and 2,001 Unicode code points after edge trimming, **When** created, **Then** the first two are accepted and the last is rejected without mutation or truncation. Include supplementary Unicode characters to verify the counting rule.
8. **Given** an empty or whitespace-only comment, **When** created, **Then** it is normalized to null; nonblank comments are stored as plain text with edge whitespace removed, without interpreting embedded markup.

### User Story 2 - Read feedback and rating summaries (Priority: P1)

As a shopper, I can browse feedback and see the product's count and average to assess previous customers' experience.

**Why this priority**: Reading feedback delivers the feature's primary shopper value.

**Independent Test**: Seed known reviews and compare paginated results and summary against the complete committed set.

**Acceptance Scenarios**:

1. **Given** a visible product with ratings 1, 4 and 5, **When** its summary is read, **Then** count is 3 and the approved rounded average is 3.33, independent of the requested review page.
2. **Given** no reviews, **When** read, **Then** the page is empty, count is zero and average follows approved policy P-04 (null).
3. **Given** equal timestamps or ratings, **When** pages are traversed without intervening writes, **Then** deterministic identifier tie-breaking prevents duplicates or omissions.
4. **Given** an unavailable attached image, **When** reviews are read, **Then** the approved safe representation retains the image identifier and position with a null URL while other review content remains readable; a physically missing object discovered only by the client may require a placeholder.
5. **Given** an unpublished product or inactive author, **When** feedback is requested, **Then** anonymous reads of PUBLISHED products succeed, DRAFT/ARCHIVED or removed products return 404, and inactive authors are displayed as “Former customer” without their username, ID or profile image; existing reviews and purchase associations are retained.
6. **Given** any review response, **When** inspected, **Then** no email, phone, private address, authentication material or sensitive order information is exposed.

### User Story 3 - Edit owned feedback and images (Priority: P2)

As an author, I can correct my rating, comment and ordered image collection while retaining the original review and purchase association.

**Why this priority**: Customers need to refine feedback without creating duplicate reviews.

**Independent Test**: Edit one persisted review and verify content, image order, timestamps and summary before and after success and failure.

**Acceptance Scenarios**:

1. **Given** valid completed uploads owned by the author, **When** zero to ten distinct identifiers are explicitly submitted, **Then** the complete image collection is replaced in the submitted order; an empty list removes all references.
2. **Given** any unknown, foreign-owned, expired or incomplete upload, null element, duplicate identifier or more than ten images, **When** creation or replacement is attempted, **Then** the whole operation fails and previous content, references and statistics remain unchanged.
3. **Given** a review, **When** images are omitted, **Then** references are preserved; explicit null is rejected under approved policy P-03.
4. **Given** a review rated 1, **When** changed to 5, **Then** count stays unchanged, the average reflects 5 after commit, createdAt is preserved and updatedAt advances on content change.
5. **Given** another customer's review, **When** an edit is attempted, **Then** it fails without mutation or disclosure.
6. **Given** concurrent edits, **When** both finish, **Then** the committed result is a complete valid edit with ordered images and matching statistics, or one edit reports a conflict; mixed partial edits are forbidden.
7. **Given** a failed image replacement, **When** retried or read, **Then** the previous committed review remains intact; reference removal never deletes the underlying object.

8. **Given** an owned review of any age and an active author, **When** valid content is edited, **Then** no age-based editing restriction applies, including when the original product is no longer published.
9. **Given** an existing comment, **When** omitted from an edit, **Then** it is preserved; explicit null, empty or blank clears it; nonblank content replaces it after normalization. A normalized 2,001-character replacement fails atomically, preserving previous content, images and statistics.

### Edge Cases

- Deleted product/variant links: existing order-item foreign keys can become null. Historical reviews must survive and retain the original product identity independently of a live purchasable catalog link. Creation for a legacy item whose product identity is already irrecoverable is rejected as not reviewable; no identity is guessed from a name snapshot.
- An archived product does not revoke purchase proof or erase feedback. Public reads of DRAFT/ARCHIVED or removed products return 404.
- Empty patch, unchanged values and image reordering have explicit semantics in P-03.
- Completed upload expiry timestamps describe the upload deadline, not an automatic expiry of an already COMPLETED image; reuse established media lifecycle rules.
- Page bounds, unsupported sorting/filtering, invalid identifiers, missing review and duplicate creation return defined errors.
- Concurrent creation and edits cannot count uncommitted reviews or count an edited review twice.

## Requirements *(mandatory)*

### Functional Requirements

All requirements and formerly proposed policies below are confirmed. The user explicitly approved all proposals on 2026-11-21, including P-01–P-05 and the HTTP, authorization, error, media-fallback and active-author-summary contracts.

- **FR-001**: Provide product review listing, product count/average statistics, creation for an owned purchased order item and owned review editing. Exclude shop ratings, unverified purchases, votes, replies and moderation. Review deletion is excluded from this release; no deletion endpoint or deletion workflow is included.
- **FR-002**: Enforce authenticated ownership using the current actor, never an authoritative author ID from input. Derive product and author from the owned order item; reject unrelated product selection. **Approved P-01**: only COMPLETED items qualify; enforce one persisted review per item regardless of quantity; allow another review for another completed purchase of the same product.
- **FR-003**: Rating is a required integer from 1 through 5 on creation. Comment and images are optional. Maximum image count is ten. Rating-only reviews and rating-plus-images without a comment are allowed. Comments are plain text, stripped of leading/trailing whitespace; empty or blank values normalize to null. Comments MUST contain at most 2,000 Unicode code points after normalization; reject longer comments without truncation. Authors may edit indefinitely, subject to ownership and active-account requirements.
- **FR-004**: Attach only the author's eligible completed uploads using current upload initiation, completion, object validation and attachment services. Validate every submitted identifier before committing any review/content/reference change. Reject unknown, expired, pending or foreign images; no partial attachment. **Approved P-02**: reject duplicate IDs instead of silently deduplicating.
- **FR-005**: Preserve submitted image order and durable image references; never persist presigned URLs as identifiers. Removing a reference must not delete an object or introduce a new retention policy. Existing upload size/type/deadline and abandoned-upload rules remain authoritative.
- **FR-006**: **Approved P-03** partial update contract: omitted fields preserve values; explicit rating replaces it and null is invalid; explicit comment null clears it, empty/blank clears it, and nonblank text replaces it; explicit image list replaces the full collection, empty clears it, and null is invalid. Empty patches are rejected. An identical normalized update succeeds without changing updatedAt; any effective change, including image order, advances it. createdAt, author and purchase identity are immutable. Editing remains tied to the original completed purchase, with no time limit.
- **FR-007**: Responses include stable review ID, rating, nullable comment, ordered image IDs and temporary access URLs following current media conventions, safe author summary, createdAt, updatedAt and verifiedPurchase=true. Public responses must not contain order/item IDs or private user fields. **Approved** author summary: displayName with username fallback and optional profile image for active accounts; author ID omitted.
- **FR-008**: Statistics cover all committed eligible reviews for the product, independent of page/filter. Edits change the rating sum but never increase count. **Approved P-04**: each purchased item contributes one review, average rounded to two decimal places using half-up rounding, and null average with zero reviews. Distribution is excluded until approved.
- **FR-009**: Allow anonymous reading of reviews and rating summaries for PUBLISHED products. Return 404 for DRAFT, ARCHIVED or removed products. Retain historical reviews regardless of publication status. On otherwise visible reviews, display INACTIVE, SUSPENDED and BANNED authors as “Former customer” without username, author ID or profile image; do not expose the underlying account status.
- **FR-010**: Account deactivation must not cascade into review deletion or remove historical purchase proof. Inactive accounts cannot create/edit using this feature. This scope does not add a deactivation endpoint or decide unrelated account lifecycle behavior. Apply the confirmed FR-009 display policy for INACTIVE, SUSPENDED and BANNED authors.
- **FR-011**: **Approved P-05** list contract: zero-based page default 0, size default 20 and range 1–100; sort createdAt or rating ascending/descending, default createdAt descending with review ID descending as final tie-breaker. No rating filters initially; reject unsupported query parameters/sorts rather than ignore them. Return application-defined items, page, size, totalElements and totalPages, with an empty page beyond the last. Stability assumes unchanged data; no snapshot guarantee across separate page requests.
- **FR-012**: Duplicate and concurrent creation must be prevented persistently. Failed writes preserve all prior content/references. Concurrent edits must not corrupt image ordering, lose parts of an accepted edit or produce inconsistent statistics. Planning must choose conflict and transaction mechanisms and document their observable behavior; no global retry or idempotency-key policy is implied.
- **FR-013**: Reads must use planned data retrieval for reviews, authors and images; conversion must not initiate hidden per-review queries. Batch cross-domain access may require extending owning services; current single-image and user lookup interfaces are not evidence of bulk capability.

### Approved HTTP Contract

These approved contracts fulfill constitution IV and govern implementation.

| Responsibility | Method and resource | Success |
| --- | --- | --- |
| Product reviews | GET /api/products/{productId}/reviews | 200 with page |
| Rating summary | GET /api/products/{productId}/rating-summary | 200 with count and average |
| Create purchased-item review | POST /api/me/orders/{orderId}/items/{orderItemId}/reviews | 201 with review and Location identifying the created review |
| Edit owned review | PATCH /api/me/reviews/{reviewId} | 200 with resulting review |

- Product/image identifiers are UUID; order/order-item/actor identifiers are positive numeric Long values. New review identifier type is a planning decision preserving those existing contracts.
- Approved create/edit access: authenticated ACTIVE purchaser with existing MANAGE_ORDER permission plus ownership checks. The user approved this existing permission mapping on 2026-11-21; no new role or permission grant is introduced.
- Approved errors: 400 malformed identifiers, query/body shape, invalid rating, content/image count, duplicate image IDs, null images or empty patch; 401 absent/invalid authentication (including rejected inactive credentials); 403 authenticated principal missing required permission; 404 missing or non-owned order/item/review, mismatched order/item, unavailable public product, unknown/foreign image; 409 duplicate review, non-reviewable order state/irrecoverable historical product identity, expired/incomplete image or detected concurrent edit; 503 unavailable attachment storage.
- Use existing ProblemDetail conventions and media exception mappings. Implementation must reconcile approved mappings with shared handlers without changing unrelated endpoints; any mismatch is recorded and resolved before implementation. Rejected requests never return success or disclose ownership of foreign resources.
- Review deletion is excluded. Any future deletion feature requires separately specified authorization, retention and statistic effects.

### Key Entities *(include if feature involves data)*

- **Product Review**: Stable identity; immutable author and purchased-item association; original product identity; rating, optional comment, createdAt and updatedAt.
- **Purchased Order Item**: Existing numeric item/order identifiers, owning customer, quantity, product/variant references and immutable purchase snapshots; order status proves eligibility.
- **Review Image Reference**: Ordered association to an existing UUID completed upload owned by the author; independently retained media lifecycle.
- **Rating Summary**: Product-level committed review count and average; no shop aggregate.
- **Author Summary**: Minimal public display respecting account status, without private account or order data.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: All completed-purchase, ownership and invalid-state acceptance cases enforce the approved eligibility policy; zero unauthorized or duplicate reviews are committed, including simultaneous creation attempts.
- **SC-002**: Across tested image counts 0, 1 and 10, every successful read preserves submitted order; all invalid-image and 11-image cases leave zero partial changes.
- **SC-003**: Across creation, rating changes, failed writes and concurrent operations, every observed post-commit summary matches the committed reviews; rating edits increase review count by zero.
- **SC-004**: Traversing an unchanged dataset with timestamp/rating ties returns every matching review exactly once; empty and out-of-range pages satisfy the documented page contract.
- **SC-005**: Every response inspected in active/inactive author and foreign-resource scenarios exposes zero prohibited private fields; unavailable media does not prevent reading otherwise valid review content.
- **SC-006**: All approved comment, update, retention and visibility scenarios pass before release, including unpublished products and inactive authors. Pending decisions cannot be counted as passing acceptance evidence.

## Assumptions

### Confirmed Scope and Approved Policies

Confirmed: all five capabilities and Q1–Q3, including optional comment/images, rating 1–5, ten-image maximum, 2,000 Unicode code points after whitespace normalization, indefinite owned editing, public published-product reads and inactive-author anonymization. On 2026-11-21 the user explicitly approved every remaining proposal: P-01–P-05, HTTP/access/error contracts (including MANAGE_ORDER), media fallback and active-author summary. No approval gate remains. Distribution, deletion, shop ratings, votes, replies and moderation remain excluded; approval of the existing proposals does not add excluded features.

### Clarifications — 2026-11-20

- Q1: User confirmed “Exclude from this release.” Review deletion remains out of scope.
- Q2: User confirmed “allow all with maximum 2000 length comment,” accepting rating-only content, rating plus images without comment, plain text, trimming/blank normalization and indefinite editing, with a 2,000-character maximum.
- Q3: User confirmed anonymous reading for published products, 404 for unpublished products, retained reviews and inactive authors displayed as “Former customer.”

### Proposal Approval — 2026-11-21

- C1: User explicitly stated all proposals are approved. This confirms the complete existing policy/contract baseline, including MANAGE_ORDER plus active ownership, P-01–P-05, read-media fallback, safe active-author summary and error/update semantics. Earlier proposal labels are now approved policies.
- I1/I2/D1: User authorized aligning test selectors, correcting flow/checkpoint order and separating invalid-rating acceptance from optional-content acceptance.

### Repository and Reference Evidence

Inspected constitution version 1.2.0 and current source on 2026-11-20:

- `demo/src/main/java/com/example/demo/order/entity/{Order,OrderItem,OrderStatus}.java`: numeric order/item/customer IDs, UUID product/variant references, COMPLETED and FAILED among six states, purchase snapshots and completedAt.
- `demo/src/main/resources/db/migration/V23__customer_order_management.sql`: customer/order history uses restricted deletion; product/variant links use ON DELETE SET NULL. Review history must not depend solely on those nullable links.
- `demo/src/main/java/com/example/demo/catalog/entity/ProductStatus.java` and `catalog/controller/ProductCatalogController.java`: DRAFT/PUBLISHED/ARCHIVED and UUID public resource conventions.
- `demo/src/main/java/com/example/demo/media/entity/ImageUpload.java`, `media/service/MediaService.java`, `media/service/impl/MediaServiceImpl.java`, and `storage/service/impl/PresignedImageUrlResolver.java`: UUID uploads, numeric owner, PENDING/COMPLETED/EXPIRED lifecycle, attachment ownership/readiness/object checks and temporary URLs. Attached-image lookup does not itself guarantee physical availability; listing resilience needs explicit design.
- `demo/src/main/java/com/example/demo/user/service/impl/UserServiceImpl.java`, `user/dto/response/PublicUserResponse.java`, `user/enums/AccountStatus.java` and `auth/config/AuthSecurityConfiguration.java`: public identity fields and account statuses exist, but no deactivation operation was found. Public user lookup currently requires authentication. Reusing its entire DTO or service unchanged is not an approved review privacy design.
- Local ProjectCV reference: `C:/Users/TrinhThanh/Downloads/ProjectCV/Backend/demo/src/main/java/com/example/demo/engagement/` review service, validator, request DTO, product review controller and statistics service, plus `order/validator/OrderItemValidator.java`. Reference behavior includes owned-item lookup, DELIVERED/COMPLETED eligibility, duplicate prevention, required 1–5 rating, optional comment, maximum ten images, owned edits, pagination and product statistics. Adapt to Backend-SWE's COMPLETED state and UUID images/products; do not copy numeric image IDs, shop ratings, full image replacement on omission, or implicit reference defaults.

### Planning and Verification Dependencies

Use owning-domain service interfaces for order eligibility, users, catalog and media; maintain acyclic dependencies. Do not copy existing cross-domain repository shortcuts. Planning chooses persistence, fetch plans, transaction/conflict handling and how to retain product identity without changing historical migrations. Reuse upload validation and storage retention; no new storage lifecycle is approved here.

Implementation coverage must use existing domain fixtures and applicable unit, MVC, PostgreSQL repository/service integration and API/flow conventions, with isolated storage dependencies. Include real commit/rollback, persistent uniqueness and concurrency verification for the stated risks. Follow constitution XIII's targeted checks, integration checkpoints and final unrestricted verification for implementation; this specification-only change requires document consistency checks rather than application tests.
