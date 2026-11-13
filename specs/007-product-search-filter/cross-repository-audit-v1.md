# Audit liên repository — Product Search & Filter v1

Ngày:2026-11-09 · Phạm vi:Catalog-Data,Backend-SWE,AI-SWE · Chủ thể thực hiện:Codex audit tự động theo chỉ dẫn người dùng.

**Kết luận:đủ nguồn để đề xuất scope và tạo corpus candidates;còn thiếu canonical ownership,target taxonomy,ground truth và calibration để nghiệm thu. T003/T004 không PASS;Registry/Rank7 không active.** Chỉ tạo báo cáo và evidence audit,không truy cập database/shared production,không network/API/import/bytecode execution,không thay application/source datasets/migration/contracts/tasks.md,không commit/push.

## 1. Phương pháp,phiên bản và evidence

- Catalog-Data:`c5dcfaabdf61b244783d46a1403107e19cd2b02c`,branch `main`.
- Backend-SWE:`c5bd4e7eeb39d9b06a0c7e36af9a65635058613f`,branch `features/SearchAndFilter`.
- AI-SWE:`bf748180e8c23822a0444f20d37190c11174e27b`,branch `main`.

Catalog-Data và AI-SWE có working tree sạch tại đầu audit;Backend-SWE có các thay đổi có sẵn được giữ nguyên. Đây là audit working tree local,không chứng minh remote đã publish hoặc dịch vụ đang chạy.

Đọc source JSONL/CSV/manifests,code/schema/migrations và selected local historical receipts/snapshots. Chạy existing Java D2 normalizer qua existing NormalizationParityProbe trong thư mục tạm để đếm exact tokens/title contexts. Không dùng substring counts thay cho family token counts. Temporary probe directory đã tự xóa;không sửa Java hoặc chạy search implementation. Không chạy classifier/fuzzy/ranking engine hay PostgreSQL.

Artifact chính:

- [Machine audit](evidence/cross-repository-audit-v1.json):source hashes,19 family counts,21 ambiguous context windows,sáu anchor rows,đủ30 recorded case references,full value distributions,133 evidence groups,21 review-candidate records,taxonomy/lineage receipts.
- [Corpus candidates](evidence/cross-repository-corpus-candidates-v1.json):36 candidates/reservations,**0 manually reviewed expected result sets**,target Product/Variant IDs và expected order chưa được freeze.
- [Script tái lập](evidence/audit-cross-repository-v1.py):chỉ đọc input và ghi hai audit JSON vào feature evidence;không chạy importer/DB. Chạy từ workspace bằng `python3 Backend-SWE/specs/007-product-search-filter/evidence/audit-cross-repository-v1.py`.

Điều kiện tái lập local:Python stdlib,Java/javac hiện có,source package đúng hash,existing Surefire XML/classpath/classes cho probe,AR2/matrix evidence và legacy local snapshot. Script pin15 manual code-evidence hashes và dừng nếu các file đó đổi;không tự chứng nhận kiến trúc của một revision code khác. Historical .private receipts/media checkpoints là local inputs có thể thiếu trên máy khác;script không cài dependencies hoặc tìm remote thay thế. Missing inputs không phải proof PASS. Các private receipts chỉ được xuất aggregate/hash an toàn,không credentials,operator identity,DSN hoặc image base64.

Tất cả hashes được manifest liệt kê đều khớp bytes local. Nguồn có1500 Products,1500 Variants,1500 specification documents,10 source categories. **1500 DRAFT,1500 specifications UNVERIFIED;478 thiếu Brand proposal,3 thiếu description,1500 empty option maps,0 khác biệt technical document giữa specifications và variants.** Có94 titles non-ASCII;không dùng con số này để kết luận ngôn ngữ hoặc Vietnamese coverage. Không có duplicate whole normalized titles trong cohort;điều đó không loại model/family collisions giữa ASINs.

## 2. T004 — tất cả46 mục và khuyến nghị approval

### 19 family terms

| Term | Products có exact D2 token trong title | Brand-token collisions |
| --- | ---: | ---: |
| galaxy | 253 | 0 |
| iphone | 209 | 0 |
| ipad | 69 | 0 |
| xperia | 5 | 0 |
| redmi | 39 | 0 |
| thinkpad | 6 | 0 |
| ideapad | 12 | 0 |
| inspiron | 7 | 0 |
| vivobook | 11 | 0 |
| zenbook | 1 | 0 |
| macbook | 18 | 0 |
| chromebook | 63 | 0 |
| elitebook | 2 | 0 |
| celeron | 37 | 0 |
| pentium | 7 | 0 |
| ryzen | 18 | 0 |
| snapdragon | 10 | 0 |
| geforce | 13 | 0 |
| radeon | 10 | 0 |

Cả19 đều có source evidence;zero observed Brand collisions không bảo đảm dataset tương lai. AR2 global whole-token fuzzy exclusion giữ nguyên,kể cả Chromebook. Không tự có MODEL7. Chấp nhận homonym/typo recall cost:galaxi không được fuzzy vào protected Galaxy;kể cả Galaxy dùng như danh từ. Exact/prefix/partial và natural Brand fuzzy giữ contract;không suy toàn query sẽ empty chỉ vì một pair bị fuzzy-excluded.

### Sáu anchors/ASIN/SKU/proposed ID

| Anchor | ASIN | SKU nguồn/CSV đã khớp | Source Product UUID proposal — KHÔNG phải target proof |
| --- | --- | --- | --- |
| wh-1000xm5 | B0C5ZC7FWR | DEMO-AMZ-B0C5ZC7FWR | 82b1eb26-7dca-5ae1-995d-a6cbca4a4abf |
| wh-1000xm4 | B08DBDSVBC | DEMO-AMZ-B08DBDSVBC | 5cffb617-88a6-5962-a04a-6c000f08e8d2 |
| a515f | B08PPSRLQ5 | DEMO-AMZ-B08PPSRLQ5 | 84d8a85d-345c-5073-b9ad-c229dd8d51ef |
| pro-i | B09L88FZ95 | DEMO-AMZ-B09L88FZ95 | 5dcd2178-361b-59d2-91eb-a7d56b4762cb |
| l210 | B081V6W99V | DEMO-AMZ-B081V6W99V | 6f5c3f0a-fbc6-5c44-bd40-c1ab2b3a2cac |
| be2026 | B08R7D4KZJ | DEMO-AMZ-B08R7D4KZJ | a55c4f7c-8789-5a71-b4cd-55857956258c |

Sáu source Variant UUID proposals và full titles nằm trong machine audit. Tất cả SKU source khớp approved CSV;toàn cohort1500/1500 khớp. Source Product/Variant proposals unique và liên kết ASIN/Product đúng trong package. Tên sản phẩm chứng minh model mention;không có dedicated device-model field đã xác minh. cpu_model là component fact,không tự thành owning device model.

Cả sáu không khớp ID/Variant/SKU trong legacy1000 snapshot khác dataset. Tuy nhiên,**cả1500 source Product UUID proposals xuất hiện trong cached media object-key paths** của Backend;đây là corroborating media evidence,không chứng minh Product/Variant row,FK hoặc ownership hiện tại. Image UUID trong object key không phải Variant UUID. Không bật rank7 từ cache.

Collision/naming boundaries:WH-1000XM4 khác XM5;XM6 typo/different-model negative không được fuzzy vào XM5. Title có cả WH-1000XM5 và WH1000XM5 nhưng alias/rank7 không tự được duyệt. L210 khác extended L210MA-DB01 nếu chưa có explicit alias scope. Accessory có Microsoft Surface Pro/Galaxy S8 Plus references không sở hữu điện thoại/tablet được nhắc. Nhiều ASIN/configuration có thể cùng model family;không merge Product bằng model text.

### 21 ambiguous terms — actual source token windows

Bảng dưới là occurrence context để reviewer đọc,không phải classifier quyết định ownership/technical role. Full windows kèm ASIN/line/title nằm trong JSON;ba dấu+ và Slash vẫn là D2 token punctuation,không bị chuẩn hóa thành alias.

| Term | Products có exact token | Một token window thực tế |
| --- | ---: | --- |
| core | 57 | samsung / galaxy / a03 / core / a032m / dual / sim / gsm |
| pro | 157 | xiaomi / redmi / note8 / pro / smartphone / 6 / gb / 64 |
| air | 21 | plus / 5s / ipad / pro / air / 2 / and / more / black |
| note | 87 | samsung / galaxy / note / 10 / lite / n770f / dual |
| plus | 83 | samsung / galaxy / s21+ / plus / 5g / factory / unlocked / android |
| max | 44 | walkerfit / m2 / max / smart / watch / answer/make / call |
| studio | 5 | sony / mdrv6 / studio / monitor / headphones / with / ccaw |
| surface | 6 | mouse / use / on / any / surface / hyper-fast / scrolling / ergonomic / shape |
| pavilion | 9 | hp / pavilion / x360 / 14 / convertible / 2-in-1 |
| aspire | 8 | acer / aspire / 5 / a515-46-r14k / slim / laptop |
| envy | 2 | hp / envy / x360 / 2-in-1 / flip / laptop |
| spectre | 1 | hp / spectre / x360 / 2-in-1 / laptop / 15.6 |
| pixel | 19 | google / pixel / 5 / 5g / android / phone |
| nord | 3 | oneplus / nord / n10 / 5g / unlocked / smartphone |
| vega | 6 | 256gb / ssd / amd / radeon / vega / 8 / graphics / narrow / bezel |
| graphics | 19 | ram / 64gb / storage / uhd / graphics / 600 / chrome / os / onyx |
| hd | 150 | 64gb / 4gb / ram / 6.5 / hd / 4g / lte / gsm / factory |
| uhd | 13 | 4gb / ram / 64gb / storage / uhd / graphics / 600 / chrome / os |
| rog | 3 | asus / rog / cetra / true / wireless / gaming |
| tuf | 3 | asus / tuf / f15 / gaming / laptop / 15.6 |
| legion | 1 | 520-22 / 520-24 / 520-27 / 720-18 / legion / y520-15 / v110-15 / black |

Các context Pro/Plus/Max/Core/Chromebook và synthetic spans tiếp tục theo [AR2](approval-resolution-v2.md). Ví dụ actual:Redmi Note 8/9 Pro,Huawei P20/P30 Pro,Galaxy S20+ Plus/S21+ Plus,LG K22+ Plus,Chromebook Plus,WalkerFit M2 Max,Intel Core,Galaxy A03 Core. Windows Pro/Watts Max/Dual Core/generic Surface không tự thành model. Chỉ exact literal token arrays đã duyệt;không regex+/alias expansion. Nhiều iPhone Pro/Max mentions thuộc compatibility lists của sạc/phụ kiện,không canonical model ownership.

### Đủ30 expected cases

Machine audit giữ D01–D16 và IV01–IV14 từ evidence hiện có. D01–D12 kiểm tra technical punctuation/numbers/near-identifiers;D13–D16 natural text,Vietnamese và separator runs. IV01–IV04 model/source-vs-synthetic,family typo và Brand typo;IV05–IV10 accessory/generic Surface,Pavilion,Pixel,Core,Studio;IV11–IV14 demo SKU,numeric/unit syntax,Galaxy homonym và unapproved cpu_model. Các source hashes vẫn khớp evidence gốc:16 recorded primitive passes+14 source/proposal checks. **Không coi đây là30 classifier/ranking passes;runtime count=0.** Grammar mới không thay input/expected values;source-only i7-1360P/C++ không được giả là catalog hardware observations.

Khuyến nghị:nguồn đủ để reviewer ký **source-scoped vocabulary policy46 mục và expected cases AR2**,với coverage/recall limits ghi rõ. Không đủ ký canonical target ownership hoặc bật MODEL7. Final T004 receipt cần named authorized reviewer,date,version/hash và accepted/deferred scope;không suy task completion trong audit này. Nếu scope acceptance chỉ source-level,phải ghi runtime ownership/eligibility vẫn BLOCKED;không tự biến DB proof thành kết quả đã có hoặc thay gate hiện hành.

## 3. T003 — bốn keys,năm candidate bindings

Giữ selected v1:display_diagonal Laptop/Tablet;physical_ram Laptop;installed_storage_gb Laptop;display_resolution Laptop. Searchable/filterable=true;identifier=false theo AR2. Tất cả technical values không fuzzy theo field/source rule,không phải Product identity. Battery deferred flags=false,cần approved Power Bank subtype;parent binding không active.

| Category | Key | Records | Observed range (quantity) | Số distinct values |
| --- | --- | ---: | --- | ---: |
| Laptop | display_diagonal | 150 | 10.1–17.3 | 12 |
| Laptop | display_resolution | 105 | None–None | 8 |
| Laptop | installed_storage_gb | 148 | 16–512 | 9 |
| Laptop | physical_ram | 150 | 1–16 | 7 |
| Smartphone | display_diagonal | 137 | 1.7–6088 | 39 |
| Smartphone | physical_ram | 127 | 1–128 | 12 |
| Tablet | display_diagonal | 74 | 7–18.4 | 18 |
| Tablet | physical_ram | 72 | 1–256 | 11 |

Type/unit của quantity nhất quán theo key:display_diagonal quantity/inch;RAM/storage quantity/GB. display_resolution là text,widthxheight,không unit. Chưa thấy active numeric/boolean production key trong chín observed keys;synthetic fixtures không tạo approval. Counts theo key overlap,không phải Product totals.

Phân bố đáng chú ý:

- Laptop RAM:4GB81;8GB43;16GB16;12GB5;2GB3;1GB1;6GB1.
- Laptop storage:64GB36;256GB33;32GB26;512GB22;128GB20;16GB5;500GB4;65GB1;320GB1.
- Laptop displays:15.6inch47;14inch45;11.6inch37;13.3inch7;các values còn lại nằm trong full distribution.
- Laptop resolution:1366x76852;1920x108044;3840x21603;1920x12002;1600x1200/1600x600/1280x800/1360x768 mỗi value1.
- Tablet display có74/75 rows;Laptop resolution105/150,storage148/150. Missing facts không được biến thành0/false. Full distributions trong JSON bao gồm các categories đang excluded.

**Phát hiện mới bên trong chính selected keys:**

1. B09TWRH21N,dòng243:title ghi64GB Storage,source Hard Drive ghi65GB SSD,fact65GB. Cần manual provenance review;không tự sửa65→64.
2. B07J1SY5QQ,dòng210:title ghi16:10,source resolution1600x600 có tỷ lệ khác. Đây là source inconsistency,không đủ dữ liệu để đoán resolution đúng.

Vì vậy approval định nghĩa bốn keys không chứng nhận mọi stored fact thuộc năm bindings là đáng tin cậy.

### Category applicability/taxonomy

Source mapping có10 entries,root Electronics+nine leaf categories,UUID/path/approval trong machine evidence. Nhưng products.category_id và1500 approved_category_uuid CSV đều chưa được điền;source mapping APPROVED không phải target row proof.

Backend V1 tạo Category UUID PK và self-parent FK;Product bắt buộc FK category. **V1–V15 không seed taxonomy category IDs.** Legacy catalog-dev-v1 snapshot có31 categories,không UUID nào trùng10 source categories. Cùng tên Laptop nhưng UUID khác:

- Source:`31549723-c1a2-56a6-a1a1-64da36e16f65`,dưới Electronics.
- Historical legacy snapshot:`34478860-8cf2-5110-86e6-9c56b5ddbaf2`,root Laptop có brand/model children.

Đây là hai snapshot/scope khác nhau,không chứng minh current target sai hoặc đã map đúng. Receipt stage100 báo tạo10 categories nhưng không có per-category row mapping để audit. Không suy mapping bằng tên hoặc overlap;target taxonomy vẫn BLOCKED.

## 4. Data quality và133 groups

Có457 key/record groups có nhiều accepted evidence entries;133 groups chứa normalized values khác nhau:77cpu_model+55cpu_family+1gpu_model. Trong đó122 chỉ khác case;11 khác punctuation/spacing như Core i3 10110u/Core i3-10110U,A6 5350M/A6-5350M,GTX1650Ti/GTX 1650 Ti. **Không phải133 khác biệt hardware đã xác minh.** Mọi group và raw/source pointers được giữ trong JSON;không auto-merge/rewrite. Case-only matching equivalence chỉ sau explicit review;11 nhóm cần manual decision. Không thay case-insensitive normalization của query đã duyệt.

Tìm được21 records cần quality review theo signals được công bố:6088-inch facts;RAM/storage nghi vấn ở Tablet/Smartphone;CPU text dưới gpu_model;hai selected-key discrepancies mới. Các ngưỡngRAM>=32 Tablet/≥64 Smartphone chỉ là **audit review signals**,không phải approved runtime constraints hoặc bằng chứng mọi fact vượt ngưỡng đều sai. Source review_issues vẫn rỗng ở cả1500 rows,cho thấy empty issue list không phải semantic QA pass.

Safest exclusion policy trước activation:

1. Giữ key/category allowlist AR2 và excluded categories;không query unapproved raw technical fields.
2. Giữ raw source/provenance;quarantine/exclude ở cấp fact trong **workflow dữ liệu được authorize/audit riêng**,không suy value sửa đúng,không swap RAM/storage hoặc chuyển CPU→GPU/CPU field.
3. Nếu canonical dataset chưa có cơ chế trusted-fact disposition đã được phép,giữ affected activation BLOCKED;không bổ sung query-time ASIN blacklist,title classifier hoặc arbitrary numeric cutoffs để che dữ liệu bẩn.
4. Với local tests,dùng controlled reviewed fixture facts,không tuyên bố production rows sạch. Không xóa Product khỏi legacy browse,count/pagination hoặc thay Product Detail/Wishlist vì audit này.
5. Definition approval,fact validation và deployment readiness có receipts riêng. Audit không thực hiện quarantine,merge,repair hoặc activation.

## 5. Source→Backend lineage:proven,declared và chưa xác minh

| Bước | Bằng chứng có thật | Điều chưa được chứng minh |
| --- | --- | --- |
| Source identity |1500 unique ASIN/source_identity;stable Product/Variant proposals,relationships khớp | Canonical target rows có cùng IDs/owner |
| SKU | DEMO-AMZ-ASIN;1500/1500 khớp approved CSV | Target actual unique SKU/Variant FK và current publication |
| Categories |10 source mapping UUIDs,CSV target UUID blank | Target ID/path reconciliation;không chỉ name matching |
| Brand |1022 có brand_reference_proposal;478 thiếu;product.brand_id/approved CSV brand IDs chưa được xác minh tại đích | Canonical Brand mapping/aliases;không suy từ title prefix |
| Specs | Variant JSON giống specifications snapshot;schema_version1/scalars/quantity | Importer có persist đầy đủ hay không;fact correctness/approval |
| Price/options/status | Approved demo VND CSV,empty option maps,DRAFT policy | Không phải verified exchange-rate conversion hoặc full configuration grouping;publication là approval riêng |
| Source links | Source-key/normalized hashes được chuẩn bị |1500 thiếu source_url,original source_payload_sha256,approved_manifest_sha256,updated_at |
| Media cache |1500 proposal Product UUIDs xuất hiện trong cached object paths | Object key presence không phải DB FK/Variant ownership proof |

Source source_csv_row_sha256/normalization_source_sha256 khác nghĩa original payload hash;không được dùng làm dummy replacement chỉ để thỏa constraint. Backend V11 catalog_source_links yêu cầu URL/hash/manifest/updated_at và FK Variant với unique(source,source_key),có thể làm lineage authority **khi populated và được xác minh**. Receipt hiện có ghi0 source links.

Historical local receipts cho biết stage100 tạo100 Products/Variants và10 categories;stage1000 tạo900 và giữ100;stage1500 tạo500 và giữ1000. Đây là aggregate receipts,không current row-level evidence. Receipt ghi manifest_sha256=`b39193350c7b37a6e4fbcf5975d8210b0dc382e9429468a092319fb146941f65`,khác SHA256 bytes current catalog_manifest.json=`09b596fb6ef5aaa209d562c612378bf54afa0661e404a065ea1f2287b931d9e5`. Thiếu importer source/old manifest/hash-algorithm proof nên **UNRECONCILED**,không kết luận tự động import sai hoặc chưa import.

README trỏ tools/catalog-import/amazon_import.py nhưng file source không hiện diện/current git không track nó;chỉ còn .pyc cache. Audit không execute/decompile cache để coi nó là reviewed importer. Vì thế không xác minh được exact transformation/UUID algorithm/import SQL chỉ từ README. Source manifest còn yêu cầu V1–V10 exact history,trong current Backend V10 là Wishlist,V11 mới là Catalog enhancement và lịch sử đếnV15. Không repurpose migration cũ;phải reconcile importer/schema compatibility bằng source version cụ thể.

Metadata trạng thái giữa source products BLOCKED/DRAFT,manifest data APPROVED và CSV import APPROVED thuộc các lớp workflow khác nhau. Package validator chủ động mong products còn BLOCKED/DRAFT;local byte consistency không phải publication/specification approval. Không dùng manifest approval để bật Registry/Rank7.

### Bằng chứng chính xác cần cho canonical ownership/taxonomy

Cần sanitized row-level export từ owner hoặc authorized isolated restored snapshot,không shared production DB access trong audit:

- Target identity/time/schema/Flyway history theo installed_rank,source package/importer versions/hashes.
- ASIN/source key→source_link(if present)→Variant.id/sku/product_id→Product.id,name,category_id,status;so sánh source proposals và SKU,không chỉ title similarity.
- Sáu anchor mapping rows với owner/non-accessory evidence;eligible Variants và publication/same-variant conditions trước rank7.
- Selected category IDs,names,parent chains và declared source→target mapping,không assume UUID equality.
- Actual stored technical docs và trusted-fact dispositions;source-link absence được ghi nhận,không bù bằng fabricated URL/hash.

Không đưa ra query đã chạy hoặc database identity giả;không có live current target verification trong audit.

## 6. T005 — corpus readiness và candidates

Đã chuẩn bị36 audit candidates/reservations trong evidence riêng,**không ghi search-evaluation-corpus.json/us1-core-cases.json,không freeze T005**. Có26 query candidates và10 RK reservations. Every candidate có origin/source reference/required capabilities;expected target IDs,Variant IDs và ranking đều null/PENDING,manual expected-result review count0.

Các nhóm gồm actual demo-SKU equality;hai actual whole names thỏa256cp/16tokens;phrase Samsung Galaxy;prefix sam;partial amsu;proposed typo pairs samsnug↔samsung,sonny↔sony;protected model negatives;family/Brand/homonym/accessory false-positive candidates;sáu anchors và alias/extended-model boundaries;multiword shared+identifier;Vietnamese synthetic normalization pair;repeated phrase/dedup candidate.

Không coi negative pair là toàn query empty:WH1000XM5 thực sự có trong title dù chưa là reviewed model alias;Galaxy Star Projector là synthetic homonym chứ không giả là source Product. Positive fuzzy intentions chưa được chấm ground truth. 868/1500 full Product Names vượt query limits;632 đủ điều kiện. Không cắt title rồi coi truncated query là exact whole Name.

Source cohort chỉ có1 Variant/Product,options rỗng,không chứng minh cross-variant false positives,RK10 best-rank-vs-cheapest hoặc orphan D7. Cần controlled fixtures,expected IDs/order do reviewer freeze độc lập;không lấy implementation SQL output làm oracle. RK01–RK06/RK08–RK10 thuộcUS1.core;RK07 vẫnUS3.configuration và chỉ chạy sau approved spec/filter prerequisites. Giữ đủ RK01–RK10 cho final integration.

Source1500 DRAFT không phải Public GET corpus cho PUBLISHED endpoint. Chỉ tạo PUBLISHED fixtures trong disposable environment có scope test rõ;không publish source/current DB.94 non-ASCII names không chứng minh Vietnamese-language representativeness;cần approved Vietnamese/NFC/NFD/đ query/name fixtures riêng. Legacy Vietnamese snapshot có thể là nguồn bổ sung sau identity/provenance review,không tự merge với Amazon cohort.

## 7. T014 — calibration readiness

Dataset đủ làm nguồn initial natural Brand/title tokens và hard negatives sau manual labeling;chưa đủ đóng calibration gate. Có sparse brands/models,478 missing Brand proposal,mostly English source và single-variant skew. Cần stratify category/Brand,đồng thời group ASIN/model family để tránh gần-trùng model rò giữa calibration và validation splits. Đo sai biệt trước/sau19 family exclusions và ambiguous context rules,không chỉ aggregate accuracy.

Exploratory grid đề xuất:`0.20,0.25,0.30,0.35,0.40,0.50`,đều **NOT_EVALUATED/NOT_APPROVED**.0.30 vẫn starting point trong design,không phải measured optimum. Chưa chạy pg_trgm hoặc báo precision/recall số liệu.

Điều kiện nghiệm thu:

- Reviewer gán relevant/nonrelevant source/fixture identities cho từng query,including no-match cases;natural typo positives chỉ là intent candidates đến khi ký.
- Đo precision/recall per query/category/Brand và macro/micro summaries;ghi TP/FP/FN rõ. Protected identifier fuzzy false positives phải0.
- So prefilter candidates với exhaustive eligible single-token oracle;không top-K truncation làm mất recall.
- Verify strict `>` boundary,tokens3/4,query/candidate identifier exclusion,Vietnamese accents/NFC/NFD,configuration modes,pool-local threshold và current PG operator versions trên disposable PG.
- Model near-collisions XM4/XM5/XM6,i7-136OP/i7-1360P;Brand samsnug/Samsung;family galaxi/Galaxy;generic Surface/Pixel/Max;không relax protected values để cải thiện recall.
- Freeze T005 corpus/model scope trước T014;production matcher chưa có(current fragment reject nonblank q),không báo prototype đã chứng minh final implementation.

## 8. AI-SWE:không phải lineage/relevance authority

Source code xác nhận backend_client chỉ tạo HTTPX pool;public recommendation/chat vẫn placeholders,agent core độc lập. ProductProvider hiện chỉ fake/unavailable,UnavailableProductProvider raise unavailable;không có concrete live Backend adapter trong inspected code. Không DB access là boundary đúng của AI-SWE.

Internal tool DTO không phải Backend contract:product_id là generic bounded string;query/name max200;price float≤1e15;specifications là≤10 string values. Backend dùng UUID,query256,exact whole VND BigInteger/30digit incoming bounds,typed JSONB quantities/numbers/boolean và listing displayPrice/representativeVariantId. Không tự chuyển precision/schema hoặc dùng fake IDs/LLM answers làm ground truth/ownership. Cần adapter/contract work riêng nếu sau này tích hợp;ngoài scope feature hiện tại.

## 9. Blockers và thứ tự tiếp theo

| Gate | Evidence đủ để chuẩn bị | Evidence chính xác còn thiếu để đóng |
| --- | --- | --- |
| T004 |19/6/21 source verification,AR2 grammar,30 expected cases | Named authorized scope/version/hash receipt;canonical owner/eligibility proof trước runtime MODEL7;không suy từ media cache |
| T003 |Four keys/five source bindings,types/units/distributions,AR2 flags/exclusions | Target category reconciliation;named definition receipt;trusted-fact disposition for selected-key discrepancies;Power Bank deferred |
| T005 |36 reproducible draft candidates/reservations | T004 scope receipt,reviewed ground truth/expected IDs/order,controlled multivariant/Vietnamese/orphan fixtures;partition manifest |
| T014 |Source token pool,proposed typo/protected pairs,exploratory grid | T005 frozen labels/splits,actual isolated PG evaluations,precision/recall/prefilter/boundary evidence,reviewed threshold |
| Lineage |ASIN/proposals/CSV/media corroboration,aggregate receipts,FK schema | Versioned importer source/manifest reconciliation và sanitized row-level canonical exports |

Khuyến nghị:(1)owner cung cấp importer source/version và sanitized target snapshot/row exports;(2)chốt T004 source-scope receipt và giữ runtime ownership gate;(3)T005 reviewed core corpus/controlled fixtures;(4)T014 calibration vàT021 khi đúng prerequisites;(5)song song review target taxonomy/trusted-fact policy để hoàn tất T003 rồiT008. Không bật Registry/Rank7 chỉ vì report hoặc policy approval. Giữ RK07 trênUS3 và full final gate riêng. Không task nào được audit này thực hiện hoặc đánh dấu hoàn thành.

## Kiểm tra bảo toàn cuối audit

Đối chiếu SHA-256 của2300 preexisting tracked/untracked files trên cả ba repository:không file nào thay đổi. Chỉ thêm4 audit artifacts được liệt kê ở đầu report. T003/T004 vẫn unchecked;16/66 task completion giữ nguyên. JSON syntax,Python AST,Markdown links và git diff --check đạt. Đây là validation của audit artifacts,không phải acceptance của Search & Filter.

Machine-audit file SHA-256:`0d58cd98e8dd93e8e794e1ef612fc04e4b33f28a497b46243942062c36a19cf8`.
