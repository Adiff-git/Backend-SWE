# Implementation coverage — current local scope

All20FR/6SC are retained. PASS below is for the owner-authorized local fixture/approved-definition scope,not deployment or semantic certification of1500 facts.

| ID | Behavior | Executed evidence |
|---|---|---|
| FR-001 | PUBLISHED selection and final publication/signing rechecks | ProductSearchRepositoryIT;ProductListingApiIT real races |
| FR-002 | Legacy0/20/max100 and UUID ordering,additive listing type | ProductSearchBaselineRepositoryIT;ProductListingControllerTest |
| FR-003 | displayPrice/representativeVariantId paired-null and unchanged consumers | CatalogContractIT;converter/detail/wishlist suites |
| FR-004 | Product/Brand/SKU/Description and four approved searchable keys | technicalKeywords_shouldUseApprovedFacts_andTypedEquality;registry tests |
| FR-005 | Vietnamese/NFD/đ/whitespace normalization,versioned immutable functions | ProductSearchNormalizerTest;V18:53 primitive/4500 source-field parity checks |
| FR-006 | Token AND,protected identifiers,same-variant keyword sources | keywords_shouldNormalizeAndRequireEveryToken_acrossProductAndBrand;multipleSpecificationGroups... |
| FR-007 | Natural-name fuzzy≥4,approved exclusions,one-edit guard,configurable0.32 | FuzzyCalibrationIT;identifier/collision repository tests;vocabulary tests |
| FR-008 | Seven tiers and eligible best-rank tuple with deterministic ties | All RK01–RK10 repository/HTTP assertions,including RK07 price/spec/combined |
| FR-009 | relevance/default and explicit price/name sorting | ProductSearchControllerIT every-sort page oracles |
| FR-010 | Category self/descendants,UNION cycle termination,brand OR | categoryCycles...;categories_shouldIncludeDescendants_andBrandsUseOr;HTTP3-level hierarchy |
| FR-011 | Inclusive exact whole-VND price bounds | parser/service validation;configurations_shouldRequireSameVariant_andSelectMatchingPair |
| FR-012 | Approved category-aware4-key registry;unsupported400;known-quality exclusions | registry startup negatives;approvedBindings...;invalidFilters... |
| FR-013 | Quantity6/text3 operators,exact canonical units,no coercion | quantityOperators...;textOperatorsAndAlternatives...;invalidLegacyDocuments... |
| FR-014 | OR alternatives and AND groups on one owned Variant | multipleSpecificationGroups_shouldNotCombineDifferentVariants_orKeywordSources |
| FR-015 | Cheapest eligible(price,UUID) pair,independent best eligible rank | baseline tied-price/huge-price test;RK07/RK10;orphan tests |
| FR-016 | One candidate/count statement snapshot,ID-first page and batch loads | beyond-last/max-offset repository cases;every-sort/filter/combined HTTP pages |
| FR-017 | Strict transport/JSON/duplicates/null/unsupported validation and ProblemDetail | ProductSearchParameterParserTest;ProductListingControllerTest;HTTP400/503 tests |
| FR-018 | Canonical business data and other Catalog consumers preserved | fresh/upgrade business-field snapshots;V1–V15 hashes;full Catalog/Detail/Wishlist regression |
| FR-019 | Measured1500 workload,no N+1,bounded statements/results | 3 executed timing classes;source-workload-performance-final.json;SQL capture20/100 with20-variant skew |
| FR-020 | Capability startup/request checks,mode headers,503 and operational keyword probe | CatalogSearchMigrationIT failures;SearchCapabilityIT;SearchExactOnlyIT;Compose probe |
| SC-001 | Controlled approved lexical/ranking examples and typo positives pass | Golden manifest/RK01–RK10;15/15 controlled typo positives.2 diagnostic confusable negatives remain;not perfect real relevance. |
| SC-002 | Cross-variant false positives rejected and eligible display pair correct | repository multi-group/config tests;HTTP RK07;D7 paired-null cases |
| SC-003 | Unchanged-data pages repeat without omissions/duplicates,exact counts | HTTP all sorts,q-only/filter-only/combined pages and repository beyond-last counts |
| SC-004 | Existing Catalog/Detail/Wishlist/security/image behavior retained | Full suite union,publication race RED→GREEN,identity/hash preservation |
| SC-005 | Invalid matrix400 and valid no-match200 | parser/MVC/actual SQL/HTTP tests,including duplicate JSON and unsupported filters |
| SC-006 | Retained1s goal at≥1000 met;practical500ms goal separate | 1500 HTTP measurement and1000 legacy HTTP benchmark. Broad laptop query is above500ms;no timeout or SQL-budget failure. |

RK01–RK06/RK08–RK10 remain US1.core;RK07 remains US3.configuration. All ten are mandatory in final integration. Consolidated test files replace proposed per-story filenames without changing expectations. Numeric/boolean keys are not invented;only the approved quantity/text definitions are active.
