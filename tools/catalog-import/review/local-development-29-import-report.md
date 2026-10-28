# First local development Catalog import: execution report

- Executed: 2026-10-29 00:29:27 +07:00, once, using the approval-gated importer in `--execute` mode.
- Target: isolated Docker Compose project `catalog-pilot-20261029-01`, container `catalog-pilot-20261029-01-postgres-1`, PostgreSQL 17.11 database `backend` at `127.0.0.1:55432`, volume `catalog-pilot-20261029-01_postgres-data`. Exact successful Flyway V1–V9 history checked before and after.
- Approval policy: 29 reviewed source identities only; source SKU as local development SKU, historical October 2026 `Offer.price` as approved LOCAL DEMO whole-VND price, reviewed category path, one Product and one `{}` ProductVariant per source record. Pilot rows 6–10 PUBLISHED; remaining 24 selected rows DRAFT. No images. The 21 excluded rows stayed NOT APPROVED.
- Offline dry-run: 29 eligible, 21 pending, 0 rejected, 0 duplicate proposed SKUs. Pre-import `--check-db`: same counts, exact target/history/schema and no existing conflicts.
- Write result: 31 categories, 29 products and 29 variants created in one transaction; 0 unchanged products on first execution; no errors.
- Post-import read-only SQL: 31 categories, 29 products, 29 variants, 5 PUBLISHED, 24 DRAFT, 0 ProductImage rows. Each product and variant UUID, category, SKU, options and price matched the approved manifest; no case-insensitive SKU duplicates.
- API: anonymous `GET /api/products?page=0&size=20` returned 200, exactly five PUBLISHED items, page 0, size 20, totalItems 5, totalPages 1. All five Product Detail GETs returned 200 with exact IDs, names, categories, one variant and local demo price; all 24 DRAFT Detail GETs returned 404.
- Read-only idempotency check: `--check-db` reported 29 unchanged products, 0 categories/products/variants to create. No second write-mode run occurred.
- Local-only executable manifest: `tools/catalog-import/output/local-development-29-approval.csv` (Git ignored, mode 0600). The canonical `review/demo-products-approval.csv` and normalized source facts were not modified.

## Approved source identities

- `cellphones|https://cellphones.com.vn/laptop-asus-vivobook-15-x1504ma-bq632w.html` → `204aa314-9d7e-5df5-9984-02e1d46db7f4` (PUBLISHED)
- `cellphones|https://cellphones.com.vn/laptop-acer-swift-air-14-sfa14-i31-52bv.html` → `e24ad827-49ed-5b8a-a855-66096deb9e76` (PUBLISHED)
- `cellphones|https://cellphones.com.vn/laptop-hp-omnibook-5-ai-16-af1046tu-bz7q8pa.html` → `e5e588ec-aa01-5e45-a5e9-55c825122d6a` (PUBLISHED)
- `cellphones|https://cellphones.com.vn/laptop-dell-16-dc16251-dc6c7557w1.html` → `2aacbe4d-cca8-5029-aecf-44ca835cd6e0` (PUBLISHED)
- `cellphones|https://cellphones.com.vn/laptop-lenovo-loq-essential-15irx11-83sc003rvn.html` → `06e292df-a263-5734-97db-c18237378f1f` (PUBLISHED)
- `cellphones|https://cellphones.com.vn/cap-type-c-to-c-cuktech-6a-240w-1-5m-ctc615w.html` → `e0e6e774-de5f-526d-9e06-f9fda5245f0b` (DRAFT)
- `cellphones|https://cellphones.com.vn/cap-momax-usb-c-to-c-100w-dai-2m-1-link-dc25w.html` → `b555a297-01b5-5708-8208-b4bb30c95d50` (DRAFT)
- `cellphones|https://cellphones.com.vn/cap-innostyle-type-c-to-c-powerflex-60w-1-5m-icc150al.html` → `4951ef30-b51f-5ca1-909f-44e0c469347b` (DRAFT)
- `cellphones|https://cellphones.com.vn/cap-cuktech-type-c-to-c-5a-100w-1-35m-c2c515.html` → `bfda0136-1863-564d-8691-d3840d492a43` (DRAFT)
- `cellphones|https://cellphones.com.vn/tai-nghe-bluetooth-true-wireless-oppo-enco-buds-3.html` → `97fa6fea-f39f-5068-be45-d51f7536307c` (DRAFT)
- `cellphones|https://cellphones.com.vn/tai-nghe-chup-tai-baseus-inspire-xh1.html` → `a8923e48-5c4a-583e-84fc-4bf52c839829` (DRAFT)
- `cellphones|https://cellphones.com.vn/tai-nghe-bluetooth-chup-tai-stargo-c200.html` → `dc4b7b5d-bcbf-5871-9361-3f69a0f33961` (DRAFT)
- `cellphones|https://cellphones.com.vn/tai-nghe-hyperx-cloud-flight-2-wireless.html` → `733caae8-39a9-5b25-9e94-6252f37b5ad7` (DRAFT)
- `cellphones|https://cellphones.com.vn/tai-nghe-gaming-hyperx-cloud-earbuds-3-s.html` → `f36eb81d-36ac-5875-a398-5269ab7d8d7d` (DRAFT)
- `cellphones|https://cellphones.com.vn/noi-com-dien-panasonic-sr-dk104wra.html` → `c26b65b0-4823-521d-a6b6-bc29f68d6b3a` (DRAFT)
- `cellphones|https://cellphones.com.vn/noi-com-dien-tu-cao-tan-tiger-jkt-f10w.html` → `e4b11f22-c82f-5c3b-a296-7854d84b7a78` (DRAFT)
- `cellphones|https://cellphones.com.vn/noi-com-dien-tu-cao-tan-tiger-jpw-h18v-kv.html` → `3759b882-d53e-587c-a9a5-b6445929dd64` (DRAFT)
- `tgdd|https://www.thegioididong.com/op-lung-flipcover/op-lung-iphone-15-pro-max-coehl-magnetic-charging-prairie-nhua-deo-iml-uniq` → `b9222ca5-ce73-5690-ba76-4fe5dc96afa4` (DRAFT)
- `tgdd|https://www.thegioididong.com/op-lung-flipcover/op-lung-magsafe-iphone-17-pro-max-pc-tpu-mipow-viralx-mgp17d-og` → `558f2e7d-68d5-5f82-82f1-9e0a3a09c034` (DRAFT)
- `tgdd|https://www.thegioididong.com/may-cao-rau/may-cao-rau-kho-va-uot-flyco-fs889vn` → `0caacd30-185f-51c3-87d4-3e2cbaf967c9` (DRAFT)
- `tgdd|https://www.thegioididong.com/may-cao-rau/may-cao-rau-kho-va-uot-flyco-fs903vn` → `c7f447dd-c4f2-5c90-b39f-6ea071077461` (DRAFT)
- `tgdd|https://www.thegioididong.com/may-cao-rau/may-cao-rau-enchen-k8` → `4c2b6ed5-c31b-5628-844a-8d84a894cb06` (DRAFT)
- `tgdd|https://www.thegioididong.com/may-cao-rau/may-cao-rau-panasonic-es534dp527` → `86b30768-cfef-5e40-951c-210ff96131b1` (DRAFT)
- `tgdd|https://www.thegioididong.com/may-cao-rau/may-cao-rau-panasonic-es-cm3a-k451` → `de5da6b3-5c5c-5e8c-b71a-25b2e9d283d1` (DRAFT)
- `tgdd|https://www.thegioididong.com/may-massage-toan-than/may-massage-co-buheung-mk-331` → `868f7d7f-5990-5f09-a24d-529c47a2270c` (DRAFT)
- `tgdd|https://www.thegioididong.com/may-massage-toan-than/may-massage-dau-buheung-mk-341` → `27bf2a87-c169-5c5f-938a-c04790d9b341` (DRAFT)
- `tgdd|https://www.thegioididong.com/may-massage-toan-than/may-massage-co-vai-gay-poongsan-n18-br` → `9412d120-3d25-5599-a380-26d1cf965878` (DRAFT)
- `tgdd|https://www.thegioididong.com/may-massage-toan-than/may-massage-co-vai-gay-bomidi-mp2-lite` → `400531f2-8ba6-5c5d-9005-bb7bdc602631` (DRAFT)
- `tgdd|https://www.thegioididong.com/may-massage-toan-than/may-massage-tay-choice-dr-dc-003` → `9842e77f-23c1-5ef5-9a02-4b9eb02bb659` (DRAFT)

## PUBLISHED product IDs

- `204aa314-9d7e-5df5-9984-02e1d46db7f4` — Laptop ASUS Vivobook 15 X1504MA-BQ632W
- `e24ad827-49ed-5b8a-a855-66096deb9e76` — Laptop Acer Swift Air 14 SFA14-I31-52BV
- `e5e588ec-aa01-5e45-a5e9-55c825122d6a` — Laptop HP Omnibook 5 AI 16-AF1046TU BZ7Q8PA
- `2aacbe4d-cca8-5029-aecf-44ca835cd6e0` — Laptop Dell 16 DC16251 DC6C7557W1
- `06e292df-a263-5734-97db-c18237378f1f` — Laptop Lenovo LOQ Essential 15IRX11 83SC003RVN

## Remaining source-data concerns

- Historical retailer prices were authorized only as LOCAL DEMO selling prices. Current commercial prices and availability are unverified.
- The reviewed source hierarchy contains the leaf `Tai nghe STARGO` for pilot row 23 and the abbreviated TGDD root `Ốp lưng đ.thoại` for rows 38 and 40. They do not conflict with the previously empty target taxonomy, but a broader catalog taxonomy should reconcile these labels before expansion.
- Source identity is not stored in Product tables; the deterministic UUID links each imported product to the approved retailer and URL in this manifest. A changed source URL requires deliberate reconciliation.
- No product images or S3 object keys were imported.
