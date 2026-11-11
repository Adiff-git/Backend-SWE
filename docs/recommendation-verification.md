# Recommendation MVP verification (2026-11-11)

Contract: v1, canonical `AI-SWE/src/ai_service/recommendation/inference.py`.
Architecture: Customer JWT -> Backend owner-scoped history/current wishlist + complete eligible
catalog -> keyed inference HTTP -> request-local TF-IDF hybrid scoring -> Backend strict ID validation,
current publication locks, batch variant/media hydration -> existing ProductListingItem cards.
Public/general requests stay Backend-controlled aggregate rankings; no guest tracking.

## Test status

Final focused run on Java 25.0.5.0.3: 11 AI client unit tests and 31 PostgreSQL integration cases
(10 recommendation, 20 behavior, one real socket two-service case), no failures/errors/skips.
AI: 255 tests pass, Ruff pass, Compose configuration validates. Existing agent/chat tests remain.
Final complete Backend regression: `bash mvnw -q verify`, exit 0 on fresh Java 25.0.5.0.3.
357 unit tests (77 classes) + 395 integration tests (87 classes) = 752 distinct test identities;
0 failures, 0 errors, 0 skips. PostgreSQL suites ran serially; existing functional and performance
cases were not excluded and their budgets were not changed.
Initial full run had 356 unit + 393 IT, one infrastructure error spawning uv after system JDK
changed from 25.0.4.1 to 25.0.5.0.3 while the old JVM was still alive. All other tests passed;
restarting with matching JVM/helper resolved the error in focused verification. No test weakened.

## Actual two-service verification

`RecommendationHttpIT` launches actual loopback FastAPI/uvicorn on an allocated port,
Spring Boot on a random HTTP port, and Testcontainers PostgreSQL 17.11 with all V1..V20 migrations.
No production keys or shared services. 1500 deterministic products/variants in 3 categories, without media, are Backend fixtures,
not Catalog-Data canonical identities. Customer login uses actual JWT issuance/validation;
existing behavior POST writes two owner events. A spy delegates to the real HTTP AI client and
asserts minimized request fields and all 1500 candidates. AI is genuinely executed, not mocked.
Checks: personalized laptop interest, other Customer isolation/cold start, guest general,
similar/source exclusion, public-safe DTOs, internal unauthenticated 401 and real stopped-AI fallback.
Guest/general/similar calls create no events; the event table remains at the two explicit events.

Latest complete-regression serial timings (milliseconds):

```text
1500 synthetic Backend-owned products; serial HTTP samples
Catalog preparation ms=8.943715
Full HTTP cold/warm ms=[133.235219, 77.679741, 75.653216, 76.282484, 73.046215, 81.664355, 70.772521, 73.103556, 105.036956]
AI HTTP inference warm ms=[51.800934, 51.563392, 48.403221, 50.145397, 51.218482, 47.369743, 48.677285, 52.305177]
Hydration ms=[5.159786, 3.042065, 4.393835, 4.890934, 2.597813, 2.204459, 3.951783, 3.385257]
Outage fallback HTTP ms=[45.505742]
Verified: owner events, minimized payload, laptop personalization, guest/cold start, similar/source exclusion, internal 401, outage fallback.

```

These are local observations with synthetic short product metadata and 2 recent events;
eight warm samples are not a production SLO or a relevance accuracy metric. Catalog prep is one
sample; AI timings include internal HTTP/JSON and TF-IDF fitting; hydration includes SQL/DTO;
full HTTP includes JWT validation, snapshot, aggregation, AI, hydration and JSON. First request is
cold inference, not whole-process boot duration. A typical local MVP expectation is subsecond for
this workload, with configurable 500ms connection / 3000ms AI response budgets, no retries.
Separate 1500-product/500-distinct-event engine runs measured 242..251ms after vectorization,
versus 5461ms initially; see AI verification and its reproducible benchmark script.

## Eligibility and security evidence

Tests cover guest/Customer/admin access, owner-only behavior, unknown/duplicate query fields,
limits, complete bounded published candidates, real representative variant/price, null price,
media signing, empty catalog, AI invalid/unknown/DRAFT/duplicate/source IDs, extra/duplicate JSON
fields and trailing documents, timeouts, 401/503, short response fill, publication/source races,
current wishlist add/remove/add, 90d cutoff and 500-event bound, per-Customer popularity caps,
explicit 3000-candidate capacity failure. Final hydration resolves credentials before locks.
No transaction spans AI HTTP. AI has no Backend database access, Customer token or profile store.
Public Product Catalog, Detail, Search & Filter, Wishlist, Auth, Behavior and migrations are
covered by the existing full regression; no historical migration changed.

## Evaluation and limitations

AI controlled expectations include gaming/office laptops, smartphone/tablet interests,
brand/price, conflicts, recency/repeats, search-only, no history, sparse metadata and concurrency.
Sanitized real content: 90 fixed titles from 9 Catalog-Data categories, same-category top similar
alternative in 90/90 cases; deduplication and source exclusion in all 90. This is fixture consistency,
not a human-reviewed relevance label. No precision/recall/NDCG/accuracy invented.

NOT VERIFIED: deployed/browser Customer personalization, production load, production network/TLS.
DEFERRED: human relevance labeling/online evaluation and specification features without approved
trustworthy values. No stock guarantee: this catalog has no available stock eligibility signal.
Latest 500 events may crowd out older interests under heavy use. Exact-token search is conservative;
metadata truncation loses detail. No persistent TF-IDF cache; current snapshot is fit per request.
Capacity >3000 returns explicit 503. Similar outage fallback is safe popular/diverse alternatives,
not a promise of content similarity. These limits do not claim production readiness.
Frontend must use Backend routes, existing Customer bearer JWT, limit <=20, render existing catalog
cards and tolerate empty/null media/price fields. No customerId or guest instrumentation.

## Changed Backend files

- `README.md`
- `RECOMMENDATION_CHECKPOINT.md`
- `demo/src/main/java/com/example/demo/auth/config/AuthSecurityConfiguration.java`
- `demo/src/main/java/com/example/demo/common/security/CatalogSecurityConfiguration.java`
- `demo/src/main/java/com/example/demo/recommendation/RecommendationAiClient.java`
- `demo/src/main/java/com/example/demo/recommendation/RecommendationContract.java`
- `demo/src/main/java/com/example/demo/recommendation/RecommendationController.java`
- `demo/src/main/java/com/example/demo/recommendation/RecommendationErrors.java`
- `demo/src/main/java/com/example/demo/recommendation/RecommendationHydrator.java`
- `demo/src/main/java/com/example/demo/recommendation/RecommendationService.java`
- `demo/src/main/java/com/example/demo/recommendation/RecommendationStore.java`
- `demo/src/main/resources/application.properties`
- `demo/src/test/java/com/example/demo/recommendation/RecommendationAiClientTest.java`
- `demo/src/test/java/com/example/demo/recommendation/RecommendationHttpIT.java`
- `demo/src/test/java/com/example/demo/recommendation/RecommendationIT.java`
- `docs/recommendation-openapi.yaml`
- `docs/recommendation.md`
- `docs/recommendation-verification.md`

## Local Git integration

Feature: features/AIRecommendation from verified draft/version-1 (8aa1dcd).
Commit/merge and post-merge verification remain pending until the complete regression passes.
No push, production deployment, Catalog-Data writes or shared database changes.
