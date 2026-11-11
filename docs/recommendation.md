# Recommendation frontend and service integration

GET `/api/recommendations?limit=10`: public aggregate recommendations, including for
signed-in users. GET `/api/me/recommendations?limit=10`: authenticated Customer only;
ADMIN is denied, matching behavior semantics. JWT identity selects owner history;
customerId and all unknown/duplicate parameters are rejected. GET
`/api/products/{productId}/similar?limit=10`: public, source must be PUBLISHED; missing,
DRAFT or ARCHIVED source returns 404. Invalid limits return ProblemDetail 400;
default 10, allowed 1..20. Responses have Cache-Control: no-store.

The public contract is [recommendation-openapi.yaml](recommendation-openapi.yaml).

All successful responses: `{ "items": [...] }`, containing existing `ProductListingItem`
fields: id, name, category {id,name}, primaryImageUrl, displayPrice {amount,currency},
representativeVariantId. Image URLs use existing trusted catalog signing and may be null.
An empty successful response is `{ "items": [] }`.
No score, reason, raw event, Customer identity or AI diagnostic is exposed. Prices use
current Backend variants and VND. Null price and null variant remain paired.

Frontend: call public endpoint for guest/general homepage, `/api/me/recommendations`
with existing bearer authentication for Customer homepage, and similar endpoint for
product detail alternatives. Render `items` using current catalog cards, tolerate empty
lists and optional images/prices. Do not submit customerId; guests produce no events.
Behavior collection remains the existing explicit Customer-only tracking API. Browser
instrumentation and deployed Customer quality validation are outside this implementation.

## Internal boundary and configuration

Canonical contract v1 is [AI-SWE inference schema](../../AI-SWE/src/ai_service/recommendation/inference.py);
algorithm/security details are [AI documentation](../../AI-SWE/docs/recommendation.md).
No cross-customer HTTP history API exists. AI receives only authorized candidates,
owner's latest <=500 retained events (product events outside current candidates are omitted) and candidate-filtered current wishlist IDs.
No JWT, customerId, names, emails, IPs or profile attributes leave Backend.

Environment variables (Spring relaxed binding): `RECOMMENDATION_AI_URL` (default
http://127.0.0.1:8001), `RECOMMENDATION_AI_KEY` (same random >=32-character value as AI
`RECOMMENDATION_SERVICE_KEY`), `RECOMMENDATION_AI_CONNECT_TIMEOUT_MS` (500),
`RECOMMENDATION_AI_RESPONSE_TIMEOUT_MS` (3000). No redirects or automatic retries.
Use network restrictions and TLS/trusted-network TLS termination as documented by AI.
Unset key never bypasses authentication. Authentication/configuration failures are logged
as explicit operational errors; public fallback remains available. Investigate these logs
rather than treating fallback as proof of healthy AI. Other inference failures log safe
exception categories, not bodies or credentials. Request <=12 MB, response <=64 KiB.

## Eligibility and failure behavior

A single bounded SQL snapshot includes the complete published catalog up to 3000 products,
with category/brand joins and one deterministic lateral representative variant selection:
lowest nonnegative finite price, then variant UUID. Missing valid price yields paired nulls.
No stock availability claim is made; catalog has no stock eligibility column.
No source dataset IDs, prices or unapproved specification facts are used. No migration needed.
More than 3000 published products returns explicit 503, requiring capacity review.

Aggregate popularity uses retained 90d views/clicks/wishlist adds, capped per Customer/
product/event type at 3. General ranking uses log popularity with soft brand/category
penalties and UUID ties. Zero-event catalog uses deterministic diversity fallback.
New Customer/no useful signal, short/invalid AI output, outage or auth failure uses Backend
fallback. Similar outage returns safe diverse/popular alternatives; content relevance is
not guaranteed in that degraded mode. No eligible products returns `items: []`.

AI output is untrusted: exact envelope/item fields, canonical UUIDs, finite nonnegative
scores, candidate membership, source exclusion, uniqueness and limit are validated.
Final batch hydration locks and revalidates PUBLISHED products after HTTP, refreshes
prices and variants, and signs media through existing catalog code, preserving rank.
Additional fallback IDs reserve replacements for concurrently unpublished/deleted results.
No database transaction or publication locks span the AI call. Snapshot and hydration
use bounded batch queries, no per-product HTTP or N+1 catalog loading.

For an already configured local Backend database, export the AI settings above alongside
existing `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `JWT_ISSUER` and media
configuration, then run `bash mvnw spring-boot:run` from `demo`. Start AI with its documented
loopback command. The disposable two-service test below needs no existing database,
production keys, AWS service or shared infrastructure; it supplies isolated test configuration.

## Tests

From demo: `bash mvnw verify` runs unit and PostgreSQL integration tests serially with
disposable Testcontainers databases (Docker required). Real two-service test additionally
requires sibling AI-SWE and `uv` on PATH; `RecommendationHttpIT` launches loopback uvicorn
with an ephemeral service key, random port and Backend random HTTP port, then stops AI.
Focused: `bash mvnw -Dtest=RecommendationAiClientTest -Dit.test=RecommendationIT,RecommendationHttpIT verify`.
Evidence: `target/recommendation-http-evidence.txt`, `target/*-reports` (generated).
Read checked-in recommendation verification report for actual outcomes and limitations.
