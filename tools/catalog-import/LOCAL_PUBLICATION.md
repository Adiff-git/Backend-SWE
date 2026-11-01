# Local Catalog publication and rollback

`publish_local_catalog.py` changes only the status column of the existing reviewed 1,000 products. It never imports products/images, publishes a server, modifies prices/SKUs/categories/variants/images or edits Flyway. This operation is for an explicitly confirmed loopback local-development database only.

The existing pilot was authorized and changed from five PUBLISHED / 995 DRAFT to **1,000 PUBLISHED / zero DRAFT**. Seed v1 and existing v2 fixture packages remain unchanged at their original five/995 split. Re-running those immutable loaders against the published pilot should reject the status conflict; do not change their manifests or force an import.

## Guards, backup and evidence

Before a first publication, the CLI verifies the expected Compose project/service, loopback port mapping, container database identity, actual PostgreSQL 17 target and exact Flyway V1–V9 history. It reconciles every Product UUID/source identity with the reviewed seed and image manifest; compares all names, descriptions, variants, SKUs, prices and categories; requires all 1,000 valid primary galleries and the expected 1,096 images; reads and validates every actual image object.

It creates a **new** mode-0600 `pg_dump -Fc` backup and verifies its archive directory with `pg_restore --list`. The private receipt records backup SHA-256, original status map, target identity and immutable Catalog fingerprints. This backup contains private database data and is not a portable seed or a teammate handoff artifact. Do not share it.

The writer locks public tables briefly, rechecks the reviewed data, changes only the 995 original DRAFT Product statuses in one transaction, forces constraints, and compares every public table plus the status-excluded Product fingerprint before commit. Any unexpected field/table change aborts. Exact repeated execution with the same receipt makes zero changes. A prepared receipt also supports safe reconciliation if the process stops after commit but before its final receipt update.

Pilot evidence is ignored under `output/publication-local/`:

- `catalog-before-publish-all.dump`: pre-publication backup (listed/validated; not restored over the pilot).
- `publication-receipt.json`: original five/995 status map, target binding, backup checksum and latest action.
- `publish-result.json`: first action, exactly 995 changed IDs and all-table before/after fingerprints.
- `idempotent-result.json`: repeated action, zero changes.
- API/image/test reports referenced by [the execution report](review/1000-products-local-publication.md).

## Preview and idempotent publication

Use the **existing private pilot environment**, not the README's fresh onboarding `.env.local`. From Backend-SWE:

```bash
set -a
source .env.catalog-pilot.local
set +a
publication_cmd=(
  /tmp/backend_catalog_import_venv/bin/python tools/catalog-import/publish_local_catalog.py
  --seed tools/catalog-import/output/catalog-dev-v1
  --image-manifest tools/catalog-import/output/1000-product-image-manifest.json
  --receipt tools/catalog-import/output/publication-local/publication-receipt.json
  --compose-project catalog-pilot-20261029-01
  --target-kind local-development --expect-database backend
  --expect-host 127.0.0.1 --expect-port 55432
  --confirm-target backend@127.0.0.1:55432
)
"${publication_cmd[@]}" --mode preview
"${publication_cmd[@]}" --mode publish \
  --backup tools/catalog-import/output/publication-local/catalog-before-publish-all.dump
```

A first publication requires a new backup path and original five/995 state. The already completed pilot action uses the existing receipt/backup rather than creating a new baseline. Changing targets, manifests or backup bytes fails. No credentials appear in these arguments; the DSN is read privately from the environment.

## Safe status-only rollback procedure

**Do not run this as routine setup.** If rollback is explicitly desired, stop Frontend testing, verify the target and retain the backup/receipt. In the same terminal with `publication_cmd` defined above:

```bash
"${publication_cmd[@]}" --mode preview
"${publication_cmd[@]}" --mode rollback
"${publication_cmd[@]}" --mode preview
```

Rollback restores only the original 995 DRAFT statuses and preserves the original five PUBLISHED products. It verifies the receipt, unchanged immutable Catalog fields and backup checksum; unexpected data/partial publication states cause refusal. It preserves all other tables and Product fields and is itself idempotent. Do not perform a full `pg_restore` over the live database to reverse status changes: that would replace unrelated data. Preserve the archive for separately reviewed disaster recovery.

Rollback was exercised on disposable PostgreSQL 17, not executed against the now-published pilot. The regression test checks publish 995, repeat zero, rollback 995, repeat zero, exact original fingerprints and drift/manifest refusal:

```bash
/tmp/backend_catalog_import_venv/bin/python tools/catalog-import/test_publish_local_catalog.py
```

The full local API audit checks all 50 pages, all 1,000 detail responses, every referenced image byte, signed URLs and unchanged nonstatus data. Publication changes API visibility only; it does not broaden loopback bindings, media reuse rights or storage access.
