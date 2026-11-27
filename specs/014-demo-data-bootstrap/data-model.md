# Data Model and Import Graph

This is a tooling data design, not new application schema. All existing tables/functions remain Flyway-managed and unchanged. Only the approved operational `bootstrap_import_runs` table is introduced by importer-owned versioned SQL.

## Source and immutable derived entities

| Entity | Identity and principal fields | Validation/relationships |
|---|---|---|
| Source package | Git revision c5dcfaabdf61b244783d46a1403107e19cd2b02c; source manifest version amazon-mvp1500-seed-1; tracked path/blob/file hashes | Exact revision bytes; working checkout differs only by approved newline conversion; no source writes |
| Product evidence | source_identity/ASIN, original product UUID/title/category/brand/specification evidence and approval references | All 1,500 retained; raw evidence immutable; local publication override separate |
| Variant evidence | Original UUID/SKU/approved CSV base price; source option/specification facts | Original identity/price retained; original option values must be evidence-based before enrichment |
| Enriched product | Same product UUID, normalized title, category/brand mapping, gallery, option vocabulary, enrichment disposition | One variant fallback or 2–6 consistent-dimension variants; suspected duplicate groups reported, not merged |
| Enriched variant | Stable UUID, source/synthetic flag, unique deterministic SKU, option map, typed specs, base price, price factors | SQL canonical identity unique within product; exact positive comparable dimensions for ratios; finite nonnegative whole VND |
| Field provenance | Entity/field, extracted or synthetic origin, source path/record/evidence, rule/model/prompt and reviewer reference | Synthetic values must not masquerade as manufacturer claims; unavailable provenance stays absent |
| Asset | Source path, product UUID, durable key, SHA-256, type, bytes, dimensions, display order/primary | Actual decode/checksum; key images/demo-bootstrap/<dataset>/<product>/<digest>.webp; product-level gallery only |
| Scenario recipe | Recipe/rule versions, seed 20261126, stable customer/order/item logical IDs, tier/price/rating choices, relative dates | Fixed artifact choices; refer to current product/variant IDs; no passwords/session tokens embedded |
| Review record | Flag/reason, fallback/disposition, reviewer/reference, reviewed catalog/rule payload digest excluding review records/manifest | Review every flagged case and >=10% enriched products sampled across categories; unresolved errors block release; detached final approval binds full artifact digest |

SKU format uses original SKU plus deterministic short ASCII suffix for synthetic siblings, with full-case-insensitive uniqueness validation. UUIDs derive from versioned namespace and product plus canonical options; original UUIDs are never recalculated. Database-generated BIGINTs use explicit preallocated identity maps within the fresh empty target; sequence next values must exceed all inserted IDs.

## Operational completion entity

`bootstrap_import_runs` is one importer-owned singleton row per target database (`id = 1`), not an application entity. Proposed fields:

| Field | Type/meaning |
|---|---|
| id | BIGINT primary key, fixed 1 |
| metadata_version | Integer version for operational table contract |
| attempt_id | UUID; changes on explicitly new fresh attempt, retained for resumed interrupted attempt |
| dataset_id, dataset_version | Nonblank artifact identity |
| artifact_sha256, source_revision | Exact artifact/source identity |
| importer_version, importer_digest | Supported importer contract and implementation identity |
| migration_fingerprint | JSONB of ordered successful histories, file SHA-256 and Flyway checksums; not just highest version |
| anchor_at | TIMESTAMPTZ; recorded once for relative-time resolution |
| state | PREPARING, IMPORTING, COMPLETED or FAILED |
| started_at, completed_at | TIMESTAMPTZ; completion nonnull only with COMPLETED |
| resource_identity | JSONB project/database/bucket/daemon identity, no credentials |
| verification_summary | JSONB counts/checks/artifact and ownership-ledger digest; no auth secrets |
| error_code | Nullable sanitized stable failure code; raw stack/environment secret dumps forbidden |

Table creation/version validation occurs outside the business transaction after schema compatibility. Do not reinterpret an existing incompatible operational table or overwrite a completed row. Record PREPARING and IMPORTING in short metadata transactions; business transaction updates the same row to COMPLETED with all business data. On failure rollback first, reread authoritative state, then mark FAILED only if completion was not committed. Retry of same identity reuses anchor; fresh reset creates new row/anchor. Metadata/schema changes beyond this one operational table are not authorized.

## External control/ownership entity

Existing checkpoint volume contains `.bootstrap/ownership.json`, written atomically with fsync, and no extra SQL table. It records control schema version, approved resource identity, source/artifact/attempt, import anchor, intended reset generation, and asset entries: key, product/source identity, expected content hash/type, checkpoint filename/hash, upload state (INTENDED/VERIFIED), cleanup state (RETAIN/DELETE_INTENDED/DELETED). Include a nonsecret report digest and exact allowlist of owned files.

Before upload or reset destructive effects, persist intention here. On retry reconcile actual S3/checkpoint/database outcomes; interrupted DELETE_INTENDED cleanup is resumable and never expands beyond saved ownership. Missing ledger or conflicting artifact/ownership stops destructive cleanup. Prefix membership alone cannot establish ownership. A fresh reset retains the ledger and non-owned files even though database metadata disappears.

Root checkpoint files remain exactly the existing format: filename SHA256(key)+`.json`, four keys `key`, `content_type`, `sha256`, `bytes_base64`. Preserve valid permissions/readability for backend UID 10001. Ledger stays in subdirectory to avoid the root image-loader glob.

## Application graph and exact quantities

| Domain | Existing targets and quantity | Constraints/invariants |
|---|---|---|
| Catalog | 10 categories including root, actual canonical brands from approved evidence; 1,500 products; 1,500–9,000 variants; 1,500 source product images | Preserve original identities/SKUs/base prices; consistent canonical option dimensions; generated search columns maintained by DB |
| People | 500 ACTIVE users, USER role mappings, 512 addresses | 12 named password login accounts with two addresses; 488 null-password background accounts with one; exactly one default each; unique synthetic usernames/emails |
| Orders | Artifact-frozen C + 3,000 orders; 1–4 distinct-variant items each, quantities within existing bounds | C >= 9,000 completed with sufficient per-product review capacity, 900 pending, 750 confirmed, 750 shipping, 400 cancelled, 200 failed; managed inventory on every order; joined_at <= placement |
| Inventory | One trigger-created balance/variant; one allocation/order item | ACTIVE for pending/confirmed/shipping/failed; CONSUMED completed; RELEASED cancelled; reserved sum equals ACTIVE quantities; stock = reserved + selected available |
| Vouchers | 8 definitions; 96 AVAILABLE demo entitlements plus actual consumed historical grants | Default limits 5,000/2; exhausted overrides 20 global and 1/customer. At least 20 global-exhaustion redemptions and one customer-exhaustion redemption, all on genuine generated orders; counters derived |
| Carts | 300 populated owner carts, 2–6 distinct variant items each | Include all 12 designated accounts; selected/unselected items; only AVAILABLE owned grants may be selected; quantity/stock failure scenarios do not corrupt balances |
| Reviews | Artifact-frozen R text reviews from exact R-40 uniform count draws, no review images | ACTIVE author equals completed order owner; reviewed item belongs to order/product; one review per item; completion <= review <= anchor |
| Wishlists | 3,500 unique owner/product entries | Deterministic selection across 500 owners; never create duplicate pairs |
| Behavior | 360 events, 30 per designated account | Supported PRODUCT_VIEW/SEARCH/PRODUCT_CLICK only, deterministic event/client UUIDs, ownership and type-specific nullability; last 30 days |

No authentication sessions, tokens, OTPs, pending registrations, Google identities, upload sessions or synthetic checkout receipts are seeded. These rows may legitimately be created later by normal user/API activity. Do not insert a second inventory balance when the variant trigger already created one.

## Import transaction order

1. Resolve reviewed graph at saved anchor. Preallocate identity maps for users/addresses/orders/items/vouchers/usages/grants/redemptions; calculate immutable money/JSON snapshots before INSERT. Use safe parameter binding and PostgreSQL JSON/money/snapshot functions rather than string interpolation.
2. Insert categories parent-first, brands, products, variants, and verified-object product image metadata. Let generated columns and inventory initialization triggers run.
3. Insert users/USER mappings and addresses. Insert vouchers/usages, then every grant as AVAILABLE with null consumed_at.
4. Insert order headers with final voucher/pricing totals, then order items with full V30 snapshots. If voucher JSON depends on redemption identity, precompute equivalent row values and use existing `voucher_order_snapshot` function before immutable order INSERT.
5. Insert reservations ACTIVE/unresolved for every managed item. Update completed allocations to CONSUMED/completed_at and cancelled to RELEASED/cancelled_at; FAILED remains ACTIVE. Never insert terminal allocations directly or mutate terminal identity/history.
6. Transition historical grants to CONSUMED, insert exact matching immutable redemptions, derive per-customer/global counters from those rows. No independent quotas randomized to appear exhausted.
7. Derive inventory reserved totals and set stock = reserved + available per S-34. Insert carts after entitlement final state, followed by reviews/wishlists/events. Advance the identity sequence of every explicit-ID table against MAX(existing id), not just the preallocated IDs, and verify trigger-generated inventory balance sequences are above their own maxima. Sequence gaps on rollback are harmless and not completion evidence.
8. Run explicit domain joins/equations and `SET CONSTRAINTS ALL IMMEDIATE`; capture initial exact counts/distribution summaries, update singleton COMPLETED and commit once. A failure rolls back all business data and marker completion, leaving external tracked assets for reuse.

## Canonicalization, prices and temporal invariants

- Use existing catalog_option_map_v1/catalog_option_identity_v1 to validate canonical combinations; do not substitute Python casefold for ICU/NFC semantics. Missing original option evidence retains single-variant fallback.
- P-31 multiplies dimension factors and clamps 0.50–1.50; explicit Decimal ROUND_HALF_UP to whole VND for new base prices, preserve originals exactly. Reject zero/negative/nonfinite comparison ratios and nonpositive factors. Use catalog_effective_price_v1 for whole-VND effective sale price and full immutable original/discount/percentage/window/pricing-instant snapshots; nonsale original=paid, discount=0.
- All lifecycle fields agree with status. Grants precede placement/redemption; placement equals allocation reserved_at and voucher redeemed_at; completion/cancellation matches terminal allocation resolved_at; review follows completion. Relative dates use UTC/calendar-month clamping and consistent microsecond precision.
- Catalog sale distribution is 900 none/300 active/150 scheduled/75 expired/75 disabled, separate from historically valid pricing snapshots. Keep snapshots as of purchase, not recomputed from today's state.
- Completed item selections approximate approved 40/30/25/5/0 tier weights across 75/225/600/450/150 products. Never-purchased tier is excluded from all order item generation to guarantee zero completed sales/reviews; it may appear in current browsing/carts/wishlists. Within-tier selection ensures intended purchase coverage and review-count/rating diversity, then derives statistics from rows.
- Existing filter registry supports four keys only; Laptop supports physical_ram/installed_storage_gb/display_diagonal/display_resolution, Tablet display_diagonal. Derived aliases require real matching evidence; arbitrary option dimensions remain options without new filter behavior.

## R-40 review/rating override — derived volume approved

Exact count groups are 300 zero-review, 300 at 1–10, 525 at 11–50, 300 at 51–100 and 75 at 101–1,000 reviews. Exact calculated-average groups across 1,200 reviewed products are 60/120/300/420/300 in [1,2]/(2,3]/(3,4]/(4,4.5]/(4.5,5]. Use seeded random assignment, uniform inclusive count draws and feasible integer ratings without intentional count/rating correlation; never-purchased products stay in the zero group. Match the existing two-decimal HALF_UP average calculation from actual records. No independently generated aggregate ratings, group tolerance or separate minimum coverage threshold.

The user confirmed replacing the old fixed review/completed/total order counts. Set R = sum of frozen per-product uniform count draws; derive C >= 9,000 completed orders with sufficient distinct eligible items per product, 1–4 distinct variants/order and preserved approximately 40/30/25/5/0 popularity selections. Maintain exactly 3,000 non-completed orders (900/750/750/400/200); total orders C + 3,000. Reserve unreviewed eligible items for the demo journey. Freeze R/C/completed-item/total-item counts and exact statuses in the artifact; actual persisted counts must match. Do not clip/reject/repeat uniform draws or shuffle review groups to concentrate them in high-popularity tiers for budget reasons.

## Runtime state transitions

Lock acquired -> target/preflight verified -> infrastructure ready -> state classified -> (confirmed fresh reset or verified unseeded migration) -> assets verified -> PREPARING/IMPORTING -> atomic business commit+COMPLETED -> backend ready -> operational result.

Normal completed path: lock/preflight -> compatible committed state -> surviving required asset verification/repair -> backend ready, with no import/migrator. Foreign/changed/incompatible/conflicting states stop. A failed backend readiness or frontend check after commit reports committed completion separately from application readiness and never resets automatically.
