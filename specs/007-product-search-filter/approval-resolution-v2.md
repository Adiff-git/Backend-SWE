# AR2 — Báo cáo giải quyết phê duyệt Product Search & Filter

Phiên bản: `approval-resolution-v2-2026-11-09` · Ngày:2026-11-09.

**Trạng thái: đã ghi nhận quyết định chính sách của reviewer qua chỉ dẫn người dùng;chưa phải biên bản hoàn tất T003/T004 hoặc quyền activation/deployment.** T003 và T004 giữ BLOCKED;tasks.md không thay đổi. Không tự ghi tên người duyệt hoặc suy approval từ dữ liệu nguồn.

Bản máy đọc:[evidence/approval-resolution-v2.json](evidence/approval-resolution-v2.json).
SHA-256 toàn bộ file JSON: `297f5004d11b3ddb1b3655d469fe1c1986c2cd149ba0edd3b5309aacc8eaa052`.

Tiền nhiệm AR1:`approval-resolution-review-v1-2026-11-09`,canonical-payload SHA-256 `096ec4ca972303ae03a8fec11a58c89cb82d93de474586e94ff1824f7c8e00f9`. Payload AR1 được giữ nguyên trong trường predecessor của JSON để audit;quyết định bị thay thế bên trong payload lịch sử không phải cấu hình AR2 hiện hành. Mọi quyết định A/X/D bên dưới tham chiếu đúng phiên bản/hash AR2 này;hash nguồn nằm trong JSON.

## 1. Quyết định đã chấp nhận — không yêu cầu duyệt lại chính sách

| ID | Quy tắc/phạm vi đã chấp nhận | Bằng chứng | Giới hạn và điều kiện còn lại |
| --- | --- | --- | --- |
| A01 | Cả19 family terms,bao gồm Chromebook:global whole-token fuzzy exclusion trên query và candidate | Quyết định người dùng;matrix46 mục | Chỉ fuzzy protection;không family/model rank7;chấp nhận giảm typo/homonym recall |
| A02 | Sáu source-scoped model anchors và điều kiện rank7 nghiêm ngặt của AR1 | Tên nguồn/ASIN đã kiểm tra;quyết định người dùng | Rank7 không được bật cho binding chưa chứng minh canonical Product ownership và eligibility |
| A03 | Giữ đủ30 expected cases và remaining context rules;synthetic spans chỉ fuzzy-protection | Draft16/review14;AR1 và matrix hiện có | Không biến synthetic span thành Product identity;không coi30 case là30 runtime passes |
| A04 | Chọn bốn keys/năm category bindings cho Registry v1;searchable/filterable=true;identifier=false | Quyết định thu hẹp/flags của người dùng;inventory nguồn | Definition selection,chưa runtime-active;target taxonomy BLOCKED |
| A05 | Battery là candidate deferred;hai flags=false;parent binding không active | Quyết định người dùng;bằng chứng phone bundles/battery kits | Cần approved Power Bank subtype binding riêng;không tự tạo UUID |
| A06 | Quarantine display6088 inch và questionable Tablet RAM/storage;exclude CPU dưới gpu_model | Chính sách người dùng;nguồn dòng65/68/326/151/172 | Chưa thực hiện quarantine/repair;không thay Detail/Wishlist hoặc source data |
| A07 | Không auto-merge/rewrite133 nhóm;case-only equivalence chỉ cho matching sau explicit review;11 nhóm punctuation/spacing manual review | 77cpu_model+55cpu_family+1gpu_model;122case-only+11 nhóm còn lại | Phân nhóm không phải approval;không thay normalization query chung đã duyệt |
| A08 | Definition approval,fact validation và deployment activation là ba phạm vi độc lập | Quyết định người dùng;tất cả1500 specifications nguồn UNVERIFIED | Không suy quyền import/rollout hoặc task PASS |

### T004 — giữ nguyên46 mục

Giữ19 family terms:`galaxy,iphone,ipad,xperia,redmi,thinkpad,ideapad,inspiron,vivobook,zenbook,macbook,chromebook,elitebook,celeron,pentium,ryzen,snapdragon,geforce,radeon`.

Giữ21 ambiguous terms:`core,pro,air,note,plus,max,studio,surface,pavilion,aspire,envy,spectre,pixel,nord,vega,graphics,hd,uhd,rog,tuf,legion`.

Sáu anchors được giữ source-scoped:

| Anchor | ASIN | Dòng products.jsonl | Canonical target ownership / runtime rank7 |
| --- | --- | ---: | --- |
| WH-1000XM5 | B0C5ZC7FWR | 384 | BLOCKED / disabled until verified |
| WH-1000XM4 | B08DBDSVBC | 377 | BLOCKED / disabled until verified |
| A515F | B08PPSRLQ5 | 1 | BLOCKED / disabled until verified |
| Pro-I | B09L88FZ95 | 2 | BLOCKED / disabled until verified |
| L210 | B081V6W99V | 155 | BLOCKED / disabled until verified |
| BE2026 | B08R7D4KZJ | 128 | BLOCKED / disabled until verified |

ASIN và Product UUID proposal không phải bằng chứng target Product. Source title thật được ghi trong JSON;không tự coi WH1000XM5 là alias của WH-1000XM5 hoặc L210MA-DB01 là L210. Accessory compatibility target có thể được fuzzy-protection,nhưng không được coi là model của accessory.

### Chromebook đã chốt

`chromebook` bị loại khỏi fuzzy theo exact normalized whole token,không cần device-brand context. Device-brand context phục vụ một ownership/model claim riêng nếu được đề xuất sau này,không làm điều kiện cho fuzzy exclusion này và không chứng minh manufacturer từ riêng từ Chromebook. Chromebook không nằm trong sáu model anchors nên không tự có MODEL7.

## 2. X03 — sửa biểu diễn hai Plus expressions sau khi đọc source titles

Hai biểu thức AR1 dùng ký hiệu meta,rất dễ bị đọc nhầm thành regex có dấu+ là quantifier. AR2 dùng các **mảng token D2 literal**,không dùng brace shorthand hoặc runtime regex:

```json
[
  ["galaxy", "s8", "plus"],
  ["galaxy", "s9", "plus"],
  ["galaxy", "s20+", "plus"],
  ["galaxy", "s21+", "plus"],
  ["lg", "k22+", "plus"]
]
```

Mỗi phần tử là đúng một token;`s20+`,`s21+`,`k22+` giữ nguyên trailing+. Các token phải liên tiếp,trong cùng field;query/candidate xét độc lập. Không optional+,không tách+ thành token riêng,không suy alias hoặc bỏ identifier punctuation.

| Span literal | Source title excerpt chính xác | Dòng / ASIN |
| --- | --- | --- |
| galaxy s21+ plus | SAMSUNG Galaxy S21+ Plus 5G | 11 / B0B8PD3H1W |
| lg k22+ plus | LG K22+ Plus LM-K200HAW Smartphone | 32 / B08KWNJ26D |
| galaxy s20+ plus | SAMSUNG Galaxy S20+ Plus 5G | 53 / B08FRS3R32;105 / B08FRQZLJ7 |
| galaxy s9 plus | SURITCH Case for Galaxy S9 Plus | 1364 / B07RX6294J;1482 / B07SW2159H |
| galaxy s8 plus | SUPCASE Unicorn Beetle PRO Series Phone Case for Samsung Galaxy S8 Plus | 1442 / B07F1GJ4VV;1491 / B06XJ1QG5N |

Source:[products.jsonl](../../../Catalog-Data/data/products.jsonl). Các S8/S9 examples ở đây là phụ kiện;chúng chỉ chứng minh text/span,không ownership của điện thoại. S9+ không tự thành S9,và các model ngoài tập không tự được thêm.

Đã chạy existing Java ProductSearchNormalizer qua existing NormalizationParityProbe trong thư mục tạm rồi xóa thư mục tạm. **8 actual source titles +5 synthetic negatives đều đạt primitive/document-literal checks.** Các negative là `Galaxy S20 Plus`,`LG K22 Plus`,`Galaxy S20++ Plus`,`LG K22++ Plus`,`Galaxy S8Plus`:không tự match năm array trên. Đây không phải production context-classifier/fuzzy/ranking test.

Chi tiết token thực tế,inputs,kỳ vọng và cách tái lập:[approval-resolution-v2-span-checks.json](evidence/approval-resolution-v2-span-checks.json). Không sửa primitive hoặc application code.

Các grammar pro/max/core,Plus spans khác và ambiguous context rules còn lại của AR1/matrix giữ nguyên. Synthetic `iphone pro`,`galaxy plus`,`iphone pro max` vẫn chỉ fuzzy-protection;không suy model identity. Việc sửa biểu diễn literal không mở rộng46-term scope.

### Điều kiện rank7 giữ nguyên

Phải đồng thời có anchor đã ký,whole original-normalized-query equality trước dedup,proven canonical owner và PUBLISHED eligibility. Binding gắn Variant phải có Variant thuộc Product và đủ same-variant conditions. Không alias,typo,family/unit/modifier hoặc accessory-target promotion. D7 chỉ dùng Product-bound anchor với shared matches và các điều kiện orphan đã duyệt. Exact canonical eligible SKU rank7 là nhánh riêng;source demo SKU không chứng minh target SKU. Quy tắc được chấp nhận nhưng model binding chưa verified vẫn disabled.

### Giữ30 expected cases và ranh giới bằng chứng

Giữ D01–D16 và IV01–IV14 nguyên input/kỳ vọng,không thay expected answers. JSON AR2 chứa đủ30 case và hash các bản gốc. Bằng chứng trước đây:16 actual D2 primitive passes;14 source/primitive/proposal checks. Classifier/fuzzy/ranking production tests:0. AR2 không biến các proposal checks thành runtime acceptance;13 primitive/span checks mới là bằng chứng bổ sung riêng,không thay30 case hoặc trở thành model ownership proof.

## 3. Registry v1 — bốn selected keys,năm bindings,chưa runtime-active

| Canonical key | Category source được chọn | Type/unit | Operators | searchable | filterable | identifier |
| --- | --- | --- | --- | --- | --- | --- |
| display_diagonal | Laptop;Tablet | quantity/inch | eq,gt,gte,lt,lte,between | true | true | false |
| physical_ram | Laptop | quantity/GB | eq,gt,gte,lt,lte,between | true | true | false |
| installed_storage_gb | Laptop | quantity/GB | eq,gt,gte,lt,lte,between | true | true | false |
| display_resolution | Laptop | text;unit absent | eq,prefix,contains | true | true | false |

`display_resolution.identifier=false` thay AR1 identifier=true. Không coi technical resolution là Product model identity. **Mọi technical specification value đều không fuzzy theo general source-field rule**,bất kể identifier flag;false không bật natural-name fuzzy. Searchable và filterable không suy từ identifier và không chứng nhận độ đúng của stored value.

Battery giữ ngoài selected v1:

| Key | Candidate applicability | searchable | filterable | Activation |
| --- | --- | --- | --- | --- |
| battery_capacity_mah | Chỉ sau approved Power Bank subtype binding | false | false | Deferred;parent Charger & Power Bank không active |

Không tạo subtype UUID,ASIN allowlist hoặc runtime title classifier. All-category parent activation bị từ chối. Nguồn có phone-plus-charger bundles và battery kits nên chỉ có capacity dưới parent category không đủ chứng minh Power Bank applicability.

Conservative exclusions giữ nguyên:Smartphone display_diagonal;Smartphone physical_ram;Tablet physical_ram. Không đồng nghĩa tất cả facts của các category này sai. internal_storage,cpu_model,cpu_family,gpu_model không được tự thêm vào selected v1;không merge installed_storage_gb/internal_storage,không conversion unit.

### Source UUIDs không phải target evidence

| Category source | Source UUID | Reconciliation tại đích |
| --- | --- | --- |
| Laptop | 31549723-c1a2-56a6-a1a1-64da36e16f65 | BLOCKED |
| Tablet | 5793dbd1-a3e4-514e-bf1d-ef50a31acc04 | BLOCKED |
| Charger & Power Bank — deferred parent | f065e70e-4abe-5b7f-9112-264bd82c2930 | Không được activate |

Source category_mapping.json ghi APPROVED cho source mapping;không thay target taxonomy evidence. Không có verified target connection mới được cung cấp;review này không truy vấn database. Không suy rằng mọi remote database đều unavailable. Chưa có approval receipt để đóng T003 hoặc runtime registry activation.

## 4. Data quality — policy đã nhận,chưa thực hiện

| Policy | Accepted disposition | Điều không được tự làm | Bằng chứng/sign-off còn cần |
| --- | --- | --- | --- |
| Display6088 inch | Quarantine affected facts trước activation | Không sửa thành6.088 hoặc đoán unit | Dòng65/68;authoritative per-product review |
| Questionable Tablet RAM/storage | Quarantine affected facts;giữ binding exclusions | Không đổi256GB RAM thành storage hoặc swap keys | Dòng326 và các mẫu RAM/storage;reviewer quyết định từng fact |
| CPU dưới gpu_model | Exclude affected GPU facts | Không tự chuyển sang cpu_model | Dòng151/172;manual provenance reconciliation |
| 133 differing evidence groups | Không merge/rewrite tự động | Không coi source parser accepted=true là semantic approval | 77cpu_model+55cpu_family+1gpu_model |
| 122 case-only groups | Chỉ cho matching equivalence sau explicit review | Không rewrite source/stored values;không tự duyệt122 nhóm | Per-group review identity/source/hash/disposition |
| 11 punctuation/spacing groups | Manual review bắt buộc | Không tự gộp Core i3-10110U/Core i3 10110u hoặc GTX1650Ti/GTX 1650 Ti | Chốt nghĩa/identifier boundary,không dùng fuzzy sửa identifier |

Phân nhóm122/11 là evidence classification,không phải122 nhóm đã được duyệt. Policy này áp dụng việc adjudicate evidence groups,không thay case-insensitive query normalization đã được duyệt từ trước. Quarantine/exclude đề cập đóng góp technical search/filter trong workflow dữ liệu được phê duyệt riêng,không xóa Product hoặc sửa API Detail/Wishlist.

Không giả định đã có per-fact quarantine/approval mechanism. Nếu chưa có cách loại suspect facts được phép và audit được,giữ affected activation BLOCKED thay vì thêm silent runtime heuristics. Definition selection không chứng minh full dataset correctness hoặc quyền deployment.

## 5. Quyết định bị từ chối/thay thế

| ID | Quyết định cũ bị thay thế | Thay bằng | Reviewer action |
| --- | --- | --- | --- |
| X01 | display_resolution.identifier=true | false;technical-value rule vẫn no-fuzzy | Đã do người dùng quyết định,không xin lại |
| X02 | Năm active keys/sáu active bindings | Bốn selected keys/năm bindings;battery deferred | Đã do người dùng quyết định |
| X03 | Brace shorthand/unescaped-regex interpretation của hai Plus expressions | Năm literal token arrays đã đối chiếu source/D2 | Acknowledge representation AR2 theo version/hash |
| X04 | Parent battery activation,source-ASIN inferred ownership,synthetic MODEL7 | Không được phép | Không có activation ngầm |

## 6. Deferred/blockers và exact sign-off requirements

| ID | Chưa giải quyết bằng policy approval | Bằng chứng bắt buộc tiếp theo |
| --- | --- | --- |
| D01 | Canonical Product/eligible Variant ownership của sáu anchors | Exact target identity;read-only source-link/canonical-ID reconciliation;publication/eligibility evidence trước MODEL7 |
| D02 | Target taxonomy cho năm selected bindings | Đối chiếu UUID,parent path,category meaning với target thực;người duyệt/ngày/hash evidence |
| D03 | Deferred Power Bank subtype | Approved subtype UUID và canonical applicability/unit/operators/flags riêng;không parent fallback |
| D04 | Data quarantine/review execution và deployment | Workflow dữ liệu được authorize riêng;per-fact/group disposition;target deployment receipt riêng |

Để ghi final approval receipt,cần:

1. Danh tính/authority reviewer,ngày,AR2 version và đúng SHA-256 JSON;nêu accepted/rejected/deferred scope. Không yêu cầu duyệt lại các policy đã được người dùng chấp nhận.
2. Xác nhận biểu diễn năm Plus literal arrays và ranh giới synthetic protection-only/remaining context rules.
3. Canonical ownership/eligibility evidence trước runtime rank7;không phải source proposal UUID.
4. Target category reconciliation trước hoàn tất T003;flags=true của bốn definitions không thay chứng cứ này.
5. Separate future Power Bank subtype approval;hiện giữ battery flags=false.
6. Explicit review để áp dụng case-only evidence equivalence;manual decisions cho11 nhóm punctuation/spacing. Không tự sửa dữ liệu.
7. Separate authorizations/evidence cho quarantine/correction execution và deployment activation.

Không tạo registry-approval.json hoặc production registry giả đã duyệt. T003/T004 vẫn unchecked/BLOCKED,16/66 tasks giữ nguyên. Không chạy task implementation,quarantine,migration,DB write,commit hoặc push.

## 7. Evidence references và kiểm tra phạm vi

- [Matrix46 mục](evidence/identifier-review-matrix.md),[machine matrix](evidence/identifier-review-matrix.json),[source vocabulary proposal](evidence/identifier-vocabulary-proposal.json).
- [Review14 cases](evidence/identifier-review-cases.json),[original verification](evidence/identifier-review-results.json),[registry inventory](registry-inventory.md).
- [Source specifications](../../../Catalog-Data/data/specifications.jsonl),[source category mapping](../../../Catalog-Data/data/category_mapping.json).
- [AR2 machine report](evidence/approval-resolution-v2.json),[13 new primitive/span checks](evidence/approval-resolution-v2-span-checks.json).

Source products/variants/specifications/import-approval hashes vẫn khớp proposal. Runtime classifier/ranking ownership không được suy từ hash khớp. Chỉ tạo báo cáo AR2 và evidence mới;không chỉnh artifacts cũ,task statuses,application/test code,API contracts,migrations hoặc source data.
