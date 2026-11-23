# Testing and Acceptance Strategy

This is an implementation test design, not implemented tests or execution results. Inherit constitutionXIII risk-based policy. Reuse Java25/JUnit5/Mockito/AssertJ/MockMvc, PostgreSQL17 Testcontainers and LocalStack. No new dependency. All new unit/slice names end Test; real integration names end IT and follow owning layer packages. Shared config/fixtures only when justified; do not relocate unrelated legacy tests.

## Responsibilities and planned tests

Java paths below are under `demo/src/test/java/com/example/demo/`. Names marked **new** are proposals and do not yet exist.

| Layer / responsibility | Existing suites to extend / new meaningful responsibility | Required proof |
| --- | --- | --- |
| Basic and publication unit | catalog validator tests; **new** `catalog/validator/ProductPublicationValidatorTest`, AdminCatalogValidatorTest | All Enabled vs Disabled, zero-variant Draft, whole/finite prices, no stock check, generic/registered facts, empty optional values, safe accumulated violations |
| Service unit | existing ProductImageServiceTest; **new** AdminProductServiceImplTest, AdminVariantServiceImplTest, AdminCategoryServiceImplTest, AdminBrandServiceImplTest, AdminGalleryServiceImplTest | Explicit lifecycle and re-enable exception, proposed-state validation, no cart/inventory mutations, stale-before-replay; mock dependencies, no ApplicationContext |
| HTTP/security slice | catalog/controller/ProductImageControllerTest, CatalogSecurityBaselineTest; **new** admin controller Test responsibilities | Strict binding/presence/unknown keys/revisions, correct DTO/status,401/403/JWT-only current-role, code-preserving actual advice scope/precedence, no raw keys |
| Repository and migration IT | existing catalog/repository/ProductSearchRepositoryIT, CatalogSearchMigrationIT, CatalogEnhancementMigrationIT; **new** catalog/repository/AdminCatalogRepositoryIT, AdminCatalogMigrationIT | PostgreSQL constraints/normalization/root/null semantics, query results/totals, version claims, fresh and V27 upgrade, deferred final commit/rollback, old checksums unchanged |
| Service/API IT | existing catalog/ProductImageAttachmentIT; **new** catalog/service/AdminProductServiceIT, AdminGalleryServiceIT, AdminTaxonomyServiceIT, catalog/controller/admin/AdminCatalogApiIT | Real product/child/gate transactions, committed revisions, actual security and field feedback, active gallery retention and ownership; no test @Transactional that hides commit |
| Concurrency IT | extend checkout/service/CheckoutConcurrencyIT and InventoryConcurrencyIT; **new** catalog/service/AdminCatalogConcurrencyIT | Independent committed transactions with deterministic latches/barriers/timeouts; final row/revision/state checks for both race orderings, no sleeps as sole synchronization |
| E2E domain flow | **new** e2e/AdminCatalogFlowIT | Draft→prepare→Publish→cart→price→withdraw→retained unavailable cart→restore Draft→republish; old order/receipt/reservation fingerprints preserved |
| Media regressions | media/integration/ImageCompletionConsistencyIT, ImageObjectReplacementIT, ImageUploadFlowIT, ReviewImageAttachmentIT, PackagedImageFlowIT; user/integration/UserImageApiIT | Shared uploader remains owned/current/immutable; real S3/LocalStack bounded content; source upload reusable, no object delete on detach, copy/DB failure safe |
| Customer regressions | cart/service/CartServiceIT, catalog/service/CatalogCheckoutServiceIT, checkout/service/CheckoutServiceIT and CheckoutConcurrencyIT, order/service/OrderServiceIT and OrderCompletionServiceIT, inventory/service/InventoryServiceIT and InventoryConcurrencyIT | Stored quantity/selection retained; current eligibility/price/inventory and partial skipping; historical replay, cancellation/completion after disable/archive, zero stock publishing |
| Search/read/recommendation | existing CatalogQueryCountIT, ProductSearchRepositoryIT, SearchQueryPlanIT, ProductSearchPerformanceIT, ProductSourceWorkloadPerformanceIT, CatalogPublicReadPerformanceIT, RecommendationIT, RecommendationHttpIT, wishlist suites and review public-read suites | Enabled same-variant predicates/representatives, post-signing fresh state/gallery, active only images, no hidden state leak; name ambiguity disables only existing bindings, no change to AI HTTP or fallback |

Tests must use real production responsibilities, not empty mechanical classes. Names can be combined only where their responsibility remains coherent; never mix unit/MVC/full-context categories. @DataJpaTest uses PostgreSQL; service/API/flow @SpringBootTest with real internal services. Preserve actual discovery by Surefire/Failsafe. No enabling of skipped Docker tests can be asserted without current reports.

## Mandatory real races and transactional failures

1. Two Admins use Product versionN: Product edit versus variant price, spec versus disable, gallery reorder versus detach. Exactly one commits; other409 and no partial fields/revision. Different Products may proceed under shared topology gate.
2. Two disable/enable requests cannot leave Published with zero sale-valid Enabled; every Enabled proposed variant validates, Disabled basic-valid zero price distinct.
3. Primary selection versus removal/reorder/attachment: one Product revision wins; active gallery exactly-one and unique positions; no lost association. Test INTEGER extreme existing order and permutation cycles to prevent overflow/transient uniqueness failures.
4. Legacy attachment route and new route use identical revision checks, including identical replay. A stale replay must409 even if it could previously converge; current-version identical replay200/no new row/revision. Reactivation preserves image identity, detachment retains legacy PUBLISHED upload FK and source object.
5. CategoryA→B and B→A simultaneously; move under descendant; sibling/root equal normalized create/rename/move; case/outer-whitespace brand race; global SKU conflict across Disabled and Archived. Verify gate+DB authoritative rejection, no automatic merge.
6. Hierarchy change versus Product creation/reassignment/publication: gate ordering ensures the accepted proposed tree and basic/applicable Published validation agree. Changing an unrelated category still invalidates stale taxonomy tokens. Allowed same-name different-parent categories succeed even if a registry family becomes ambiguous; verify existing disable semantics, not global uniqueness.
7. Brand deletion versus Product brand assignment: either committed reference blocks delete or deletion makes assignment404; no dangling FK. Category deletion versus Product assignment and child creation has equivalent no-orphan proof including Archived references.
8. New checkout versus Unpublish/Archive/Disable/price: explicitly force each side to lock first. Old-price order/reservation commits coherently or new snapshot skips unavailable; no ineligible new reservation after admin linearization. Mixed cart keeps valid rows; all unavailable422, cart rows retained. Receipt-first replay remains byte-equivalent after all changes.
9. Existing order cancellation/internal completion versus catalog withdrawal: exact owned allocation release/consume, no Admin balance writes. Compare all pre-existing order/receipt/attribution values and no double-resolution.
10. Inject invalid final deferred invariant after revision claim, simulate late uniqueness/dependency failure and storage-copy-success/DB-failure: commit fails, all business row/revision changes roll back, private object safely retained/reconciled, no success response or unsafe delete. Do not mock real PostgreSQL transaction outcomes.

## Acceptance traceability (all 43 scenarios)

| Scenario IDs | Primary test responsibility / oracle |
| --- | --- |
| AC-01/02/08 | Product API/service + repository: minimal zero-variant Draft, complete omitted/null edits, all four admin states/public hiding |
| AC-03/04/05/09 | Publication validator + real service: all Enabled requirements/image primary; invalid Published updates atomically rejected; stock0 Publish succeeds |
| AC-06/07 | Product flow + Cart/Order/Inventory: explicit withdrawal/archive retained history and unavailable cart |
| AC-10/11/13/14 | Variant/service/repository: mandatory basic fields, future exact price, retained SKU/options, last Enabled protection |
| AC-12 | Disable real history/reservation flow; no release/delete/cart cleanup |
| AC-15/16/17/18/19/20 | Media/gallery service/API/LocalStack: original owner, primary switch/permutation/detach, limits/replacement/storage failure |
| AC-21/22/23/24 | Category service/repository: CRUD/tree/cycles, all-product and child dependencies, assignment preserved/ancestry updated |
| AC-25/26/27 | Brand service/repository/Product Draft API: unique stable code, normalized name/dependencies, optional Brand |
| AC-28/29/30 | Specification validator/variant API/search repository: only addressed facts, exact envelope/type/unit, optional empty/false/zero |
| AC-31/32 | Security/current-session API + real revision races: denied matrix, stale rejection and reload, associated modifications covered |
| AC-33/34/35 | Checkout race/Inventory/Media rollback flow: retained carts and history, fresh sale rules, safe failure/no partial success |
| AC-36/37/38/39 | Product/Variant/Gallery service: Restore→Draft, re-enable parent unchanged incl Archived, Disabled basic validity vs Enabled sale validity, empty non-Published gallery |
| AC-40/41/42 | Normalization/DB race: destination/root/sibling and Brand collisions; different-parent/category/Product duplicates allowed; retained SKU ownership |
| AC-43 | Category/application/readiness transaction: fact becomes applicable with invalid registered type/unit, reject move unchanged; no fictitious mandatory fact policy |

FR coverage groups: FR-001/030/031 security/errors/identity; FR-002–008/032/036 product lifecycle; FR-009–012/034/035 taxonomy; FR-013–017/033 variants/prices; FR-018–021 gallery; FR-022–025 facts/presence; FR-026–029 commerce/concurrency. BR-01–20 remain independently traceable through the spec table. All six NFRs are covered by security/integrity/race/contract/customer-regression/evidence gates; ten SCs retain their43-case denominators, no invented completion percentage.

## Execution policy and integration checkpoints

- **After a focused implementation change**: compile and run the owning unit/MVC/repository/service selectors applicable to its risk. Report exact command, current working-state revision, discovered test/case counts and results. A failed assertion is not an environment waiver.
- **Checkpoint A, persistence foundation**: AdminCatalogMigrationIT plus existing catalog/search/cart/order/checkout/inventory/voucher/review migration suites. Test empty install, V27 upgrade, conflicting data failure with unchanged checksum/rows, gate and deferred invariants. Update tests that assert latest version or create synthetic migration successors, not old migration contents.
- **Checkpoint B, mutation/API/media**: Admin unit/MVC/service/API/races and legacy attachment/security/media/avatar/review regressions; compare both routes and advice precedence. Real PostgreSQL and LocalStack for storage consistency.
- **Checkpoint C, consumer compatibility**: Cart/Checkout/Order/Inventory/Wishlist/Search/Recommendation/Review regression and real cross-domain races, query-budget and existing performance workload tests. Preserve genuine RecommendationHttpIT and pinned/compatible AI runtime; never mock/disable it to make the gate green.
- **Near implementation completion**: at least one unrestricted repository-wrapper `./mvnw verify` from demo (or `bash ./mvnw verify` if executable permission is absent), no skip/selective flags. Normal Test and IT discovery/package phases required. Current CI uses `mvn -B -ntp clean verify`; report the exact chosen equivalent. Existing wrapper/network cache requirements must be verified before execution.
- **After isolated fixes**: rerun affected checks first. Repeat full verify only for broad/shared infrastructure impact or remaining regression uncertainty; record rationale. Prior full success remains attributed to its actual revision.

Docker/LocalStack/AI/tooling absence leaves concrete checks BLOCKED/NOT RUN; it does not turn unit success into full-suite success. No application tests are required or executed for this documentation-only Plan. [quickstart](quickstart.md) provides future commands and operator oracles.
