# Bàn giao Search & Filter MVP — local PASS

Cập nhật2026-11-10 theo ngân sách hiệu năng owner phê duyệt. US1–US4 đã nghiệm thu local;production **BLOCKED/NOT_VERIFIED** theo T063. Git finalization:feature đã commit và merge thành công vào draft/version-1;59IT sau merge PASS. Hash và receipt ở phần cuối tài liệu. Không push/deployment.

US1 giữ normalization tiếng Việt,AND tokens,7tầng ranking,brand/SKU/name/description/approved-spec search,fuzzy0.32+guard một edit/hoán vị liền kề và bảo vệ identifiers. Rank7 cho canonical eligible SKU;6source model anchors chưa xác minh ownership vẫn DEFERRED. Family/synthetic spans không thành model identity. Fuzzy corpus kiểm soát15TP/2FP/0FN,precision88,24%,recall100%;không tuyên bố ground truth toàn catalog.

US2 category self/descendants,multiple brands OR,AND giữa nhóm,onlyPUBLISHED. US3 giá VND inclusive và4keys/5bindings:display_diagonal Laptop/Tablet,physical_ram Laptop,installed_storage_gb Laptop,display_resolution Laptop. Backend taxonomy/UUID có thẩm quyền;không dùng source UUID. Quantity eq/gt/gte/lt/lte/between,text eq/prefix/contains;mọi điều kiện variant cùng variant. US4 tất cả sort,ID-first/count distinct/batch hydration,tie-break ổn định,page0/size20/max100,representative variant/displayPrice nhất quán. Detail/Wishlist/image signing và paired-null orphan giữ contract.

API GET /api/products:q,categoryId,brandId,minPrice,maxPrice,specFilters,sort,page,size. Duplicate singleton/JSON members hoặc invalid filter400ProblemDetail;valid no-match200empty;keyword mode headers giữ nguyên,capability required mất503. OpenAPI3.1hai docs PASS,41probes/60refs;không bỏ hai assertion mode header.

## Kiểm chứng mới

**705test riêng biệt PASS:342unit/MVC+363IT,0failure/error/skip**;case identities khớp snapshot trước. Full functional job chỉ359IT vì loại `*PerformanceIT`;4case trong3lớp performance chạy riêng(1+2+1). Hai lần sourcebenchmark và postmerge subset là rerun,không cộng trùng vào705. Chi tiết:performance-policy-final-test-results.json và logs nén.

Migration V16–V19 đầy đủ;V15 là foundation có trước,byte-identical. V1–V15hash không đổi.10migration tests và15receipts PASS bao gồm fresh,V15→latest,capability/failure/retry/checksum-stop,backfill và parityJava/SQL trên source fields. Toàn bộ dùng disposable containers;không DB chung/production. Không thêm speculative index.

## Hiệu năng thực tế1500

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

Cả hai JVM độc lập PASS từngscenario theo FR-019/SC-006 mới;không lấy trung bình p95. Product texts/category/brand/specs là frozen source1500;publication/giá/UUID/SKU và fanout1:1 là fixture kiểm soát,không chứng minh persistent import. Mã production/ranking/SQL không đổi trong revision này.

601ms/1194ms/1017ms:fixture/hash,boundary,warmup và plan shape nhất quán;SQL execution600msso với1043ms ở các capture,shared-hit4452/read0. Hai lượt mới cũng tái hiện broad601/1050ms trên cùng code. Phần lớn biến thiên ở DB execution;không đủ telemetry lịch sử để kết luận CPU/scheduling/host contention/GC là nguyên nhân. Xem performance-policy-investigation.md và runtime/plan receipts. Các FAIL theo policy cũ được giữ trong git-finalization-review-first-attempt.md,không sửa lịch sử.10k chỉexploratory,không làreleasegate.

## Giới hạn và task matrix

**64PASS,T059DEFERRED,T063BLOCKED**. T059chưa có index mới được evidence chứng minh cần thiết;T063không approved deployment target. Không tuyên bố66/66.

Battery/subtypePowerBank,modelRank7ownership,semantic review toàn1500/11punctuation groups,fullhuman relevance vàAIintegration vẫn deferred. Tất cả source facts vẫn UNVERIFIED;chỉbest-effort structurallyvalid facts và guard/quarantine exclusion được phép. Không sửa/merge source hay convertunit tự động;known suspicious facts bị loại matching. Definition approval không đồng nghĩa dataset/deployment approval.

## Tái lập

```bash
# From Backend-SWE;Java25,Docker and existing Maven dependencies.
mvn -o -f demo/pom.xml '-Dit.test=*IT,!*PerformanceIT' -Djunit.jupiter.execution.parallel.enabled=false verify
# Two independent serial JVM runs;preserve each metrics file.
for run in 1 2; do
  mvn -o -f demo/pom.xml -Dit.test=ProductSourceWorkloadPerformanceIT -Djunit.jupiter.execution.parallel.enabled=false test-compile failsafe:integration-test failsafe:verify || exit 1
  cp demo/target/source-workload-performance/results.json /tmp/search-mvp-run${run}.json
done
mvn -o -f demo/pom.xml -Dit.test=ProductSearchPerformanceIT,CatalogPublicReadPerformanceIT -Djunit.jupiter.execution.parallel.enabled=false test-compile failsafe:integration-test failsafe:verify
python3 -m venv /tmp/search-openapi-repro
/tmp/search-openapi-repro/bin/pip install -r specs/007-product-search-filter/evidence/t006-validator-freeze.txt
/tmp/search-openapi-repro/bin/python specs/007-product-search-filter/evidence/probe-openapi-capabilities.py --approved-tools
git diff --check
```

Không chạy lại historical handoff/evidence generators để thay thế kết quả mới. Baseline preservation:performance-policy-preservation.json;tested code:performance-policy-source-snapshot.json. Latest acceptance và Git handoff:git-finalization-review.md,resume-checkpoint.md. Các index lịch sử giữ hash tại thời điểm capture;git-finalization-artifact-index.json là index finalization hiện hành.


## Git finalization completed — 2026-11-10T03:31:37.491871+00:00

- Featurecommit:`067ba56395176c5158a7085e3360bf53f0069837`(305reviewed files),branch`features/SearchAndFilter`retained.
- Direction:`features/SearchAndFilter`→`draft/version-1`.
- Mergecommit:`c8b7435746a44640432b8166b55578b89a439c6c`;two parents,conflict-free,--no-ff. Target fetch equal/no divergence.
- Currentbranch:`draft/version-1`;featureancestor verified. Source matches full705test snapshot.
- Postmerge regression:**59ITPASS,0failure/error/skip**;raw log and suite hashes in git-finalization-postmerge*.
- V1–V15unchanged;V16–V19present;Brand/Specifications,Detail/Wishlist preserved.
- **64PASS,T059DEFERRED,T063BLOCKED**;no local blocker. DeploymentNOT_VERIFIED/BLOCKED,no approved target.
- No push,deployment,branch deletion,source repository modification or discarded user change.

A docs-only followup commits this actual operation/test receipt,leaving a clean working tree. Its exact HEAD is obtainable with `git rev-parse HEAD`;it does not replace the mergecommit above. Next local action:none required. Deployment requires separate target authorization/evidence.
