# Feature Specification: Admin Catalog Management

**Feature Branch**: `features/AdminCatalogManagement` (existing clean feature branch retained; no branch-creation hook configured)

**Created**: 2026-11-21

**Status**: Clarified — BR-01–BR-20 approved; business requirements ready for a separately invoked `$speckit-plan`; implementation and data compatibility work remains

**Input**: User-approved Chạm Tech Specify brief (BR-01–BR-09), Clarify decisions (BR-10–BR-20), and the explicit sibling-category normalization answer recorded below. Extend the existing backend; Clarify only, with no application changes, migrations or planning.

## Feature Overview

Enable an authenticated Administrator with the existing `ROLE_ADMIN` to maintain the electronic-device catalog: prepare incomplete Drafts, manage products/categories/brands/variants/prices/images/specifications, publish ready products, withdraw or archive products, restore Archived products to Draft and re-enable valid variants without destroying business history.

The feature extends existing capabilities. It does not rebuild customer catalog/search, authentication, uploads, inventory, carts, checkout or order history. Publication readiness and stock availability are separate: a complete product may be Published with zero stock, while purchasing remains subject to existing availability checks. A product variant remains the owner of its price and technical specifications.

The approved business operations Draft, Publish, Unpublish and Archive are required business concepts, not a decision to introduce four distinct stored state values. Existing behavior, gaps and source references are recorded in [compatibility.md](compatibility.md). Current implementation does not override the approved brief.

**Decision precedence**: BR-01–BR-20 and the recorded category-name answer govern this feature. Preserve existing behavior except for these explicitly approved extensions. Older specifications requiring variants on every Draft do not override BR-06/BR-14; current constraints do not override approved business outcomes. Technical representation, persistence changes and HTTP design belong to Plan.

## Clarifications

### Session 2026-11-21

The product owner supplied eleven approved decisions as BR-10–BR-20. They resolve CQ-01–CQ-11 without re-asking those questions. One additional material equality question was asked and answered; it refines BR-19, not the feature scope. The question-count limit applies to questions asked in this run, not to decisions supplied in the user's instruction.

- Q: What happens when an edit invalidates a Published product (CQ-01)? → A: Reject it; the Administrator must explicitly Unpublish first. Never implicitly Unpublish; successful Published updates preserve readiness (BR-10/17).
- Q: May Archived products be restored (CQ-02)? → A: Yes, to Draft, preserving history/references; publication requires validation again. A restored Draft is an explicit exception to the initial never-Published Draft meaning (BR-11/20).
- Q: May Disabled variants be re-enabled (CQ-03)? → A: Yes, after applicable validation, without changing the parent's lifecycle or bypassing inventory/reservation constraints (BR-12).
- Q: What happens when the primary image is removed (CQ-04)? → A: A Published product must retain one valid primary; designate a valid replacement before removal or reject it. Draft/Unpublished products do not require a publication-ready gallery, but existing gallery integrity still applies (BR-13).
- Q: How are conflicting administrative updates handled (CQ-05)? → A: Each update is based on identifiable relevant-data versions; stale changes are rejected with a clear conflict and the Administrator must reload before retrying. Coverage includes associated variants, prices and gallery changes; persistence/HTTP choices are deferred (BR-15).
- Q: What does category reparenting do (CQ-06)? → A: Permit acyclic moves; products keep their assigned category, while hierarchical ancestor browsing/filtering follows the new position. Preserve referential integrity and applicable Published validity (BR-16/10).
- Q: Which variants must pass publication validation (CQ-07)? → A: At least one Enabled Variant must be sellable and every Enabled Variant must satisfy sale validity. Disabled Variants retain basic integrity but need not satisfy every publication-specific condition; positive inventory is not required (BR-17/14).
- Q: What happens to carts after withdrawal (CQ-08)? → A: Retain cart rows, expose unavailability through the existing cart contract and reject unavailable items at new checkout. Revalidate current eligibility/inventory if eligible again; history is unchanged. Disabled variants follow the same existing unavailable-item safeguards (BR-18/09).
- Q: Which names and identities are unique (CQ-09)? → A: SKU remains globally unique, including Disabled variants and Archived products; product names may repeat; category names are unique among siblings including roots; brand names are unique ignoring case and outer whitespace. Existing brand-code uniqueness/immutability and variant-option constraints remain unchanged (BR-19; category equality refined below).
- Q: How do business lifecycle states differ (CQ-10)? → A: Draft is initial/incomplete or restored; Published satisfies readiness; Unpublished was Published and explicitly withdrawn; Archived is logically removed and restorable to Draft. Publish/republish requires validation; no four-enum implementation is mandated (BR-20/11).
- Q: May a created Draft variant omit SKU or price (CQ-11)? → A: No. The product may have zero variants, but each created variant requires valid SKU and selling price under existing basic rules. Do not make those fields nullable merely for Draft preparation (BR-14).
- Q: Within the same parent, including roots, do “Laptop” and “ laptop ” count as the same category name (CQ-12)? → A: Yes—ignore case and outer whitespace. The same equality rule applies to creation, rename and destination siblings during a move. No accent folding, internal-whitespace collapsing or alias merging is added by this answer.

## User Scenarios & Testing *(mandatory)*

Acceptance identifiers are stable within this specification. Tests are business-level acceptance definitions, not test code or execution results. Each story can be exercised using prepared records independently of the other administrative workflows. All scenarios now have business outcomes; clarification resolution does not claim that the future feature has been implemented or runtime-tested.

### User Story 1 - Prepare and govern products (Priority: P1)

As an Administrator, I list and inspect products in all business lifecycle conditions, save incomplete Drafts, complete their information and publish, unpublish, archive or restore them without damaging customer transactions. **Use case**: UC-031.

**Why this priority**: Product preparation and controlled public availability are the foundation of catalog administration.

**Independent Test**: Start with a valid existing category and prepared products in the relevant lifecycle conditions; exercise Draft preparation, publication validation and withdrawal without needing category creation or stock adjustment.

**Acceptance Scenarios**:

1. **AC-01 — Incomplete Draft creation. Given** an authenticated Administrator and a valid existing category, **When** only Product Name and Category are supplied, **Then** a Draft is saved without requiring Brand, Description, Variants, Prices, Images or Technical Specifications; it is visible to the Administrator and absent from public catalog results and new purchases.
2. **AC-02 — Draft editing. Given** an incomplete Draft, **When** the Administrator changes its name/category or supplies optional information, **Then** the accepted information is saved, omitted information is preserved, the product stays Draft and publication readiness is not a condition of saving it. An invalid supplied category or blank required name rejects the change without partial edits.
3. **AC-03 — Successful publication. Given** a Draft or Unpublished product with valid name/category/gallery, at least one Enabled sellable variant, and every Enabled variant satisfying all applicable sale/publication requirements, **When** the Administrator publishes it, **Then** it becomes Published. Disabled variants need only basic integrity; zero inventory does not block publication and subsequent purchases still observe stock rules.
4. **AC-04 — No valid Enabled variant. Given** a Draft/Unpublished product with zero variants, only Disabled variants, or no Enabled sellable variant, **When** publication is requested, **Then** it is rejected with meaningful variant-related feedback and no lifecycle or partial catalog change.
5. **AC-05 — Missing primary image. Given** a Draft with no valid primary image, including an empty gallery or inconsistent imported gallery, **When** publication is requested, **Then** it is rejected with meaningful image/primary feedback and remains non-public. Multiple primary images also fail; no silent primary selection is assumed by publication.
6. **AC-06 — Unpublish. Given** a Published product, **When** the Administrator explicitly unpublishes it, **Then** it becomes Unpublished, is removed from public discovery and new-purchase eligibility, and retains identity/history/reservations. Existing cart rows are retained, display unavailable through the existing cart contract and cannot be accepted by a new checkout; no stored quantity or selection is silently rewritten.
7. **AC-07 — Archive with history. Given** a product referenced by historical orders and possibly active reservations, **When** the Administrator archives it, **Then** it is retained for administration and historical references, excluded from public catalog and new purchases, and prior order quantities/prices/totals and reservation attribution are unchanged. Existing orders can still follow their independently authorized lifecycle.
8. **AC-08 — Administrative visibility. Given** all four business states and a restored Draft, **When** an Administrator lists products and views details, **Then** state is distinguishable, incomplete Drafts and Disabled variants remain inspectable, bounded navigation has accurate results and public reads stay restricted. An initial Draft need not have been Published; a restored Draft may have publication history.
9. **AC-09 — Invalid Published edit rejected. Given** a Published product, **When** an edit would invalidate its name/category, Enabled-variant sellability, positive publication price, image/primary requirement or applicable specifications, **Then** reject the whole edit with meaningful readiness feedback; the previous valid Published state/data remain unchanged. Do not stage, automatically Unpublish or silently accept it. After an explicit Unpublish the Administrator may save publication-incomplete information subject to basic integrity.
10. **AC-36 — Restore to Draft. Given** an Archived formerly Published product with historical orders/reservations, **When** the Administrator restores it, **Then** it becomes Draft with unchanged identities/references/history/allocations and no public purchase eligibility; publishing it again requires full validation. Restore does not republish or supply inventory.

### User Story 2 - Manage variant choices and future prices (Priority: P1)

As an Administrator, I view, create, update, disable and re-enable product variants and change future selling prices while retaining the identities and purchase records of existing variants. **Use case**: UC-034; specification content is elaborated in UC-036.

**Why this priority**: Sellable choices and authoritative prices are necessary for publication and safe future purchasing.

**Independent Test**: Use an existing product, variants, historical order and an active reservation; update a price, validate identity/option conflicts and disable a variant without creating an order or changing stock through this feature.

**Acceptance Scenarios**:

1. **AC-10 — Draft variant validation. Given** a Draft with no variants, **When** it is saved, **Then** it succeeds without a variant. When a variant is subsequently created, missing/null/blank SKU or missing/null/fractional/non-finite/wrong-currency price is rejected; accepted variants retain required SKU/whole-VND price and existing option/specification integrity. A basic-valid zero-priced preparation variant does not qualify for publication while Enabled.
2. **AC-11 — Price change. Given** an existing variant and a historical order, **When** a version-current Administrator accepts a new whole-VND price, **Then** future purchases use the current price under cart/checkout rules and original order item prices/totals/receipts remain unchanged. Fractional/non-finite/wrong-currency prices fail; a nonpositive price for an Enabled variant on a Published product is rejected unless the product was explicitly Unpublished first.
3. **AC-12 — Disable referenced variant. Given** a variant referenced by historical transactions and an active reservation, and either another valid Enabled variant on its Published parent or a non-Published parent, **When** the Administrator disables it, **Then** new purchases cannot use it while identity, SKU ownership, history, physical/reserved units and allocation attribution remain intact. Cart rows are retained and unavailable; existing authorized reservation resolution still works. No order is canceled or units released by disabling.
4. **AC-13 — Retained SKU/options conflict. Given** a SKU owned by any Enabled/Disabled variant, including a variant of an Archived product, or an option combination already used within the product, **When** a conflicting create/update is attempted, **Then** existing case-insensitive SKU uniqueness and option rules reject it without partial updates. Neither disabling nor archival frees the identity for reuse.
5. **AC-14 — Last Enabled variant protected. Given** a Published product with exactly one valid Enabled variant, **When** an operation would disable it or otherwise remove its sale validity, **Then** reject the whole change, retain Published validity and preserve allocations/history. After the Administrator explicitly Unpublishes, disabling is permitted under basic integrity and reservation rules.
6. **AC-37 — Re-enable safely. Given** a Disabled variant and an identifiable current version, **When** the Administrator re-enables it, **Then** applicable SKU/price/options/specification, uniqueness, parent readiness and reservation rules are validated; invalid input is rejected unchanged. Success enables only the variant and leaves parent lifecycle and stock/reservations unchanged; a Draft/Unpublished/Archived parent does not become Published.
7. **AC-38 — Enabled versus Disabled publication. Given** a ready product with one valid Enabled variant and a basic-valid Disabled variant whose price is zero, **When** it is Published, **Then** the Disabled variant does not fail publication merely for that publication-specific price condition. If an Enabled variant is invalid or all variants are Disabled, publication is rejected. Disabled variants with missing/invalid basic SKU or price remain invalid data.
8. **AC-42 — Duplicate product names and retained SKUs. Given** two products with the same name but valid independent identities, **When** they are saved, **Then** the duplicate product name alone is not a conflict. Reusing an existing SKU, including one on a Disabled variant or Archived product, is rejected.

### User Story 3 - Manage product images safely (Priority: P1)

As an Administrator, I use the existing completed-upload mechanism to attach and view product images, select the primary image, reorder the gallery and remove associations without deleting referenced media. **Use case**: UC-035.

**Why this priority**: Publication requires valid imagery and one primary image; unsafe gallery changes could corrupt public display or shared media.

**Independent Test**: Use a prepared product and valid owned completed uploads, plus pending/foreign/invalid uploads; exercise gallery operations and public/administrative visibility without introducing another uploader.

**Acceptance Scenarios**:

1. **AC-15 — Existing upload and attachment. Given** an Administrator's valid completed image from the existing supported upload workflow, **When** it is attached with valid metadata, **Then** a product association is saved and the Administrator can view it without publishing a Draft. Pending, expired-pending, foreign-owned, missing or invalid images are rejected under existing media eligibility rules without a false successful attachment.
2. **AC-16 — Change primary. Given** an existing valid gallery, **When** the Administrator selects another associated image as primary, **Then** that image becomes primary and the previous one ceases to be primary as one completed change; a Published product's subsequently generated catalog imagery reflects the new selection.
3. **AC-17 — Reorder. Given** a gallery, **When** the Administrator submits a valid new order for its associated images, **Then** subsequent gallery views reflect that order without losing associations, duplicating positions or changing the primary selection unless separately requested.
4. **AC-18 — Remove non-primary association. Given** a non-primary image association, **When** the Administrator removes it, **Then** that association disappears from subsequent gallery results, other associations remain correct and objects still referenced elsewhere are not physically deleted.
5. **AC-19 — Published primary removal. Given** a Published product and its current primary image, **When** the Administrator designates another valid associated image as primary before removing the old primary association, **Then** removal succeeds with exactly one valid primary and preserved order/references. Removal without a designated valid replacement, including the last image, is rejected unchanged; no automatic replacement or implicit Unpublish occurs.
6. **AC-20 — Existing image limits. Given** supported image types and existing gallery/content limits, **When** an invalid image or an over-limit attachment is attempted, **Then** it fails without a new association or public exposure. Previously issued temporary viewing links retain their established expiry semantics after removal or unpublication; no instant revocation promise is introduced.
7. **AC-39 — Non-Published gallery removal. Given** a Draft or Unpublished product, **When** its last image association is removed, **Then** an empty gallery is allowed and no object still referenced elsewhere is deleted. If images remain, existing nonempty-gallery integrity still requires exactly one primary; removal of the primary must leave a designated valid primary or is rejected. The product does not become Published.

### User Story 4 - Enforce authorized, consistent changes (Priority: P1)

As an Administrator, I receive meaningful validation/dependency/conflict outcomes, and as a customer I retain accurate history and protected inventory when catalog changes overlap other work. **Applies to**: UC-031–UC-036.

**Why this priority**: Authorization and integrity apply to every administrative workflow, not an optional later slice.

**Independent Test**: For each operation, compare anonymous, authenticated non-admin, valid current Administrator and invalidated Administrator sessions; overlap two administrators and customer checkout against prepared products/reservations.

**Acceptance Scenarios**:

1. **AC-31 — Unauthorized operations. Given** an anonymous user or authenticated user without `ROLE_ADMIN`, **When** any administrative list/detail/mutation is attempted, **Then** it is denied under existing authentication/authorization behavior with no administrative disclosure or state change. A role asserted in submitted data grants nothing; an invalidated session/account cannot retain access merely through an old token.
2. **AC-32 — Stale administrative update rejected. Given** two Administrators editing the same identifiable version of relevant catalog data, **When** one change is accepted and the other submits its now-stale update, **Then** reject the latter with an explicit conflict and no partial change. The caller must reload current data/version before retrying. Product edits versus associated variant/price/gallery edits and stale taxonomy/brand modifications are covered; stale state is not silently merged or automatically retried.
3. **AC-33 — Withdrawal versus checkout and recovery. Given** a product/variant in a cart, **When** Unpublish/Archive/Disable takes effect before new checkout acceptance, **Then** retain the row, expose unavailable through the existing cart contract, exclude it under existing checkout eligibility and create no purchase/reservation for it. A previously committed order/receipt/allocation remains valid and replay stays historical. If sale eligibility returns after valid republish/re-enable, revalidate current price, eligibility and inventory; restored Draft alone is still unavailable.
4. **AC-34 — Reservation preservation. Given** active allocations for a variant, **When** its product information, price, specifications, category, brand, gallery or eligibility changes, **Then** the catalog operation does not directly increase/decrease stock, rewrite reserved quantities, delete allocations or cancel existing orders. Existing authorized cancellation/completion retains its established allocation behavior.
5. **AC-35 — Failure integrity. Given** an invalid input, dependency conflict, competing mutation or storage failure, **When** an administrative change cannot complete, **Then** no partially accepted business change is exposed as successful, meaningful safe feedback identifies the problem, and referenced media/transaction history remains intact. Uncertain storage outcomes do not imply physical rollback or safe object deletion.

### User Story 5 - Maintain categories and hierarchy (Priority: P2)

As an Administrator, I list, create and update categories, manage valid hierarchy relationships and delete only categories without dependencies. **Use case**: UC-032.

**Why this priority**: Product organization must be maintainable without orphaning products or corrupting category-based search.

**Independent Test**: Use category trees with and without products, including archived products, and test valid creation, naming and dependency rejection without using product creation.

**Acceptance Scenarios**:

1. **AC-21 — Category CRUD. Given** valid information and an optional valid parent, **When** an Administrator creates/lists/updates a category, **Then** accepted information/hierarchy is retrievable. Unknown parent, blank required name, self-parenting, a descendant as parent or a duplicate normalized sibling name, including among roots, rejects the whole change.
2. **AC-22 — Referenced category deletion. Given** a category referenced by any product, including an Archived product, **When** deletion is attempted, **Then** it is rejected with dependency feedback; no product is removed, automatically reassigned or detached.
3. **AC-23 — Dependent subcategories. Given** a category with children and no directly assigned products, **When** deletion is attempted, **Then** it is rejected until child dependencies are resolved; no child deletion/reparenting is implied. A category with neither products nor children can be deleted.
4. **AC-24 — Hierarchy effects. Given** an otherwise valid acyclic move to a parent with no normalized sibling-name collision, **When** accepted, **Then** the category keeps its identity and every product retains its assigned category; browsing/filtering under ancestors follows the new hierarchy. Facts, historical records and allocations are not rewritten. A move introducing a cycle, sibling collision or invalidating affected Published readiness is rejected unchanged under the applicable rules.
5. **AC-40 — Sibling uniqueness on move. Given** “Laptop” under a parent, **When** another sibling is created/renamed/moved there as “ laptop ”, **Then** the whole change is rejected. The same rule applies among roots. Categories under different parents may share that normalized name; a failed move keeps the original parent and product assignments.
6. **AC-43 — Category move preserves Published readiness. Given** a move that changes applicable category/specification rules for assigned products, **When** the proposed result would invalidate any affected Published product, **Then** reject the move without partial hierarchy changes or implicit Unpublish. The Administrator can explicitly Unpublish affected products before editing; later publication validates the current category rules.

### User Story 6 - Maintain product brands (Priority: P2)

As an Administrator, I list, create and update brands and delete only brands with no referring products. **Use case**: UC-033.

**Why this priority**: Canonical brands improve product identification while deletion must preserve associations and integrity.

**Independent Test**: Use existing brands with and without referencing products, including archived products, and test maintenance/deletion without creating products.

**Acceptance Scenarios**:

1. **AC-25 — Normalized brand maintenance. Given** valid existing code/name rules, **When** a brand is created or its editable name is changed, **Then** it is retrievable and selectable for products. A duplicate code or a name matching another brand after case/outer-whitespace normalization rejects the change. Existing brand codes remain stable; changing a name does not regenerate identity or merge brands.
2. **AC-26 — Referenced brand deletion. Given** a brand referenced by a product in any lifecycle condition, including Archived, **When** deletion is attempted, **Then** it is rejected with dependency feedback and existing associations remain. An unreferenced brand can be deleted.
3. **AC-27 — Optional brand. Given** a Draft with a valid name/category and no brand, **When** it is saved or later completed with a valid existing brand, **Then** both states are valid Draft information; a brand is not invented or made mandatory for publication by this feature.
4. **AC-41 — Brand-name collisions. Given** a brand named “Apple”, **When** another brand is created or renamed to “ apple ” or another case-only equivalent, **Then** reject it regardless of distinct brand code, current product visibility or Archived product references. No existing brand identities/references are merged or deleted as a remedy.

### User Story 7 - Maintain searchable variant specifications (Priority: P2)

As an Administrator, I create, inspect and update each variant's technical facts using the existing supported structures so catalog filtering continues to describe the correct variant. **Use case**: UC-036.

**Why this priority**: Accurate specifications must remain compatible with existing variant-level search, units and types.

**Independent Test**: Use known valid/invalid typed specification examples and existing category/filter definitions; change one variant's facts and verify its details/filter behavior without creating a new search implementation.

**Acceptance Scenarios**:

1. **AC-28 — Modify specifications. Given** variants with different specifications, **When** the Administrator saves supported facts for one variant, **Then** that variant's facts change and unrelated variants/options/prices remain unchanged; existing applicable search/filter results use the accepted facts for that same variant.
2. **AC-29 — Invalid facts. Given** malformed structure/version, invalid keys, unsupported values or invalid applicable types/units, **When** specifications are saved or publication is checked, **Then** meaningful validation rejects invalid input without silently converting units, guessing values or clearing unrelated facts.
3. **AC-30 — Optional facts. Given** a Draft without technical facts, **When** it is saved, **Then** no fabricated facts are required. A structurally valid empty fact collection remains governed by existing applicability rules; this feature does not introduce mandatory facts for every category. False and numeric zero remain real supplied values, not absent data.

### Edge Cases

- A Product Draft may have zero variants; a created variant may not omit SKU/price. Do not fabricate a variant or make SKU/price nullable to reconcile older constraints. Existing basic finite whole-VND preparation rules are distinct from positive-price publication rules.
- Publication requires at least one Enabled sellable variant and sale validity for every Enabled variant. Disabled variants retain basic data integrity and SKU/option uniqueness; publication-specific conditions are not imposed unnecessarily on them. Positive stock is never a publication prerequisite.
- Reject any successful-state proposal that would leave a Published product invalid, including its last Enabled variant, required primary image or category-dependent specifications. Explicit Unpublish is a separate Administrator action, never automatic compensation.
- Restore always yields Draft, even if the product was previously Published or Unpublished. This exception does not erase history, restore sale eligibility, cancel allocations or bypass republishing validation.
- Re-enabling validates the variant in its current context without changing the parent's state or stock. Parent product visibility/purchase eligibility and inventory remain independent gates.
- Draft and Unpublished galleries may be empty. Preserve the existing one-primary rule when images remain; a Published primary removal needs an explicitly designated valid replacement. Temporary previously issued image URLs retain their established expiry; do not promise instant revocation or physically delete referenced objects.
- Category sibling equality ignores case and outer whitespace, including root siblings. Different-parent duplicates are allowed, but a move can create a destination collision. Products retain assigned category identity; ancestor discovery follows the resulting acyclic hierarchy.
- Brand names are unique ignoring case and outer whitespace. Product names may duplicate. SKU uniqueness remains global and case-insensitive, including Disabled variants/Archived products; existing brand-code and option/order uniqueness remain protected. No accent folding, internal-whitespace collapsing or fuzzy alias merging is introduced.
- Stored cart rows and preferences are retained after Unpublish/Archive/Disable; unavailable rows remain identifiable through the existing safe cart contract, do not contribute eligible purchase totals and are not accepted at new checkout. Re-eligibility requires fresh checks; restored Draft alone is not sufficient.
- Historical order/receipt snapshots and current allocation identities/quantities are not rewritten by catalog updates or withdrawal. Existing independently authorized order resolution can still release/consume its own allocations.
- A stale product update must not overwrite a newly accepted variant/price/gallery change, or vice versa. Gallery, taxonomy and brand edits also need identifiable relevant versions; conflict requires reload, not blind merge/retry. Exact representation is a Plan dependency.
- Concurrent reference insertion versus deletion, reparenting versus uniqueness checks and publication/withdrawal versus checkout must preserve accepted-state invariants without a partial success. Omitted values preserve information; explicit empty/false/zero retain their documented meanings.
- Storage copy success is not proof of committed attachment; persistence failure is not proof that a referenced object can safely be deleted. Preserve existing reconciliation safeguards.
- Existing persisted data may violate newly approved publication/name-uniqueness rules. Before migration design, identify collisions (including root/sibling groups and normalized brand names), invalid legacy Published records and relevant references. Do not automatically merge/delete identities, rewrite history, unpublish records or invent stock; actual target-data findings and remediation authorization are Plan prerequisites, not presumed completed checks.

## Requirements *(mandatory)*

### Functional Requirements

#### Common access and product lifecycle — UC-031–UC-036

- **FR-001**: Every administrative catalog read and mutation MUST require an authenticated current eligible user with the existing `ROLE_ADMIN`; the existing identity/session/role model MUST be reused. No new RBAC system, role hierarchy, substitute permission or caller-supplied identity authority is introduced. Existing authenticated non-admin customer capabilities remain unchanged.
- **FR-002**: Administrators MUST list/inspect all four distinct business states (Draft, Published, Unpublished, Archived), incomplete Drafts and Disabled variants with bounded list navigation, correct results and explicit empty results. Public catalog access MUST remain publication-restricted. Administrative state must distinguish an initial Draft, an intentionally withdrawn Unpublished product and a restored Draft without requiring four distinct stored enum values.
- **FR-003**: Administrators MUST be able to create a Draft using only a valid nonblank Product Name and a valid existing Category. Brand, Description, Variants, Prices, Images and Technical Specifications MUST be optional at initial Draft save. Publication-readiness validation MUST NOT be applied as a Draft-creation prerequisite.
- **FR-004**: Administrators MUST update product information and complete Draft/Unpublished products incrementally, validating supplied fields and references without requiring publication readiness or fabricating missing data. Optional Brand must reference an existing brand. Successful edits on Published products MUST preserve publication readiness; FR-008 governs rejection. Archived products remain retained/inspectable and restoration supplies a Draft for active product preparation.
- **FR-005**: Publication from Draft or Unpublished MUST validate name/category, at least one Enabled sellable variant, sale validity of every Enabled variant (valid unique SKU, positive whole-VND price and applicable specifications), at least one valid image and exactly one valid primary. Disabled variants MUST satisfy basic data integrity, including mandatory valid SKU/price, but need not meet every publication-specific sellability condition. Invalid publication MUST identify violated readiness conditions with no partial change. Positive inventory MUST NOT be required.
- **FR-006**: Administrators MUST explicitly Unpublish a Published product to Unpublished, removing public visibility and new-purchase eligibility while retaining information, identities, historical references, stored cart rows and active allocations. An Unpublished product may return to Published only through full current publication validation. Unpublish MUST NOT be an implicit side effect of editing.
- **FR-007**: Product deletion MUST mean logical Archive, retaining products/variants and relevant orders, receipts, references and reservation attribution while hiding the product from public catalog and new purchases. Administrators MUST be able to restore an Archived product to Draft under FR-032; restore does not imply republish or renewed purchase eligibility.
- **FR-008**: Reject the entire administrative operation if its proposed result would leave a currently Published product violating publication readiness. This covers product edits and associated variant/price/specification/gallery or category changes affecting its validity. Preserve the prior valid Published result; never implicitly Unpublish. An Administrator must explicitly Unpublish affected products before saving publication-invalidating changes. Basic required name/category/variant/media integrity still applies after Unpublish.

#### Categories and brands — UC-032, UC-033

- **FR-009**: Administrators MUST list/create/update categories and change their parent within the existing hierarchy. Required names and supplied parents MUST be valid; reject missing parents, self-parenting and all cycles, including a descendant chosen as parent. Products assigned to a moved category MUST retain that category identity; hierarchical ancestor browsing/filtering follows its new position. Apply sibling uniqueness and preserve affected Published readiness when accepting a move.
- **FR-010**: Category deletion MUST be rejected while any product references it, regardless of product lifecycle, or while dependent subcategories remain. The Administrator MUST resolve dependencies separately; deletion MUST NOT cascade product/subcategory deletion or silently detach/reassign them. An unreferenced leaf category MUST be deletable.
- **FR-011**: Administrators MUST list/create/update permitted brand information and delete an unreferenced brand. Reject deletion while any product, including an Archived product, references it; do not automatically delete products or detach brand references. Enforce normalized-name uniqueness under FR-035 and preserve existing unique stable brand codes; the approved name-edit capability does not authorize changing the code/identity.
- **FR-012**: Taxonomy edits MUST preserve existing identities/references and existing applicable category/brand discovery/specification rules. Category moves change ancestor inclusion, not product assignment. Preserve product-specific facts rather than converting or fabricating them; reject operations invalidating affected Published readiness under FR-008. New name uniqueness follows FR-034/035. Backend input validation and authoritative persistence integrity MUST jointly enforce approved uniqueness/dependency rules, including concurrent creates/renames/moves.

#### Variants and prices — UC-034

- **FR-013**: Administrators MUST list/view/create/update product-owned variants with independent mandatory valid SKU and selling price, selectable options and technical specifications. Draft products may have zero variants, but every created Enabled or Disabled variant MUST satisfy existing basic SKU/whole-VND-price/options/specification integrity. Do not introduce nullable SKU/price just for Draft products; do not collapse variant facts into product-level price/specification fields.
- **FR-014**: SKU MUST remain globally unique across all variants ignoring case, including Disabled variants and variants of Archived products; Disable/Archive does not free an SKU. Existing within-product distinct option-combination and nonblank option-key/value rules remain enforced, including the existing nonempty-map rule for multiple variants. Product names MAY duplicate and do not establish identity or uniqueness.
- **FR-015**: Administrators MUST change a variant selling price in exact whole-đồng VND, rejecting missing required prices, fractional/non-finite amounts and wrong currency rather than rounding/converting. Existing basic preparation validity remains, while every Enabled variant of a Published product MUST have a positive sale-valid price. Reject an update violating this under FR-008; explicitly Unpublish first to make publication-incomplete edits. Future purchases use current accepted prices under existing cart/checkout validation; historical snapshots/receipts never reprice. No advanced price-history system is added.
- **FR-016**: Administrators MUST disable a variant to prevent future purchases while preserving identity, SKU ownership, historical transactions, retained references and active allocations. A disable that would leave a Published product without an Enabled sellable variant MUST be rejected unless the Administrator explicitly Unpublishes first. Disabling does not delete stored cart rows, cancel orders or change stock/allocations; re-enabling follows FR-033.
- **FR-017**: Variant availability eligibility MUST remain distinct from physical stock availability. Catalog changes MUST NOT expose an alternative stock-adjustment operation, fabricate stock, or bypass existing inventory/reservation invariants. Ordinary existing zero-balance initialization for a newly created variant remains an inventory dependency rather than an adjustment capability.

#### Product images — UC-035

- **FR-018**: Administrators MUST use the existing supported staged upload/completion mechanism and its ownership, content-validation and access rules; a separate upload architecture MUST NOT be introduced. Product attachment MUST use eligible completed images and preserve current product-image metadata/content/type/size/count rules. Invalid, incomplete or foreign-owned media MUST NOT become attachable through the new administration workflows.
- **FR-019**: Administrators MUST be able to attach completed images to a product, view its gallery including authorized non-public galleries, choose an associated image as primary, change gallery order and remove image associations. Association removal MUST NOT cause unsafe physical deletion of media still referenced elsewhere, nor deletion of the reusable completed upload merely because one product association was removed.
- **FR-020**: A Published product MUST retain exactly one valid primary image and satisfy its required valid-gallery condition after every accepted administrative change. Primary selection/reordering MUST preserve distinct order and unrelated associations consistently. Before removing a Published primary, the Administrator MUST designate a valid associated replacement; reject removal without it, including removal of the last image. Draft/Unpublished products may have no images; when a gallery remains nonempty, retain its existing exactly-one-primary integrity rule without requiring a publication-ready product.
- **FR-021**: Removing an image association MUST be distinguished from deleting the object. Retain existing temporary signed-access expiry and reference-safe reconciliation; no automatic unsafe object deletion or physical rollback assumption is permitted. A non-Published primary may be removed if the resulting gallery is empty or retains an explicitly designated valid primary under existing nonempty-gallery integrity; do not silently pick a replacement. Image/gallery mutations MUST also satisfy the version-aware conflict semantics in FR-029.

#### Technical specifications — UC-036

- **FR-022**: Administrators MUST be able to view, create and update each variant's technical specifications using the existing supported versioned structure and supported text, numeric, boolean and quantity-with-unit values. Malformed structures/versions/keys, unsupported values and invalid applicable category/type/unit combinations MUST produce meaningful validation feedback; no speculative facts or unit conversions are introduced.
- **FR-023**: Technical facts MUST remain separate from selectable variant options, identity and price, and MUST preserve compatibility with existing product detail, keyword search, same-variant specification filtering and applicable category definitions. Changing one variant's facts MUST NOT overwrite other variants or introduce a new search/filter registry-management feature.
- **FR-024**: Missing optional Draft specifications MUST NOT block initial Draft save. Existing valid empty fact collections and supplied false/zero values MUST retain their established meanings. Publication MUST apply existing relevant specification rules without inventing mandatory nonempty facts for every category.

#### Update semantics, commerce compatibility and errors — all six use cases

- **FR-025**: Information edits MUST distinguish omission, explicit removal and explicit values. Omitted fields preserve existing values; explicit valid values replace only the named information. Explicit removal may clear optional Brand or Description but MUST NOT clear required Product Name or Category. Empty/blank required names and invalid referenced identities MUST be rejected. A valid explicit empty specification fact collection removes facts for the addressed variant only; omission preserves them, false/zero are supplied values, and null/unsupported specification structures are invalid. Image/variant collection deletion is not implied by omission or an empty generic product edit; use the explicit association/disable operations. The transport representation is deferred to planning.
- **FR-026**: Administrative operations MUST preserve historical order recipient/item/SKU/options/price/amount snapshots, successful checkout receipts and relevant catalog/source/media references. Catalog edits or withdrawal MUST NOT reprice/rewrite historical transactions, invalidate a successful receipt replay, cancel an existing order or cause allocation loss.
- **FR-027**: Preserve existing cart pricing/selection/eligibility/stock validation. Unpublish/Archive/Disable MUST retain existing affected cart rows and stored quantity/selection preferences; expose unavailable using the existing safe cart response contract and exclude those rows under existing eligibility/totals/new-checkout behavior. Do not accept unavailable products or variants into new purchases. If sale eligibility later returns, freshly validate current product/variant eligibility, current price and inventory; no restored Draft automatically becomes purchasable. Previously committed orders/receipts/allocations remain unaffected.
- **FR-028**: Every accepted catalog change MUST preserve physical/reserved/available stock invariants and existing order-owned allocation identity/quantity/state. Catalog operations MUST NOT perform UC-037 adjustments, consume/release allocations or block their independently authorized resolution merely because a product/variant was withdrawn. Rejected operations MUST leave related business state unchanged.
- **FR-029**: Use optimistic concurrency semantics for administrative updates: each update MUST be based on an identifiable version of all relevant data it changes or relies on; reject a stale update clearly, with no partial change, and require the Administrator to reload current data/version before retrying. Protection MUST cover product-level changes and associated variant, price, specification and gallery operations, plus relevant taxonomy/brand changes; conflicting associated edits must not evade protection. Do not silently overwrite, merge stale edits or automatically retry them as current. Preserve dependency/uniqueness/primary and checkout/allocation invariants; persistence and HTTP/version representation are Plan decisions.
- **FR-030**: Administrators MUST receive meaningful safe outcomes distinguishing malformed/invalid information, publication-readiness failure, dependency conflicts, missing resources, denied access, competing changes and storage/dependency failures. Feedback MUST identify the actionable field/condition where safe, MUST NOT report success for failed changes, and MUST NOT expose credentials, storage keys or internals. Existing authentication/denial and customer-facing response behavior MUST be preserved; detailed new transport mappings are not designed here.
- **FR-031**: No existing product/variant/category/brand/media identity or source linkage may be regenerated or implicitly merged as a consequence of administration. Required dependency protection and historical preservation apply to Archived products and disabled variants as well as active ones.

- **FR-032**: Administrators MUST restore an Archived product to Draft while preserving identity, existing references, order/receipt history and allocations. A restored Draft is an explicit exception to the initial never-Published Draft meaning. Restore MUST NOT Publish, bypass readiness validation, change stock or undo prior transactions; subsequent Publish requires FR-005.
- **FR-033**: Administrators MUST be able to re-enable a Disabled variant only after applicable basic data/uniqueness and parent-context validation. An Enabled variant on a Published parent must be valid for sale; otherwise reject unchanged. Re-enable MUST NOT change the parent's lifecycle, automatically Publish or bypass current inventory/reservation rules; purchase eligibility still needs an eligible Published parent and available inventory.
- **FR-034**: Category names MUST be unique among categories sharing the same parent, treating all roots as one sibling group. Name equality MUST ignore case and leading/trailing whitespace. Categories under different parents MAY share the same normalized name. Apply the rule to create, rename and reparenting at the destination, with no automatic merge/reassignment/deletion of existing records. Do not add accent folding, internal-whitespace collapsing or aliases to this equality policy.
- **FR-035**: Brand names MUST be unique across brands after ignoring case and leading/trailing whitespace, regardless of current product visibility or Archived references. Apply the rule to create and name updates, together with existing code uniqueness/stability. Input validation and persisted integrity MUST protect this, including competing writes; no automatic alias merge or identity regeneration is authorized.
- **FR-036**: Maintain four unambiguous business states and the lifecycle transition table below. Draft is initial/incomplete or explicitly restored; Published satisfies current readiness; Unpublished was intentionally withdrawn from Published and can be republished only after validation; Archived is logically removed and can be restored only to Draft. Administrative reads retain all states; active preparation of Archived data uses Restore. Repeated commands MUST NOT introduce an extra transition, erase history or bypass current version/eligibility validation. No four-enum schema, version field or HTTP status mechanism is prescribed.

### Approved Business Rules

| Rule | Authoritative feature requirement | Traceability |
| --- | --- | --- |
| BR-01 | Create Draft; explicitly Publish, Unpublish or Archive. Non-Published states are hidden/unpurchasable; Published purchase availability still depends on variant/inventory eligibility. | FR-002–008/036; AC-01–09 |
| BR-02 | Product deletion is logical Archive, retaining historical records/references and excluding public visibility/new purchases. | FR-007/026/031; AC-07 |
| BR-03 | Reject category/brand deletion while any product, including Archived, references it; reject category deletion while dependent subcategories exist. | FR-010/011; AC-22/23/26 |
| BR-04 | Variant prices apply to future purchases; preserve historical snapshots/receipts and existing cart/checkout pricing rules. No advanced price history. | FR-015/026/027; AC-11 |
| BR-05 | UC-037 separately owns stock increases/decreases/reasons/history; catalog must protect existing reservations and balance invariants without adjustment operations. | FR-017/028; AC-12/34 |
| BR-06 | Initial Draft needs only name + valid category; Brand, Description, Variants, Prices, Images and Technical Specifications may be absent without publication validation. | FR-003/004/024; AC-01/02/27/30 |
| BR-07 | Publication requires valid name/category, sellable variant(s), unique valid SKUs, positive whole-VND sale prices, valid image(s), exactly one primary and applicable specifications; no positive-stock requirement. BR-17 defines the variant validation set. | FR-005/015/020/024; AC-03–05/38 |
| BR-08 | Reuse supported uploads; attach/view/select-primary/reorder/remove associations safely without unsafe deletion of referenced objects. | FR-018–021; AC-15–20/39 |
| BR-09 | View/create/update/disable independent variants; disable prevents new purchases while preserving history and allocations; no destructive physical deletion that breaks them. | FR-013–017/026–028; AC-10–14/33/34 |
| BR-10 | Reject changes leaving a Published product invalid. Explicit Unpublish must precede publication-invalidating edits; never automatically Unpublish. | FR-004/008/015/020; AC-09/14/19/43 |
| BR-11 | Restore Archived to Draft, retaining historical transactions/references; publication validates again. Restored Draft is the explicit exception to a never-Published initial Draft. | FR-032/036; AC-36 |
| BR-12 | Permit re-enable only after applicable variant validation; do not change parent lifecycle or bypass inventory/reservation rules. | FR-033; AC-37 |
| BR-13 | Published primary removal needs a designated valid replacement; otherwise reject. Published always retains exactly one valid primary; Draft/Unpublished use relevant existing gallery integrity without publication-only completeness. | FR-020/021; AC-19/39 |
| BR-14 | Draft product may have zero variants, but every created variant needs valid mandatory SKU and selling price. Do not make SKU/price nullable to support an incomplete product. | FR-003/013/015; AC-01/10 |
| BR-15 | Use version-identifiable optimistic update semantics; reject stale updates with explicit conflict and require reload before retry. Cover product and associated variant/price/gallery changes; persistence/HTTP design deferred. | FR-029; NFR-003; AC-32 |
| BR-16 | Allow acyclic reparenting; prohibit self-parenting/descendant cycles. Products retain assigned category identity; ancestor browsing/filter inclusion may change. Preserve references/hierarchy constraints. | FR-009/012; AC-21/24/43 |
| BR-17 | At least one Enabled variant is sellable and every Enabled variant is sale-valid at Publish; Disabled variants retain basic integrity without all publication-specific conditions. Reject removal/invalidation of the last Enabled valid variant while Published. Stock is not publication readiness. | FR-005/008/013/016; AC-03/04/14/38 |
| BR-18 | Retain cart rows after Unpublish/Archive; mark unavailable in existing cart responses and prevent new checkout acceptance. Re-eligibility revalidates current catalog and inventory; orders remain unchanged. Disabled variants retain the same existing unavailable-item safeguards under BR-09. | FR-026–028; AC-06/12/33 |
| BR-19 | Global SKU uniqueness includes Disabled/Archived ownership; product names may repeat; normalized category names are sibling-unique including roots (different-parent duplicates allowed); brand names are globally unique ignoring case/outer whitespace. Validation and persisted integrity act together; identify existing-data conflicts before migration design. Category normalization is the explicit CQ-12 answer. | FR-012/014/034/035; AC-13/21/25/40–42 |
| BR-20 | Draft is initial/incomplete or restored; Published is readiness-valid; Unpublished was previously Published and withdrawn; Archived is logically removed/restorable to Draft with retained history. These four business states do not require four stored enum values. | FR-002/006–008/032/036; AC-06/08/36 |

**Business lifecycle transitions** (not a persistence or HTTP design):

| Operation | Business source | Business result and validation |
| --- | --- | --- |
| Create | No product | Draft; name + existing category, optional publication information absent |
| Publish / Republish | Draft or Unpublished | Published only after complete current publication validation |
| Unpublish | Published | Unpublished; explicit withdrawal, retained carts/history/allocations |
| Archive | Draft, Published or Unpublished | Archived; logical removal, retained references/history/allocations |
| Restore | Archived | Draft; never direct Published restoration, revalidation needed to Publish |
| Information / association update | Draft or Unpublished | Same lifecycle; basic integrity applies without publication-only completeness |
| Information / association update | Published | Same valid Published lifecycle; reject any result violating readiness |
| Variant re-enable | Variant Disabled | Variant Enabled only after applicable validation; parent lifecycle unchanged |

Archived records remain administratively inspectable; Restore is the route back to active product preparation. A variant re-enable does not itself restore or publish its parent, including when that parent is Archived. Commands outside these defined transitions cannot invent another state/transition. A repeated target-state command makes no additional business transition, retains history and remains subject to authorization/current-version rules; detailed response mapping belongs to Plan.

### Non-Functional Requirements

- **NFR-001 — Authorization**: Administrative protections apply to reads, mutations and non-public media access using current authenticated identity and `ROLE_ADMIN`, including session/account invalidation. Neither public catalog access nor ordinary authentication is administrative authorization.
- **NFR-002 — Integrity and history**: Successful changes preserve required references/uniqueness, historical transactions and allocation invariants; rejected changes do not partially alter business state. Storage-side outcomes must not be misrepresented as rolled back simply because a catalog write fails.
- **NFR-003 — Concurrent consistency**: Every administrative update must identify the relevant observed version, reject stale state with a clear conflict and require reload before retry. Cover product and associated variant/price/specification/gallery changes and relevant taxonomy/brand changes; preserve cross-domain checkout/allocation invariants. Version/persistence/HTTP representation and internal coordination belong to Plan; do not silently merge or retry stale changes.
- **NFR-004 — Input and errors**: Required/optional/removal semantics, field validation and readiness/dependency errors must be explicit and actionable without leaking internal or sensitive details. No success-looking response may conceal failure.
- **NFR-005 — Customer compatibility**: Preserve public visibility/privacy, established search/filter semantics and customer cart/checkout/order behavior except for the explicitly authorized extensions of incomplete Drafts and disabled-variant eligibility, and clarified lifecycle policies. No stock availability requirement is added to publication.
- **NFR-006 — Evidence**: Future verification must demonstrate relevant business, authorization, history, storage and concurrent integrity outcomes using the existing constitution's risk-based policy. Specification/document validation is not implementation or runtime verification. No new latency, load, throughput or completion-time target is invented.

### Key Entities *(include if feature involves data)*

- **Administrator**: An existing currently authorized user holding `ROLE_ADMIN`; no new user/role system.
- **Product**: Stable catalog identity, required name/category, optional brand/description and four business states (Draft, Published, Unpublished, Archived). A restored Draft may have prior publication history; relevant historical references remain intact.
- **Product Variant**: Stable product-owned configuration with mandatory basic-valid SKU/price, options/specifications and Enabled/Disabled eligibility. Disabling retains SKU ownership; re-enabling validates current context. Eligibility and parent lifecycle remain distinct from independently owned inventory.
- **Category**: Stable named classification with optional parent and dependent subcategories/products. Normalized names are unique among siblings (including roots); acyclic reparenting preserves direct product assignments and changes ancestor inclusion.
- **Brand**: Existing canonical stable identity/code and globally unique case/outer-whitespace-normalized name, optionally referenced by products. References constrain deletion; normalization never authorizes identity merging.
- **Product Image Association / Completed Image**: Gallery membership/order/primary/metadata is distinct from an eligible completed media object and its temporary viewing access. Removing membership does not imply physical deletion.
- **Variant Technical Specifications**: Existing versioned typed facts for one variant, distinct from selectable options and compatible with applicable search/filter definitions.
- **Historical Transaction / Inventory Reservation**: Existing order/receipt snapshots and attributed allocations that constrain destructive catalog changes; not new aggregates or editable administration resources in this feature.

## Success Criteria *(mandatory)*

### Measurable Outcomes

These are acceptance targets for later implementation, not runtime results achieved by clarification. Each “every” refers to the named 43-scenario acceptance matrix and its applicable valid/invalid cases, including archived/disabled and concurrent cases. No project completion percentage or performance SLA is implied.

- **SC-001**: All six scoped use cases are exercisable by an authorized Administrator with prepared records; no scoped workflow requires frontend implementation, catalog-data import, stock adjustment or a replacement identity/upload/search system.
- **SC-002**: In every AC-01/02 incomplete-Draft case, name + valid category is sufficient, zero missing optional fields are fabricated, and zero such Drafts appear in public discovery or new purchases.
- **SC-003**: Every ready-product publication/republish case succeeds with positive or zero stock when all Enabled variants and shared readiness rules pass; every no-Enabled/all-Disabled/invalid-Enabled/missing-image case rejects publication unchanged. Every proposed update leaving a Published product invalid is rejected until explicit Unpublish; Disabled variants are tested separately for basic integrity versus publication-only conditions.
- **SC-004**: Every tested unpublish/archive/disable/deletion/price-edit case preserves 100% of the pre-existing order/receipt snapshot values and active reservation identities/quantities; zero administrative changes perform stock adjustments or unauthorized allocation resolution.
- **SC-005**: Every referenced category/brand deletion, including Archived dependencies and category children, is rejected without detached references; unreferenced leaf-category/brand deletion succeeds. Every normalized sibling/root collision and global normalized brand-name collision is rejected on create/rename/move as applicable, while different-parent category and product-name duplicates remain allowed.
- **SC-006**: Every accepted primary selection/reorder yields the designated primary and valid requested order. Published primary removals without a valid designated replacement reject unchanged; empty Draft/Unpublished galleries remain allowed under their existing integrity rules. Every association removal preserves other references and referenced objects; zero invalid uploads become eligible images.
- **SC-007**: All supported specification value cases retain their meaning on the addressed variant and applicable discovery; all invalid structure/type/unit cases are rejected with zero unrelated-variant changes or silent conversions.
- **SC-008**: Across every administrative operation, anonymous/non-admin/invalidated-identity cases allow zero unauthorized reads or mutations; authorized successful cases remain accessible without introducing a new role system.
- **SC-009**: In every stale administrative update case, the stale mutation is rejected clearly and current data must be reloaded before retry; zero accepted changes or associated catalog edits are silently overwritten. Every withdrawal-versus-checkout case preserves orders/allocations, retains affected cart rows and creates zero ineligible new purchases. Fresh re-eligibility checks precede renewed purchasing.
- **SC-010**: All 43 acceptance scenarios have a defined business oracle and safe actionable error category for failures, with no false success. Restore always yields Draft; re-enable never changes parent lifecycle; both preserve all tested historical/stock/reservation values. These targets require later implementation verification, not a declaration of working functionality.

## Assumptions

- The Administrator role and existing authentication/session behavior are reused. Provisioning administrators or changing roles is outside scope; prepared authorized accounts may be used for later acceptance.
- Existing catalog identifiers, supported media types/content/count limits, variant option constraints, SKU/code uniqueness and specification/search definitions are compatibility dependencies, not redesigned here.
- “Draft creation” does not create placeholder variants/stock and does not need UC-037. Ordinary new-variant inventory initialization remains the existing zero-balance behavior.
- Optional Brand/Description may be deliberately cleared; absence on an edit is not a clear request. This explicit update contract does not imply automatic dependent-object deletion.
- Restoration, re-enabling, primary-removal, name-uniqueness and stale-update policies are explicitly approved in BR-10–BR-20 and the CQ-12 answer, not assumptions. Existing code immutability/identity rules remain unless explicitly changed. No automatic legacy-data cleanup/merge or bulk state rewrite is authorized.
- This specification describes backend business outcomes only. Existing frontend preview data and historical specs are context, not proof of implemented administrative capabilities or approved new business rules.

## Dependencies and Existing Functionality to Reuse

- Existing current-user authentication, session/account eligibility, `ROLE_ADMIN` checks and safe authorization/error behavior.
- Existing product/category/brand/variant identities and associations, public catalog details, keyword search, category/brand/price/specification filtering and publication-aware access.
- Existing variant-level VND pricing and historical order/checkout-receipt snapshots; customer cart/checkout validation and reservation ownership remain owned by their existing modules.
- Existing supported staged image upload/completion, ownership/content validation, durable media references, private object access, product image attachment and publication-aware galleries. Add missing gallery administration rather than replacing storage.
- Existing versioned technical-specification validation and applicable filter definitions. This feature does not manage the registries themselves.
- Existing inventory balance/reservation initialization and lifecycle integrity. UC-037 remains a separate future feature for increases/decreases, reasons and adjustment history.
- Constitution 1.2.0 and the relevant existing feature specs identified in [compatibility.md](compatibility.md). Current source is the basis for factual compatibility observations; the approved brief governs the requested changes.

## Out of Scope

- UC-030: View Product Sales Overview.
- UC-037: Manage Product Stock, including adjustment actions, reasons and adjustment history; its future agreed behavior is recorded as a dependency only.
- UC-038: Import Product Data.
- UC-044: Process Customer Orders, including new confirmation/shipping/completion workflows.
- UC-047: Manage Store Settings.
- UC-048: Manage Customer Accounts.
- Optional administrative use cases, payment processing, frontend implementation, a new RBAC architecture, advanced price history and unrelated refactoring.
- Rebuilding catalog/search, authentication/session handling, image uploads, carts/checkout/orders/inventory; changing source registries, automatic migration/data repair or physical media cleanup policy as a side effect of administration.
- Controller/service/repository design, endpoint/DTO design, schema/SQL migrations, specific persistence/version/locking implementation, tasks or code. Approved optimistic concurrency semantics are business requirements; their technical realization belongs to a later separately invoked Plan stage.

## Compatibility Conflicts Requiring Explicit Treatment

Current-source evidence and the classified technical dependencies are in [compatibility.md](compatibility.md). All prior business ambiguity is resolved; current code has not been changed by this Clarify stage.

1. **C-01 — Variantless Drafts**: The old aggregate rule requires a variant for every product; BR-06/14 permits a variantless Product Draft but retains non-null valid SKU/price on every created variant. **Resolved business decision; technical dependency**: reconcile lifecycle-sensitive aggregate validation without fake variants or nullable preparation fields.
2. **C-02 — Lifecycle vocabulary**: Existing Draft/Published/Archived does not distinguish Unpublished or the approved restore semantics. **Resolved by BR-11/20; technical dependency**: represent four business states and transitions while retaining identities/history; do not prescribe enum/schema choices here.
3. **C-03 — Price/publication readiness**: Existing finite-whole price checks and customer nonnegative-price rules do not enforce positive sale prices for every Enabled variant on Published products. **Resolved by BR-10/14/17; technical dependency**: separate basic preparation validity from publication/sale validation, inventory eligibility and historical snapshots; identify legacy Published incompatibilities before rollout design.
4. **C-04 — Variant eligibility**: Existing variant/customer paths have no Disable/Enable behavior. **Resolved by BR-09/12/17/18; technical dependency**: propagate current eligibility across catalog consumers/cart/checkout without losing SKU ownership, history or allocations, and without parent lifecycle side effects.
5. **C-05 — Gallery**: Existing gallery permits no images but demands one primary when nonempty; attachment is implemented while general edit operations are missing. **Resolved by BR-08/10/13**: Published requires a valid replacement before primary removal; Draft/Unpublished can be empty and preserve nonempty-gallery integrity. **Technical dependency**: integrate association changes, stale-update protection and existing storage safety.
6. **C-06 — Hierarchy/spec applicability**: Reparenting changes ancestor-based discovery/applicability. **Resolved by BR-16/10**: products retain their direct category; only accepted acyclic/name-valid moves update ancestor inclusion, and Published readiness remains protected. **Technical dependency**: consistent validation/discovery and existing hierarchy references, not fact rewriting.
7. **C-07 — Updates and uniqueness**: No complete version-aware admin contract or normalized sibling-category/global brand-name integrity is implemented. **Resolved by BR-15/19 and CQ-12; technical dependency**: protect relevant product/associated updates, make input and persisted uniqueness agree, assess actual data collisions and root groups before migration design. Existing SKU/option/brand-code identity safeguards remain.
8. **C-08 — Missing admin workflows**: The foundations/attachment do not implement the complete six use cases. **Defined scope, technical implementation dependency**: extend the existing backend and preserve adjacent feature contracts. No business ambiguity authorizes rebuilding excluded modules or removing their safeguards.

## Outstanding Clarification Questions

**None remain at the business-requirement level.** CQ-01–CQ-11 were answered by the supplied BR-10–BR-20 decisions; CQ-12 was the one new question asked and answered in this run. The table retains identifiers for traceability without retaining obsolete alternatives as requirements.

| Question | Status | Approved decision source | Resolved business outcome |
| --- | --- | --- | --- |
| CQ-01 | Resolved | BR-10/17 | Reject invalid Published edits; explicit Unpublish first; retain valid Published state. Legacy data readiness is a separate preflight dependency. |
| CQ-02 | Resolved | BR-11/20 | Restore Archived to Draft without erasing history; revalidate to Publish. Restore provides active preparation; no direct restored sale. |
| CQ-03 | Resolved | BR-12 | Re-enable after applicable validation; no parent lifecycle or inventory/reservation side effects. |
| CQ-04 | Resolved | BR-13/10 | Published primary removal needs a designated valid replacement or is rejected; non-Published empty galleries allowed with existing integrity. |
| CQ-05 | Resolved | BR-15 | Version-identifiable updates; stale rejection, clear conflict and reload before retry; associated variants/prices/gallery covered. |
| CQ-06 | Resolved | BR-16/10 | Acyclic reparenting keeps product assignment and updates ancestor browsing/filtering; preserve Published readiness and references. |
| CQ-07 | Resolved | BR-17/14 | At least one Enabled sellable variant; all Enabled sale-valid; Disabled basic integrity only; stock not a publication condition. |
| CQ-08 | Resolved | BR-18/09 | Retain cart rows, mark unavailable, prohibit new purchase, and revalidate current price/eligibility/inventory on renewed eligibility; historical orders unaffected. |
| CQ-09 | Resolved | BR-19 + CQ-12 | Retained global SKU uniqueness; duplicate product names allowed; normalized sibling-category and global brand names unique. Existing stable unique brand codes retained. |
| CQ-10 | Resolved | BR-20/11 | Four distinct business states with validated Publish/Republish, explicit Unpublish, logical Archive and Restore-to-Draft; representation deferred. |
| CQ-11 | Resolved | BR-14 | Product Draft may have zero variants; created variant SKU/price mandatory and basic-valid, never nullable solely for incomplete Drafts. |
| CQ-12 | Resolved | Explicit user answer in this session | Category sibling/root equality ignores case and outer whitespace for create/rename/move; different-parent duplicates remain allowed. |

Remaining work is technical: data preflight, lifecycle/eligibility representation, validation/uniqueness integrity, optimistic version scope/HTTP contracts and storage-safe integration. These are recorded dependencies for Plan, not unanswered business questions or authorization to run migrations. Actual current target catalog data is unverified; no data-clearing/merge policy is assumed.

**Stage boundary**: Clarify only. Preserve the existing feature/branch and BR-01–BR-20. No Plan, Tasks, Analyze, Implement, application changes, migrations, commit or push is invoked. The specification is ready to be considered by a separately user-invoked `$speckit-plan`; readiness is not implementation or deployment completion.
