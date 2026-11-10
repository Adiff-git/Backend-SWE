# Bàn giao Search & Filter MVP

> **Cập nhật Git finalization 2026-11-10: BLOCKED.** Regression chức năng PASS nhưng benchmark nguồn1500 chạy lại FAIL (p95 laptop1193.912ms;JVM riêng1017.153ms>1000ms). Chưa stage/commit/merge. Các kết quả dưới đây là snapshot nghiệm thu trước đó;đọc [git-finalization-review.md](git-finalization-review.md) để biết trạng thái mới nhất.

**Snapshot nghiệm thu trước đó: PASS cho MVP local gồm US1–US4. Chưa triển khai production.**

Điểm dừng trước:branch features/SearchAndFilter,HEAD c5bd4e7eeb39d9b06a0c7e36af9a65635058613f;341 unit/MVC và346 IT đã qua. Lượt bổ sung bị lỗi fixture10k;benchmark sau tối ưu chưa hoàn tất và task statuses còn16/66. Không khởi tạo lại công việc đã xong.

Phần tiếp tục:đã sửa fixture bằng option chuỗi,thêm guard typo bảo thủ,normalization nhanh với parity thực tế,an toàn cho stored text lỗi,cột token mô tả tự sinh và sửa publication race ở trang trộn có/không ảnh. Không sửa V1–V15 hoặc Catalog-Data/AI-SWE.

US1 có normalization tiếng Việt,AND tokens,phrase/token/prefix/partial/fuzzy,brand/SKU/description/approved specs và tie-break ổn định. Thứ tự7→1 là whole canonical eligible SKU/verified Model > whole Name > Phrase > Token > Prefix > Partial > Fuzzy. Sáu source anchors chưa xác minh ownership nên model7 không hoạt động;canonical SKU7 vẫn hoạt động. Best eligible rank độc lập với cheapest eligible display pair.

US2 có self/descendants,brand OR và AND với các nhóm khác. US3 có khoảng giá VND inclusive và bốn approved keys/năm bindings;quantity eq/gt/gte/lt/lte/between,text eq/prefix/contains. Mọi điều kiện variant dùng cùng một Variant. US4 có mọi sort đã duyệt,pagination0/20/max100,count distinct,ID-first/batch loads và image signing hiện có.

API vẫn GET /api/products với9 parameters đã nêu trong contract. Duplicate parameter/JSON member,type/unit/key/category/operator sai trả400 ProblemDetail;valid no-match200 empty. Keyword có mode header;capability mất trả503. Detail/Wishlist giữ contract.

Migration mới:V16–V19;V15 là foundation đã có trước mission và giữ nguyên. V19 cache tự sinh trong cùng transaction,khi mô tả đổi PostgreSQL cập nhật cache;không có search replica/job hay index suy đoán. Fresh/V15-upgrade,failure/retry/checksum-stop và bảo toàn business fields đã chạy trên disposable containers.

Fuzzy0.32+one-edit/adjacent transpose:15TP,2FP,0FN;precision88,24%,recall100% trên24 cặp kiểm soát. Canon/Canyon và Intel/Inter vẫn mơ hồ;không tuyên bố relevance hoàn hảo hoặc ground truth cho toàn catalog. Hai/multiple-edit typos có thể mất recall.

Registry dùng UUID thực của các root Laptop/Tablet duy nhất trong Backend;root thiếu/mơ hồ chỉ vô hiệu bindings đó. Không dùng source UUID. Battery deferred.1500 source facts vẫn UNVERIFIED;known suspicious/type/unit facts bị loại khỏi matching,không sửa/merge source. Một mô tả có2 ký tự control bị loại field đó khỏi search.122 case-only groups chỉ so khớp case-insensitive;11 punctuation/spacing groups không được merge.

## Kết quả cuối

| Scenario | p95 ms | Data SQL | SET |
|---|---:|---:|---:|
| browse | 64.3 | 4 | 0 |
| normal | 600.8 | 5 | 1 |
| fuzzy | 631.8 | 5 | 1 |
| brand-category | 12.1 | 4 | 0 |
| combined | 121.6 | 5 | 1 |

Đây là phép đo HTTP1500 nguồn qua MockMvc,20 warmups+100 samples/scenario,concurrency1. Giá/UUID/trạng thái publication là fixture kiểm soát;không chứng minh import hoặc fanout của target database. Broad normal search vượt mục tiêu tham khảo500ms;ngưỡng1s hiện hữu và fuzzy1s đều đạt. Không timeout/N+1;SET được đếm riêng. Diagnostic10 readers dùng pool2 có queueing,không cam kết1s tại concurrency10.

705 tests:342 unit/MVC+363 integration,0 failure/error/skip. Bao gồm Catalog/Detail/Wishlist/auth/image,all RK01–RK10,duplicate JSON,same-variant,invalid facts,orphan và real publication races. OpenAPI3.1:hai docs hợp lệ,41 probes/60 refs. Không bỏ hai assertion mode header.

Exploratory10k/fanout1/5/20 đã qua assertions/plans;đây không phải p95 hoặc release gate.1085ms lịch sử là1500 synthetic trước tối ưu,không phải10k. Các kết quả cũ và kế hoạch lỗi được giữ lại có provenance.

## Trạng thái công việc và giới hạn

64 task PASS theo scope local được owner cho phép;T059 DEFERRED vì chưa có index mới chứng minh lợi ích;T063 BLOCKED vì chưa có deployment target được phép. Điều kiện no-new-index được ghi rõ,không bỏ qua migration verification. Không tuyên bố66/66. Chi tiết mỗi task ở task-completion-matrix.json và tasks.md.

Deferred:activation model anchors cần canonical ownership,khóa subtype Power Bank/battery,manual semantic review toàn dataset/11 groups,full-source human relevance labels,historical importer lineage,AI integration và tuning tiếp mục tiêu normal500ms. Các phần này không ngăn MVP local;không được coi là đã xác minh.

Không còn blocker bên ngoài cho việc tái lập MVP bằng disposable tests. Deployment,actual persistent import/distribution,taxonomy/privileges/recovery của target vẫn NOT_VERIFIED/BLOCKED. Không commit,push,deploy hay migrate DB chung/production.

## Tái lập

```bash
# From Backend-SWE;Java25,Docker,existing Maven dependencies.
mvn -o -f demo/pom.xml '-Dit.test=*IT,!*PerformanceIT' -Djunit.jupiter.execution.parallel.enabled=false verify
mvn -o -f demo/pom.xml -Dit.test=ProductSearchRepositoryIT,ProductSearchControllerIT,ProductSearchPerformanceIT,ProductSourceWorkloadPerformanceIT,CatalogPublicReadPerformanceIT -Djunit.jupiter.execution.parallel.enabled=false test-compile failsafe:integration-test failsafe:verify
# Final actual1500 HTTP measurement (also records exact source plans):
mvn -o -f demo/pom.xml -Dit.test=ProductSourceWorkloadPerformanceIT -Djunit.jupiter.execution.parallel.enabled=false test-compile failsafe:integration-test failsafe:verify
# Approved tooling,isolated Python environment:
python3 -m venv /tmp/search-openapi-repro
/tmp/search-openapi-repro/bin/pip install -r specs/007-product-search-filter/evidence/t006-validator-freeze.txt
/tmp/search-openapi-repro/bin/python specs/007-product-search-filter/evidence/probe-openapi-capabilities.py --approved-tools
python3 specs/007-product-search-filter/evidence/collect-mvp-evidence.py
```

Git branch/HEAD không đổi;working tree còn các thay đổi cũ và mới chưa commit. File list và bằng chứng bảo toàn:preservation-final.json. Raw metrics,plans,migration receipts,suite hashes và logs nén nằm trong thư mục evidence/. Checkpoint hiện tại:resume-checkpoint.md.
