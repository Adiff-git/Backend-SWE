# Feature Specification: Development and Demo Data Bootstrap

**Feature Branch**: Existing branch `feature/product-detail-additional-infor` (no branch created)
**Created**: 2026-11-26
**Last Updated**: 2026-11-27
**Status**: Analysis corrections and R-40 volume change approved — specification, plan and tasks synchronized. F-39 restricted educational-use scope is complete; public redistribution verification is not applicable to current scope, reuse permissions remain unverified and implementation/runtime gates remain unexecuted.
**Input**: Reproducible local development/demo bootstrap for Backend-SWE; inspect and interview before finalizing. No implementation or import/reset execution is authorized at this stage.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Normal startup (Priority: P1)

A developer runs `bash scripts/start-dev.sh` to start a usable local application. A migrated, unseeded environment receives the approved dataset; a completed environment retains subsequent demo activity.

**Why this priority**: Provides one repeatable entry point without duplicate imports.
**Independent Test**: Operationally verify first startup and a repeated startup after a demo interaction; no new automated test suite is in scope.
**Acceptance Scenarios**:
1. Given compatible infrastructure and an uninitialized application dataset, when normal startup succeeds, then required verification has passed before normal application traffic can observe the dataset.
2. Given a completed import followed by user activity, when normal startup runs again, then it preserves that activity and does not import again.

### User Story 2 - Fresh startup (Priority: P1)

A developer runs `bash scripts/start-dev.sh --fresh` and provides typed confirmation to recreate an approved local dataset.

**Why this priority**: Enables repeatable demos while bounding deletion.
**Independent Test**: Verify the approved resources are reset and unrelated resources remain intact.
**Acceptance Scenarios**:
1. Given an explicitly approved local application database, when fresh startup is confirmed, then infrastructure starts before reset, the established migrations recreate the schema, and the chosen dataset is imported and verified before backend availability.
2. Given absent confirmation or an unapproved target, when fresh startup is requested, then no reset occurs.

### Edge Cases

- Migrated but unseeded databases are import candidates; Flyway, roles, permissions or one product alone never prove completion.
- Foreign nonempty application data, inconsistent completion metadata, changed dataset/importer identity, unsupported migration history or conflicting objects cause a nonzero safe stop without overwrite.
- A second invocation fails clearly under the exclusive whole-operation lock, including during database recreation.
- Interrupted object upload leaves only tracked, reusable owned objects. Interrupted database import rolls back all business records; a committed completion marker is authoritative even if the client lost its commit response.
- Missing required owned objects are repaired from verified assets/checkpoints without reseeding records. Corrupt recovery sources, conflicting content or lost ownership evidence stop startup.
- Fresh reset preserves unrelated resources and non-owned uploads/checkpoints; their database references may be removed by the approved database reset. No automatic rollback of a destructive fresh reset is promised.
- Expired promotions on later normal starts remain expired; normal startup never moves dates. Unknown options/flags, invalid configuration or absent fresh confirmation fail before destructive changes.

### User Story 3 - Prepare and inspect realistic demo scenarios (Priority: P2)

A maintainer prepares an offline enriched artifact and a demonstrator uses the designated accounts to exercise existing commerce workflows.

**Why this priority**: Realistic linked records make browsing, purchase history and review demos useful while retaining source traceability.
**Independent Test**: Inspect the preparation report and operational summaries, then perform the named journeys on the available frontend. Do not add a test suite or claim unexecuted journeys passed.
**Acceptance Scenarios**:
1. Given the approved source revision, when preparation completes, then all 1,500 products were analyzed, source bytes remain unchanged, extracted facts and synthetic additions are distinguished, every record is validated and all required human review is recorded.
2. Given a successful fresh import, when operational verification runs, then all exact initial counts and linked invariants below hold, including the artifact-frozen R-40 review total R and completed-order total C, with total orders C + 3,000.
3. Given twelve designated accounts, when frontend verification is performed, then results for each named journey are recorded as passed, failed or unexecuted with actual evidence.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Support Linux and WSL2. Exactly two operating modes use the commands above, `compose.dev.yaml`, and the existing `.env.local` contract.
- **FR-002**: Fresh startup requires `RESET backend_demo` and resets only database backend_demo in Compose project backend-swe-demo, retaining the PostgreSQL volume. Storage cleanup is limited to tracked importer-owned objects/checkpoints under the approved ownership policy.
- **FR-003**: Never remove unrelated containers, databases, volumes, or objects, expose credentials, or accidentally target production databases or AWS buckets.
- **FR-004**: Wait for actual database and storage readiness and compatible migrations. Use the temporary unpublished backend migration instance and normal-backend stop/start policy in I-26 to prevent traffic from observing partial initialization.
- **FR-005**: Mark initialization complete only after required integrity verification. Failures exit nonzero with safe recovery instructions and never claim readiness.
- **FR-006**: Preserve source files and manifests; derived generation is separate. Do not publish source products without separate publication approval.
- **FR-007**: Future implementation is limited to Bash, Python, supporting SQL, nonsecret configuration examples and concise documentation. No production Java changes, Java seed helpers/endpoints, new automated test suites, rewritten applied migrations, or disabled constraints/triggers.
- **FR-008**: Report dataset/import identity, per-domain imported/skipped/rejected counts, object results, approved accounts/scenarios, consistency results and initialization state without secrets.
- **FR-009**: Dependent records and counters must agree: stock/reservations/order lifecycle, historical pricing/totals, voucher usage/entitlements/redemptions, eligible owned purchases/reviews, image objects/metadata, default addresses and role uniqueness.
- **FR-010**: Use synthetic personal information. Do not fabricate OAuth identities, active sessions, refresh tokens or OTPs; demo users authenticate through normal application flows.
- **FR-011**: Analyze all 1,500 products offline using AI-assisted preparation informed by names, categories, descriptions, specifications and existing variants. Produce a separate enriched dataset with product-specific appropriate option dimensions and realistic synthetic combinations; unsuitable products may remain single-variant. No runtime AI API dependency is permitted in startup, seeding or import.
- **FR-012**: Distinguish extracted source facts from synthetic additions in the derived dataset, preserve source bytes/manifests and the original variant's approved base price, and validate enriched records against current constraints and business rules. Apply the approved enrichment policies below.
- **FR-013**: Apply approved enrichment policies E-10 through E-18 and approved numeric prices P-31. No generation or implementation is authorized by specification decisions alone.
- **FR-014**: Populate the approved exact commerce quantities, popularity tiers, sale states, stock distribution, eight voucher definitions and named scenarios below. Derive dependent records and aggregates from business facts rather than randomizing counters.
- **FR-015**: Preserve dataset identity across fresh runs while recording the fixed seed, artifact checksum, importer version, compatible migration fingerprints and each import's UTC anchor. Regeneration produces a new reviewed artifact version; startup makes no AI calls.
- **FR-016**: Recognize initialization only through the approved operational completion metadata and verification evidence. Distinguish unseeded, completed, interrupted, foreign, changed and incompatible states using the state table below.
- **FR-017**: Validate local target ownership, runtime prerequisites, nonsecret configuration and compatible migrations before mutation. Hold the exclusive lock for the whole workflow and use only the named Compose project's infrastructure.
- **FR-018**: Preserve completed demo activity on normal startup. Validate identity/compatibility and required surviving image references; do not re-enforce initial row counts by altering later user activity.
- **FR-019**: Honor F-37–F-39: exact voucher parameters, evidence-based operational/frontend verification, and the restricted private non-commercial educational scope with preserved provenance, no public redistribution/commercial use, and permission verification required before either future use.

### Key Entities

- Source dataset identity and revision; derived scenario profile; operational importer metadata with identity/version, schema compatibility, anchor and completion evidence.
- Approved local resource ownership boundary, including database, objects and image checkpoints.
- Catalog: all 1,500 source products in one profile, with local-demo publication approved. Customers: 500 total including 12 designated login accounts. Commerce: R-40 review-driven completed-order capacity C and derived total C + 3,000 orders; exact values freeze in the reviewed artifact.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A repeated normal startup creates zero duplicate dataset records and preserves subsequent demo activity.
- **SC-002**: Fresh startup changes zero resources outside the approved ownership boundary.
- **SC-003**: Every required imported image reference resolves to verified object content before readiness is reported.
- **SC-004**: Every required consistency check passes before completion is marked; any failure reports nonzero exit status.
- **SC-005**: A verified fresh dataset has exactly 1,500 products, 500 customers, 512 addresses, R reviews drawn under the exact R-40 bands, C completed orders plus exactly 900 pending/750 confirmed/750 shipping/400 cancelled/200 failed orders, 300 populated carts, 3,500 wishlist entries and 360 behavior events; variants total 1,500–9,000 according to reviewed suitability, with at most six per product.
- **SC-006**: All 1,500 original variant identities/SKUs/approved base prices are retained; source-byte modifications and invented provenance fields total zero. All enriched records pass validation and every flagged case plus at least 10% of enriched products receives recorded review across categories.
- **SC-007**: Failed operational verification or incomplete import produces no completion claim and a nonzero exit. Retry, missing-object repair, concurrent invocation and reset isolation have recorded operational outcomes before implementation is declared verified.
- **SC-008**: Every named frontend journey has an explicit passed/failed/unexecuted result; only exercised journeys may be described as working. No production-scale timing or throughput target is asserted.

## Assumptions

No unapproved domain, quantity, publication, generation, recovery or import-strategy choice is adopted. Repository evidence establishes current capabilities, not successful runtime validation.

## Approved Decisions

The following records preserve the interview sequence. Later approved replacements govern earlier provisional counts/open questions: C-22–C-24, S-34–S-36 and F-37–F-39 are authoritative for final scope. Historical statements that a choice was open are not current requirements.

2026-11-26, user response `1.C, 2A, 3A`:
- Support Linux and WSL2.
- Use `bash scripts/start-dev.sh` / `bash scripts/start-dev.sh --fresh`, `compose.dev.yaml` and `.env.local`.
- Reset only the named application database while preserving its PostgreSQL volume; clean only workflow-owned objects/checkpoints; require typed confirmation.

2026-11-26, user response `4A, 5A, 6A`:
- One profile includes all 1,500 source products plus a compact set of synthetic scenarios; final counts are supplied by the subsequent extended simulation decisions.
- Explicit approval to publish all source products in this local demo only. Record this separately in a derived approval record; preserve source manifests. This does not establish source-content reuse rights or approve production publication.
- Full commerce scope: categories/brands/products/variants/product images, synthetic customers and addresses, stock, selected/unselected carts, supported order statuses with coherent reservations, direct sales and historical snapshots, vouchers with consistent dependent records, eligible purchase reviews, and wishlists.
- Subsequent decisions 20–24 and F-38 settle media, events, accounts, distributions and named journeys.

Subsequent user decisions: `7A, 9A` and an extended replacement for option 8B:
- Twelve synthetic USER accounts, including named shopping, order-history and review demos; two addresses per account with exactly one default. Shared demo password supplied through uncommitted configuration, compatible password hashes stored, password never printed.
- Mostly available stock with named out-of-stock and insufficient-stock cases. Approved CSV prices map to base prices. Include active, scheduled, expired and disabled sales, plus valid, expired, exhausted and minimum-spend voucher cases. Subsequent S-34–S-35 and F-37 settle numeric distributions and voucher parameters.
- AI-assisted enrichment covers analysis of the entire 1,500-product source dataset, using each product's names, categories, descriptions, specifications and existing variants. Different categories/products may use different appropriate dimensions; no Color × Storage-only restriction applies. Products unsuitable for multiple variants may retain their original single variant.
- Generate a separate enriched dataset and clearly distinguish extracted facts from synthetic additions; preserve source dataset/manifests and original variant approved base prices. Offline preparation only; no runtime AI calls from startup/seeder/importer. No generation or implementation has been authorized at this stage.
- Enriched output must satisfy current database constraints and business rules, including consistent option dimensions per product, unique canonical combinations, valid prices and specifications. Subsequent E-10–E-18 and P-31 decisions approve the enrichment policies.

### Extended Commerce Simulation — Approved Scope

User approved `20A, 21A` and replaced decision 19 with extended option C:

- Historical initial target (order volume superseded by R-40): generate 12,000 synthetic orders across 500 synthetic customers, including the 12 designated demo login accounts. This supersedes the earlier twelve-customer total; only twelve accounts are designated for login. Their previously approved password and two-address/default-address policy remains; C-22 below settles credentials, status and address policy for the additional 488 customers.
- Target 9,000 COMPLETED, 900 PENDING, 750 CONFIRMED, 750 SHIPPING, 400 CANCELLED and 200 FAILED orders, subject to supported lifecycle. These historical counts sum to 12,000; R-40 replaces the fixed completed/total order counts while retaining the other five status counts. Each order still contains 1–4 items.
- Generate approximately 4,500–6,500 eligible purchase reviews, 300 populated carts and 2,500–5,000 wishlist entries. C-24 initially fixes 5,500 reviews (later superseded by R-40), 3,500 wishlist entries and the popularity distribution.
- Spread historical purchases over the preceding twelve months using deterministic, non-uniform popularity with best-selling, popular, regular, low-demand and never-purchased products. Obtain diverse rating averages and review counts from eligible reviews, not independently generated aggregates.
- Reviews must reference eligible purchased items owned by their authors and respect uniqueness. Derive sales counts/statistics, rating/review statistics, inventory reservations, voucher counters and all dependent aggregates from underlying business records.
- Provide meaningful data coverage for existing sorting, filtering, pagination, product ranking, sales statistics and rating queries. Do not introduce domain features or schema changes solely for seeding. Missing application query capabilities must be reported rather than implicitly implemented.
- Seed no avatars or review images initially; retain text reviews and product images. Seed 360 synthetic PRODUCT_VIEW, SEARCH and PRODUCT_CLICK events balanced across the 12 designated accounts with coherent product/category interests. Additional customers do not automatically expand that event scope. No working recommendation engine is implied.
- Simulation is a specification requirement only; no generation, import, reset or implementation has been authorized or performed.

### Extended Simulation Policies — Approved

User approved `22A, 23A, 24A`:

- **C-22 — Background customers**: The additional 488 customers are ACTIVE synthetic USER records with null passwords and no OAuth identities or other seeded login credentials. Give each one address. The 12 designated demo login accounts retain two addresses each and their approved password provisioning. Total initial address target: 512, exactly one default per customer.
- **C-23 — FAILED orders**: Retain the 200 FAILED orders with ACTIVE inventory reservations as required by existing V25 constraints. Include this behavior explicitly in scenario/verification reports. These allocations contribute to reserved totals; no trigger bypass or implicit release is allowed.
- **C-24 — Exact simulation**: The historical 5,500-review count is superseded by R-40; retain 3,500 unique customer/product wishlist entries. Product popularity tiers cover 5% best-selling (75 products), 15% popular (225), 40% regular (600), 30% low-demand (450) and 10% never-purchased (150), totaling all 1,500 products. Allocate completed-purchase item selections approximately 40%/30%/25%/5%/0% across those tiers using a deterministic distribution.
- Spread completed purchase history across the preceding twelve months with seasonality; keep PENDING, CONFIRMED and SHIPPING orders recent. Vary product-specific rating distributions through the actual R-40 eligible review records. Chronological lifecycle consistency and dependent counts must be validated rather than independently randomized.

### Import and Startup Policies — Approved

User approved `25A, 26A, 27A`:

- **I-25 — Direct import**: Use Python utilities with direct SQL and all database constraints/triggers enabled. Validate schema-consistent historical scenarios explicitly rather than relying on application API side effects. Upload image objects through local S3. Do not add lifecycle endpoints or Java helpers.
- **I-26 — Migration and traffic isolation**: Start PostgreSQL, LocalStack, image-storage and Mailpit first. Stop the normal backend during destructive reset/import. Run the existing backend image temporarily without published ports to execute its existing Flyway mechanism; verify successful compatible migrations, stop the temporary instance, import and verify, then start the normal backend. If the temporary instance cannot migrate without incompatible startup side effects, report the blocker rather than modifying production Java or enabling traffic early.
- **I-27 — Marker and transactions**: Explicit exception approved for an importer-owned operational metadata table outside Flyway-managed application tables. It records dataset checksum/identity, importer version, schema compatibility and import state; no Java mapping or new domain feature. Upload and verify objects before business import. Commit business records and completion atomically after required checks; rollback a failed database import. Retry may reuse matching importer-owned objects. Foreign nonempty application data causes a safe stop. Durable attempt/failure recording must not commit partial business records or falsely mark completion.
- The metadata-table exception is limited to importer bookkeeping; it does not authorize other schema changes, rewrites of Flyway migrations, aggregate/cache tables or new domain features.

### Resource and Recovery Policies — Approved

User approved `28A, 29A, 30A`:

- **R-28 — Owned local resources**: Dedicated Compose project `backend-swe-demo`, application database `backend_demo`, and LocalStack bucket `backend-demo-images`, using the existing `.env.local` contract. Reuse this project's resources on later startups and retain its PostgreSQL volume during fresh reset. Fresh confirmation requires exactly `RESET backend_demo`. Unrelated existing projects/resources are not adopted or deleted.
- **R-29 — Asset ownership**: Stable imported keys under `images/demo-bootstrap/`, with exact importer-owned object and checkpoint tracking. Fresh removes only importer-owned assets. Normal startup repairs missing imported objects from verified local assets without reseeding database records; conflicting content causes a safe stop. Preserve assets outside importer ownership, including later user uploads; fresh database reset removes their database references but does not authorize deletion of those objects/checkpoints.
- **R-30 — Lock and compatibility**: Exclusive lock spans the entire startup/reset/import operation, including database recreation, and must not depend solely on the reset database. A concurrent invocation exits clearly. Preserve completed datasets with matching identity/importer version and supported schema. Dataset/importer identity changes or unsupported schema compatibility cause a safe stop with recovery instructions, never automatic overwrite/reset. Exact compatible migration history must be verified before import; a version number alone is insufficient.

### Synthetic Pricing and Source Integrity — Approved

User approved `31A, 32A, 33A`:

- **P-31 — Numeric prices**: Preserve each original approved base price exactly. Cosmetic options have zero adjustment. For synthetic siblings, Storage factor is `1 + 0.15 * log2(new/original)`, RAM `1 + 0.10 * log2(new/original)`, capacity/power `1 + 0.10 * log2(new/original)`, cable length `1 + 0.05 * log2(new/original)`; pack-size factor is `new/original`. Multiply applicable factors, clamp the resulting multiplier to 0.50–1.50 and round the resulting base price to whole VND. Require finite positive comparable quantities for ratios; do not infer missing original quantities. Unsupported dimensions are price-neutral and flagged. Record factors, rule version and final adjustments. These are synthetic demo rules, not claimed market prices. Do not double-count one physical dimension under multiple names; dimensional applicability follows the reviewed product-specific option vocabulary. Negative factors from extreme ratios are invalid candidates, not a means of bypassing bounds.
- **P-32 — Newline/integrity handling**: Validate source manifest checksums against exact Git revision bytes in a separate preparation copy. Verify working-tree differences are only expected newline conversion, rejecting other changes. Preserve source checkout/manifests. Freeze enriched output with strict byte-level checksums. Do not silently normalize arbitrary source content or waive failed checksums.
- **P-33 — Incomplete provenance**: Retain available source identifiers, evidence and hashes in the derived audit manifest. Omit application catalog_source_links rows when mandatory provenance is unavailable; report omissions. Never invent source URLs or payload hashes. This does not authorize dropping catalog records or pretending missing provenance was verified.

### Stock, Promotion and Timestamp Policies — Approved

2026-11-27, user approved `34A, 35A, 36A`:

- **S-34 — Stock and carts**: Derive ACTIVE reservations first, then assign approximately 90% of variants 20–200 available units, 8% 1–5 available units and 2% zero available units. Stored stock equals reserved plus available, never independently randomized. Populate exactly 300 carts with 2–6 distinct variants each and selected/unselected items, including named successful-checkout, insufficient-stock and out-of-stock scenarios. Rounded variant-count allocations must sum to the actual enriched variant count.
- **S-35 — Sales**: Of 1,500 products, 60% (900) have no sale, 20% (300) active, 10% (150) scheduled, 5% (75) expired and 5% (75) disabled. Choose discounts deterministically from 5%, 10%, 15%, 20% and 30%. Historical order pricing uses the sale configuration effective at its pricing instant and immutable snapshots, not today's effective catalog price.
- **S-35 — Voucher coverage**: Eight scenarios: valid fixed amount, valid percentage, minimum spend unmet, expired, scheduled, disabled, globally exhausted and customer-limit exhausted. Establish exhausted usage from actual historical redemptions; F-37 supplies the approved amounts, limits and grants. No independently assigned counters.
- **S-36 — Reproducible time**: Fixed generation seed 20261126. Freeze identities, choices and relative timestamp offsets in the reviewed artifact. Each fresh import records one UTC anchor and resolves timestamps consistently. Completed history spans the previous twelve calendar months; PENDING, CONFIRMED and SHIPPING orders fall within the preceding seven days. Active promotions remain usable for thirty days after anchor. Normal startup never rebases persisted timestamps, including after promotions expire. Exact anchor is import-instance metadata, distinct from frozen dataset identity.

## Approved Enrichment Policies

User approved `10A, 11A, 12A, 13A, 14A, 15A, 16A, 17A, 18A`:

- **E-10 — Counts**: Analyze all 1,500 products. Retain one variant where unsuitable; otherwise produce 2–6 total variants per product, including the original, with an overall upper bound of 9,000. No forced enrichment percentage or full Cartesian expansion.
- **E-11 — Options and original mapping**: Use a versioned category-aware vocabulary with canonical names and units (appropriate examples include Color, RAM, Storage, Cable Length, Capacity and Pack Size). Preserve original variant ID, SKU and approved base price. Establish its option values from source evidence; if required values cannot be established, retain that product as single-variant and flag it. All variants of an enriched product share one dimension set and have unique canonical combinations.
- **E-12 — Prices**: Preserve original approved base prices exactly. Generate synthetic prices using deterministic category/dimension rules: cosmetic changes have the same price; capacity/size/pack changes use bounded adjustments rounded to whole VND. Record each applied rule and adjustment. Numeric rules were subsequently approved in P-31.
- **E-13 — Names**: Normalize whitespace and remove only confidently identified option-specific phrases when enrichment would make the title misleading. Preserve brand, model and compatibility identifiers, retain the original title and record changes. Flag ambiguous titles.
- **E-14 — Specifications**: Copy invariant specifications and update option-dependent fields for each synthetic variant while preserving typed values, units and provenance. Validate consistency across title, options, description and specifications. Unresolved products remain single-variant pending review; do not retain contradictory synthetic siblings.
- **E-15 — Images**: Retain each existing product gallery for its sibling variants and record that it illustrates the source configuration. No fabricated variant-specific image association or image recoloring. Product-level gallery behavior is retained.
- **E-16 — Deduplication**: Preserve all 1,500 products and identities. Report suspected duplicate/model-family groups for review; never merge automatically.
- **E-17 — Validation/review**: Validate every record for identifiers, canonical option dimensions/uniqueness, prices, specification shape, relationships and asset references. Review every flagged case and at least 10% of enriched products across categories. Unresolved errors block artifact approval. Use operational validation rather than new automated test classes/suites.
- **E-18 — Reproducibility**: Freeze approved output as a versioned, checksummed artifact, recording source revision, prompts, model identifier, rules, random seed, decisions and provenance. Startup imports the frozen artifact without AI. Regeneration produces a new reviewable version; AI reruns are not assumed byte-identical.

## Verified Discovery

- Dataset: `data/Catalog-Data`, clean nested checkout at `c5dcfaabdf61b244783d46a1403107e19cd2b02c` when inspected. Parsed 1,500 products (all DRAFT), 1,500 variants (all empty proposed options), 1,500 specification rows, 1,500 image manifest entries. All referenced image files exist and match their recorded SHA-256.
- Approval CSV hash matches raw bytes. Five other catalog-manifest file hashes fail on raw working-tree bytes and match after CRLF-to-LF normalization. This is evidence of a checkout newline incompatibility, not approval to bypass strict checks or edit source files.
- Manifest identity `amazon-mvp1500-seed-1`; required history says V1–V10, while repository migration files reach V30. Applied runtime history is unverified.
- CSV publication approval is PENDING_APPROVAL; raw records retain BLOCKED/PENDING_APPROVAL fields. The source-mapping section defines approved derived-overlay precedence. No license/terms file was found at the dataset root; reuse restrictions remain unverified.
- README refers to absent `tools/catalog-import/amazon_import.py`. Package validator exists and requires Pillow; its success has not been claimed.
- Compose services: postgres, localstack, image-storage, mailpit, backend. Named volumes: postgres-data and product-checkpoints (actual project-prefixed names unresolved). LocalStack persistence is disabled; checkpoint restoration code exists. Backend runs Flyway and validates ORM schema.
- V25 creates zero-stock balances for variants and enforces reservation/balance/order consistency. V26 enforces voucher history consistency. V28 adds base and effective sale pricing; V29 enforces canonical option identity; V30 validates immutable direct-sale snapshots.
- Existing controller routes cover customer login, registration/verification, addresses, carts/selection, checkout, cancellation, wishlists, reviews and behavior events. The inspected customer order controller exposes listing/detail/cancellation, not arbitrary lifecycle advancement. SQL order statuses are PENDING, CONFIRMED, SHIPPING, COMPLETED, CANCELLED and FAILED.
- V11 source-link records require nonnull source URL and hashes, whereas inspected raw source records contain missing provenance fields. P-33 approves omission/reporting of incomplete links; provenance must not be fabricated.
- Password authentication uses BCrypt with cost 10. Migrations seed the USER role; no additional role or administrative demo capability is assumed.
- V29 requires a consistent option-dimension set within each product and unique canonical combinations. The existing empty-option variant cannot coexist unchanged with additional nonempty-option variants of the same product.
- V28 maintains selling_price as a base_price mirror; effective price is calculated separately, with discount rounded down in whole currency units. Decision 9A/P-31 maps approved CSV prices to base_price.

### Extended Simulation Compatibility Findings

- Current catalog sort enum exposes relevance, price ascending/descending and name ascending/descending; no sales-count or rating sort is exposed there. Rating summary queries derive count, average and histogram from product_reviews. Sales/ranking coverage can be verified operationally from underlying records; new frontend/API sorting or statistics capabilities are not included.
- V25 requires inventory-managed CANCELLED orders to have RELEASED reservations, COMPLETED orders CONSUMED reservations, and other statuses, including FAILED, ACTIVE reservations. C-23 explicitly approves the 200 FAILED orders under this existing behavior; do not silently release those reservations or bypass checks.
- V13 allows null passwords, and DomainUserDetailsService rejects password authentication when the encoded password is null. The active background-customer policy without seeded credentials or OAuth identities is approved in C-22.
- Review creation requires an ACTIVE author and an owned COMPLETED purchase; review uniqueness is per order item. Background customers and review timestamps must remain consistent with eligibility.

## Final Voucher and Verification Decisions

2026-11-27, user approved `37A, 38A, 39A`.

### F-37 — Voucher definitions and grants

All amounts are whole VND; the percentage cap applies only to the percentage voucher. Default limits are 5,000 global and two uses/customer, except explicit overrides. Each designated account has one AVAILABLE entitlement per definition; separate CONSUMED entitlements support actual historical redemptions. An AVAILABLE entitlement does not imply that time, enabled state, minimum spend or quota permits redemption.

| Scenario | Discount | Minimum subtotal | Required state |
|---|---|---|---|
| Valid fixed | 100,000 | 500,000 | Active until anchor +30 days |
| Valid percentage | 10%, cap 300,000 | 500,000 | Active until anchor +30 days |
| Minimum spend unmet | 200,000 | 5,000,000 | Named selected cart subtotal below threshold |
| Expired | 100,000 | 500,000 | Expired before anchor |
| Scheduled | 100,000 | 500,000 | Starts anchor +7 days |
| Disabled | 100,000 | 500,000 | Disabled despite valid dates |
| Globally exhausted | 100,000 | 500,000 | Global limit 20; exactly 20 valid historical redemptions |
| Customer exhausted | 100,000 | 500,000 | Customer limit one; named account already redeemed once |

Use eligible underlying orders for redemptions; grants precede consumption and redemption equals original placement time. Current order discount, redemption snapshots and all counters must agree. Do not synthesize invalid historical redemptions merely to satisfy a scenario label. Voucher application uses existing calculation/rounding semantics on merchandise after direct-sale reductions; no new discount rules.

### F-38 — Readiness and evidence

Require exact initial quantities, asset/checkpoint checksum verification, zero consistency violations and meaningful popularity/rating coverage. Operationally verify repeat startup, interrupted upload/import retry, missing-object repair, concurrent invocation and reset isolation. These are import checks, not a new test-class/suite project.

| Named journey | Required prepared data and evidence |
|---|---|
| Login/profile/address | Twelve documented usernames; configured password never printed; normal login, two addresses and one default on each designated account |
| Browse/search/filter/page | All nine source leaf categories, mapped brands/specifications, varied prices and enough results for multiple pages; exercise supported relevance/price/name sorts |
| Variant selection | Reviewed product-specific options and valid distinct combinations; report if source evidence prevents usable enrichment instead of inventing original option values |
| Checkout success | Available selected items, unselected retained items, owned default address and eligible voucher entitlement |
| Stock failure | Selected quantity above available stock and an out-of-stock selection; no intentionally invalid stored stock |
| Voucher states | Eight definitions above and designated carts/accounts showing applicable rejection or success |
| Order history/cancellation | All six approved statuses, coherent historical snapshots and a cancellable owned order under current rules |
| Reviews/wishlist | Existing eligible review history plus at least one unreviewed completed item on a designated account; owned wishlists and editable owned text reviews |
| Behavior history | 360 supported events across twelve accounts; no fabricated recommendation-engine result |
| Sales/rating analysis | Operational queries derive completed sales and rating/count distributions, including zero-sale/zero-review products; no new sales/rating frontend sort or statistics endpoint |

Import completion is established by committed business integrity and required object verification while normal backend traffic remains stopped. Frontend journey verification occurs after backend readiness and is separately reported; it cannot be claimed by a pre-start marker. A post-start verification failure exits nonzero and reports the actual committed state rather than claiming rollback or performing a silent reset.

### F-39 — Restricted educational use; scope decision complete

The project is a private, non-commercial university course project used solely for education and submission to the instructor. Access to the project, source catalog/images and derived artifacts is restricted to team members and the instructor. Private instructor submission is within this recorded project scope; it is not approval for public redistribution.

Preserve original source identifiers, links where genuinely available, manifests, checksums, attribution/provenance and the distinction between extracted facts and synthetic additions. Do not remove or fabricate provenance to facilitate submission. Public redistribution (including public repositories, public artifact/image links and public demos exposing the dataset) and commercial use are prohibited under the current project scope.

The educational-use scope decision is COMPLETE. Public redistribution permission verification is NOT APPLICABLE TO CURRENT SCOPE because public redistribution is excluded. Copyright clearance, source reuse rights and reuse permissions remain UNVERIFIED; private, non-commercial or educational use is not recorded as proof of clearance or permission. Do not claim that an applicable license or authorization has been verified.

Before any future public redistribution or commercial use, obtain and verify applicable permissions/license evidence for source content and images, record conditions and provenance, and explicitly reassess the project scope. Current approval does not pre-authorize such future uses. This future gate does not block work within the recorded restricted educational scope; runtime/artifact verification remains required separately.

## Source-to-Schema Mapping Requirements

| Input or derived record | Application target | Required mapping/verification |
|---|---|---|
| category_mapping.json | categories | Approved stable UUIDs and parent-before-child hierarchy; ten category records including root; reconcile approvals that leave category UUID blank |
| Product records and approved enrichment | products, brands | Stable source product UUID; mapped approved category and canonical brand proposal with traceability; local-only publication override; preserve original title/description evidence |
| Approved CSV and original variant | product_variants | Retain ID/SKU; approved selling-price CSV becomes base_price; selling_price remains trigger-managed base mirror; empty options stay only for single-variant products |
| Enriched sibling variants | product_variants | Deterministic IDs/unique SKUs from product identity plus canonical option map; consistent dimension set; P-31 price rule; specifications use schema_version 1 with typed values and applicable units |
| Product/specification evidence | technical_specifications | Carry extracted facts separately from synthetic per-variant changes in audit records; resolve name/options/description/spec conflicts or retain source single-variant fallback |
| Source link evidence | catalog_source_links | Populate only complete genuine mandatory provenance; omit/report incomplete links per P-33; keep original available identifiers in audit artifact |
| image_manifest and local bytes | product_images and local S3/checkpoints | Stable owned key; verified actual content type, byte size, dimensions, checksum and product mapping; valid primary/display-order metadata; do not fabricate pending uploads or variant-image links |
| Synthetic people and addresses | users, user_roles, addresses | 500 ACTIVE USER customers; twelve BCrypt-compatible configured-password accounts and 488 null-password accounts; synthetic personal details; 512 addresses/one default each; join dates precede owned activity |
| Synthetic commerce graph | orders, order_items, inventory_balances, inventory_reservations | C + 3,000 orders with artifact-frozen C completed orders, 1–4 items each, valid snapshots/timestamps/totals and exact attributed allocations; all generated orders use current managed-inventory semantics |
| Voucher definitions/grants/history | vouchers, voucher_customer_usages, voucher_entitlements, voucher_redemptions | Eight definitions; grants and actual redemptions determine consumption/counters; original order snapshots/totals agree; no consumed entitlement selected by a cart |
| Current selections | carts, cart_items | Exactly 300 populated customer carts, 2–6 distinct variants each; valid quantities/selection state and owned available entitlement references |
| Eligible completed items | product_reviews | Exactly artifact-frozen R owned completed-purchase reviews under R-40, one per eligible order item at most; 1–5 ratings, nonblank/valid optional comments and chronological timestamps; no review images |
| Synthetic interests | wishlist_items, user_behavior_events | 3,500 unique customer/product wishlist entries and 360 supported coherent events; stable event identity and valid ownership; verify exact field constraints during planning |

Approved CSV/category mappings, local publication decision and reviewed enrichment overlay take precedence over stale proposal/BLOCKED fields only for the fields explicitly approved. They do not waive checksum validation, missing provenance, schema compatibility or source permission evidence. Freeze that precedence and each field's extracted/synthetic origin in the derived manifest. Source V1–V10 history is historical metadata; the importer must declare and verify an exact current compatible migration set/checksums, initially researched against repository V1–V30, without editing old manifests or applied migrations.

Keep all 1,500 products; never-purchased products have zero completed sales and no purchase reviews. Derive sales from COMPLETED item quantities, and ratings from review records. Guarantee nonzero purchase coverage across purchasing tiers where their approved weights permit it and diverse review-count/rating groups; no fixed per-product aggregate values are independently seeded. The exact deterministic item-count, within-tier, seasonality and rating sampling algorithms are planning choices constrained by the approved bounds and reviewable distribution report.

## Startup, Initialization and Recovery Contract

### Prerequisites and configuration

- Linux or WSL2 Bash, working Docker daemon and Compose v2, readable `.env.local`, the pinned source revision and approved enriched artifact. Git is required for source-revision verification/preparation; Python and required image/database/S3 libraries must be available in a documented reproducible execution environment. Dependency versions/install placement are planning decisions; no production Java dependency changes are authorized.
- Use existing LOCAL_DB_*, LOCAL_STORAGE_*, JWT and Compose configuration contracts with the approved dedicated resource values. Keep secrets uncommitted, never print passwords/JWT/storage credentials, and validate presence without exposing them. Additional importer configuration must be nonsecret or externally supplied and documented.
- Determine targets through the named Compose project's owned postgres/localstack services and labels, not an arbitrary DB_URL, AWS profile or cloud endpoint. Reject mismatched database/bucket/project, external database/storage targets, and unsafe overrides before reset or upload. Verify effective backend environment also points to these same owned services. Do not print resolved secret-bearing Compose output.
- Readiness checks have bounded, documented timeouts, actionable diagnostics and nonzero failure; fixed sleep alone is insufficient. Confirm database connections, signed S3 access/bucket, successful exact Flyway history, image gateway, Mailpit and final backend readiness separately.
- Preserve the existing Compose structure; minimal orchestration configuration necessary to run an unpublished temporary migration instance is within approved development-orchestration scope. No Java changes or new application endpoints.

### Ordered operation

1. Validate command/configuration, local ownership and required source/artifact integrity; acquire the exclusive whole-operation lock. Fresh requires exact confirmation before destructive work. Only normal and --fresh modes are exposed; no additional user-facing import/reset modes.
2. Stop normal backend traffic before initialization/reset/migration/repair work. Start owned postgres, localstack, image-storage and mailpit and wait for actual readiness. Existing non-owned project services remain untouched.
3. Inspect existing operational metadata and business state. In fresh mode, capture/verify owned asset identities before resetting only backend_demo, retain its PostgreSQL volume and delete only owned assets/checkpoints; never wildcard-delete an unverified resource.
4. Run the existing backend image temporarily without published ports for Flyway, confirm migration success and compatible history, then stop the temporary instance before SQL import. Validate preexisting initialized metadata compatibility before applying an unexpected migration; no silent incompatible upgrade.
5. Ensure bucket exists through established local initialization. Restore/check required checkpoints, upload valid owned assets with stable keys and verify content. Existing same-content objects may be skipped; conflicting content or untrusted checkpoints stop. Save valid checkpoints using the existing format so LocalStack recreation does not lose imported assets.
6. For an unseeded database, record operational attempt identity, generate/resolve the reviewed scenario graph at one UTC anchor, then import all business records in one transaction with enabled constraints. Explicitly verify deferred constraints and consistency inside the transaction before committing completion metadata with the business data. Attempt state may persist separately, but partial business records must not.
7. For a completed compatible database, preserve user activity, repair only required missing owned assets using trusted evidence, and skip import. Do not reinterpret ordinary later row-count changes as an incomplete seed.
8. Start normal backend only after required committed dataset/object checks succeed. Confirm backend readiness and print nonsecret connection information, usernames/scenario locations, counts/results, dataset/import identity and initialization state. Preserve exact failure/commit status and safe recovery instructions; release lock on success/failure.

| Observed state | Normal startup behavior | Recovery |
|---|---|---|
| Migrated, only migration-owned baseline rows; no completion | Import approved artifact after compatibility checks | Retry normal after correcting prerequisite failure |
| Completed matching identity/version and compatible history | Preserve data/activity; skip import; verify required assets | Normal start or explicit fresh reset when intentionally desired |
| Attempt interrupted before business commit | Business transaction absent/rolled back; reuse verified owned objects and retry | If evidence shows unexplained partial/foreign rows, stop instead |
| Commit response lost | Read authoritative committed metadata and business state; do not duplicate import | Treat verified completion as completed |
| Nonempty application data without trustworthy completion | Stop; never adopt, erase or silently seed it | Explain findings; only separately confirmed --fresh resets approved database |
| Complete PostgreSQL but missing required owned objects | Restore from trusted source/checkpoints; verify before backend traffic | Missing/corrupt source or conflicting object stops without database reseed |
| Changed artifact/importer or unsupported schema | Stop before automatic upgrade/reseed | Restore matching artifact/version or intentionally use confirmed fresh startup with a compatible implementation |
| Concurrent invocation | Exit clearly without competing mutation | Retry after active owner finishes; stale-lock handling must establish prior process is gone |
| Fresh reset fails midway | No ready claim; normal backend remains stopped | Report what was deleted/committed; rerun confirmed fresh once cause is fixed; no backup/undo promise |

Track importer asset ownership before irreversible database reset, because operational metadata in the reset database cannot be its only recovery copy. Never infer ownership solely from a key prefix, and never remove non-owned checkpoints. Marker compatibility must include artifact digest, importer version, source identity, migration checksums and verification results; existence of a table, role or product is not a completion marker. Whole-operation locking must coordinate the same resource set even when invoked from different checkouts.

## Verification and Delivery Requirements

At preparation: verify source Git bytes/manifests and all image bytes/decode/metadata; validate every enriched record, canonical options, price factors/specifications, identity preservation and provenance; report fallback/suspected-duplicate groups and record required human reviews. Freeze the approved artifact and relative-time recipe separately from source files. No runtime AI dependency.

At fresh import: verify all exact counts/statuses/ownership, default addresses and roles, order item/snapshot/totals and currency rounding, inventory attribution/states/reserved sums, voucher definition/grant/redemption/counter equations, eligible review authors/items/times/uniqueness, no forbidden authentication secrets, wishlist/event identity and object/checkpoint resolution. Check tier membership/zero-purchase coverage and derived sales/rating distributions. Database constraints remain enabled and are forced through validation before completion commit. No performance or successful-run claim without actual evidence.

On repeated startup: verify metadata/version/schema compatibility and required surviving asset references, then preserve user-created activity and timestamps. Recheck initial exact seed counts only on fresh-import verification; do not reset an active demo to make a count check pass.

Operational evidence must identify the command/mode, source/artifact/importer revisions, import anchor, relevant environment, counts, uploaded/skipped/rejected assets, per-domain outcomes, consistency results, completion state, recovery steps and unexecuted checks, without secrets. Operational interruption/retry/concurrency/reset checks are required future implementation verification, not claimed as executed during specification.

Document the two commands, Linux/WSL prerequisites, `.env.local` setup, ownership/destructive boundaries, password provisioning, source/enriched separation, normal/fresh behavior, named accounts/scenarios, nonsecret URLs, compatibility failures and recovery. Frontend URLs must come from actual available frontend configuration; this repository's backend Compose does not establish a running frontend.

## R-40 — Exact Review Distributions and Approved Derived Volumes

The user approved all five analysis recommendations and replaced vague review diversity with the following exact distributions. The user subsequently confirmed the volume change. These distributions supersede qualitative coverage and the previous fixed 5,500-review/9,000-completed/12,000-total targets. Preserve exact distributions and uniform draws; derive review total R and increase completed-order capacity C as necessary.

### Review counts — all 1,500 products

| Persisted review count | Exact proportion | Exact products |
|---|---|---|
| 0 | 20% | 300 |
| 1–10 inclusive | 20% | 300 |
| 11–50 inclusive | 35% | 525 |
| 51–100 inclusive | 20% | 300 |
| 101–1,000 inclusive | 5% | 75 |

Assign product groups with the deterministic seed and sample each product's count discrete-uniformly within its inclusive group's range. Respect purchase eligibility: the 150 never-purchased products must be among the 300 zero-review products; choose the other zero-review products deterministically from the purchasing population. Do not modify sampled counts to fit a review budget or silently truncate them. All positive counts require distinct eligible owned completed order items.

### Average ratings — only the 1,200 products with reviews

| Calculated average interval | Exact proportion | Exact products |
|---|---|---|
| [1.0, 2.0] | 5% | 60 |
| (2.0, 3.0] | 10% | 120 |
| (3.0, 4.0] | 25% | 300 |
| (4.0, 4.5] | 35% | 420 |
| (4.5, 5.0] | 25% | 300 |

Randomize assignments reproducibly without intentional correlation between count and rating groups, except feasibility constraints. For example, a one-review product cannot achieve (4.0, 4.5] from an integer 1–5 rating. Use a seeded feasible assignment retaining exact group cardinalities; do not invent fractional review ratings or independently persist average/count aggregates.

Generate actual integer ratings on actual eligible reviews so each resulting calculated average belongs to the assigned interval. Existing repository aggregation is AVG(rating::NUMERIC), and ReviewStatisticsServiceImpl rounds the nonempty average to two decimals with HALF_UP; use that same calculated two-decimal value for interval classification and report raw SUM/COUNT/AVG for traceability. Zero-review products have no average and are excluded from the average-rating denominator.

Verify COUNT and calculated AVG from persisted product_reviews, including zero-review products via a complete product population join. Require exact group membership counts with no tolerance. No separate minimum coverage thresholds apply to these exact review/rating distributions. Existing approximate popularity/stock policies are separate and are not silently converted to exact distributions.

### Approved volume change and feasibility

The count bands imply a minimum of `300*1 + 525*11 + 300*51 + 75*101 = 28,950` reviews, maximum 134,250, and expected total 81,600 under the required uniform draws. The previous 5,500-review target is impossible. Existing 9,000 completed orders with at most four items and at most one review/item provide at most 36,000 eligible items; uniform draws do not guarantee this capacity. Maintaining approved popularity tiers/selection weights may require additional eligible orders beyond the review total divided by four.

User confirmation: "I confirm that volume change." Set R to the sum of the deterministically sampled per-product review counts, never to the expectation 81,600 or a separately chosen budget. Derive sufficient completed orders C (at least the original 9,000), each with 1–4 distinct-variant items, to supply all required eligible item identities and preserve the approved popularity proportions and named demo cases. Keep exactly 900 PENDING, 750 CONFIRMED, 750 SHIPPING, 400 CANCELLED and 200 FAILED: total orders are C + 3,000. Only the completed/total order counts and total reviews change; customer/catalog/cart/wishlist/event counts and all business constraints remain.

Review and freeze R, C, completed-item count, total item/order counts and status breakdown in the artifact manifest before import. Every reviewed product requires at least its sampled count in distinct completed purchased items; quantity greater than one does not create extra eligible review identities. Include additional unreviewed completed items for the named review journey. Never-purchased products remain excluded from all orders and therefore occupy 150 of the 300 zero-review positions. Expand purchase capacity as needed to maintain approximately 40/30/25/5/0 tier selections; do not reorder count/rating group assignments to force a popularity correlation, clip/resample counts or relax the review distributions to reduce volume. No fixed performance or revised order-count estimate is asserted before the deterministic graph is prepared.

### Other approved analysis corrections

- C1: Helper image requires multi-stage build, unconditional non-root runtime, dedicated narrow build context and applicable .dockerignore exclusions; checkpoint permissions do not authorize a root-runtime fallback.
- I1: Stop normal backend traffic before infrastructure startup/restoration or other initialization effects.
- I2: Read-only preflight and exact fresh confirmation precede Docker lock-container creation; lock then covers all subsequent initialization/reset/import mutations.
- U2: Before V15 accept pg_trgm not yet installed if package 1.6 is available and CREATE privilege exists; reject incompatible installed version/schema, and require installed 1.6/public after migration.

## Open Evidence and Planning Boundaries

- **F-39 scope decision complete**: private non-commercial educational use and instructor submission only, restricted to team/instructor. Public redistribution verification is not applicable to current scope; copyright clearance/reuse permissions remain unverified. Verify permissions before any future public redistribution or commercial use. R-40 volume reconciliation is approved; no scope question remains open.
- The referenced historical discussion was not present as a separate attachment in the supplied attachment directory; no claims from it were adopted as facts. Current repository inspection and recorded user decisions govern.
- Runtime migration success, temporary-backend startup side effects, performance at the derived R-40 order volume, actual imports and frontend journeys remain unverified. Planning must investigate these limitations without adding Java/domain/schema changes beyond the approved operational metadata exception. Report blockers rather than weaken constraints.
- Category-aware vocabulary, exact model/prompt choice and dataset output location, deterministic sampling details, tooling/dependency versions, lock primitive, importer metadata layout and compatible migration fingerprint are bounded planning decisions. Any choice that changes approved business scope, reset resources or invariants requires renewed clarification.
- Specification, plan and tasks now incorporate the approved R-40 volume change. The remaining gates are concrete dependency approval, artifact review, actual runtime compatibility/verification. F-39 prohibits public redistribution/commercial use; permission verification is a future-use gate, not an incomplete current educational-scope decision. This document-editing approval does not authorize implementation, generation, import/reset execution, commits or pushes.
