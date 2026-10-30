# Local Product Image integration and controlled bulk ingestion

The hardened local topology now confines raw S3 to a shared trusted loopback namespace using Hypercorn, with restoration-aware health checks. See [the network trust boundary](../../docker/localstack/gateway/README.md), [portable seed v2](PORTABLE_SEED_V2.md), and [final acceptance](review/1000-products-final-verification.md). Seed v2 round-trip tests use generated authorized fixtures; packaging actual retailer images remains blocked without separate reuse/redistribution permission.

The local import was completed on 2026-10-29 following the user's explicit instruction to use the existing sources: 1,000 products now have 1,096 verified ProductImages, with five PUBLISHED and 995 DRAFT. See [the execution report](review/1000-product-images-executed.md) for scope, evidence and restart verification. The permission record below is retained for execution auditing; the user instruction does not assert a separate retailer license or grant redistribution rights.

## Product-specific API and authorization

The existing OpenAPI prepare/complete routes are implemented by `ProductImageUploadController` and `ProductImageUploadService`. The Catalog filter chain now uses the same JWT decoder and current-state converter as Auth. Product POST requests require a real JwtAuthenticationToken and ROLE_ADMIN loaded from database roles on every request. A mock username/role, request body or unverified token claim cannot grant upload access. Anonymous requests remain 401 and ordinary authenticated users remain 403. Generic user-owned Media routes and their ownership model remain separate.

Preparation validates Product UUID, type, declared size and alt text, then records a 900-second product-scoped session and a private signed staging PUT. Completion validates bounded actual bytes, single-frame JPEG/PNG/WebP decoding, dimensions and SHA-256; claims the session with a fenced token; conditionally copies into `images/products/<product UUID>/<upload UUID>/<SHA-256>`; reads and verifies stored bytes and metadata; then publishes ProductImage metadata in a short transaction. The Product row serializes gallery edits, the final sampled PostgreSQL clock check enforces session/claim expiry, and existing deferred constraints protect maximum ten images, unique order and exactly one primary. Idempotent replay returns the same image. A different preparer gets 403. Requested primary reassignment is atomic.

Retries may reclaim an expired lease only for the same validated bytes/key. Stage mutations cannot replace a claimed image. S3 failures and gallery rollback leave a tracked session for retry. Scheduled cleanup expires abandoned sessions, removes staging objects after completion, and retries removal of unreferenced expired/failed product objects and local checkpoints. Cleanup never deletes a key referenced by ProductImage or a generic user-owned object. Cross-service S3/DB atomicity is not assumed: tracked sessions/checkpoints and repeated cleanup provide recovery.

The public Catalog read adapter still signs private GET URLs for 300 seconds. No object keys, credentials or upload internals are added to public JSON.

## Start private local storage without replacing PostgreSQL

From Backend-SWE, use the existing private `.env.catalog-pilot.local` and unused loopback port 14566:

```bash
mkdir -p tools/catalog-import/output/product-object-checkpoints
chmod 700 tools/catalog-import/output/product-object-checkpoints
docker compose --env-file .env.catalog-pilot.local -p catalog-pilot-20261029-01 \
  -f compose.yaml -f compose.catalog-images.yaml up -d localstack image-storage
```

The override mounts the executable bucket initializer, creates/retains the project LocalStack volume, blocks public bucket access and CORS-configures browser image requests. Raw LocalStack has no host port. `image-storage` exposes only `127.0.0.1:14566` and verifies complete SigV4 HMAC signatures, payload hashes and expiration before forwarding. This is necessary because the Community emulator can otherwise return unsigned object reads despite private ACLs. The gateway is explicitly local-development infrastructure, not a production IAM replacement. It uses the existing dummy local storage credentials, not production credentials.

LocalStack Community native snapshot APIs are unavailable in the pinned image; setting PERSISTENCE alone did not preserve a probe. Therefore verified product bytes are synchronously checkpointed before metadata commit when the local backend has `CATALOG_IMAGES_CHECKPOINT_DIRECTORY` set to the absolute path of `tools/catalog-import/output/product-object-checkpoints`. JSON object checkpoints are private mode 0600 and Git ignored. The override mounts that directory read-only into LocalStack; the ready hook restores and hash-verifies those objects before the gateway is started. Stage objects are temporary; a missing stage after restart can be re-uploaded from the client's retained checkpoint/cached source. Do not delete the checkpoint directory or either project volume. This durability mechanism is for Product images; it does not claim persistence for unrelated Generic Media objects.

Start/restart the backend normally with the local profile after exporting its private environment. `LOCAL_STORAGE_ENDPOINT` and `LOCAL_STORAGE_SIGNING_ENDPOINT` must both be `http://127.0.0.1:14566`. The repository's existing five-minute signer settings are unchanged. Never print/source the admin JSON file as shell environment.

## Obtain legitimate development ADMIN access

Install the isolated image-tool dependencies:

```bash
/tmp/backend_catalog_import_venv/bin/pip install -r tools/catalog-import/requirements-images.txt
set -a
source .env.catalog-pilot.local
set +a
/tmp/backend_catalog_import_venv/bin/python tools/catalog-import/catalog_admin.py \
  --provision --username catalog-admin --credentials .env.catalog-admin.local \
  --api-base http://127.0.0.1:18080 --target-kind local-development \
  --expect-database backend --expect-host 127.0.0.1 --expect-port 55432 \
  --confirm-target backend@127.0.0.1:55432
```

The operator utility verifies the PostgreSQL 17/Flyway target, refuses to elevate a pre-existing username, registers a generated-password account through the actual Auth API, assigns ADMIN via an explicit local operator database action, and obtains a token through normal login. It preserves ROLE_USER and never exposes a public role-grant endpoint or commits a password. The private JSON file is mode 0600. The pilot account has already been provisioned; do not repeat `--provision`. Refresh its short-lived login token by running the same command **without** `--provision` (and without needing `--username`). Other local targets must use their actual database/port/API and a new credentials file. Fixtures may be provisioned only on explicit disposable `_test` databases.

## Permission record and real-media gate

The owner must supply a separate reviewed authorization JSON under ignored `output/`, identifying:

- `decision`: `APPROVED`, explicitly supplied by the authorized reviewer;
- `scope`: `local-development`;
- `target`: `backend@127.0.0.1:55432`;
- `approved_by`, `evidence_reference`: actual approver and documented permission basis;
- `expires_at`: future timezone-qualified ISO timestamp;
- `usage`: includes `download` and `store-private-local`;
- `source_identities`: exact permitted `retailer|source_url` values from the reviewed inventory;
- `image_hosts`: the permitted CDN hosts (intersected with the tool's retailer host allowlist);
- `image_manifest_sha256`: SHA-256 of the exact reviewed image inventory.

Do not put placeholder approvals into this record. This record does not grant redistribution rights. The importer freezes its hash in the checkpoint and refuses scope/target/manifest drift, expired authorization or an unapproved source before any external download.

Current inventory: `output/1000-product-image-manifest.json`, 1,000 exact source identities and 1,130 source URL entries. The contract allows at most ten images per Product; eight source galleries exceed that limit. The stable first-ten distinct valid URLs yield 1,096 planned image entries; the remaining 34 source entries are explicitly outside this gallery plan. Actual byte duplicates are mapped to the same completed image instead of creating a duplicate row.

## Exact dry-run, execution, resume and verification commands

These commands are run from the repo root with the private pilot environment exported. First inspect the loopback Compose target, exact Flyway history, private bucket/gateway, and current Catalog counts, then create a new backup with `pg_dump` before any authorized real-media write. The existing `output/catalog-before-images.dump` is a pre-image checkpoint; make a new one if state has changed.

```bash
/tmp/backend_catalog_import_venv/bin/python tools/catalog-import/import_product_images.py \
  --mode dry-run --manifest tools/catalog-import/output/1000-product-image-manifest.json \
  --seed tools/catalog-import/output/catalog-dev-v1 \
  --checkpoint tools/catalog-import/output/real-image-checkpoint.json \
  --api-base http://127.0.0.1:18080
```

After actual permission evidence is supplied for the reviewed full inventory, start with the same execute command below plus `--published-only`. It selects only the original five PUBLISHED UUIDs while keeping the same inventory hash, permission record and checkpoint for later scaling. Verify stored hashes, galleries and public signed GETs before removing `--published-only` and processing the full batches. Do not change publication statuses. If only a smaller permission scope was granted, use that reviewed inventory/record instead and obtain expanded permission before scaling; preserve the old checkpoint when creating a new one for a newly authorized scope.

For the reviewed full inventory, execute bounded batches of 25. Change offset through 0, 25, 50, ... 975 only after each batch result has been reviewed. If a batch fails, refresh the ADMIN token if needed and use `--mode resume` with that same offset/checkpoint. No direct SQL image writer is used.

```bash
/tmp/backend_catalog_import_venv/bin/python tools/catalog-import/import_product_images.py \
  --mode execute --batch-size 25 --offset 0 --download-interval 1 \
  --manifest tools/catalog-import/output/1000-product-image-manifest.json \
  --permission tools/catalog-import/output/approved-media-permission.json \
  --seed tools/catalog-import/output/catalog-dev-v1 \
  --checkpoint tools/catalog-import/output/real-image-checkpoint.json \
  --admin-credentials .env.catalog-admin.local --api-base http://127.0.0.1:18080 \
  --target-kind local-development --expect-database backend \
  --expect-host 127.0.0.1 --expect-port 55432 --confirm-target backend@127.0.0.1:55432
```

A checkpoint contains upload session IDs and temporary signed PUT capabilities until completion; keep it private and Git ignored. It is atomically written mode 0600 before PUT, so a lost response can be recovered. Resume first replays completion, then re-uploads a missing stage or replaces an expired unpublished session. Completed entries are verified from storage without another external download. If an operator must start a new checkpoint, matching Product UUID plus actual source-byte SHA-256 is reconciled against existing ProductImage metadata and stored bytes instead of uploading duplicate images. The fixture-only path accepts only local generated files and is restricted to disposable database targets.

To verify without writing database/S3 or downloading source media, use the same command with `--mode verify`; permission is not needed for read-only verification. It verifies checkpoint image IDs against PostgreSQL, downloads only already-stored private local S3 objects and compares actual SHA-256, and checks gallery invariants. Missing/corrupt objects and incomplete sources are reported by product/source entry. `verified` counts mapped checkpoint entries; `unique_verified_images` counts distinct verified ProductImage IDs. Source permission is still required to resume external downloads. Do not manually alter checkpoint IDs or source mappings.

The downloader uses HTTPS only, exact CDN allowlists, public-IP DNS validation with a pinned checked address and original-host TLS validation, redirect revalidation, no environment proxies, timeouts, a 10 MiB limit, bounded retries/backoff and an enforced minimum interval. Actual single-frame JPEG/PNG/WebP decoding and dimensions are validated before upload, and the server independently validates everything again.

## Tests and current execution boundary

```bash
/tmp/backend_catalog_import_venv/bin/python tools/catalog-import/test_image_import.py
/tmp/backend_catalog_import_venv/bin/python tools/catalog-import/test_s3_gateway.py
/tmp/backend_catalog_import_venv/bin/python tools/catalog-import/test_product_image_flow.py
cd demo
mvn -B -ntp clean verify
```

The E2E test uses fresh PostgreSQL 17, real Flyway V1–V9, the portable Catalog seed, isolated LocalStack/SigV4 gateway, real Auth register/login, and generated PNGs for all five PUBLISHED fixture products. It verifies interruption/resume, repeated import, stored bytes, gallery order/primary, primary reassignment, rollback after copy, duplicate prevention, expired-object cleanup, role revocation, preparer mismatch, actual signed GETs, tampered/unsigned/partial-signature rejection, a valid-but-expired 300-second signature, and checkpoint restoration after LocalStack restart. No fixture ProductImage is inserted into the persistent pilot database.

Real-media execution is complete as documented in [the execution report](review/1000-product-images-executed.md). Seed v1 remains immutable. Image-bearing seed v2 export/restoration is implemented and tested with authorized generated image fixtures; no retailer-image seed was exported. See [PORTABLE_SEED_V2.md](PORTABLE_SEED_V2.md) for the separate redistribution gate, checksums and restore workflow.
