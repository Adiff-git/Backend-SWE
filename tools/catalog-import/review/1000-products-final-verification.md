# Final acceptance — Import 1,000 Products with Images

Subsequent local-only publication changed the existing pilot to 1,000 PUBLISHED / zero DRAFT, with no other Catalog changes. See [the publication acceptance report](1000-products-local-publication.md). The five/995 split below records this earlier import/seed verification; immutable seeds retain that original split.

**PASS — IMPORT 1,000 PRODUCTS WITH IMAGES COMPLETE (technical acceptance)**

**FULL TECHNICAL COMPLETION** for the existing local catalog, hardened storage, health checks, APIs, and the image-bearing seed v2 workflow tested with authorized generated fixtures. **Actual retailer-image seed export/distribution remains BLOCKED** pending separate reuse and redistribution permission. No actual retailer-image v2 package was produced; fixture restoration is not represented as a tested clone of retailer image bytes on another machine.

Verified on 2026-10-29 on `feature/product-catalog`. This replaces the earlier PARTIAL technical verdict. All three engineering blockers have implementations and passing acceptance evidence. Media permission is tracked separately below.

## 1. Actual environment and preserved data

- Target: PostgreSQL **17.11**, `backend@127.0.0.1:55432`, Compose project `catalog-pilot-20261029-01`.
- Actual running backend: PID 41757, local profile, loopback `127.0.0.1:18080`; storage/signing endpoint remains `http://127.0.0.1:14566`.
- PostgreSQL, LocalStack and image gateway all report **HEALTHY** after deployment and a subsequent storage restart.
- Existing PostgreSQL and LocalStack volumes retained; PostgreSQL was not recreated. No persistent product/image import, restore-loader execution, publication change, source-media download, migration change, commit or push was performed.
- LocalStack startup recovers its existing image bytes from the already configured durable checkpoints. This is storage recovery, not a new crawler/source import. Catalog rows and image mappings are preserved.
- All existing Git changes were preserved. Seed v1 files retain valid original checksums; the v1 archive SHA-256 remains `055416889480fe8b88ad46806c47c24d72c89fdbdccffca80f1c6639087a3d32`.

Persistent SQL audits used read-only transactions. No persistent ADMIN login/provisioning was performed. Fixture accounts, product/image writes and repeated seed loads were confined to fresh disposable test environments.

## 2. Database and exhaustive image results

| Measurement | Verified result |
|---|---:|
| Products | **1,000** |
| ProductVariants | **1,000** |
| Categories | **31** |
| PUBLISHED / DRAFT | **5 / 995**, unchanged |
| Products with valid stored images | **1,000 / 1,000** |
| Products without images | **0** |
| ProductImage records / verified S3 objects | **1,096 / 1,096** |
| Valid primary images | **1,000** |
| Average images per product | **1.096** |
| Broken/missing/corrupt referenced images | **0** |
| Orphan variants, category parents or ProductImages | **0** |
| Duplicate Product UUIDs, case-insensitive SKUs or object keys | **0** |
| Invalid gallery orders / primary counts | **0 / 0** |
| Unreferenced product image objects / other bucket objects | **0 / 0** |
| Invalid selling prices | **0** |

All products, variants and categories match the immutable reviewed v1 catalog: 1,000 unchanged products and zero products to create. Prices remain positive integral VND demo prices; no retailer-price freshness claim is made. SHA-256 fingerprints over every field in Products, ProductVariants, Categories and ProductImages match the original pre-fix baseline and the end-of-task read-only guard.

Every referenced object was fetched and decoded after storage reconfiguration and again after the final pilot storage restart. Actual nonzero sizes, MIME types, dimensions, SHA-256, Product UUID/key prefixes, image IDs, checkpoint mapping and reviewed source URL membership matched. Galleries are contiguous from zero, contain at most ten images and have exactly one primary. Exact failed Product UUID list: **`[]`**.

The inventory has 1,130 source entries; eight galleries exceed the ten-image contract, leaving 1,096 selected entries and 34 intentionally excluded source URLs. No additional source download or import was used to perform this audit.

## 3. Security bypass fix and trust boundary

Implementation: `compose.catalog-images.yaml` and `docker/localstack/gateway/signed_s3_gateway.py`; trust boundaries are documented in `docker/localstack/gateway/README.md`.

- Raw S3 now binds **127.0.0.1:4566 inside the storage network namespace**, not a Docker bridge interface.
- Only the trusted gateway shares that namespace (`network_mode: service:localstack`). The namespace is on the separate `storage-private` bridge; ordinary application containers remain on the default network.
- The gateway listens on **4567**; only that port is published as **127.0.0.1:14566**. There is no host mapping of raw S3.
- The pinned Community image's Twisted server ignores IPv4 listener interface addresses. The configuration explicitly selects supported **Hypercorn**, which honors the loopback bind. This is an enforced bind boundary, not an obscure port or an assumption about emulator ACLs.
- The separate bridge is not Docker `internal: true`: that setting prevented the signed endpoint's host publication on this Docker setup. Tests prove raw S3 is still unreachable even when an unprivileged attacker is deliberately attached to the storage bridge.

Fresh disposable tests and final pilot probes both showed:

| Probe | Actual result |
|---|---|
| Valid signed GET | **200**, exact image bytes |
| Signed S3 PUT and real Product prepare/PUT/complete | **Passed** on disposable hardened topology |
| Unsigned image GET through gateway | **403** |
| Tampered / incomplete signature | **403** |
| Valid but expired 300-second signature | **403** |
| Raw S3 from application-network attacker | **Blocked**, timeout |
| Raw S3 from same-storage-network attacker | **Blocked**, connection refused |
| Unsigned gateway GET from storage-network attacker | **403** |
| Raw bridge GET from host / raw loopback host port | **Connection rejected** |

Attacker containers had read-only root filesystems, dropped capabilities, no-new-privileges, no Docker socket and no storage credentials. These results do **not** claim protection against Docker administrators, privileged containers, host-root access, leaked valid signing credentials, or workloads deliberately granted the trusted shared namespace. LocalStack Community and the development test/test keys are not production IAM. Private ACL/PublicAccessBlock remain configured, but namespace isolation and full signature checks provide the tested local boundary.

## 4. Health checks, startup and restart

The inherited image-gateway health check ran the LocalStack CLI against a process that was only a Python proxy. It failed parsing a gateway response. It has been replaced, not disabled, with a gateway-specific Python readiness command.

Both checks now require S3 availability **and completed, successful LocalStack ready hooks**. The gateway exposes only readiness status at `/_gateway/health`, not object data or credentials. This also fixes a real recovery race found during implementation: listener-only readiness initially exposed a partially restored fixture inventory of 842 objects. The strengthened regression test waits for initialization and now verifies **all 1,096** objects after an actual raw storage/gateway restart.

Disposable Compose startup, gateway restart, full storage restart and persistent pilot startup/restart passed. PostgreSQL, LocalStack and gateway were all healthy, and every existing pilot image remained accessible through authorized paths. Ready-hook errors or incomplete restoration do not count as healthy.

## 5. Public APIs and signed URL contract

- Listing `GET /api/products?page=0&size=20`: **200**, expected response fields, all five PUBLISHED UUIDs, totalItems 5, totalPages 1.
- Size-2 pagination returned the five public products exactly once over three pages; page 1 with size 20 returned an empty list and 200.
- **All five public detail endpoints returned 200**, with names, descriptions, SKU, VND price, variant/options, category, primary image and gallery metadata matching the database.
- **20 distributed DRAFT samples returned 404**; no DRAFT product appeared in any listing. Nonexistent UUID returned 404.
- Every public gallery/thumbnail signed URL returned actual hash-verified image bytes; declared expiry is **300 seconds**.
- The exact five live URLs that initially returned 200 were retained and retried after their real five-minute deadline: **all five returned 403 at approximately 301.7–301.8 seconds after capture**. Final statuses and elapsed times are in `output/acceptance-v2/live-url-expiration.json`.
- Unsigned, altered and incomplete signatures were rejected. New disposable Product upload tests used the actual Auth and ADMIN provisioning path; anonymous prepare returned 401, and successful prepare/PUT/complete produced a valid primary image served through the hardened endpoint.

This is verified API/image delivery, not a browser-rendering claim. Frontend-SWE in this workspace contains only a README.

## 6. Image-bearing portable seed v2

Implemented in `tools/catalog-import/portable_seed_v2.py`; usage and recovery details are in `PORTABLE_SEED_V2.md`. The existing `portable_seed.py` only gained an optional manifest filename so v2 can bundle the existing validated Catalog representation; default v1 behavior and v1 data remain unchanged.

V2 packages Catalog-only data, allowlisted ProductImage metadata and real image payload files, with per-file SHA-256, a manifest and `SHA256SUMS`. Product, Variant, Category and Image UUIDs/object mappings are preserved. It has no crawler SQLite dependency and restoration has no dependency on a separate v1 directory. It contains no users, credentials, auth sessions or upload-session data. Generated datasets remain Git-ignored.

The loader validates the entire package and image bytes before writes, rejects unsafe paths/symlinks and conflicting target data, preflights existing storage, uses a database advisory lock and writes verified durable object checkpoints before commit. Identical existing objects are read/verified rather than PUT again. PostgreSQL/S3 are not a distributed transaction: interrupted loads can retain verified unreferenced objects/checkpoints; the same seed can be retried without overwriting unrelated content or deleting storage.

### Fresh authorized-fixture round trip

All image bytes in these tests were generated by the test itself. The source and target were separate fresh PostgreSQL 17 and hardened Compose LocalStack environments, each with validated Flyway V1–V9.

| Restore acceptance | Actual result |
|---|---:|
| Restored Products / Variants / Categories | **1,000 / 1,000 / 31** |
| Restored images / actual S3 objects | **1,096 / 1,096** |
| Valid primary images | **1,000** |
| Publication split | **5 PUBLISHED / 995 DRAFT** |
| Restored user records | **0** |
| Repeated restore: created Products / Variants / Images | **0 / 0 / 0** |
| Repeated restore: object uploads | **0** |
| Fully verified objects after storage restart | **1,096** |
| API correctness / signed URL security / isolation / health | **Passed** |

Source export and target restoration preserved the full fixture image metadata exactly. Corrupt existing object data was rejected before overwriting. Corrupt packages, missing objects, invalid primary flags, wrong product mappings, traversal and symlinks were rejected. A fixture-only permission cannot authorize a persistent real-media export.

Retained generated fixture package: `output/catalog-dev-v2-authorized-fixtures-b09bd66ace`. It is an explicitly marked authorized-fixture package, not the retailer-image handoff. Machine-readable round-trip results are in `output/acceptance-v2/seed-v2-fixture-results.json`.

### Actual retailer-image export/distribution gate

The existing real-media permission record authorizes private local download/storage only. The exporter requires a **separate**, current `portable-seed-v2` permission with both `package` and `redistribute`, actual reviewer/evidence, and a hash binding it to the exact image inventory. The real pilot record was checked read-only: it was rejected with **`MEDIA_REDISTRIBUTION_PERMISSION_REQUIRED` before any S3 image read or package creation**. No real-image v2 export was attempted beyond that gate and no package was distributed.

Remaining non-technical handoff action: establish the actual reuse/redistribution basis, then authorize a new versioned real-media export and verify that particular package on a fresh target. The fixture round trip proves the software path; it does not invent rights or imply that a retailer-image package was produced.

## 7. Idempotency without reimporting the pilot

The persistent import checkpoint already maps all 1,096 planned image entries to completed, verified rows/objects. Existing importer read-only verification and manifest analysis show zero entries requiring prepare, upload or new metadata for that same checkpoint. No execute/resume/import mode was run against the persistent pilot in this task.

Actual repeat execution was tested only on disposable fixtures: the original Product image-flow tests exercise interrupted PUT/resume and repeated import; the new seed v2 test explicitly reports **zero created rows and zero object PUTs** on its second restore. Primary/order metadata and all Catalog fields were preserved.

## 8. Fresh automated tests

`cd demo && mvn -B -ntp clean verify`: **BUILD SUCCESS**, 2 minutes 38 seconds. XML results came from this task's clean build. No tests were disabled.

| Suite | Passed | Failed | Errored | Skipped |
|---|---:|---:|---:|---:|
| Maven Surefire | 174 | 0 | 0 | 0 |
| Maven Failsafe | 171 | 0 | 0 | 0 |
| Normalizer | 12 | 0 | 0 | 0 |
| PostgreSQL product importer | 19 | 0 | 0 | 0 |
| Image importer | 8 | 0 | 0 | 0 |
| Image inventory preparation | 7 | 0 | 0 | 0 |
| Portable seed v1 | 8 | 0 | 0 | 0 |
| Gateway signatures and restoration-aware readiness | 6 | 0 | 0 | 0 |
| Original Product upload/Auth/recovery fixture flow | 1 | 0 | 0 | 0 |
| Portable seed v2 validation/full round trip | 10 | 0 | 0 | 0 |
| Hardened Compose isolation/health/restart/real upload flow | 1 | 0 | 0 | 0 |
| **Total** | **417** | **0** | **0** | **0** |

Development failures exposed the ignored Twisted bind and premature ready state; the final tests include those regression cases and pass after the fixes. Existing Java suites cover authentication/authorization, Catalog listing/detail, gallery constraints, storage and LocalStack integration. The disposable Python flows cover real register/login, role checks, Product upload completion, source export, full restoration, object integrity, restart recovery and unprivileged network probes.

Logs: `/tmp/catalog-v2-final-maven.log`, `/tmp/catalog-v2-python-suites.log`, `/tmp/catalog-portable-seed-v2.log`, `/tmp/catalog-storage-isolation.log`. Machine-readable aggregate: `output/acceptance-v2/test-results.json`.

## 9. Storage and performance

| Measurement after final pilot storage restart | Result |
|---|---:|
| PostgreSQL database bytes | **12,539,571** |
| Logical S3 image bytes | **25,963,954** |
| Mean stored image size | **23,689.74 bytes** |
| Listing, 10 localhost requests | mean **8.55 ms**, median **7.46 ms** |
| Detail, 10 localhost requests | mean **6.97 ms**, median **6.04 ms** |

These are small non-destructive localhost samples, not a load test or SLA. LocalStack Community's ephemeral storage is recovered from private durable checkpoints; retain that directory and the project volumes.

## 10. Completion and evidence

**PASS — FULL TECHNICAL COMPLETION.** The existing 1,000-product import is intact; security isolation, actual gateway/LocalStack health, all-image integrity, public APIs and reproducible image-bearing restoration with authorized fixtures pass. No technical blocker from the preceding verification remains open.

**Real-image seed distribution: BLOCKED BY MEDIA PERMISSION**, separately from the passing technical result. No real-media v2 snapshot or redistribution is claimed.

Evidence under ignored `output/acceptance-v2/`: `summary.json`, `all-image-objects.json`, `failed-products.json`, `broken-images.json`, `environment-security.json`, `network-isolation.json`, `live-url-expiration.json`, `seed-v2-fixture-results.json`, `seed-v1-preservation.json`, `real-media-export-gate.json`, and `test-results.json`. The original fingerprint baseline and final guard remain in `output/final-verification/`. Do not share private evidence or cached signed URLs as a media package.
