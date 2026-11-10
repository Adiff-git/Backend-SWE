# Product listing search contract

Implemented extension of GET /api/products; public access unchanged. This feature contract supplements the existing specs/001-product-catalog/contracts/openapi.yaml. It does not redefine detail/image endpoints. Versioned OpenAPI companion: openapi.yaml.

## Query parameters

| Name | Format and default | Validation |
| --- | --- | --- |
| page | Singleton canonical unsigned decimal integer; default0 | 0..2147483647, no sign/leading zeros except0; duplicates400 |
| size | Singleton same syntax; default20 | 1..100; duplicates400 |
| q | Singleton UTF8 text, trim/collapse whitespace | ≤256 Unicode code points before normalization and ≤16 tokens; blank means omitted; control characters except whitespace rejected |
| categoryId | Singleton lowercase canonical UUID | Empty/malformed400; required with specFilters |
| brandId | Repeated parameter, one lowercase UUID per occurrence | 1..20 occurrences; duplicates collapse; no comma encoding; each empty/malformed400 |
| minPrice / maxPrice | Singleton nonnegative decimal whole VND | 1..30 digits, canonical unsigned syntax; min≤max; fractional/exponent/empty400 |
| sort | Singleton enum relevance,price-asc,price-desc,name-asc,name-desc | Default relevance when q nonblank; UUID ascending otherwise; relevance without q400 |
| specFilters | Singleton URL-encoded JSON array of key groups | 1..8 distinct keys, ≤4 alternatives/key and ≤32 total; duplicate group keys/null/empty/malformed/unknown fields400 |

All unknown parameter names and repeated singleton parameters are400. Reject duplicate JSON object members at every specFilters level before binding, even if values agree. Enforce all collection/token limits before duplicate collapsing. Total decoded specFilters length≤4096 code points; key≤64 ASCII canonical snake_case; text operand1..128 normalized characters, nonblank. Numeric/quantity JSON operands must be finite JSON numbers with ≤30 significant digits and ≤12 fractional digits; use BigDecimal, no binary-double coercion. Price limits apply only to incoming bounds and do not reprice or cap stored catalog values. HTTP request-target size remains an infrastructure setting; this contract guarantees400 for application-level limits on requests that reach the controller, not a universal proxy limit.

Example specFilters shape (uses an OBSERVED key, executable only after its category definition is approved):

```json
[{"key":"physical_ram","alternatives":[{"op":"gte","value":16,"unit":"GB"}]}]
```

Objects accept exactly the fields for their operator. eq/gt/gte/lt/lte use value; between uses min/max with min≤max and no value. Quantity requires exact canonical unit; all other types forbid unit. Text permits eq,prefix,contains; boolean only eq with JSON true/false. Numbers permit eq,gt,gte,lt,lte,between. No ne/not/exists, nulls, stored ranges/lists, case-sensitive switches, custom thresholds or conversion syntax. Ranges are inclusive for between. Different keys AND, alternatives within a key OR. Price lower/upper AND all these groups on the same variant.

## Keyword and ordering

Normative lexical, typed-value and edge rules are [D1–D5](../design-resolutions.md); they supplement this contract. Nonblank q producing zero tokens is400. Deduplicate normalized token strings for membership/ranking in first-occurrence order after the raw16-token limit; retain original normalized ordered query for phrase preference.

Normalization ignores Vietnamese accents including đ/Đ, case, canonical composition and redundant whitespace; it does not translate or stem. Token AND spans Product fields plus one variant. Preserve model/SKU punctuation/digits; query text is not a wildcard/regex language. Short tokens(<4) allow exact/prefix only. Fuzzy is restricted to eligible natural tokens in Product/Brand names. See research.md for the versioned classifier, ranking tuple and calibration gate.

Relevance order is whole-query SKU/reviewed Model > whole-query Product Name > exact phrase (≥2 original token occurrences in one field) > exact token > prefix > partial > fuzzy. Eligibility is checked first. The R3 tie tuple and golden RK01–RK10 in analysis-remediation.md are normative;no identifier-count bonus may let a scattered exact token outrank a Name/phrase. Complete-query model sources require reviewed anchored scope,not inferred columns. Exact subtiers preserve Exact>Prefix>Partial>Fuzzy;final Product UUID ASC breaks ties. Prefix is token-start. Explicit price uses representative displayPrice; name uses normalized text COLLATE C; UUID ascending is the final tie-breaker for either direction. q blank and no explicit sort reproduces UUID ascending.

Representative = cheapest eligible real variant, UUID ascending on equal price. D7 explicitly retains published products with no owned variants only if there are no price/spec filters and all keyword tokens match Product fields; both new fields are then present and null. With price/spec filters or required variant keyword hits, an orphan does not match. Price sort puts nulls LAST for both directions. No missing-variant409 is introduced for listing; existing detail409 stays unchanged. Eligibility includes keyword variant conditions as well as active filters. Keyword matching only Product fields leaves all otherwise eligible variants available. Product rank uses its best eligible match, not necessarily its cheapest eligible variant.

## Category and registry validation

Include category self and descendants. For specFilters, categoryId is mandatory; resolve only approved category-specific definitions. Unsupported keys/types/operators/units or incompatible subtree definitions400. No conversion or inference from unreviewed JSON. For a known category, a key with zero approved applicability in its subtree or a multi-key combination supported by no category in it is400, even with zero products. With partial applicability, inapplicable categories produce no matching variants. A supported valid filter with no matching products returns200 empty. Missing/invalid stored facts do not match, including false vs absent. Approved searchable facts may contribute to keyword-only queries without requiring categoryId.

Unknown but syntactically valid category/brand IDs normally produce200 empty. Unknown brand alternatives contribute no OR match, so known-brand matches are retained in a mixed selection. With specFilters first validate global approved key/type/unit/operator syntax, then resolve known subtree definitions; an unknown category has no matches, while an unapproved key is still400. This avoids using an empty catalog as a bypass for unsupported filters.

## Responses and compatibility

200 application/json: same items/page/size/totalItems/totalPages envelope. Each item retains id,name,category{id,name},primaryImageUrl and adds displayPrice{amount,currency:"VND"},representativeVariantId. Both added properties are present but nullable ONLY for the D7 no-variant compatibility branch; otherwise both are nonnull. Prices are exact whole JSON numbers; clients requiring arbitrary precision must parse accordingly. Do not expose score, registry evidence or private keys. Only PUBLISHED products contribute to candidate counts/results. Empty and later pages retain accurate distinct totals at candidate statement snapshot. Signed-image expiration/publication handling remains as existing Catalog.

400 application/problem+json: preserve type="about:blank",title,status,detail and existing optional instance behavior. Example: {"type":"about:blank","title":"Bad Request","status":400,"detail":"Unsupported specification unit"}. Do not promise a new structured code/fieldErrors extension; public detail identifies the invalid parameter without SQL/path/secrets. Authentication failures for an explicitly supplied invalid bearer retain existing401 handling; no new credentials required. No listing409 is specified for missing variants; that was an unsupported earlier planning assumption. Detail409 and other endpoint contracts are unchanged. Infrastructure/signing failures retain current error handling; do not translate infrastructure failures into200 empty or400.

Old accepted page/size requests preserve membership/order/fields; two additive fields are intentional. Previously rejected new parameter names become accepted only as documented. Detail, wishlist summaries, images and ProductSummary's other consumers remain unchanged. No filtering in memory after pagination and no variant-level count duplication.

Concurrent mutations: counts/page candidates share one eligibility statement snapshot; final publication checks can suppress newly private items. No stable snapshot across HTTP requests is promised. Acceptance for complete stable pagination assumes unchanged data, with separate concurrent publication tests.

## Fuzzy capability response semantics

pg_trgm is a prerequisite of feature rollout even if operator configuration disables fuzzy evaluation; missing startup capability fails readiness/rollout, not a successful degraded release. With a nonblank keyword,200 responses include X-Catalog-Search-Mode:fuzzy-enabled in normal mode, or exact-only when the operator explicitly sets fuzzy.enabled=false. The latter keeps exact/prefix/partial matching but is diagnostic and does not satisfy full-feature fuzzy acceptance. This adds header metadata, not JSON fields or a client toggle. Blank/absent q needs no mode header or fuzzy evaluation.

If required search capability is lost after startup in either configured mode, nonblank keyword search returns503 application/problem+json (type about:blank,title Service Unavailable,status503,detail Fuzzy search capability is unavailable); mark readiness unhealthy and surface an operator incident. Never silently fall back to exact-only, return200 empty, install extensions in GET or add request retry loops. No-keyword requests retain their ordinary path only while their required DB objects remain healthy. D3 defines the full mode matrix; D6 defines separately authorized recovery.

## Typed keyword and lexical examples

D1 canonical decimals/quantity views/boolean literals contribute only through approved searchable definitions.16 or16.0 matches a typed numeric16 exactly; a quantity16 GB exposes amount16 and unitgb, with no implicit MB/TB/GiB conversion; true/false only match whole boolean tokens.16GB is one protected identifier and is not expanded to16 GB. Typed equality does not rewrite text model tokens. Text specifications never fuzzy-match. Examples are synthetic semantics, not approval of a production key.

D2 specifies exact connectors and Unicode whitespace: WH-1000XM5/i7-1360P/USB-C remain protected,3.50mm stays one token,3.50 mm stays two, A--B splits, comma never means thousands grouping, C++ is protected. D4 rejects nonblank zero-token input and duplicate JSON members, while whitespace-only remains omitted.

## Operational probe and implementation acceptance scopes

[analysis-remediation.md](../analysis-remediation.md) R6 defines a keyword-only HTTP health probe using this existing endpoint (`q=catalog-readiness-probe&page=0&size=1`). A nonblank keyword checks required search capability in both modes even for protected tokens or no matches;healthy200 is operationally healthy and recognized capability loss503 makes the existing HTTP probe fail. Blank/absent q behavior is unchanged. Coordinate the future Compose URL with the feature-capable binary;no new API route/dependency is introduced.

This is the COMPLETE feature contract. Foundation+US1.core is only a development/review increment:it does not claim approved technical-keyword/filter support or full US1 compliance. Those paths require real registry approval,not a dummy manifest. Every documented request behavior is required before full local acceptance. The partitioned corpus and all20FR/6SC are mandatory at final integration;local acceptance may coexist with deployment NOT_VERIFIED/BLOCKED,which never authorizes rollout.

## Local MVP registry and configuration

Owner-approved v1 enables `display_diagonal` (Laptop/Tablet,quantity inch), `physical_ram` (Laptop,quantity GB), `installed_storage_gb` (Laptop,quantity GB), `display_resolution` (Laptop,text). All four are searchable/filterable;resolution is not a Product identifier. All technical values remain non-fuzzy. Battery is deferred. Units are exact;no conversion. Runtime category roots use unique actual Backend names/UUIDs;missing/ambiguous family bindings fail closed independently. See mission-scope.md for read-time exclusions and approval receipts.

`catalog.search.fuzzy.enabled=true`, `catalog.search.fuzzy.threshold=0.32` are operator configuration. The selected threshold comes from controlled typo-pair measurements and exhaustive source-token candidate checks;it is not an all-catalog human relevance guarantee. Unverified model anchors never get rank7;exact owned eligible canonical SKU equality does.

The calibrated local matcher additionally requires one insertion,deletion,substitution or adjacent transposition for fuzzy admission. This is a conservative recall trade-off,not a change to the seven ranking tiers. Two-edit typos may be missed;Canon/Canyon and Intel/Inter remain measured one-edit ambiguities. Invalid stored text contributes no field evidence and is not rewritten;invalid query controls still400. Generated cache fields are internal and never serialized.

Specification inheritance stops at another recognized catalog family,including Smartphone/Tablet below Laptop;plain category browsing still includes all descendants. Only uniquely resolved actual Backend approved roots can supply supported bindings. The source1500 workload is controlled fixture evidence,not persistent import/deployment proof.
