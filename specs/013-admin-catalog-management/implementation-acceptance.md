# Implemented acceptance and evidence reconciliation — T080

Reviewed2026-11-23 at the tested uncommitted working state. These rows bind approved requirements/scenarios to actual source responsibilities and discovered final-run tests/oracles. They supplement, not replace, the unchanged approved associations in [tasks-traceability.md](tasks-traceability.md).

**Scope:**36/36 FR,6/6 NFR,43/43 AC,20/20 BR,7/7 stories and30/30 contracted Admin operations have implemented and executed verification paths. This is evidence of tested Feature1 development/integration, not production coverage, target rollout or a formal proof for every possible dataset. External C01 and G3 remain blocked. Exact run/count/source/artifact boundaries are in [final-verification.md](final-verification.md).

## Actual implementation and proof index

Java paths below are relative to `demo/src/main/java/com/example/demo/`, except the explicit migration resource. Test class short names resolve to the full discovered class names/counts in [verification-test-inventory.tsv](verification-test-inventory.tsv); no listed test is only planned.

### P — Product lifecycle and presence

Implementation: `catalog/service/AdminProductServiceImpl.java; catalog/validator/ProductPublicationValidator.java`.

Executed owning suites: `AdminProductServiceIT,AdminProductServiceImplTest,ProductPublicationValidatorTest,AdminCatalogApiIT`.

Variantless Draft, omitted/null fields, all-state pages/no-op/restore, stock0 Publish, invalid Published proposals roll back; HTTP `productsShouldFollowRealHttpLifecycleWithVariantlessDraftAndZeroStock`.

### V — Variant/price/enable identity

Implementation: `catalog/service/AdminVariantServiceImpl.java; catalog/validator/AdminCatalogValidator.java`.

Executed owning suites: `AdminVariantServiceIT,AdminVariantServiceImplTest,AdminCatalogApiIT,AdminCatalogConcurrencyIT`.

Required/retained SKU, exact whole VND including huge integer values, preparation0 vs Published positive, last Enabled rejection, Archived enable exception, parent revision and unchanged price/history.

### G — Gallery/media/reference safety

Implementation: `catalog/service/AdminGalleryServiceImpl.java; catalog/service/CatalogImageAttachmentPreparer.java; media/service/impl/MediaServiceImpl.java`.

Executed owning suites: `AdminGalleryServiceIT,AdminGalleryServiceImplTest,ProductImageAttachmentIT,AdminCatalogApiIT,ImageObjectReplacementIT,ImageCompletionConsistencyIT,ImageUploadFlowIT,ReviewImageAttachmentIT,UserImageApiIT,PackagedImageFlowIT`.

Owned completed content, primary replacement/max10/permutation/extreme order/reactivation, null source/legacy replay, source/shared object retention and real copy-success/deferred-DB-failure handling. Local signing in other suites is not this storage proof.

### T — Category hierarchy/applicability

Implementation: `catalog/service/AdminCategoryServiceImpl.java; catalog/repository/CatalogTaxonomyQueries.java; catalog/repository/CatalogApplicabilitySql.java`.

Executed owning suites: `AdminTaxonomyServiceIT,AdminCategoryServiceImplTest,AdminCatalogConcurrencyIT,ProductSearchRepositoryIT,AdminCatalogApiIT`.

D05 root/sibling/destination equality, different-parent duplicates, cycle/self/descendant rejection, dependency protection, stable assignments, ambiguous roots and invalid newly applicable Published facts roll back. Twenty forced taxonomy schedules use real PG gate waits.

### B — Brand references and normalized identity

Implementation: `catalog/service/AdminBrandServiceImpl.java; catalog/repository/CatalogMutationRepository.java`.

Executed owning suites: `AdminTaxonomyServiceIT,AdminBrandServiceImplTest,AdminCatalogConcurrencyIT,AdminCatalogApiIT`.

Stable code, global normalized uniqueness, optional assignments, Archived dependency, own revisions and normalized create/delete-vs-assignment races; no dangling FK/identity merge.

### S — Shared typed specifications

Implementation: `catalog/validator/PersistedSpecificationValidator.java; catalog/validator/TechnicalSpecificationValidator.java; catalog/service/AdminVariantServiceImpl.java`.

Executed owning suites: `PersistedSpecificationValidatorTest,TechnicalSpecificationValidatorTest,AdminVariantServiceIT,ProductSearchRepositoryIT,AdminCatalogApiIT`.

Version1 typed envelope, units/category applicability, generic/nonsearchable facts, addressed-only complete replacement, omitted/empty/false/zero semantics. Dedicated A25 HTTP races (four schedules) and stale/deferred rollback/reload case are separate from PATCH/shared-service proof.

### H — Authorization/errors/aggregate transactions

Implementation: `common/security/CatalogSecurityConfiguration.java; common/exception/AdminCatalogExceptionHandler.java; catalog/repository/CatalogMutationRepository.java; catalog/service/CatalogAggregateMutation.java`.

Executed owning suites: `AdminCatalogApiIT,AdminCatalogRepositoryIT,AdminCatalogConcurrencyIT,AdminCatalogErrorSupportTest,ApiErrorFactoryTest`.

All30+alias actual bearer/current session/account route matrix,217 identity-route observations; strict400/404/409/415/422/503/500, real late commit failures/no success, aggregate claim/refresh/rollback, observed PG waits and unrelated Product progress. Filter-chain MockMvc is distinguished from socket HTTP.

### C — Commerce/history/inventory compatibility

Implementation: `catalog/service/CatalogCartReadServiceImpl.java; catalog/service/CatalogCheckoutServiceImpl.java; catalog/service/CatalogOrderReadServiceImpl.java; cart/validator/CartValidator.java; checkout/validator/CheckoutValidator.java`.

Executed owning suites: `CartServiceIT,CatalogCartReadServiceIT,CheckoutServiceIT,CatalogCheckoutServiceIT,CheckoutConcurrencyIT,OrderServiceIT,OrderCompletionServiceIT,InventoryServiceIT,InventoryConcurrencyIT,AdminCatalogFlowIT,AdminCatalogConsumerCompatibilityIT,CartFlowIT,CheckoutFlowIT,ReviewHistoryIT,BehaviorIT,WishlistServiceIT`.

Cart membership/selection/quantity retained, current price/eligibility, historical-zero replay and snapshots/receipts, exact allocation quantity/state/attribution and one-time release/consume. Actual Admin edits vs checkout in both lock directions; socket flows and all legacy25 corrective cases execute in final run.

### F — Public visibility/search/recommendations/performance

Implementation: `catalog/service/ProductCatalogServiceImpl.java; catalog/repository/CatalogSearchSql.java; catalog/repository/ProductSearchRepositoryImpl.java; recommendation/RecommendationStore.java; recommendation/RecommendationHydrator.java`.

Executed owning suites: `AdminCatalogPublicEligibilityIT,ProductSearchRepositoryIT,CatalogReadFlowIT,SearchCapabilityIT,RecommendationIT,RecommendationHttpIT,CatalogQueryCountIT,SearchQueryPlanIT,ProductSearchPerformanceIT,ProductSourceWorkloadPerformanceIT,CatalogPublicReadPerformanceIT,AdminCatalogQueryObservationIT`.

Current Published/Enabled/positive candidates and post-lock hydration; detached primary never signed after preparation pause; same-variant/ancestry/four-key/quarantine semantics and pagination; genuine1500-candidate AI socket/outage; original native-JDBC budgets/workloads and raw separate-run percentiles.

### D — Audit/migration/recovery evidence

Implementation: `catalog/audit/CatalogCompatibilityAudit.java; catalog/repository/CatalogApplicabilitySql.java; resources/db/migration/V28__admin_catalog_management.sql`.

Executed owning suites: `CatalogCompatibilityAuditIT,CatalogAllocationPreflightIT,CatalogLocalReferenceIT,AdminCatalogMigrationIT,AdminCatalogRepositoryIT,CatalogConstraintsIT`.

Actual V27-only read-only PostgreSQL audit/preflight/type/unit/applicability/allocation violations; safe failures/bounds. Source-qualified normalizer and final guard correction, fresh/V27 upgrade/negative transactional rollback/mapping plus separate frozen representative backup/restore40-relation G2 evidence. Target deployment remains unexecuted.

## Functional requirements

| Approved ID | Concrete proof responsibilities / business oracle |
|---|---|
| FR-001 | H — Authorization/errors/aggregate transactions |
| FR-002 | P, V, F — Product lifecycle and presence; Variant/price/enable identity; Public visibility/search/recommendations/performance |
| FR-003 | P, V — Product lifecycle and presence; Variant/price/enable identity |
| FR-004 | P — Product lifecycle and presence |
| FR-005 | P, V, G, S, T — Product lifecycle and presence; Variant/price/enable identity; Gallery/media/reference safety; Shared typed specifications; Category hierarchy/applicability |
| FR-006 | P, C, F — Product lifecycle and presence; Commerce/history/inventory compatibility; Public visibility/search/recommendations/performance |
| FR-007 | P, C, F — Product lifecycle and presence; Commerce/history/inventory compatibility; Public visibility/search/recommendations/performance |
| FR-008 | P, V, G, T, S, H — Product lifecycle and presence; Variant/price/enable identity; Gallery/media/reference safety; Category hierarchy/applicability; Shared typed specifications; Authorization/errors/aggregate transactions |
| FR-009 | T — Category hierarchy/applicability |
| FR-010 | T — Category hierarchy/applicability |
| FR-011 | B — Brand references and normalized identity |
| FR-012 | T, B, S, F, D — Category hierarchy/applicability; Brand references and normalized identity; Shared typed specifications; Public visibility/search/recommendations/performance; Audit/migration/recovery evidence |
| FR-013 | V, S — Variant/price/enable identity; Shared typed specifications |
| FR-014 | V, P, D — Variant/price/enable identity; Product lifecycle and presence; Audit/migration/recovery evidence |
| FR-015 | V, C — Variant/price/enable identity; Commerce/history/inventory compatibility |
| FR-016 | V, C, F — Variant/price/enable identity; Commerce/history/inventory compatibility; Public visibility/search/recommendations/performance |
| FR-017 | V, C — Variant/price/enable identity; Commerce/history/inventory compatibility |
| FR-018 | G — Gallery/media/reference safety |
| FR-019 | G — Gallery/media/reference safety |
| FR-020 | G, P, H — Gallery/media/reference safety; Product lifecycle and presence; Authorization/errors/aggregate transactions |
| FR-021 | G, H — Gallery/media/reference safety; Authorization/errors/aggregate transactions |
| FR-022 | S, D — Shared typed specifications; Audit/migration/recovery evidence |
| FR-023 | S, F, T — Shared typed specifications; Public visibility/search/recommendations/performance; Category hierarchy/applicability |
| FR-024 | S, V — Shared typed specifications; Variant/price/enable identity |
| FR-025 | P, S, V, G, H — Product lifecycle and presence; Shared typed specifications; Variant/price/enable identity; Gallery/media/reference safety; Authorization/errors/aggregate transactions |
| FR-026 | C, G, D — Commerce/history/inventory compatibility; Gallery/media/reference safety; Audit/migration/recovery evidence |
| FR-027 | C — Commerce/history/inventory compatibility |
| FR-028 | C, D — Commerce/history/inventory compatibility; Audit/migration/recovery evidence |
| FR-029 | H, V, G, T, B, S, C — Authorization/errors/aggregate transactions; Variant/price/enable identity; Gallery/media/reference safety; Category hierarchy/applicability; Brand references and normalized identity; Shared typed specifications; Commerce/history/inventory compatibility |
| FR-030 | H — Authorization/errors/aggregate transactions |
| FR-031 | V, G, T, B, C, D — Variant/price/enable identity; Gallery/media/reference safety; Category hierarchy/applicability; Brand references and normalized identity; Commerce/history/inventory compatibility; Audit/migration/recovery evidence |
| FR-032 | P, C — Product lifecycle and presence; Commerce/history/inventory compatibility |
| FR-033 | V, P, C, F — Variant/price/enable identity; Product lifecycle and presence; Commerce/history/inventory compatibility; Public visibility/search/recommendations/performance |
| FR-034 | T, D — Category hierarchy/applicability; Audit/migration/recovery evidence |
| FR-035 | B, D — Brand references and normalized identity; Audit/migration/recovery evidence |
| FR-036 | P, C — Product lifecycle and presence; Commerce/history/inventory compatibility |

FR-008 includes Product, variant/price, gallery, category binding and specification proposed-state validation; FR-025 includes Product optional Brand/description presence as well as fact documents; FR-027 includes actual Cart implementation/retention/revalidation and owning Cart regressions, not Checkout-only tags.

## All43 acceptance scenarios

| Scenario | Approved business trigger / concrete proof index |
|---|---|
| AC-01 | Incomplete Draft creation; P |
| AC-02 | Draft editing; P |
| AC-03 | Successful publication; P, V, G, T, S, H |
| AC-04 | No valid Enabled variant; P |
| AC-05 | Missing primary image; P |
| AC-06 | Unpublish; P, C, F |
| AC-07 | Archive with history; P, C, F |
| AC-08 | Administrative visibility; P |
| AC-09 | Invalid Published edit rejected; P, V, G, T, S, H |
| AC-10 | Draft variant validation; V, P, C |
| AC-11 | Price change; V, P, C |
| AC-12 | Disable referenced variant; V, P, C |
| AC-13 | Retained SKU/options conflict; V, P, C |
| AC-14 | Last Enabled variant protected; V, P, C |
| AC-15 | Existing upload and attachment; G, H, F |
| AC-16 | Change primary; G, H, F |
| AC-17 | Reorder; G, H, F |
| AC-18 | Remove non-primary association; G, H, F |
| AC-19 | Published primary removal; G, H, F |
| AC-20 | Existing image limits; G, H, F |
| AC-21 | Category CRUD; T, F |
| AC-22 | Referenced category deletion; T, F |
| AC-23 | Dependent subcategories; T, F |
| AC-24 | Hierarchy effects; T, F |
| AC-25 | Normalized brand maintenance; B, P |
| AC-26 | Referenced brand deletion; B, P |
| AC-27 | Optional brand; B, P |
| AC-28 | Modify specifications; S, P, V, F |
| AC-29 | Invalid facts; S, P, V, F |
| AC-30 | Optional facts; S, P, V, F |
| AC-31 | Unauthorized operations; H, G, S, C |
| AC-32 | Stale administrative update rejected; H, G, S, C |
| AC-33 | Withdrawal versus checkout and recovery; P, C, F |
| AC-34 | Reservation preservation; P, C, F |
| AC-35 | Failure integrity; H, G, S, C |
| AC-36 | Restore to Draft; P |
| AC-37 | Re-enable safely; V, P, C |
| AC-38 | Enabled versus Disabled publication; V, P, C |
| AC-39 | Non-Published gallery removal; G, H, F |
| AC-40 | Sibling uniqueness on move; T, F |
| AC-41 | Brand-name collisions; B, P |
| AC-42 | Duplicate product names and retained SKUs; P, V, D |
| AC-43 | Category move preserves Published readiness; T, S, P, F |

The index describes actual oracles (not simply requirement tags): for example P proves stock0 Publish/variantless Draft/Restore, G proves shared-object retention despite DB rollback, T proves AC-43 invalid applicable facts leave tree/revisions unchanged, H/S prove dedicated PUT endpoint stale/rollback, C proves immutable full commerce rows across actual catalog lifecycle changes, F proves detached-gallery signing interleavings and real AI HTTP. Individual typed/boundary subcases also execute in the listed unit/MVC validators/services; database assertions are not inferred from those mocks.

## Non-functional acceptance

| ID | Proof / limit |
|---|---|
| NFR-001 | H/G actual current bearer/account/session/filter-chain and owned-upload verification. Production CORS remains a separate unresolved constitutional release gate. |
| NFR-002 | D/H/G/C committed constraint failure, references/history/storage-retention and allocation fingerprints. Private copied objects are not claimed rolled back with the DB. |
| NFR-003 | H/T/B/S/C observed PG waits, one aggregate winner/stale conflicts, taxonomy/Brand protection, final rollback and checkout/allocation schedules. |
| NFR-004 | P/V/S/H strict presence/types/lossless money, current error advice, safe no-success failures; local/public contracts regressed. |
| NFR-005 | C/F/G actual commerce/search/recommendation/media regression, same-variant semantics, stock0 publication and original query/performance workloads. |
| NFR-006 | D plus final unrestricted957+862 run, actual per-class source discovery, durable failures/reruns and explicit G1/G2/G3/C01 scope distinctions. No new SLA invented. |

## Business rules and unchanged associations

| Rule | Approved FR association → actual proof index |
|---|---|
| BR-01 | FR-002, FR-003, FR-004, FR-005, FR-006, FR-007, FR-008, FR-036 → C, F, G, H, P, S, T, V |
| BR-02 | FR-007, FR-026, FR-031 → B, C, D, F, G, P, T, V |
| BR-03 | FR-010, FR-011 → B, T |
| BR-04 | FR-015, FR-026, FR-027 → C, D, G, V |
| BR-05 | FR-017, FR-028 → C, D, V |
| BR-06 | FR-003, FR-004, FR-024 → P, S, V |
| BR-07 | FR-005, FR-015, FR-020, FR-024 → C, G, H, P, S, T, V |
| BR-08 | FR-018, FR-019, FR-020, FR-021 → G, H, P |
| BR-09 | FR-013, FR-014, FR-015, FR-016, FR-017, FR-026, FR-027, FR-028 → C, D, F, G, P, S, V |
| BR-10 | FR-004, FR-008, FR-015, FR-020 → C, G, H, P, S, T, V |
| BR-11 | FR-032, FR-036 → C, P |
| BR-12 | FR-033 → C, F, P, V |
| BR-13 | FR-020, FR-021 → G, H, P |
| BR-14 | FR-003, FR-013, FR-015 → C, P, S, V |
| BR-15 | FR-029 → B, C, G, H, S, T, V |
| BR-16 | FR-009, FR-012 → B, D, F, S, T |
| BR-17 | FR-005, FR-008, FR-013, FR-016 → C, F, G, H, P, S, T, V |
| BR-18 | FR-026, FR-027, FR-028 → C, D, G |
| BR-19 | FR-012, FR-014, FR-034, FR-035 → B, D, F, P, S, T, V |
| BR-20 | FR-002, FR-006, FR-007, FR-008, FR-032, FR-036 → C, F, G, H, P, S, T, V |

## Stories / API acceptance

| Story | Approved priority / use case | Actual proof |
|---|---|
| US1 | P1 UC-031 | P/H/C/F |
| US2 | P1 UC-034 | V/H/C |
| US3 | P1 UC-035 | G/H/F |
| US4 | P1 all six scoped UCs | H/T/B/S/C |
| US5 | P2 UC-032 | T/H/F |
| US6 | P2 UC-033 | B/H/C |
| US7 | P2 UC-036 | S/H/F |

All30 method/path tuples below are compared to the approved API table, implemented by existing concrete Admin controllers and exercised by functional API flows plus the real current-identity31-route matrix. Legacy attachment is an additional existing alias, not A31. Upload endpoints remain reused existing services. No inventory/order/customer/sales/settings/import/price-history endpoint was added.

| ID | Contracted method / relative path | Actual functional proof |
|---|---|
| A01 | GET `/products` | P + H; dedicated A25 endpoint races/rollback are S/H, not shared PATCH substitution |
| A02 | GET `/products/{productId}` | P + H; dedicated A25 endpoint races/rollback are S/H, not shared PATCH substitution |
| A03 | POST `/products` | P + H; dedicated A25 endpoint races/rollback are S/H, not shared PATCH substitution |
| A04 | PATCH `/products/{productId}` | P + H; dedicated A25 endpoint races/rollback are S/H, not shared PATCH substitution |
| A05 | POST `/products/{productId}/publish` | P + H; dedicated A25 endpoint races/rollback are S/H, not shared PATCH substitution |
| A06 | POST `/products/{productId}/unpublish` | P + H; dedicated A25 endpoint races/rollback are S/H, not shared PATCH substitution |
| A07 | POST `/products/{productId}/archive` | P + H; dedicated A25 endpoint races/rollback are S/H, not shared PATCH substitution |
| A08 | POST `/products/{productId}/restore` | P + H; dedicated A25 endpoint races/rollback are S/H, not shared PATCH substitution |
| A09 | GET `/categories` | T + H; dedicated A25 endpoint races/rollback are S/H, not shared PATCH substitution |
| A10 | GET `/categories/{categoryId}` | T + H; dedicated A25 endpoint races/rollback are S/H, not shared PATCH substitution |
| A11 | POST `/categories` | T + H; dedicated A25 endpoint races/rollback are S/H, not shared PATCH substitution |
| A12 | PATCH `/categories/{categoryId}` | T + H; dedicated A25 endpoint races/rollback are S/H, not shared PATCH substitution |
| A13 | DELETE `/categories/{categoryId}` | T + H; dedicated A25 endpoint races/rollback are S/H, not shared PATCH substitution |
| A14 | GET `/brands` | B + H; dedicated A25 endpoint races/rollback are S/H, not shared PATCH substitution |
| A15 | GET `/brands/{brandId}` | B + H; dedicated A25 endpoint races/rollback are S/H, not shared PATCH substitution |
| A16 | POST `/brands` | B + H; dedicated A25 endpoint races/rollback are S/H, not shared PATCH substitution |
| A17 | PATCH `/brands/{brandId}` | B + H; dedicated A25 endpoint races/rollback are S/H, not shared PATCH substitution |
| A18 | DELETE `/brands/{brandId}` | B + H; dedicated A25 endpoint races/rollback are S/H, not shared PATCH substitution |
| A19 | GET `/products/{productId}/variants` | V + H; dedicated A25 endpoint races/rollback are S/H, not shared PATCH substitution |
| A20 | POST `/products/{productId}/variants` | V + H; dedicated A25 endpoint races/rollback are S/H, not shared PATCH substitution |
| A21 | PATCH `/products/{productId}/variants/{variantId}` | V + H; dedicated A25 endpoint races/rollback are S/H, not shared PATCH substitution |
| A22 | POST `/products/{productId}/variants/{variantId}/enable` | V + H; dedicated A25 endpoint races/rollback are S/H, not shared PATCH substitution |
| A23 | POST `/products/{productId}/variants/{variantId}/disable` | V + H; dedicated A25 endpoint races/rollback are S/H, not shared PATCH substitution |
| A24 | GET `/products/{productId}/variants/{variantId}/specifications` | S + H; dedicated A25 endpoint races/rollback are S/H, not shared PATCH substitution |
| A25 | PUT `/products/{productId}/variants/{variantId}/specifications` | S + H; dedicated A25 endpoint races/rollback are S/H, not shared PATCH substitution |
| A26 | GET `/products/{productId}/images` | G + H; dedicated A25 endpoint races/rollback are S/H, not shared PATCH substitution |
| A27 | POST `/products/{productId}/images` | G + H; dedicated A25 endpoint races/rollback are S/H, not shared PATCH substitution |
| A28 | PUT `/products/{productId}/images/primary` | G + H; dedicated A25 endpoint races/rollback are S/H, not shared PATCH substitution |
| A29 | PUT `/products/{productId}/images/order` | G + H; dedicated A25 endpoint races/rollback are S/H, not shared PATCH substitution |
| A30 | DELETE `/products/{productId}/images/{imageId}` | G + H; dedicated A25 endpoint races/rollback are S/H, not shared PATCH substitution |

## Remaining external obligations

No mandatory Feature1 development/runtime test remains omitted at this source. G1 is historical verified evidence only for the frozen designated synthetic demo; G2 is pinned corrected-source disposable rehearsal/recovery. The stopped demo has not been live re-queried during finalization and has no V28 execution/post-migration result. G3 needs the exact approvals/freshness/compatible-writer plan in [rollout-readiness.md](rollout-readiness.md). C01 remains OPEN/UNASSIGNED with production allowlist/remediation/effective-origin/auth evidence outstanding.88 completed implementation/documentation tasks cannot waive these boundaries.
