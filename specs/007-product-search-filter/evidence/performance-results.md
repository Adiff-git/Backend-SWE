# Measured performance — actual1500 source workload

| Scenario | p95 ms | Data SQL | SET |
|---|---:|---:|---:|
| browse | 64.3 | 4 | 0 |
| normal | 600.8 | 5 | 1 |
| fuzzy | 631.8 | 5 | 1 |
| brand-category | 12.1 | 4 | 0 |
| combined | 121.6 | 5 | 1 |

20 warmups/100 measured samples per scenario,concurrency1,PostgreSQL17.11,pg_trgm1.6. Boundary:MockMvc HTTP controller/security/parser/service/SQL/serialization plus client JSON parse. Original1s acceptance PASS. Suggested500ms broad normal-search goal FAIL/non-blocking;normal laptop query matches251 Products. Fuzzy1s goal PASS. Source-derived text/category/brand/specs with controlled PUBLISHED state,UUIDs and VND prices;not a persistent import or intended-target distribution proof. Source distribution is1 Variant/Product;target imported distribution is NOT_VERIFIED.

Controlled1500 fixture results are in performance-final.json. Its10-reader/2-connection-pool run is diagnostic,not the concurrency1 SLO;queueing must not be represented as a1s concurrency10 guarantee. CatalogQueryCountIT separately proves constant statements at20/100 results and20 variants/Product,including images. All JDBC statements in the synchronous journey are captured;SET counted separately. No request loads all result Products into Java.

Historical1085ms was1500 synthetic data before optimization. The actual-source pre-cache plans were5665/5220ms for laptop/samsnug;those were EXPLAIN times,not p95. V19 fixes measured repeated description tokenization. No extra index was demonstrated necessary;existing indexes remain.

Exploratory10k fanout1/5/20 selective-query plans/assertions passed;their EXPLAIN times are diagnostic and not an MVP timing gate. Raw final plans/metadata are in final-artifacts/search-plans/. No ten-thousand-product p95 claim.
