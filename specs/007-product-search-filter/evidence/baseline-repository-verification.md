# T016 — native Catalog listing foundation

Status: PASS for T016 only. UTC:2026-11-09T07:47:00.056762+00:00. Branch:features/SearchAndFilter;HEAD:c5bd4e7eeb39d9b06a0c7e36af9a65635058613f plus preserved existing changes.

## Verified prerequisites / scope

T010,T011,T015 are checked complete and their evidence files exist. V15 SHA-256 matches recorded migration proof;all V1–V15 files remained untouched. Baseline/evidence snapshot was captured before writes. Checklists retain requirements16/16 and reviewer-owned design0/40 markers;the user authorized only T016 with those known pending scoped gates. No hooks before/after because .specify/extensions.yml is absent.

No endpoint/service/converter/DTO/parser behavior or applied migration is changed. Keyword,fuzzy,ranking,category/brand/price/specification filters and explicit sorting are NOT implemented. Passing this repository task does not imply G1/T019 or full-feature acceptance.

## Implementation

- ProductSearchRepository is a Spring Data fragment,composed into existing ProductRepository without changing its existing queries,including the uncommitted Brand fetch join. ProductSearchRepositoryImpl uses the existing EntityManager;read-only transaction is local to the read fragment and joins an existing ambient service transaction without replacing publication/signing orchestration.
- ProductCandidatePage is immutable ID-first metadata only:Product UUID,selected Variant UUID,exact BigInteger amount and full distinct total. No entities,lazy relationships,image access,DTO conversion or in-memory Product filtering.
- One reusable SQL statement constructs published_products then eligible_variants. **One row per owned variant before product reduction.** Its orphan UNION has NOT EXISTS(any owned variant) and the bound allowOrphans flag. The baseline guard allows no variant conditions or nonblank keyword,so required tokens are vacuously shared. It does not claim to support filtered/shared-keyword orphans;later keyword/variant stages must implement their actual eligibility before relaxing this guard.
- DISTINCT ON(product_id) selects minimum(price,variant UUID);Product IDs then sort UUID ASC before LIMIT/OFFSET. All statements derive from the same eligible_products relation;no collection JOIN FETCH,search replica,prelimited keyword candidates or per-variant projection fetch.
- totals CTE LEFT JOIN candidate_page returns a total-only row even for empty or beyond-last-page pages. **An empty later page still carries the full count.** Mapper skips only the total-only row,not a Product. D7 orphan Product has a real Product UUID and exactly two null metadata values;zero amount stays nonnull with real Variant UUID.
- All values (PUBLISHED status,orphan eligibility,size,long offset) are bound. SQL operators,paths,relations and UUID ORDER BY are fixed trusted literals. Offset multiplication widens to long before multiplication,including page2147483647/size100.
- Null/invalid pagination and every not-yet-supported nonempty keyword,category,brand,price,sort or specification criterion reject BEFORE SQL,not silently ignored. Even nonnull empty collection criteria fail closed;an omitted/normalized-empty keyword is the only legacy-compatible keyword state.
- SpecificationKeywordContributor is a trusted Catalog SPI with fixed p/v alias contract and namespaced bindings. A lazy ObjectProvider leaves room for the later keyword stage to consume independently registered approval-backed beans. The baseline never dereferences it;no provider,registry file,dummy allowlist or raw technical-fact search exists. T023 must implement keyword consumption;T030 remains approval-gated and may register its own module without a registry-specific baseline constructor change.

## Test-first / fixture correction

Six integration cases were authored against a fragment stub:initial red run failed for the intended missing query behavior (1 failure,5 errors,UnsupportedOperationException),not missing infrastructure. The first real-query run passed query assertions but the D7 cleanup attempted TRUNCATE with deferred trigger events. Cleanup was corrected to let the rollback-only D7 test transaction revert its exceptional fixture;no trigger was disabled,no invalid orphan committed and no production assertion weakened. Cheapest-variant tie expectation is independently computed from fixture UUIDs in Java,not from the implementation SQL.

## Final executed validation

```sh
mvn -o -f demo/pom.xml -Dtest=ProductSearchNormalizerTest,ProductSearchRequestTest,ProductSearchParameterParserTest,ProductCatalogConverterTest,ProductListingControllerTest,ProductDetailControllerTest,ProductCatalogServiceImplTest,WishlistConverterTest -Dit.test=ProductSearchBaselineRepositoryIT,ProductListingRepositoryIT,ProductDetailRepositoryIT,CatalogEnhancementMappingsIT test failsafe:integration-test failsafe:verify
```

BUILD SUCCESS:50 unit/MVC tests +12 integration tests =62;0 failures/errors/skips. Integration comprises6 new baseline cases plus6 existing Listing/Detail/Brand-Specification mapping cases. No complete API/full-regression/performance gate is claimed.

The six baseline cases cover published-only legacy membership/UUID order,distinct counts,cheapest tied-variant selection and exact30-digit amount,empty/deep/max-offset totals,D7 deferred rollback-only orphan,unimplemented criteria rejected before SQL and zero active providers,images not multiplying candidates and legitimate zero price. Source V3 trigger remains enabled in D7. NORMAL committed fixtures always satisfy existing constraints;the orphan is visible only inside an uncommitted rollback-only fixture transaction.

[baseline-repository-results.json](baseline-repository-results.json) archives actual per-page IDs/selected pairs/totals and statement observations,test case identities/report hashes,new source hashes,migration hashes and PostgreSQL container IDs extracted from the run log. Each baseline candidate call prepares exactly1 Hibernate-owned native data statement;unsupported inputs prepare0. This validates this fragment only,not all JDBC/S3/signing costs of a future endpoint or the final ≤6/≤8 budgets. Same-statement total/page snapshot is guaranteed by the one-statement query structure;cross-request/concurrent-publication response behavior still needs later tests and existing service rechecks.

## Disposable environment / remaining gates

Tests use @DataJpaTest with active test profile,existing jdbc:tc:postgresql:17-alpine:///backend_test and Flyway through V15. This creates a dedicated disposable test-process container,not DB_URL or a development/shared target. Test classes execute serially (new class explicitly SAME_THREAD);the shared test JDBC URL does NOT prove per-worker parallel isolation. Fixtures are cleaned between committed cases;D7 rolls back. Runtime log container IDs/cleanup status are recorded separately. No manual migration on a shared database or Docker volume reset was performed.

T003 registry,T004 identifier sign-off,T006 semantic OpenAPI,T014 calibration and all later gates remain untouched. T018 is still blocked by T006;T019/G1 does not pass merely because this repository test passes. No further implementation task becomes fully unblocked by T016 alone. Stop after T016;no commit/push.

## Post-execution preservation check

Pre/post SHA-256 comparison found only tasks.md and ProductRepository.java changed among pre-existing files. Removing only the added ProductSearchRepository superinterface reproduces ProductRepository's exact original hash,including all prior Brand/Specifications query changes. All other pre-existing application/tests,DTO/parser/normalizer,Detail/Wishlist contracts,ignore files,checklists,prior evidence,constitution and V1–V15 migration bytes remain unchanged. Seven new artifacts are exactly the fragment/interface/SPI/projection,test,and this evidence/results pair.

Only T016 was newly checked:11 completed tasks,55 unchecked. Dependency graph now leaves only T003/T004/T006 prerequisite-ready verification work;their missing external approval/sign-off/tool evidence still blocks completion. T018 specifically remains blocked by T006. File links,JSON,whitespace,git diff --check and new-file ignore checks pass. Both PostgreSQL container IDs captured in the final runtime log are confirmed removed. Stop after T016.
