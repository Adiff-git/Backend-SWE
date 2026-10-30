# First 29-product local-development import proposal

This is a proposal for human review, not an approval manifest. Stable identity is `source_retailer|source_url`; source product ID is included for cross-checking. The proposed demo price equals the exact October 2026 crawler `Offer.price` **as a review option only**. Every `approved_selling_price_vnd` in the actual CSV remains blank. No current retailer price or PostgreSQL SKU was checked.

All 29 proposal rows have `import_approval=NOT APPROVED` and `publication_approval=NOT APPROVED`, as do their SKU, selling-price, category and variant approvals. The other 21 pilot rows remain unapproved and excluded. This document is not executable input.

| Original row | Stable source identity | Source product ID | Proposed catalog SKU | Proposed category path | Verified source price VND | Proposed local demo price VND | Variant proposal | Import / publication |
|---:|---|---|---|---|---:|---:|---|---|
| 6 | `cellphones|https://cellphones.com.vn/laptop-asus-vivobook-15-x1504ma-bq632w.html` | `004847b4303a771ab9c8934e982fa358556399ef14279e6e512bedd9ca1d8d4c` | `laptop-asus-vivobook-15-x1504ma-bq632w` | Laptop › ASUS › Vivobook | 19,490,000 | **Proposal:** 19,490,000 | one `{}` option map; unapproved | NOT APPROVED / NOT APPROVED |
| 7 | `cellphones|https://cellphones.com.vn/laptop-acer-swift-air-14-sfa14-i31-52bv.html` | `005476652ed98eba18466105aac3ac7b8b9a71d03ac3fdc65588c187dfba8d6c` | `laptop-acer-swift-air-14-sfa14-i31-52bv` | Laptop › Acer › Swift | 24,990,000 | **Proposal:** 24,990,000 | one `{}` option map; unapproved | NOT APPROVED / NOT APPROVED |
| 8 | `cellphones|https://cellphones.com.vn/laptop-hp-omnibook-5-ai-16-af1046tu-bz7q8pa.html` | `006a13ce7d11d79e3f45007889161773d5349a981a478ef2ea439832e7304b8a` | `laptop-hp-omnibook-5-ai-16-af1046tu-bz7q8pa` | Laptop › HP › HP Omnibook › HP Omnibook 5 | 27,990,000 | **Proposal:** 27,990,000 | one `{}` option map; unapproved | NOT APPROVED / NOT APPROVED |
| 9 | `cellphones|https://cellphones.com.vn/laptop-dell-16-dc16251-dc6c7557w1.html` | `00b25d8bef8f7e1ece5e7792393870cd620ffb84e3bf92d56c20e1b119aa9d4b` | `laptop-dell-16-dc16251-dc6c7557w1` | Laptop › Dell › Dell 16 | 33,990,000 | **Proposal:** 33,990,000 | one `{}` option map; unapproved | NOT APPROVED / NOT APPROVED |
| 10 | `cellphones|https://cellphones.com.vn/laptop-lenovo-loq-essential-15irx11-83sc003rvn.html` | `00c0a9e9cc53b26a709f083fcfd943bd77ae48da37c73351758b5e185fd0b693` | `laptop-lenovo-loq-essential-15irx11-83sc003rvn` | Laptop › Lenovo › Lenovo LOQ | 38,990,000 | **Proposal:** 38,990,000 | one `{}` option map; unapproved | NOT APPROVED / NOT APPROVED |
| 16 | `cellphones|https://cellphones.com.vn/cap-type-c-to-c-cuktech-6a-240w-1-5m-ctc615w.html` | `000a8dd573d3adac8c539768b6b407cec9ade376d423da93a2e8380e5f30505c` | `cap-type-c-to-c-cuktech-6a-240w-1-5m-ctc615w` | Phụ kiện › Sạc - Cáp › Cáp › USB-C | 159,000 | **Proposal:** 159,000 | one `{}` option map; unapproved | NOT APPROVED / NOT APPROVED |
| 17 | `cellphones|https://cellphones.com.vn/cap-momax-usb-c-to-c-100w-dai-2m-1-link-dc25w.html` | `04860b02b61e0a8f62d113949ba881420e17dd0368513d71b8b730a5d584db66` | `cap-momax-usb-c-to-c-100w-dai-2m-1-link-dc25w` | Phụ kiện › Sạc - Cáp › Cáp › USB-C | 190,000 | **Proposal:** 190,000 | one `{}` option map; unapproved | NOT APPROVED / NOT APPROVED |
| 19 | `cellphones|https://cellphones.com.vn/cap-innostyle-type-c-to-c-powerflex-60w-1-5m-icc150al.html` | `06361d56316419a2bfb2977ac3e4aac127ace9e8b4192373589387fbfe754b24` | `cap-innostyle-type-c-to-c-powerflex-60w-1-5m-icc150al` | Phụ kiện › Sạc - Cáp › Cáp › Innostyle | 160,000 | **Proposal:** 160,000 | one `{}` option map; unapproved | NOT APPROVED / NOT APPROVED |
| 20 | `cellphones|https://cellphones.com.vn/cap-cuktech-type-c-to-c-5a-100w-1-35m-c2c515.html` | `064f053db6ec4e65b7fc37f63c447994e0de3fa16fbad7b7a7740de78e9acdbe` | `cap-cuktech-type-c-to-c-5a-100w-1-35m-c2c515` | Phụ kiện › Sạc - Cáp › Cáp › USB-C | 449,000 | **Proposal:** 449,000 | one `{}` option map; unapproved | NOT APPROVED / NOT APPROVED |
| 21 | `cellphones|https://cellphones.com.vn/tai-nghe-bluetooth-true-wireless-oppo-enco-buds-3.html` | `009f7330e83e4839e4c887b9c314efa7b779f5f4a177a35093053a7e29c470a2` | `tai-nghe-bluetooth-true-wireless-oppo-enco-buds-3` | Âm thanh › Tai nghe › Oppo | 790,000 | **Proposal:** 790,000 | one `{}` option map; unapproved | NOT APPROVED / NOT APPROVED |
| 22 | `cellphones|https://cellphones.com.vn/tai-nghe-chup-tai-baseus-inspire-xh1.html` | `018c3cac56d68c9452c4dac11916c00ab3f67a4274ed93461dd8c49a739e91c5` | `tai-nghe-chup-tai-baseus-inspire-xh1` | Âm thanh › Tai nghe › Baseus | 2,490,000 | **Proposal:** 2,490,000 | one `{}` option map; unapproved | NOT APPROVED / NOT APPROVED |
| 23 | `cellphones|https://cellphones.com.vn/tai-nghe-bluetooth-chup-tai-stargo-c200.html` | `024777f3ea2b7d9db4bf5967167b4eae6986153f8209004f2ba1820e1f3c4cf1` | `tai-nghe-bluetooth-chup-tai-stargo-c200` | Âm thanh › Tai nghe › Tai nghe STARGO | 250,000 | **Proposal:** 250,000 | one `{}` option map; unapproved | NOT APPROVED / NOT APPROVED |
| 24 | `cellphones|https://cellphones.com.vn/tai-nghe-hyperx-cloud-flight-2-wireless.html` | `0394285eef6ee48edcbaa44a6598dc3688a7fd27036beca26759cf898c47caa8` | `tai-nghe-hyperx-cloud-flight-2-wireless` | Âm thanh › Tai nghe › HyperX | 3,090,000 | **Proposal:** 3,090,000 | one `{}` option map; unapproved | NOT APPROVED / NOT APPROVED |
| 25 | `cellphones|https://cellphones.com.vn/tai-nghe-gaming-hyperx-cloud-earbuds-3-s.html` | `03ac0d040e7981c6ec637ed90e1ad6b168fac3c5c3cb56a61f8cdb9733da69cd` | `tai-nghe-gaming-hyperx-cloud-earbuds-3-s` | Âm thanh › Tai nghe › Tai nghe Earbuds | 1,590,000 | **Proposal:** 1,590,000 | one `{}` option map; unapproved | NOT APPROVED / NOT APPROVED |
| 26 | `cellphones|https://cellphones.com.vn/noi-com-dien-panasonic-sr-dk104wra.html` | `01ed7c7f2f51988673a39e3fe0644aae2265c291984f8e84578ae955ce5b57e9` | `noi-com-dien-panasonic-sr-dk104wra` | Đồ gia dụng › Nồi cơm điện › Nồi cơm điện tử | 2,780,000 | **Proposal:** 2,780,000 | one `{}` option map; unapproved | NOT APPROVED / NOT APPROVED |
| 27 | `cellphones|https://cellphones.com.vn/noi-com-dien-tu-cao-tan-tiger-jkt-f10w.html` | `0424f8c95fc7a0e05f5728046c3a9580f11d23390944f568f400ce07d03d0b1c` | `DRS.TG.18` | Đồ gia dụng › Nồi cơm điện › Nồi cơm điện tiger | 6,840,000 | **Proposal:** 6,840,000 | one `{}` option map; unapproved | NOT APPROVED / NOT APPROVED |
| 29 | `cellphones|https://cellphones.com.vn/noi-com-dien-tu-cao-tan-tiger-jpw-h18v-kv.html` | `09757775896fb601e5f14379317407382eb8065d15342e45cf039221ddeda08b` | `DRS.TG.30` | Đồ gia dụng › Nồi cơm điện › Nồi cơm điện tiger | 7,660,000 | **Proposal:** 7,660,000 | one `{}` option map; unapproved | NOT APPROVED / NOT APPROVED |
| 38 | `tgdd|https://www.thegioididong.com/op-lung-flipcover/op-lung-iphone-15-pro-max-coehl-magnetic-charging-prairie-nhua-deo-iml-uniq` | `01bc6a4c828876715e2753e762c39abc50fd7b5d35e85c4b4ec59a5ae8fa21ca` | `330329` | Ốp lưng đ.thoại | 500,000 | **Proposal:** 500,000 | one `{}` option map; unapproved | NOT APPROVED / NOT APPROVED |
| 40 | `tgdd|https://www.thegioididong.com/op-lung-flipcover/op-lung-magsafe-iphone-17-pro-max-pc-tpu-mipow-viralx-mgp17d-og` | `063c458bca19571d27c62578fe99014bcf9b9117b87cc736c297f1a6093f2c9b` | `342353` | Ốp lưng đ.thoại | 495,000 | **Proposal:** 495,000 | one `{}` option map; unapproved | NOT APPROVED / NOT APPROVED |
| 41 | `tgdd|https://www.thegioididong.com/may-cao-rau/may-cao-rau-kho-va-uot-flyco-fs889vn` | `02803561e4fdf999bd68df40c27683985a78da8d109bc43a089bcfd1e210c92e` | `366828` | Máy cạo râu | 550,000 | **Proposal:** 550,000 | one `{}` option map; unapproved | NOT APPROVED / NOT APPROVED |
| 42 | `tgdd|https://www.thegioididong.com/may-cao-rau/may-cao-rau-kho-va-uot-flyco-fs903vn` | `0534f3dcbfff504546c5e9922fdc8aa1f1ab51ced89b364cfd1f0397f8efdae3` | `366864` | Máy cạo râu | 790,000 | **Proposal:** 790,000 | one `{}` option map; unapproved | NOT APPROVED / NOT APPROVED |
| 43 | `tgdd|https://www.thegioididong.com/may-cao-rau/may-cao-rau-enchen-k8` | `059bdfe6bddda360cbd178c426e45d27932569dd16dbefefb90f6f699d5171c7` | `338553` | Máy cạo râu | 290,000 | **Proposal:** 290,000 | one `{}` option map; unapproved | NOT APPROVED / NOT APPROVED |
| 44 | `tgdd|https://www.thegioididong.com/may-cao-rau/may-cao-rau-panasonic-es534dp527` | `0c0f4f37439d40f169374a9df354d44e0b71220602042ff2ba88ebb737e6e109` | `198952` | Máy cạo râu | 250,000 | **Proposal:** 250,000 | one `{}` option map; unapproved | NOT APPROVED / NOT APPROVED |
| 45 | `tgdd|https://www.thegioididong.com/may-cao-rau/may-cao-rau-panasonic-es-cm3a-k451` | `0d9622966d25a8d3a6a8ec2fae7a52bffadbcd47d476e25afc2dcce9bdfad00f` | `340246` | Máy cạo râu | 2,800,000 | **Proposal:** 2,800,000 | one `{}` option map; unapproved | NOT APPROVED / NOT APPROVED |
| 46 | `tgdd|https://www.thegioididong.com/may-massage-toan-than/may-massage-co-buheung-mk-331` | `14bb22bedfc440e6a5d5a5a308a3a924fae3c0cd369a1731b7e356cbe6f7de23` | `342443` | Máy massage cá nhân | 1,290,000 | **Proposal:** 1,290,000 | one `{}` option map; unapproved | NOT APPROVED / NOT APPROVED |
| 47 | `tgdd|https://www.thegioididong.com/may-massage-toan-than/may-massage-dau-buheung-mk-341` | `1fe9a5bda240c979c688246252d5a7393a4c154eacaed3d517f6e08f70bec1bd` | `342446` | Máy massage cá nhân | 490,000 | **Proposal:** 490,000 | one `{}` option map; unapproved | NOT APPROVED / NOT APPROVED |
| 48 | `tgdd|https://www.thegioididong.com/may-massage-toan-than/may-massage-co-vai-gay-poongsan-n18-br` | `3440c942ae6350a912865bf9ca03cc73bb5d60519d7faf76edf78d34efce35b2` | `344409` | Máy massage cá nhân | 1,490,000 | **Proposal:** 1,490,000 | one `{}` option map; unapproved | NOT APPROVED / NOT APPROVED |
| 49 | `tgdd|https://www.thegioididong.com/may-massage-toan-than/may-massage-co-vai-gay-bomidi-mp2-lite` | `37f4e480d25fee76d9633b1fd219518373870a0f807c810c3abf7df3fec434f8` | `342031` | Máy massage cá nhân | 960,000 | **Proposal:** 960,000 | one `{}` option map; unapproved | NOT APPROVED / NOT APPROVED |
| 50 | `tgdd|https://www.thegioididong.com/may-massage-toan-than/may-massage-tay-choice-dr-dc-003` | `43f4d5da15b69afb96baf683a03e4d47e6b8446ff480cdb2f5518dd812e39b1e` | `363026` | Máy massage cá nhân | 690,000 | **Proposal:** 690,000 | one `{}` option map; unapproved | NOT APPROVED / NOT APPROVED |

## Decisions before a selected manifest exists

1. Confirm each proposed catalog SKU. CellphoneS slugs and `DRS.TG.18`/`DRS.TG.30` are source identifiers; TGDD numeric values are retailer identifiers. Check case-insensitive uniqueness against the eventual target. Keep `source_sku` unchanged.
2. Approve each category path or choose an existing category UUID. Source-specific paths can create semantically duplicate categories across retailers. Row 23 has a STARGO naming question; TGDD phone cases use an abbreviated root.
3. Confirm one sellable product with one empty-option variant for each selected identity. The importer cannot regroup source products into shared variants. No product images are proposed.
4. Decide whether each historical source price is suitable as a local demo price. The proposal column does not populate `approved_selling_price_vnd` and is not an approval.
5. Set `sku_approval`, `selling_price_approval`, `category_approval`, `variant_mapping_approval`, and `import_approval` separately after review. Keep `publication_approval=NOT APPROVED` for DRAFT unless publication is independently approved. Keep the other 21 rows unapproved.

## Fresh PostgreSQL 17 setup for a later approved run

The repository `compose.yaml` runs PostgreSQL 17 and uses a Compose project-scoped `postgres-data` volume. Use a **new project name and unused host ports**, never the default project or an existing volume. Run from the Backend-SWE root. The commands below are instructions only; they were not executed for this review.

```bash
export COMPOSE_PROJECT_NAME=catalog-pilot-20261028-01
export LOCAL_DB_NAME=backend
export LOCAL_DB_USER=backend
export LOCAL_DB_PORT=55432
export LOCAL_STORAGE_PORT=14566
docker compose -p "$COMPOSE_PROJECT_NAME" ps -a
docker volume ls --filter "name=${COMPOSE_PROJECT_NAME}_postgres-data"
```

Proceed only when both checks show no existing project container or volume and both ports are free. Choose a different project name/ports if anything exists. Do not remove an existing volume. Enter secrets privately without echoing them:

```bash
read -r -s -p "New local database password: " LOCAL_DB_PASSWORD; printf '\n'
export LOCAL_DB_PASSWORD
read -r -s -p "Local JWT secret: " JWT_SECRET; printf '\n'
export JWT_SECRET
export JWT_ISSUER=urn:backend-swe:catalog-pilot
export LOCAL_DB_HOST=127.0.0.1
export LOCAL_STORAGE_ENDPOINT="http://127.0.0.1:${LOCAL_STORAGE_PORT}"
export LOCAL_STORAGE_SIGNING_ENDPOINT="$LOCAL_STORAGE_ENDPOINT"
export CATALOG_IMAGE_ENDPOINT="$LOCAL_STORAGE_ENDPOINT"
export SERVER_PORT=18080
docker compose -p "$COMPOSE_PROJECT_NAME" up -d postgres localstack
docker compose -p "$COMPOSE_PROJECT_NAME" ps
```

The Spring `local` profile uses `backend`, the default `classpath:db/migration` Flyway location, and Hibernate `ddl-auto=validate`. After the new services become healthy, start the application once to apply V1–V9 and validate the schema, then stop the application normally. This is future schema setup, not a product import:

```bash
mvn -B -ntp -f demo/pom.xml spring-boot:run -Dspring-boot.run.profiles=local
```

Inspect the **new** database with read-only queries. Never run Flyway clean, repair, or baseline:

```bash
docker compose -p "$COMPOSE_PROJECT_NAME" exec -T postgres psql -X -v ON_ERROR_STOP=1 -U backend -d backend -c "SELECT current_database(), current_schema(), current_setting('server_version');"
docker compose -p "$COMPOSE_PROJECT_NAME" exec -T postgres psql -X -v ON_ERROR_STOP=1 -U backend -d backend -c 'SELECT installed_rank, version, script, checksum, success FROM public.flyway_schema_history ORDER BY installed_rank;'
docker compose -p "$COMPOSE_PROJECT_NAME" exec -T postgres psql -X -v ON_ERROR_STOP=1 -U backend -d backend -c 'SELECT (SELECT count(*) FROM public.products) AS products, (SELECT count(*) FROM public.product_variants) AS variants;'
```

Stop if history is absent, incomplete, checksum-different, or has extra entries. Never point this plan at the teammate's old V1–V6 database. A later `--check-db` preview requires a privately supplied importer DSN and exact target confirmation; that preview was not run here.

## Offline validation

```bash
python3 tools/catalog-import/import_postgres.py --dry-run --manifest tools/catalog-import/review/demo-products-approval.csv
```

The unchanged 50-row manifest currently yields 0 eligible, 50 pending, and 0 rejected. The proposal does not change it.
