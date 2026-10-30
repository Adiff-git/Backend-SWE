# Portable Catalog seed v2

`portable_seed_v2.py` exports and restores the reviewed 1,000-product Catalog plus 1,096 image records and their actual bytes. It does not read the crawler SQLite database, export users/authentication/upload sessions, alter Flyway migrations, or modify `catalog-dev-v1`. Generated seed directories stay under Git-ignored `tools/catalog-import/output/`.

## Format and verification

A v2 directory contains:

- `manifest.json`: `catalog-dev-v2`, exact counts, content class and per-file SHA-256.
- `SHA256SUMS`: payload and manifest checksums.
- `catalog.json` and `catalog-manifest.json`: the existing reviewed Catalog-only representation and its v1-format validation manifest, bundled inside v2. Restoration has no dependency on a separate v1 directory.
- `images.json`: allowlisted ProductImage fields only, preserving UUIDs, Product UUIDs, object keys, alt text, MIME, size, dimensions, ordering, primary flags and creation timestamps.
- `objects/<SHA-256>`: validated JPEG/PNG/WebP bytes. Identical payloads can share an archive file while retaining their distinct original S3 keys and image mappings.

Offline verification checks every checksum and decoded image; validates product/object mappings, UUIDs, metadata, one primary per product, contiguous order and the ten-image limit; rejects symlinks, unexpected files, path traversal, missing payloads and unreferenced payloads. SHA-256 establishes integrity, not the publisher's identity; obtain packages and expected hashes from a trusted authorized source.

```bash
/tmp/backend_catalog_import_venv/bin/python tools/catalog-import/portable_seed_v2.py \
  verify --seed tools/catalog-import/output/REVIEWED-V2-DIRECTORY
```

## Separate media-export permission

Local import permission does **not** authorize image packaging or redistribution. Real retailer-media export remains blocked. No real-image v2 package was generated in this task. A passing fixture export is explicitly marked `authorized-fixtures` and cannot grant permission for a persistent retailer dataset.

Before any source image bytes are fetched for an export, the exporter requires a separate reviewed JSON record with:

- `decision`: `APPROVED`;
- `scope`: `portable-seed-v2`;
- `usage`: includes `package` and `redistribute`;
- `content_class`: `retailer-media` for a real local-development target;
- `approved_by` and `evidence_reference`: actual reviewer and documented reuse/redistribution basis;
- `expires_at`: future timezone-qualified ISO timestamp;
- `image_inventory_sha256`: SHA-256 over the exact canonical image inventory.

Do not fill these fields with invented approvals. To obtain the inventory digest without fetching or packaging images, export the private target environment, then run the read-only inventory command:

```bash
/tmp/backend_catalog_import_venv/bin/python tools/catalog-import/portable_seed_v2.py inventory \
  --target-kind local-development --expect-database backend \
  --expect-host 127.0.0.1 --expect-port 55432 \
  --confirm-target backend@127.0.0.1:55432
```

Once actual permission exists, use a **new** output directory. The exporter refuses an existing output and reads the database in a read-only transaction; the catalog must exactly match the reviewed seed. It reads only referenced images and verifies their actual bytes against database metadata.

```bash
/tmp/backend_catalog_import_venv/bin/python tools/catalog-import/portable_seed_v2.py export \
  --seed tools/catalog-import/output/catalog-dev-v2-NEW-VERSION \
  --catalog-seed tools/catalog-import/output/catalog-dev-v1 \
  --permission tools/catalog-import/output/REVIEWED-REDISTRIBUTION-PERMISSION.json \
  --s3-endpoint http://127.0.0.1:14566 \
  --target-kind local-development --expect-database backend \
  --expect-host 127.0.0.1 --expect-port 55432 \
  --confirm-target backend@127.0.0.1:55432
```

This is a future authorized operation, not a command executed on real retailer media during this task. There is no publication or upload-to-sharing-service step in this tool.

## Restore into a fresh local target

Use a separate Compose project, ports, volumes and private environment. Use both `compose.yaml` **and** `compose.catalog-images.yaml`; the latter enforces the raw listener isolation and verified gateway. Apply Flyway V1–V9 through normal backend startup before loading the seed. Bucket initialization comes from the storage ready hook. Supply the target DSN privately through `CATALOG_IMPORT_DSN`.

Restoration requires explicit `--execute`, exact loopback target confirmation and a durable checkpoint directory. The database name for a disposable target must end in `_test`. The S3 endpoint must also be explicit loopback. `--checkpoint-directory` must be the **same** directory mounted through that project's `CATALOG_IMAGES_CHECKPOINT_DIRECTORY`, so startup recovery can restore committed image objects after LocalStack Community restarts.

```bash
/tmp/backend_catalog_import_venv/bin/python tools/catalog-import/portable_seed_v2.py restore \
  --seed tools/catalog-import/output/REVIEWED-V2-DIRECTORY --execute \
  --checkpoint-directory /absolute/path/to/THIS-TARGET/object-checkpoints \
  --s3-endpoint http://127.0.0.1:TARGET-SIGNED-S3-PORT \
  --target-kind disposable --expect-database image_seed_test \
  --expect-host 127.0.0.1 --expect-port TARGET-POSTGRES-PORT \
  --confirm-target image_seed_test@127.0.0.1:TARGET-POSTGRES-PORT
```

Do not run a restore against the already imported pilot for this task. A fresh restoration produces 1,000 Products, 1,000 Variants, 31 Categories, 1,096 images, 1,000 primary images, and five PUBLISHED / 995 DRAFT, with no users. Repeating the same restore reports zero created products/variants/images and zero object PUTs. Existing conflicting metadata or object bytes cause failure; the loader never overwrites conflicting objects or modifies existing catalog fields.

All package files are validated before writes. A database advisory lock serializes loaders; every existing database row/object is preflighted before restoring missing objects. Actual stored bytes are re-read and verified. Object checkpoints are written atomically and fsynced before database commit. PostgreSQL/S3 do not share a distributed transaction: a failed load may leave verified, unreferenced fixture objects/checkpoints; retain the same seed and resume the idempotent load. No automatic deletion or rollback of unrelated storage is attempted.

## Tests

```bash
/tmp/backend_catalog_import_venv/bin/python tools/catalog-import/test_portable_seed_v2.py
/tmp/backend_catalog_import_venv/bin/python tools/catalog-import/test_storage_isolation.py
```

The v2 round trip generates all 1,096 image payloads, exports from a fresh source PostgreSQL/S3, restores into another fresh PostgreSQL 17/isolated LocalStack, checks exact rows/objects and APIs, verifies repeat restoration has zero mutations/PUTs, detects corrupted existing objects, restarts raw storage plus gateway, and verifies all objects again. It also checks network isolation from unprivileged application/storage-network containers. The isolation suite separately exercises real Product upload prepare/PUT/complete with an actual fixture ADMIN login on the hardened topology. Disposable test bind directories are retained under `/tmp`; tests do not delete Docker volumes.
