# Backend-SWE: real product images only

This patch changes the Backend demo media pipeline to accept **only**
`images/products/<ASIN>.webp` from a separately reviewed,
redistribution-authorized `Catalog-Data` release. It never falls back to
`images/fixtures/` and never marks any Amazon image as licensed.

## Install (in your local SWE workspace)

Unzip this bundle and copy **only** the files under `scripts/` and
`tools/catalog-import/` into the corresponding directories of `Backend-SWE`.
The bundled `tests/test_backend_media.py` is an independent offline test file.
Keep a backup or use `git diff` before replacing your originals.

## What changes

- `tools/catalog-import/amazon_image_import.py`: rejects fixture manifest mode
  and paths, enforces exact 1,500 ASIN-to-Product mappings, image metadata and
  SHA-256 checks. Execution requires a target-bound permission record and
  corresponding recorded image rights. `--mode verify-only` does not upload;
  it compares every existing DB ProductImage S3 object to the real-image SHA-256.
- `scripts/demo_preflight.py`: requires `LICENSED_PRODUCT_IMAGES`, the reviewed
  `amazon_import_approved_1500.csv` (not the template), and a valid bootstrap
  policy and media approval record. Never permits fixture fallback.
- `scripts/run_demo.py`: uses the reviewed 1,500 CSV and passes only the real
  media permission record to the image importer. For existing volumes, first
  checks actual S3 bytes against the released real-image manifest; an existing
  database containing fixtures is **rejected**, not silently accepted or reset.

## Important outstanding dependencies

The currently uploaded `Catalog-Data/manifests/catalog_manifest.json` declares
`image_rights_status=GENERATED_FIXTURES_ONLY`, and `image_manifest.json`
points to `images/fixtures/`. **Those manifests are NOT converted by this patch.**
The local 1,500 Amazon WebPs were not uploaded into this conversation, so their
real bytes/hash/dimensions cannot be generated here. Use the previously
provided `Catalog-Data/scripts/prepare_local_media_candidate.py` to inventory
all 1,500 authentic WebPs and prepare an external, local-only report with
`rights_status=UNKNOWN`.

The real-only release cannot be activated until the project has valid
redistribution permissions for these photos. At that point, review the license
scope and evidence, prepare `Catalog-Data`'s licensed image manifest and
catalog manifest, update and validate all hashes, and include the target-bound
`data/approvals/image_permission.json`. The code verifies these *recorded*
claims, not the legal validity of a license. Do not fabricate approvals or
push an unlicensed image package to GitHub.

`run_demo.py` also expects `scripts/verify_demo.py` and other existing
`Backend-SWE` Docker/DB support. These weren't provided in the recent upload
and the full Docker flow has not been exercised here.

An existing demo volume containing fixture photos is **not** automatically
migrated: the existing Backend image replacement/deletion and primary-image
behavior has not been verified. For a new, isolated `demo-*` project with an
empty DB/S3, bootstrap can import authentic images directly once the rights and
manifest gates are satisfied. Do NOT remove or reset your current pilot volume
without a separate verified backup and explicit authorization.

## Local tests (no Docker or database/network writes)

```bash
python3 -m unittest discover -s tests -p 'test_backend_media.py' -v
python3 -m py_compile scripts/demo_preflight.py scripts/run_demo.py \
  tools/catalog-import/amazon_image_import.py
```

Result in this isolated tool environment: **11/11 offline tests passed**.
Actual Docker, PostgreSQL, S3 and API end-to-end tests were **not run**.
