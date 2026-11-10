# T012/T017 — request parser and listing mapping verification

Status: PASS for T012 and T017 only. Required T011 is complete;the existing T001 baseline and T007 primitive behavior are preserved. Branch:features/SearchAndFilter;HEAD:c5bd4e7eeb39d9b06a0c7e36af9a65635058613f plus dirty changes. No public endpoint,repository query,fuzzy/ranking or specification filtering was implemented.

## T012 transport parsing

ProductSearchParameterParser accepts already URL-decoded parameter lists. Its private Jackson3 mapper enables strict duplicate-member detection at EVERY object nesting,FAIL_ON_TRAILING_TOKENS,exact BigDecimal tree numbers and retained trailing zeros. It is neither a global ObjectMapper nor a Spring component. Tests prove ordinary mappers retain their prior behavior.

Nine parameter names are whitelisted;unknown/null names,null lists/null values,empty lists and duplicate singletons fail via InvalidCatalogRequestException (existing400 mapping). Repeated brandId is accepted without CSV;raw occurrence bounds precede first-occurrence dedup. Canonical page/size/UUID/whole-price lexemes are checked before conversion;defaults0/20,max100,int32 page and30-digit whole VND bounds remain. Keyword controls/raw limits/zero-token rules reuse T007. Explicit relevance requires a nonblank token-bearing keyword;omitted sort remains unspecified for the later workflow to choose its documented default.

Decoded specFilters is bounded before JSON parsing;categoryId is required. Groups1..8,alternatives1..4,unique group keys,allowed object fields,null rejection and all supported operator shapes are checked. Typed number/range precision and exact decimals are retained;strings are not coerced to numbers,booleans are not numbers. Equal duplicate JSON members reject before DTO binding. Alternatives are NOT collapsed;raw bounds and validation therefore cannot be bypassed by semantic duplication. Parser checks transport/operator shape,NOT approval or category applicability of any key;synthetic test keys grant no registry support. Unit authorization/quantity required-unit checks need approved registry semantics in T040;no conversion/filter execution is enabled. Endpoint wiring remains T018 and blocked by its own gates.

Ten focused parser tests include transport URL roundtrip (decode once),global mapper isolation,operator matrix,explicit null/malformed/unknown/nested duplicate members,numeric/string limits,leading zeros/exponents/page overflow,duplicate brands/groups,limits before dedup and exact30-significant/12-fractional digits. JSON fractional trailing zeros are retained so an input with13 fractional digits cannot bypass its bound.

## T017 listing-only mapping

ProductListingItem extends unchanged ProductSummary and adds exactly displayPrice/representativeVariantId. Mapper accepts a preloaded summary,already selected BigInteger amount and UUID. It constructs SellingPrice(amount,VND),copies four original summary fields,and never accesses a repository/entity relationship,selects variants or calculates a business price. Mixed null pairs fail structural construction;the D7 pair is explicitly present/null,never synthetic zero/UUID.

Incremental Java compatibility bridge:ProductPage.items is List<? extends ProductSummary>,allowing both existing summary callers and the new listing subtype. This avoids changing service orchestration outside T017;its JSON envelope remains items/page/size/totalItems/totalPages. Only actual listing items gain the two fields. Existing endpoint still returns its old summary instances until T018 wires repository-selected metadata;this increment does not claim display fields are live. ProductSummary,ProductDetail,Wishlist and their contracts are untouched. Existing brand/specification detail mapping and its tests were preserved,with focused listing tests appended.

## Executed tests

```sh
mvn -o -f demo/pom.xml -Dtest=ProductSearchParameterParserTest,ProductSearchNormalizerTest,ProductSearchRequestTest,ProductCatalogConverterTest,ProductListingControllerTest,ProductDetailControllerTest,ProductCatalogServiceImplTest,WishlistConverterTest test
```

Exit0/BUILD SUCCESS;8 suites,50 tests,0 failures/errors/skips. First run before added transport-isolation coverage had49 tests passing;the final50-test report is authoritative. [parser-listing-results.json](parser-listing-results.json) records case identities,source/report hashes and counts. Java25.0.4.1,Maven3.8.7,Surefire3.5.6,JUnitPlatformProvider,offline existing dependencies. Full integration/performance gates are not run or closed.

Pending registry T003,alphabetic scope T004 and semantic OpenAPI T006 remain BLOCKED. No endpoint hook/SQL migration/dependency/commit/push occurred. T013 evidence is separately scoped in normalization-verification.md.

## Post-execution preservation and scope checks

Pre/post SHA-256 comparison allows only tasks.md,ProductPage.java,ProductCatalogConverter.java and ProductCatalogConverterTest.java among pre-existing files. Removing ONLY the newly appended listing mapper/imports/tests reproduces the exact original converter and test hashes,including all pre-existing Brand/Specifications work. Reversing only the items wildcard reproduces the original ProductPage hash. All other pre-existing source/tests,request primitives,contracts,ignore files,checklist markers,prior evidence,constitution and V1–V14 migrations remain byte-identical.

Eleven new files belong to the selected parser,listing DTO and parity evidence/probes. Nine completed tasks are exactly T001/T002/T007/T009/T010/T011/T012/T013/T017;remaining57 stay unchecked. File links,whitespace,JSON/Python syntax and git diff --check pass. No T013-labelled container remains. Before/after extensions.yml is absent;no hooks dispatched. No task outside the selected scope,shared database migration,commit or push was executed.
