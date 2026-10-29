# Local publication acceptance — 1,000 products

**DONE — all 1,000 existing products are PUBLISHED and retrievable through the private local API.**

Executed on 2026-10-29 on `feature/product-catalog`, following explicit authorization to publish the existing 995 DRAFT products for LOCAL DEVELOPMENT/Frontend testing. No public/shared deployment or image distribution was performed.

## Target, backup and controlled update

- Verified target: **`backend@127.0.0.1:55432`**, PostgreSQL 17, Compose project **`catalog-pilot-20261029-01`**.
- Verified running backend: **`http://127.0.0.1:18080`**, loopback only. Signed image gateway remains **`http://127.0.0.1:14566`**. PostgreSQL, LocalStack and gateway are healthy.
- All 1,000 Product UUIDs/source identities matched the reviewed seed and original image-import manifest. All names, descriptions, prices, SKUs, variants and categories matched; all 1,000 primary galleries and 1,096 actual image objects were validated before publication.
- New private backup: `output/publication-local/catalog-before-publish-all.dump`, **528,977 bytes**, mode 0600. `pg_restore --list` succeeded with **110 archive entries**. Its SHA-256 is bound to the private publication receipt. It was not restored over the pilot.
- `publish_local_catalog.py` changed **exactly 995** original DRAFT Products to PUBLISHED in one transaction. The original five PUBLISHED products were preserved. No insertion, image upload/import, price/SKU/category/variant/image update or Flyway modification occurred.
- Public tables were locked briefly, constraints were forced, and before/after fingerprints proved that only authorized Product status values changed. All other public tables and every nonstatus Product field were unchanged.
- A real repeated publication with the same receipt returned **zero changed rows** and retained 1,000 PUBLISHED / zero DRAFT.

The mode-0600 receipt records original statuses, exact target and manifest hashes, backup identity, immutable Catalog fingerprints and the latest action. First-write evidence remains separately in `publish-result.json`. This also allows reconciliation after interruption between database commit and receipt finalization.

[LOCAL_PUBLICATION.md](../LOCAL_PUBLICATION.md) contains exact preview/publication and **status-only rollback** commands. Rollback restores only the original 995 DRAFT statuses, preserves the original five PUBLISHED products, checks for data drift and is idempotent. Publish/rollback/repeat/drift refusal were tested on disposable PostgreSQL 17; rollback was **not** executed against the now-published pilot. Retain the backup and receipt. A full database restore is not the routine status rollback procedure.

## Final counts and exhaustive live verification

| Measurement | Actual result |
|---|---:|
| Products | **1,000** |
| ProductVariants | **1,000** |
| Categories | **31** |
| ProductImages / actual referenced S3 objects | **1,096 / 1,096** |
| PUBLISHED / DRAFT | **1,000 / 0** |
| Valid primary images | **1,000** |
| Missing, corrupt or orphaned referenced images | **0** |
| Unreferenced bucket image objects | **0** |
| Missing/failed Product UUIDs | **`[]`** |
| Actual image bytes | **25,963,954** |

The running API was checked completely:

- Page 0 with size 20 returned **200**, **20 products**, **totalItems 1000**, **totalPages 50**.
- All **50 pages** returned 20 products each. Their UUID sets covered the exact 1,000 database products, with no duplicates or omissions. Page 50 returned an empty list and 200.
- **All 1,000 detail endpoints returned 200**. Names, descriptions, category UUID/name, variants, SKUs, options and integral VND prices matched PostgreSQL.
- Every gallery matched database image IDs, alt text, dimensions, order and primary flags. Each product had exactly one valid primary image.
- All **1,096 S3 objects** were fetched and decoded. MIME, nonzero size, dimensions and SHA-256 matched database metadata and object keys. Bucket objects exactly matched referenced keys.
- All **1,096 gallery signed GET URLs returned 200 with matching image bytes**. Every primary image is included in that verified set; listing/detail primary URL paths matched the corresponding primary gallery object.
- All **1,096 unsigned requests and 1,096 tampered requests returned 403**. Returned URLs retain `X-Amz-Expires=300`; expiry rejection is also exercised in the passing signature/integration suites. Network isolation and the gateway were not weakened.
- Every public-table fingerprint matched the committed publication snapshot throughout verification; all nonstatus Product fields and the other Catalog tables also matched the original pre-publication baseline.

Machine-readable per-page, per-Product and per-image evidence is under ignored `output/publication-local/`: `api-pages.json`, `api-details.json`, `verified-images.json`, `signed-url-security.json`, `failed-products.json` and `verification.json`. These are private evidence, not an approved media package.

## Root README and actual startup verification

The root `README.md` now covers:

1. Spring Boot/PostgreSQL/S3 architecture, Catalog, Auth/JWT, User/Address and Media modules; actual Frontend/AI integration boundaries.
2. Java 25, Maven/wrapper, Docker Compose, PostgreSQL 17, Python and other prerequisites.
3. Safe `.env.example`, a tested private configuration generator, local profile, separate fresh/pilot ports, secrets and checkpoint configuration.
4. Exact hardened Compose PostgreSQL/storage commands, health checks, Maven Spring Boot startup, normal Flyway startup, availability checks and volume-preserving stop.
5. Fresh empty database behavior, approved catalog-only v1 delivery, generated-fixture v2 generation/restoration, publication visibility and real-image redistribution restrictions.
6. Working Product curls, pagination, five-minute signed URLs and image display/refresh behavior.
7. Local versus separately approved shared Frontend backends, loopback reachability, actual CORS settings and AI base URL guidance.
8. Maven/Python tests, Docker/SQL/storage checks, logs and missing-image/migration/port troubleshooting.
9. Project folders, detailed documentation links and explicit unverified/onboarding limitations.

### README smoke test

A **new empty** `backend-swe-local` project was created, independently of the pilot, using the exact README environment-generator block and `.env.example`. The test exercised:

```bash
docker compose --env-file .env.local -f compose.yaml -f compose.catalog-images.yaml up -d --wait postgres
docker compose --env-file .env.local -f compose.yaml -f compose.catalog-images.yaml up -d --wait --wait-timeout 240 localstack image-storage
mvn -B -ntp -f demo/pom.xml spring-boot:run -Dspring-boot.run.profiles=local
```

Actual fresh ports were PostgreSQL **55433**, backend **18081** and signed gateway **14567**. All containers were healthy. Normal application startup created/validated exactly successful Flyway **V1–V9**. The empty Catalog returned 200 and zero products, proving that startup does not magically import the dataset. The documented SQL connectivity/history checks and browser CORS behavior also passed.

The smoke test found that Springdoc routes currently return **401**, despite dependencies being present. README now documents that existing deny-by-default behavior instead of advertising unauthenticated Swagger access; security was not changed. The test was rerun with the corrected expectation.

The test backend and its Compose services were then stopped using the documented volume-preserving procedure. Its new empty volumes and private `.env.local` remain available for a future local restart; no existing volumes were deleted. The original pilot/backend remain running. Its private env file now explicitly retains `SERVER_ADDRESS=127.0.0.1` for future Maven startups; all existing secrets were preserved. Evidence: `readme-startup.json`, `/tmp/backend-swe-readme-first-start.log`, `/tmp/backend-swe-local.log`, `/tmp/catalog-readme-startup.log`.

## Fresh automated tests

`cd demo && mvn -B -ntp clean verify`: **BUILD SUCCESS**, **2 minutes 35 seconds**. Fresh XML reports and Python runner results:

| Suite | Passed | Failed | Errored | Skipped |
|---|---:|---:|---:|---:|
| Maven Surefire | 174 | 0 | 0 | 0 |
| Maven Failsafe | 171 | 0 | 0 | 0 |
| Normalizer | 12 | 0 | 0 | 0 |
| PostgreSQL product importer | 19 | 0 | 0 | 0 |
| Image importer | 8 | 0 | 0 | 0 |
| Image inventory preparation | 7 | 0 | 0 | 0 |
| Portable seed v1 | 8 | 0 | 0 | 0 |
| Signed gateway/readiness | 6 | 0 | 0 | 0 |
| Product upload/Auth/recovery fixture flow | 1 | 0 | 0 | 0 |
| Portable seed v2 validation/full fixture restore | 10 | 0 | 0 | 0 |
| Hardened isolation/health/real upload flow | 1 | 0 | 0 | 0 |
| New transactional publication/idempotency/rollback/drift test | 1 | 0 | 0 | 0 |
| **Total** | **418** | **0** | **0** | **0** |

The README startup smoke check and exhaustive running-pilot audit are additional acceptance checks, outside those 418 automated cases. Fixture imports/uploads/rollback tests ran only on disposable test databases and storage, never as new imports into the pilot. No test was disabled.

Logs: `/tmp/catalog-publish-maven.log`, `/tmp/catalog-publish-python-suites.log`, `/tmp/catalog-publish-test_*.log`. Aggregate: `output/publication-local/test-results.json`.

## Files, Git state and remaining limitations

This task changed/added `README.md`, `.env.example`, `publish_local_catalog.py`, `test_publish_local_catalog.py`, `LOCAL_PUBLICATION.md`, this report, and short current-state cross-references in the import README, v1 onboarding guide and preceding verification report. Existing staged/tracked/untracked application, gateway and seed work was preserved. The working tree remains dirty; no commit or push was performed. Complete final status is retained in ignored `output/publication-local/git-status-after.txt`.

No existing portable seed was overwritten. Seed v1 archive SHA-256 is still `055416889480fe8b88ad46806c47c24d72c89fdbdccffca80f1c6639087a3d32`; all immutable seeds still carry their original five-PUBLISHED split. The authorized change is isolated to this local database. Future export/handoff must account for its reviewed publication state rather than force immutable seed manifests to match.

A teammate still needs the reviewed code revision and an approved seed: cloning alone does not provide either the latest unpushed changes or the retailer image dataset. Real retailer-image packaging, redistribution, public/shared hosting and exposure of this pilot remain unauthorized. A separately approved shared environment must use suitable authorized media and reachable API/signing endpoints; no shared environment was deployed here. Remote clone/access, wrapper distribution download, actual browser rendering and real-image distribution were not performed.

**DONE:** all 1,000 existing products are visible through the loopback local API; all images remain valid/private, all required services are healthy, onboarding runtime instructions passed their smoke test, and all 418 automated tests pass. No remaining technical blocker for this local publication/onboarding task.
