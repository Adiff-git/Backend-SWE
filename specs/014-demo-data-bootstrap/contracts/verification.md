# Operational Verification and Evidence Contract

**Version**: demo-bootstrap-verification-v1. No new automated test classes/suites. Implementation must provide operational validators and reports as part of importer safety.

## Evidence envelope

Each report records invocation/attempt, mode, UTC times/duration, actual Git/importer revision or working-state digest, source/artifact/rule/dependency-lock digest, compatible Flyway history, saved anchor, verified project/database/bucket/daemon identity, stage and completion/commit/backend-readiness state. Include expected/actual counts, domain imported/skipped/rejected records, assets uploaded/skipped/repaired/rejected, invariant names/results, safe recovery guidance and frontend journey results. Secrets, password hashes, token values, raw effective environments and signed URLs are prohibited.

Record report content atomically under `.private/demo-bootstrap/reports/<attempt>/`; external ownership ledger/checkpoints have independent durable state. Report write failure must not retroactively misreport a committed import as rolled back. Console identifies actual known state and an explicit report-write failure.

## Required fresh-import checks

| Check | Passing condition |
|---|---|
| Source/catalog identity | 1,500 source product and original variant IDs/SKUs/base prices unchanged; every source/image hash valid under P-32 |
| Enriched graph | Actual artifact variant count 1,500–9,000; each product 1 or 2–6 variants; consistent canonical dimension sets; no canonical duplicates; every typed specification/price/provenance valid |
| Publication/assets | All approved local-demo source products published; 1,500 verified gallery images with valid primary/order/metadata; every required reference resolves to object and verified compatible checkpoint |
| People | 500 ACTIVE USER customers, twelve designated hashes, 488 null passwords, 512 addresses, exactly one default per owner; synthetic details/valid formats |
| Forbidden workflow artifacts | No seeded OAuth/session/token/OTP/pending registration/upload session or checkout receipt rows; later real interactions are not failures of repeated normal startup |
| Orders/items | Exactly artifact-frozen C + 3,000 orders and C/900/750/750/400/200 status counts, C >= 9,000 with sufficient eligible purchase capacity; each 1–4 distinct variant items with allowed quantity; correct owner/snapshots/line totals/header sums |
| Inventory | Trigger-created unique balances; one attributed allocation per item; correct state/time by order status including FAILED ACTIVE; stored reserved equals ACTIVE sum; stock>=reserved>=0 |
| Prices | Original approved base preserved; full V30 snapshots, valid pricing instant/window, FLOOR direct discounts, whole VND totals; current selling_price mirror not effective sale price |
| Vouchers | Eight definitions per F-37; owned grants begin AVAILABLE, consumed match redemptions; order/voucher snapshots/totals agree; counters equal actual redemptions and limits respected; consumed grants never cart-selected |
| Carts | Exactly 300 populated owner carts, 2–6 distinct items, selected/unselected coverage and named stock/voucher outcomes |
| Reviews | Exactly artifact-frozen R unique eligible item reviews from exact R-40 count groups; author=order.customer_id, COMPLETED, matching product and review after completion; rating 1–5; no seeded image rows |
| Wishlists/events | 3,500 unique owner/product pairs; 360 supported type-correct events across twelve users in last 30 days; stable client-event UUIDs |
| Temporal graph | Customer joins/address/grant precede use, placement/consumption/allocation times agree, completion/cancellation/review ordered; UTC anchor/month clamping coherent |
| Distributions | 75/225/600/450/150 product tiers; completed selections approximate 40/30/25/5/0%; guarantee zero purchased/reviewed never-purchased products; report derived sales/rating/count groups |
| Sale/stock groups | 900/300/150/75/75 sale states; approximately 90/8/2 available-stock proportions rounded to actual variants, required named cases present |
| DB protection | All constraints/triggers enabled; no session_replication_role bypass; deferred constraints forced successfully before atomic completion commit |

Approximate tier selection targets use absolute tolerance 2 percentage points per tier for the artifact's final completed item count; never-purchased remains exactly zero. Stock proportion rounding tolerance is one variant per group. Review/rating groups instead follow exact R-40 below with zero tolerance and no separate minimum coverage thresholds. No independent sales/rating counters are stored to satisfy the report.

### R-40 exact persisted review/rating and derived-volume verification

Across all 1,500 products require exactly 300 with zero reviews, 300 with 1–10, 525 with 11–50, 300 with 51–100 and 75 with 101–1,000 reviews. Across only 1,200 reviewed products require exactly 60 in [1,2], 120 in (2,3], 300 in (3,4], 420 in (4,4.5] and 300 in (4.5,5]. Product count-group assignment and discrete-uniform inclusive count draws use the fixed deterministic seed; rating-group assignment avoids intentional count correlation subject to discrete rating feasibility. Never-purchased products must remain zero-review. Generate actual eligible item-owned integer ratings, never aggregate substitutes.

Verify from persisted rows: LEFT JOIN every product to grouped product_reviews, classify COUNT, calculate AVG(rating::NUMERIC) and apply the existing service's two-decimal HALF_UP rounding for rating intervals. Exclude zero-review products from the rating denominator; include them in count groups. All group cardinalities must match exactly. Report raw count/sum/average and calculated average for traceability.

Confirmed volume change: R is the sum of frozen uniform review-count draws, within 28,950–134,250; 81,600 is only an expectation. Require persisted review total R, completed orders C >= 9,000 and total orders C + 3,000 exactly as frozen in the artifact; fixed other statuses remain 900/750/750/400/200. Verify sufficient distinct owned completed items per reviewed product, additional unreviewed demo items, 1–4 distinct variants/order, derived total/completed-item counts and unchanged popularity proportions. Do not clip/resample reviews, move high counts preferentially into popular tiers or relax exact rating/count groups to meet an order budget. No independently seeded aggregate totals.

On compatible completed normal startup do not rerun exact initial-count assertions as a reason to reseed. Validate committed identity/compatibility and required surviving asset references; activity, expired promotions, deleted/edited reviews or changed carts are legitimate later states.

## Mandatory operational scenarios

1. First normal startup from empty/migration baseline, then repeated normal startup after real demo activity. Preserve activity/password hashes/anchor; import zero extra rows.
2. Confirmed fresh startup affects only backend_demo and ledger-owned assets/checkpoints; PostgreSQL volume, unrelated resources and non-owned user uploads remain. Compare nonsecret before/after ownership inventories.
3. Interrupt upload before/after ownership intention, after S3 write and before/after checkpoint verification. Retry exact same artifact/anchor; no untracked references or conflicting overwrite.
4. Interrupt business import after some inserts and before constraint checks. PostgreSQL rollback leaves no partial business graph/completion; retry uses correct reservation/entitlement transitions.
5. Lose response after commit. Reread committed marker before retry; never duplicate business rows or label committed data rolled back.
6. Two real starts from different checkouts sharing daemon contend on same lock; loser changes no resources. Crash leaves fail-closed sentinel; verify no prior workers before exact recovery.
7. Remove a required owned object in controlled verification and run normal repair; preserve business records/anchor. Introduce a conflicting owned object and prove failure before restore can hide it. Restore fixtures after check.
8. Recreate/start LocalStack under controlled verification; root checkpoints restore valid content. Fresh-owned cleanup removes owned checkpoint sources so future recreation does not resurrect deleted assets. Corrupt unrelated checkpoint produces a reported blockage, not deletion.
9. Foreign nonempty DB, changed artifact/importer, failed/pending/checksum-changed migration and incompatible ICU each stop safely without auto-adoption/reset or normal traffic.
10. Post-commit backend readiness failure reports initialized=true/backend_ready=false; no automatic rollback/reset. Operational fault setup/recovery is documented and confined to approved resources; never execute it as part of this planning stage.

## Frontend and regression evidence

Record actual URL/build identity and twelve account/role mappings, without passwords, for each named F-38 journey. Mark PASS/FAIL/UNEXECUTED; absence of a frontend or missing feature is not a pass. Existing supported sorts/filters only; sales/rating coverage uses operational queries where no frontend/API exists. Normal login may create legitimate auth sessions after import verification.

Near implementation completion run existing unrestricted `./mvnw verify` in demo, with no skip flags or selective discovery. Inspect current reports/discovery rather than count stale files. Targeted checks after local changes and cross-domain integration checkpoints are distinct from full-suite success. Repeat full verification only when shared/broad changes or unresolved regression uncertainty justify it. Document environment blockers, actual failed commands and remaining uncertainty; never replace an unavailable full gate with selective checks and label it full.

Verify that the F-39 usage-scope record is COMPLETE for private non-commercial educational course use and instructor submission, access is restricted to team/instructor, and source provenance is preserved. Record public redistribution verification as NOT_APPLICABLE_CURRENT_SCOPE because public redistribution and commercial use are prohibited. Keep copyright clearance and reuse permissions UNVERIFIED; neither educational scope, local publication nor a successful operational run proves clearance. Require applicable permission verification and explicit scope reassessment before any future public redistribution or commercial use.
