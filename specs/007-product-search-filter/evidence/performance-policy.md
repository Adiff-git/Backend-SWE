# Owner-approved local MVP performance policy — 2026-11-10

Authorization:project owner instruction “MISSION:FINALIZE SEARCH & FILTER MVP”,Performance Acceptance Policy. This is the authorization record,not a fabricated human signature. Production behavior/ranking/SQL and all functional assertions remain unchanged. FR-019/SC-006 and T061 are synchronized;all20FR/sixSC identifiers remain.

| Scenario | Required warm p95 |
|---|---:|
| Browse | <500ms |
| Normal/selective natural-name keyword (`thinkpad`) | <1000ms |
| Broad keyword (`laptop`) | <1500ms |
| Fuzzy (`samsnug`) | <1500ms |
| Category+Brand | <500ms |
| Price+Specification | <500ms |
| Combined keyword/category/price/specification | <500ms (existing measured selective combination retained) |

Actual1500 frozen source names/brands/categories/descriptions/specifications,one controlled fixture variant/product. Source product hash `6c5dead52ce5691a3277126ff8ee9c588da70e085dae095b4e2bd0bb829e74b9`. Actual persistent database import/fanout is NOT_VERIFIED by fixture tests. Fixture prices,UUIDs,publication and SKU are controlled;do not infer canonical source anchor ownership.

Protocol:two independent Maven/Failsafe JVM invocations,serial,disposable PostgreSQL17-alpine,Java25,poolmax2,minidle0;20warmup requests then100measured HTTP MockMvc journeys per scenario,size20. Measure server controller/security/parser/service/SQL/signing/serialization plus test JSON parse. p50=sorted[49],p95=sorted[94],max=sorted[99]. Record all sorted samples;evaluate runs individually. Any HTTP error,request exception or timeout fails immediately;timeoutCount0 is reported only for fully completed scenario. No swallowing retries,no averaging p95. Browse≤6/search-filter≤8data statements;SET counted separately;pagebounded20 and stable counts asserted. No production/shared database writes.

Only actual-source benchmark assertions change by scenario;synthetic1500 and legacy Catalog tests keep their existing thresholds. No tests removed. Optional10000exploration remains non-blocking. Database constraints/migrations and ranking untouched.

Results:performance-policy-run1.json and run2.json,individual logs and plans. Investigation:performance-policy-investigation.md. Missing runs/failed budgets must not be recorded as PASS.

## Measured acceptance — PASS independently in both JVMs

|Scenario|Run1 p50 / p95 / max(ms)|Run2 p50 / p95 / max(ms)|Budget p95(ms)|
|---|---:|---:|---:|
|browse|52.52 / 64.72 / 114.96|92.90 / 127.15 / 137.46|<500|
|broad-laptop|583.99 / 601.10 / 629.89|999.20 / 1049.65 / 1107.13|<1500|
|fuzzy|611.01 / 632.40 / 648.75|1072.61 / 1159.76 / 1221.68|<1500|
|selective|305.15 / 322.06 / 333.46|524.38 / 646.73 / 857.63|<1000|
|brand-category|6.26 / 12.21 / 23.83|26.59 / 33.80 / 40.27|<500|
|combined|104.40 / 113.01 / 142.18|202.76 / 285.87 / 350.28|<500|
|price-specification|17.45 / 30.37 / 34.91|44.63 / 68.09 / 73.71|<500|

All14scenario-runs:100samples after20warmups,zero unexpected timeouts. Maxlatency is disclosed separately and is not the p95 acceptance statistic. Browse/category-brand/price-spec use4dataSQL,keyword/combined5dataSQL+1SET;all pages≤20. Distinct PIDs61093and79003. No averaging.
