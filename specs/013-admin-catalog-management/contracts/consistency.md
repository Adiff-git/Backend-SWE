# Internal Services, Transactions and Concurrency

**Normative proposed design** for FR-008/012/018–029, BR-10/13/15–19. Implementation is absent. Default REQUIRED / PostgreSQL READ COMMITTED is retained.

## Service and dependency boundaries

Catalog Admin services own Product/Variant/Category/Brand/Image repositories and the topology gate. AdminProduct orchestrates basic edit/lifecycle; AdminVariant uses the same aggregate mutation helper/validator; AdminGallery uses existing attachment preparation plus the short command boundary. Shared catalog aggregate-loading/revision helpers are internal responsibility, not a new domain or circular service graph. MediaService owns ImageUploadRepository; Catalog calls MediaService preparation/revalidation, never its repository. Storage remains ignorant of consuming domains.

Existing Cart→Catalog→Inventory and Checkout→Cart/Catalog/Inventory/Order/Voucher boundaries remain. Catalog administration never calls cart/order/inventory mutation services. Recommendation candidate queries already touch catalog-owned data; extend only necessary eligibility predicates in its existing read path; do not add new cross-domain repository dependencies. No reverse Catalog→Checkout/Order/Recommendation dependency.

## Version contract

- Product response `version` covers scalar fields, lifecycle, ALL variant fields/prices/specs/enabled and all current gallery membership/order/primary metadata. Child responses carry `productVersion`; there is no independent editable child token.
- Category/Brand response `version` covers each entity's information; category/administrative product reads also include `taxonomyVersion`. All category mutations include expectedTaxonomyVersion; update/delete also include expectedVersion. This conservatively rejects changes based on a topology that changed elsewhere.
- All existing-product administrative mutations include expectedVersion (Product) and expectedTaxonomyVersion. Brand assignment, when supplied as a nonnull new reference, also carries expectedBrandVersion from the brand picker/detail. A product creation needs name/category only: no pre-existing Product token; the server validates/locks current references under the taxonomy gate. Optional brand selection includes observed Brand version when supplied.
- Versions are nonnegative signed BIGINT internally; JSON **decimal strings** avoid JavaScript53-bit truncation. Canonical `0|[1-9][0-9]*`, ≤Long.MAX_VALUE; invalid/missing update token400, stale409 CATALOG_STALE_VERSION. Server checks overflow and fails safely with503/catalog maintenance rather than wrapping/reusing versions.
- Conditional claim increments only after the client version matches, before any child write. Claim and all subsequent writes commit/roll back together. For a genuine current-version no-op, lock/read/compare still occur but do not increment. A failed update never leaves a consumed revision.
- No auto-merge or server auto-retry. Caller reloads detail/current tokens and explicitly reapplies intent. DELETE204 requires a GET before next edit; no response/header token is invented. Create has no idempotency-key guarantee, and no new global idempotency subsystem.

## Lock order and transaction algorithm

All same-kind IDs use PostgreSQL UUID ordering, equivalent canonical UUID text ordering in Java; do not use Java signed UUID natural order implicitly.

**Administrative product transaction**:

1. Complete external image preparation/credential resolution before entering the mutation transaction. No S3/AI/SMTP network while holding new Catalog locks.
2. Lock taxonomy singleton **FOR SHARE**; read/compare expectedTaxonomyVersion. This blocks reparent/rename/create/delete while catalog preparation validates references/applicability.
3. Lock Product FOR UPDATE and compare expectedVersion. Evaluate a genuine no-op under this lock without incrementing; otherwise claim its revision with conditional UPDATE before child writes. Obtain the same write lock before any child mutation. Refresh/load entities after claim; never read an old persistence-context object as fresh.
4. For a changed Brand reference, lock selected Brand FOR SHARE and check expectedBrandVersion/existence. Existing Category references are stable under the taxonomy gate. For child changes lock affected variants/images in UUID order after Product. Re-read all relevant siblings; Product lock alone is not asserted to lock child rows, but all supported writers claim Product before their child locks.
5. Apply presence-aware proposed changes; validate basic aggregate and, if resulting Product is PUBLISHED, full readiness. Archive/Unpublish preserve references, not child deletion. Explicit enable on Archived is the only preparation exception stated in the spec.
6. Flush; deferred catalog constraints check final aggregate at real commit. Service returns DTO only after proxy/TransactionTemplate transaction completion; a deferred failure must not send a success. On any failure, revision/child changes roll back together. CreateDraft does the same current-reference gate/validation without an existing-row claim.

**Category transaction**:

1. Lock taxonomy row FOR UPDATE, compare expectedTaxonomyVersion; then lock changed Category and referenced parent rows in UUID order, compare own expectedVersion if applicable.
2. Inspect fresh descendant paths, destination siblings and ALL relevant references (Archived products too). Gate excludes all new Catalog product assignments and competing topology changes. Check self/descendant/cycle before writes. Foreign keys remain the final guard against uncoordinated reference insert/delete races.
3. Resolve old/proposed category applicability. A name mutation can change globally unique family bindings, so the affected set can extend beyond the subtree. For changed applicability, acquire affected Product write locks in UUID order, validate proposed Published aggregates; reject with safe affected IDs/violations if readiness fails, not an implicit Unpublish.
4. Persist accepted hierarchy/name; direct Product.category_id is unchanged. Advance Category revision and taxonomy revision atomically. Product revisions need not increment solely for taxonomy context because every Product mutation explicitly checks the observed taxonomyVersion. Return affectedBindings with old/new applicability or disabled reason, not invented fact changes.
5. Category deletion counts all products/children under the gate and restrictive FKs; no reassignment/cascade. Current own/taxonomy version required even for dependency failures. Create also requires current taxonomy token to protect the hierarchy decision.

**Brand transaction**: conditional Brand revision/write lock, current name-key uniqueness and all-product reference checks, then update/delete. Never lock Product rows after locking Brand. Product assignment takes Product then Brand share lock: delete either sees a reference and conflicts, or holds Brand and the concurrent assignment waits then gets missing reference; FKs close the race. Brand name change does not rewrite Product fields/history and uses its own observed revision. No inventory/cart locks.

**Database hierarchy backstop**: named deferred cycle check locks topology gate before fresh recursion; service ordering avoids inversions. Direct DML that violates the declared write protocol can deadlock and abort, but cannot commit a cycle. No automatic deadlock retry; recognize safe conflict, caller reload. Migration/bootstrap are quiesced and must explicitly maintain revision/gate consistency.

## Existing checkout versus admin

Existing checkout order: successful receipt recovery/fence → User → Cart → Products sorted FOR SHARE → Variants sorted FOR SHARE → Inventory balances sorted → Voucher definition/usage/grant → order/reservation/receipt commit. Preserve its configured transaction-local lock timeout and owned resource rules. Admin locks no User/Cart/Order/Inventory/Voucher, preventing reverse edges.

- Checkout share lock first: admin Product claim waits. Checkout commits a coherent old current-price purchase/reservation; later withdrawal does not revoke it.
- Admin claim first: checkout waits then reads current status/enabled/price; unavailable rows get existing skip reasons. Other valid selected rows may still succeed, none eligible gives existing422. No new order/reservation for withdrawn rows.
- Price race: exactly one current locked price is snapshotted; no mix of old/new totals. Successful receipt replay always returns its stored historical body before current eligibility checks.
- Archive/Disable never releases/consumes reservations; existing authorized cancel/complete still resolves owned allocations.

## Reads, signing and changed eligibility

Admin list uses a single paged metadata projection with version/taxonomyVersion and deterministic ID tie breaks; fetch detail before editing. Admin detail/gallery must load a coherent aggregate snapshot under taxonomy share then Product share lock, with signer credentials prepared before those locks. Load brands through prepared projections and report their own revision; when selecting/replacing a Brand, its own expected token applies. Sign locally from current active associations only.

Public read/signing paths prepare credentials first, then take existing ordered Product share locks and re-read current variants/prices/active image memberships. Do not sign pre-lock-detached gallery rows or show stale disabled choices. Search candidate statement keeps statement-snapshot counts; revalidate returned representative enabled/positive/current price under Product protection before projection, and fail closed if that candidate stopped satisfying the request. Do not silently substitute an unqualified cheap variant, refill pages or claim frozen totals at READ COMMITTED. Consolidate current representative/image projections into existing batched fetches rather than adding per-item reads or a redundant statement; batch this recheck and preserve existing query budgets and sorting/filter invariants for unchanged data. Public detail returns Enabled variants only; corrupt Published data retains defensive conflict behavior rather than pretending complete.

Cart retains stored rows and safe snapshots. Extend internal state with enabled; after signer preparation use coherent current Product/variant/price reads under the existing share-lock protocol including imageless cases. Mask unavailable rows through the existing response; no automatic stored selection/quantity rewrite or reservation. Recommendation refresh already runs under Product locks: filter Enabled positive candidates during both snapshot and refresh, preserve fallback and AI request/response contract. Wishlist membership stays stored/hidden by Published state. Existing order views may lose optional current images after withdrawal; historical prices/text remain intact.

## Gallery transaction details

- Preparation reuses MediaService owned COMPLETED lookup and immutable upload version/final-key metadata; validate actual bounded bytes/ETag/hash and durable copy/checkpoint using current ProductImageContentValidator.
- Short command claims Product after taxonomy lock, revalidates prepared upload identity/status/owner through MediaService's own contract, then evaluates same-source replay/reactivation, max10 active, content duplicate and expected gallery version.
- If MediaService takes upload row share locks for final revalidation, acquire them **after** Catalog Product/child locks; Media completion does not call Catalog, so no reverse dependency is introduced. No storage I/O in this step. Persisted legacy images with null shared source remain valid; do not impose original upload ownership on existing gallery view/publish.
- First attachment defaults primary as existing behavior; otherwise explicit primary optional false; attach alone never publishes. Exact current-version replay is200/no write, stale replay409. Reactivation is a new membership201, preserving image identity.
- Reorder requires an exact distinct permutation of ALL current active image IDs. Pick ≤10 unused nonnegative INTEGER temporary positions disjoint from occupied and final positions, move all entries there, then0..n-1. Primary unchanged; inactive positions are ignored by the active unique index. No max+offset overflow assumption.
- Set primary clears current primary, flushes it, then sets target. Replacing primary and removing old in one command is allowed only with explicit replacementImageId; DELETE with no replacement is valid only for nonprimary or a non-Published last image. If images remain, one designated primary must remain. No silent choice.
- Detach sets active=false/primary=false, not physical delete. Reconciliation/reference queries intentionally include inactive retained rows; they must never interpret inactive as unreferenced/safe-to-delete. Private objects/copied uploads are not removed by Catalog.

## Failure mapping and proof obligations

Malformed request400; denied401/403; missing404; stale/business/dependency/uniqueness409; readiness/semantic fact invalid422; bad media type415; storage dependency503; unexpected500. Only named catalog integrity constraints map to defined conflict/readiness codes; unknown failures stay safe500. Lock timeout/deadlock409 CATALOG_CONCURRENT_CONFLICT requires reload and a fresh intent, not generic automatic retry.

Prove real two-transaction schedules for child-only races, cross-child vs Product, topology cycle/assignment, uniqueness, primary/order and checkout; verify both successful linearization directions, final commit and full rollback. No mocks substitute for these boundaries. Detailed scopes are in [testing-strategy.md](../testing-strategy.md).
