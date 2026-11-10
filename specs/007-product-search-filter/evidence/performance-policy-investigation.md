# Performance discrepancy investigation — actual1500 MVP

## Verified invariants

All historical and latest realistic-source observations use1500products,1500variants,identical frozen source product SHA-256 `6c5dead52ce5691a3277126ff8ee9c588da70e085dae095b4e2bd0bb829e74b9`,all PUBLISHED,random fixture ownership UUIDs,controlled prices/source-fixture SKUs. Distribution:150Laptop,75Tablet,150Smartphone,150Speaker,75Smartwatch,225Headphone&Earbuds,225Charger&PowerBank,150PhoneAccessories,300ComputerAccessories. This is not proof of target import/fanout.

Boundary is HTTP MockMvc security/controller/parser/service/nativeSQL/batchloading/signing/serialization and client JSON parse. Warm20/measured100,size20,concurrency1,Java25.0.4.1/PostgreSQL17.11,poolmax2/minidle0. Each new invocation has distinct JVM and disposable PostgreSQL container. Pool startup,cold request and warm journeys are separated;startup/cold timing is not in p95. Existing synthetic benchmark is a different fixture and is reported separately.

No production code,ranking,normalization,SQL,migration or query budget changed in this policy revision. Earlier finalization import cleanup had equivalent executable disassembly for its two production classes (`git-finalization-bytecode-proof.json`). Current source baseline comparisons record only the benchmark instrumentation as a Java change. Protected identifiers and all functional assertions retained.

## What the plans establish

Historical broad endpoint p95=600.797ms;latest old-policy mixed=1193.912ms;old-policy isolated=1017.153ms. All exceed or pass thresholds according to their recorded original policy;do not rewrite those historical outcomes. All satisfy the newly authorized broad1.5s budget,though old incomplete runs alone cannot prove all scenarios.

Historical EXPLAIN execution600.003ms and old isolated plan observed1042.521ms show most delay inside PostgreSQL,not necessarily Java ranking/normalization. Same plan node structure and shared-hit4452/read0:neither an obvious plan change nor cold disk reads explain that observed difference. New run1plan execution604.922ms;new run2timing is in performance-policy-plan-comparison.json. Single instrumented SQL timings are diagnostics,not endpoint p95 or a Java-overhead percentile;do not subtract unlike statistics.

Realistic source benchmark uses prewarmed JVM/DB and includes no simultaneous Maven tests in our workflow. Host still shares resources with user desktop and unrelated containers;none stopped/modified. New PostgreSQL containers have no configured CPU quota/cpuset/memory limit,shared memory64MiB,as recorded in runtime receipts. No historical CPU-frequency,per-core scheduling,GC/host contention or resource time series was captured. Runtime/host scheduling/resource variance is a plausible explanation,**not a proven root cause**;there is no evidence justifying a production query or ranking change. The new independent runs permit per-run acceptance without averaging or repeated-until-green runs.

## Count discrepancy

The full functional command excludes `*PerformanceIT`:359integration. Historical full intended integration coverage is363. Exactly4cases are in three performance suites:ProductSearchPerformanceIT(1),ProductSourceWorkloadPerformanceIT(2),CatalogPublicReadPerformanceIT(1). Unit/MVC342unchanged. Thus701is the nonperformance job,705is the complete union. No test removal or changed discovery. Fresh suite/case identities and commands are recorded in finalization results and logs;postmerge subset reruns do not add new unique tests to705.

## Acceptance

Owner-approved budgets and measurement protocol:performance-policy.md. Runs1and2 are evaluated separately with p50/p95/max/all100samples/timeoutCount and SQL counts. Unrelated legacy/synthetic assertions unchanged. Failed historical logs remain archived in git-finalization-review-first-attempt.md and git-finalization-* logs. Current verdict is based on complete new runs,not retrospective relabeling of the old failed command.
