# T001 — Catalog / toolchain / security baseline

Captured UTC: 2026-11-08T16:42:18.347829+00:00

**Status: PASS for T001 baseline collection only.** No feature implementation, runtime verification gate, registry approval or deployment acceptance is implied. Authorization covers ONLY T001;T002–T066 remain unexecuted.

## Exact task and scope

T001 has no dependency (initial read-only baseline). Acceptance:record branch/worktree edits,Java25/Boot4.1.2,Maven discovery,dependency/tool availability,published-only ID-first listing,0/20/max100,shared summary consumers,D7 source behavior,V1–V14 checksums,and reserve free successor migration names. The referenced pom.xml,API contract and migration directory are inspection inputs,not permission to edit them.

## Repository and preservation

- Root: `/home/hza/Downloads/SWE/Backend-SWE`.
- Branch: `features/SearchAndFilter`.
- HEAD: `c5bd4e7eeb39d9b06a0c7e36af9a65635058613f` — Configure Mailpit development stack and local validation.
- No staged changes and no unmerged paths (`git diff --cached --stat`;`git diff --name-only --diff-filter=U`).
- Existing unstaged work:10 tracked files (104 insertions,15 deletions),plus untracked BrandSummary.java and the feature directory. Existing changes were inspected and preserved.
- Existing Product Catalog enhancement:add nullable brand to detail,technicalSpecifications to VariantSummary,brand fetch join,corresponding converter/tests/base OpenAPI changes. These predate T001 and are NOT Search & Filter implementation or registry approval.
- Existing .gitignore change removes ignore rules for specs/.agents/.specify/.codex and retains docs/Database-Design exclusions. No ignore file is edited by T001.
- `git check-ignore --no-index --stdin` over every existing feature file returns1/no matches. Future evidence/baseline.md and proposed migration paths also are not ignored. Feature docs are currently untracked,not lost or silently excluded.
- No merge conflict found;`git diff --check` passes. Later DTO/repository/converter/contract work must incorporate the existing uncommitted detail changes rather than overwrite them.
- Another registered worktree is `/home/hza/Downloads/SWE/Backend-Catalog-Enhancement` at0aebbf8,marked prunable. No worktree/prune/ref change performed.

Initial worktree status:

```text
 M .gitignore
 M demo/src/main/java/com/example/demo/catalog/converter/ProductCatalogConverter.java
 M demo/src/main/java/com/example/demo/catalog/dto/response/ProductDetail.java
 M demo/src/main/java/com/example/demo/catalog/dto/response/VariantSummary.java
 M demo/src/main/java/com/example/demo/catalog/repository/ProductRepository.java
 M demo/src/test/java/com/example/demo/catalog/ProductDetailApiIT.java
 M demo/src/test/java/com/example/demo/catalog/controller/ProductDetailControllerTest.java
 M demo/src/test/java/com/example/demo/catalog/converter/ProductCatalogConverterTest.java
 M demo/src/test/java/com/example/demo/catalog/repository/ProductDetailRepositoryIT.java
 M specs/001-product-catalog/contracts/openapi.yaml
?? demo/src/main/java/com/example/demo/catalog/dto/response/BrandSummary.java
?? specs/007-product-search-filter/
```

## Checklist and workflow scope

| Checklist | Total | Checked | Unchecked | Marker status |
| --- | ---: | ---: | ---: | --- |
| requirements.md | 16 | 16 | 0 | Complete |
| design-quality.md | 40 | 0 | 40 | Reviewer markers incomplete |

Custom inline assessment remains32 PASS/0 FAIL/8 NEEDS VERIFICATION,not reviewer approval or feature test evidence. No checklist marker is changed. The user explicitly authorized the read-only T001 baseline despite these known pending evidence gates;no general checklist bypass or later-task execution is inferred. G-S/G-ID/G-N/G-F and local/deployment gates remain open. `.specify/extensions.yml` absent before execution;no hooks dispatched.

## Toolchain and dependency availability

| Evidence | Observed result | Limit |
| --- | --- | --- |
| `java -version`;`javac -version` | OpenJDK/Javac25.0.4.1 | Available locally |
| `demo/pom.xml` and offline effective POM | Java25;Spring Boot parent4.1.2;single demo module | No dependency/build file edited |
| `mvn --version` | System Maven3.8.7;Java25;UTF-8 | Available;not the wrapper version |
| `demo/.mvn/wrapper/maven-wrapper.properties` | wrapper3.3.4;configured distribution Maven3.9.16 | No cached distribution found in ~/.m2/wrapper/dists;wrapper not invoked or downloaded;other cache locations not ruled out |
| `docker --version` | Docker CLI29.6.0 | Daemon/images/capabilities NOT_VERIFIED;belongs to later prerequisites |
| `command -v psql` | Not on PATH | No client installed;no database accessed |
| Python/Git | /home/hza/miniconda3/bin/python3;Git2.43.0 | Available |
| Python modules | yaml/jsonschema present;openapi_spec_validator absent | No semantic OpenAPI PASS;T006 remains pending |

Read-only model inspection command (exit0/BUILD SUCCESS;no lifecycle tests or migrations):

```sh
mvn -o -f demo/pom.xml help:effective-pom -Doutput=/tmp/t001-effective-pom.xml -Dstyle.color=never
```

Effective POM SHA-256: `87b9b8337ccab0440f330f63ae7537b5fbc0555d5142874ee34bb728a8a6c339`. The temporary file is not a committed/runtime artifact. Offline descriptor warnings report uncached release-plugin2.5.3/enforcer-plugin3.6.3;they did not prevent effective-POM generation. No download/install/retry online was performed.

All21 direct effective dependency artifacts exist in the default ~/.m2/repository cache. This does not prove all transitives/plugins can execute offline. Existing dependencies include MVC,Security/OAuth2,Validation,JPA,Flyway/PostgreSQL,Lombok,Springdoc3.1.0,AWS SDK BOM2.57.2,S3,Testcontainers PostgreSQL/LocalStack and Boot test starters. No new dependency is introduced.

## Maven test discovery — configuration vs historical execution

- Effective plugins:compiler3.15.0;Surefire3.5.6 default-test in test phase;Failsafe3.5.6 bound to integration-test and verify;Boot plugin4.1.2. No custom includes/excludes were found in effective Surefire/Failsafe configuration,so standard discovery applies.
- Test source inventory:68 *Test.java,73 *IT.java,18 other Java support files. Counts describe filenames,not proof of discovered/executed tests.
- Existing XML reports provide actual historical discovery evidence:

| Reports | XML suites | Tests | Failures/errors/skips | Latest file mtime UTC |
| --- | ---: | ---: | --- | --- |
| demo/target/surefire-reports/TEST-*.xml | 5 | 20 | 0/0/0 | 2026-11-08T09:36:41.509230+00:00 |
| demo/target/failsafe-reports/TEST-*.xml | 4 | 13 | 0/0/0 | 2026-11-08T09:36:53.708357+00:00 |

Surefire suites:ProductListingControllerTest,ProductDetailControllerTest,TechnicalSpecificationValidatorTest,ProductCatalogConverterTest,ProductCatalogServiceImplTest. Failsafe suites:ProductDetailRepositoryIT,ProductDetailApiIT,CatalogEnhancementMappingsIT,ProductListingApiIT. Report properties record Java25.0.4.1.

These pre-existing reports cover a subset,have no verified source-revision binding,and are not a fresh T001 run or full-regression acceptance. No test goal was invoked. T019/T031/story gates/T061/T062 must obtain their own actual discovery/results later.

## Catalog and security baseline — verified source behavior

| Topic | Current source evidence | Required preservation |
| --- | --- | --- |
| Public/security | common/security/CatalogSecurityConfiguration.java:GET /api/products and /api/products/{id} permitAll;stateless bearer processing;CatalogSecurityProblemHandler returns401/403 ProblemDetail | Public reads do not bypass supplied invalid bearer processing;image POST/admin rules untouched |
| Existing parameters | catalog/controller/ProductCatalogController.java:35–61 accepts only singleton page/size;canonical nonnegative integers;unknown/duplicate parameters400 | q and other search parameters currently rejected;new contract is future behavior |
| Pagination | catalog/service/ProductCatalogService.java:DEFAULT_PAGE0,DEFAULT_SIZE20,MAX_SIZE100 | Preserve defaults,bounds and ProductPage fields |
| ID-first published query | catalog/repository/ProductRepository.java:17–23:SELECT id WHERE status=PUBLISHED ORDER BY id ASC;separate published count;batch category load | No collection-fetch pagination,no variant count multiplication |
| Batch/projection | catalog/service/ProductCatalogServiceImpl.java:107–149:ID page,batched primary-image/product fetch,publication recheck,ordered projection;ProductPage total metadata from ID page | Existing signing path needs short FOR SHARE locks/write-capable transaction;do not make it readOnly merely because GET |
| DTO consumers | ProductSummary{id,name,category,primaryImageUrl};ProductPage items;ProductDetail extends ProductSummary;WishlistServiceImpl/WishlistItemResponse/WishlistConverter consume summaries | Later listing additions must not accidentally change detail/wishlist payload or summary semantics |
| D7 listing/detail | listProducts never loads/asserts owned variants;getProduct:157–159 throws CatalogConflictException for empty variants | Listing orphan behavior cannot be inferred from detail409;preserve existing paths |
| D7 database constraint | V3__enforce_catalog_relationships.sql:deferred constraint triggers require at least one variant at commit | Legacy orphan is exceptional fixture/data state;do not weaken constraints or claim actual live orphan existence |
| Contracts | specs/001-product-catalog/contracts/openapi.yaml current listing200/400,published-only,defaults0/20/max100;feature contracts/openapi.yaml defines future additive fields/search | No live endpoint/schema conformance inferred from source inspection |

Actual database contents,applied Flyway history,extensions,normalization parity,registry approvals and deployed API behavior remain NOT_VERIFIED. No GET request,DB connection,container startup or migration was performed.

## Migration checksums and successor reservation

Repository files V1–V14 all match their HEAD bytes. The hashes below are SHA-256 file-integrity snapshots,NOT Flyway schema-history CRC checksums or evidence that these migrations are applied to any target.

| File | SHA-256 |
| --- | --- |
| V1__create_catalog_core.sql | `57335af270525b03f723a14bcadf17c16a2b23c3ce2181061660b17193ddc613` |
| V2__create_product_images.sql | `87e45d16aecb62f4c31c53a71d01173f31e415a9430684dce2d60bcc2c275e09` |
| V3__enforce_catalog_relationships.sql | `d3ad76330c02dba1f9f2a1a973664441986d422ac7a57e1c8e4dd1a8c599d454` |
| V4__create_users.sql | `dd065d0c85e93a87573a9396ec8b427c9a4a9e7a80df272d279c5e412497f628` |
| V5__create_addresses.sql | `a303f220e2c59feb0fc538648ace280a954723e5c89c43ed58fdf7597fb787a2` |
| V6__authorities_and_username_policy.sql | `976182f523d5f8910477ff81ede01d770313204f7109293eaa42f75763a4d6bf` |
| V7__authentication_sessions.sql | `a063ab4e474da4b63187af2653b7b83be9dc1b7b8083da89426479a6fdbf36f0` |
| V8__image_uploads.sql | `3e142dc5084141fd3808b40f0b29de14408380b3c5694d2768738037a1114723` |
| V9__user_profile_images.sql | `795c2571d9ad36ca95f2c9f4a230d9d2ab05a2dcee90e9c83188d2c3db99a5ac` |
| V10__create_wishlist_items.sql | `4643e4fdfeccaf2339dcb922cf3315ffa5973e73833a8a5d0f49ba4b1d9aef47` |
| V11__catalog_data_enhancement.sql | `8d69dc8b8bc5b648c4fd6cfc5e95574c3ebe6c3f29acedcc63f0f459d3d783cd` |
| V12__product_image_attachments.sql | `26b01726a537c448b617e3cba8e76f49c88d92216f03c4b5d10c24e8316574ed` |
| V13__email_google_auth.sql | `8051bb28f137088b0816476fb82373641e10c059f933383deff806a42c3b6085` |
| V14__remove_request_rate_limiting.sql | `79bb9bd5dcc159bd85073d84d3d78736e1cbd008e8a6626d4ef28f65720c55d6` |

No migration at version15 or16 exists in the inspected current migration directory;both exact paths are absent and not ignored. **Document-only reservations for this feature:**

- `demo/src/main/resources/db/migration/V15__catalog_search_support.sql` — normalization/capability support,subject to T002/T013/T015 evidence.
- `demo/src/main/resources/db/migration/V16__catalog_search_indexes.sql` — measured indexes,subject to T058/T059 justification.

No placeholder SQL file was created and no existing migration changed. Reservations are not a cross-branch lock:recheck all version/name collisions immediately before later creation;report/reconcile references if another change occupies the version. Existing feature references still agree with V15/V16.

## Pre-existing modified/untracked artifacts — preservation hashes

Hashes captured before T001 writes. T001 may change only this baseline evidence and its own completion/current-state line in tasks.md.

| Path | Pre-T001 SHA-256 |
| --- | --- |
| .gitignore | `3af483dcc74c97e4e6f737d3e7463a0a4cf923fdfe148769215e6effe7527aed` |
| demo/src/main/java/com/example/demo/catalog/converter/ProductCatalogConverter.java | `84f2af64c6be75f8fdd134a492f7b541636de2f569e0503a5fd82e8d45078b04` |
| demo/src/main/java/com/example/demo/catalog/dto/response/ProductDetail.java | `ba627c558b13662748ab19f6ae112975ecdfe773e7079b4b4cb5198ade7b7979` |
| demo/src/main/java/com/example/demo/catalog/dto/response/VariantSummary.java | `0be3ada8fe9654aa395a4b7fe74be5d499f5ed2ad3a3b7489723c19bae697118` |
| demo/src/main/java/com/example/demo/catalog/repository/ProductRepository.java | `a2b95eecc5d1529a9e02273399df802c07cb9e6e539a7576f64752782d5763b0` |
| demo/src/test/java/com/example/demo/catalog/ProductDetailApiIT.java | `541194a09305f920ffb1fce41f57783f6796fa18728402fe46d850f3be80a40d` |
| demo/src/test/java/com/example/demo/catalog/controller/ProductDetailControllerTest.java | `7086c9e987dd0c87c01efb44e1571cff3c6adb9ca67e4dad9b3c89e5decaa24d` |
| demo/src/test/java/com/example/demo/catalog/converter/ProductCatalogConverterTest.java | `4afbe2d7814c5a0aea80edfd262a6d66a79eb001755c23ff33cf60042146f2fb` |
| demo/src/test/java/com/example/demo/catalog/repository/ProductDetailRepositoryIT.java | `78778fbc36645cc07225d8272982e3851c82815d9018950b4654a6aa974b872b` |
| specs/001-product-catalog/contracts/openapi.yaml | `7a0062156c1637283772458ace4750d59b8834e5770c7eb2495263d80bce0f83` |
| demo/src/main/java/com/example/demo/catalog/dto/response/BrandSummary.java | `bc528c3699e1db721c7ecc5b16d376eeb05cbc10acd4cea7bf5829b1629c96c2` |
| specs/007-product-search-filter/analysis-remediation.md | `8cf26a9aa97f2c849162126ff3d624076860ec0745053ebabbb1597d80dbca6d` |
| specs/007-product-search-filter/checklists/design-quality.md | `edfa508559c1f35de69ef251a094ef44157284dbf571b1dd7f5cd7ee648a08ec` |
| specs/007-product-search-filter/checklists/requirements.md | `3057e457eb7e31aaea41fe059d0e7833d7d4c1274a7313f16ed0336109552d42` |
| specs/007-product-search-filter/contracts/openapi.yaml | `1ea2e2aa9d9586522fbd2884e2a153c0089492f031ef5a314f710746abe35c76` |
| specs/007-product-search-filter/contracts/products-search.md | `3e43066c6e2450025dbee51d4cb2d764ce881f16b47b97d7e4b0199a5d150a5b` |
| specs/007-product-search-filter/data-model.md | `400e9f3309514f749742bb2385fcbbcf84718f5c5ca39f4c9e18861680904d28` |
| specs/007-product-search-filter/design-resolutions.md | `5b94ae2a392e6a5acb7570a5e9242276358030943f1ea54989ad3f2818160e6d` |
| specs/007-product-search-filter/plan.md | `9ed7ad423590375af7179ecde8913cda3f71df4848adcccffb4997178bf48b63` |
| specs/007-product-search-filter/planning-validation.md | `116b5a7f43a7070458094d708051cc2ef63a1fb5c23ce87e0e812182497ab36b` |
| specs/007-product-search-filter/quickstart.md | `369ba77c961a908a83d798d107fee14495c8b132128743a507aec3c864ce1564` |
| specs/007-product-search-filter/registry-inventory.md | `5d143e3b33c10e81c628cc0bd0f07fc8fff89f77095d7f8e4004bb007d6c6900` |
| specs/007-product-search-filter/research.md | `51bd75b077531aa66d34942b9dfdfaaa6142b7a6f8a4f4d2558c59b8c69f22f1` |
| specs/007-product-search-filter/spec.md | `1691a7f5a2a382e216b77608d49ebe6acdb3e26d5a1dec1ad13e52a5dc56fba0` |
| specs/007-product-search-filter/tasks.md | `c28d4ce538aef4734405f413ec06320c2d89cf61dbd92725709d701f62fbf8cc` |

## Acceptance and next boundary

- Every T001 acceptance item above has source/tool/file evidence;missing runtime capabilities are explicitly unverified rather than claimed PASS. No migration filename collision was found.
- T002–T066 remain unchecked and unexecuted. After T001,the dependency-ready tasks are T002,T003,T004,T006,T007,T009,T011. Evidence collection may begin on its own prerequisites;T003 still requires an external authorized approval and unavailable tooling/sign-off can block its relevant task.
- Eight NEEDS VERIFICATION items,reviewer markers and all ranking/registry/deployment decisions remain intact. T001 completion is not their approval.
- Stop after writing/validating this baseline and marking only T001.

## Post-write validation

- PASS:pre/post snapshot comparison preserved all9,634 pre-existing files other than the authorized tasks.md edit;only evidence/baseline.md was added. This includes all existing application/tests,ignore files,contracts,constitution,migrations and the other feature documents.
- PASS:only T001 is marked[X];all65 later tasks remain unchecked. git diff --check and direct whitespace checks pass;baseline.md is not ignored. No extension hooks are registered because extensions.yml is absent after execution too.
- No fresh test suite,PostgreSQL/registry/OpenAPI capability gate or deployment verification was executed. Stop after T001.
