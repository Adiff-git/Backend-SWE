# Local Catalog Data Bootstrap

The data-only workflow imports the approved `Catalog-Data` Product and Variant
dataset into one explicitly selected `demo-*` Compose project. It does not
upload images or require Admin credentials. Authentic retailer images remain
outside this workflow because their redistribution rights are unknown.

## Configuration

Use Linux, Docker Compose, Python 3.12+, and the Python packages in
`tools/catalog-import/requirements-import.txt`. Place the complete sibling
`Catalog-Data` repository beside `Backend-SWE`. Keep `.env.demo.local` private.
It must set `COMPOSE_PROJECT_NAME=demo-...`, a loopback `LOCAL_DB_HOST`,
`LOCAL_DB_NAME=backend`, explicit `LOCAL_DB_PORT`, `SERVER_PORT`, and
`LOCAL_STORAGE_PORT`, `LOCAL_DB_USER`, `LOCAL_DB_PASSWORD`, `JWT_SECRET`, and a
`CATALOG_IMPORT_DSN` pointing to that exact database. For a fresh clone:

```bash
python3 -m venv /tmp/backend-catalog-bootstrap-venv
/tmp/backend-catalog-bootstrap-venv/bin/pip install -r tools/catalog-import/requirements-import.txt
/tmp/backend-catalog-bootstrap-venv/bin/python scripts/create_demo_env.py \
  --backend "$PWD" --project demo-catalog-fresh-01 \
  --db-port 55443 --server-port 18091 --storage-port 14577
# Confirm these ports are unused before starting Docker.
```

The entry point uses the Python on `PATH`; activate the venv before invoking
it. A complete existing catalog is checked read-only and returns
`DATA_READY_MEDIA_PENDING` without asking for import approval.

```bash
source /tmp/backend-catalog-bootstrap-venv/bin/activate
bash scripts/setup-demo.sh --data-only
```

For an empty or partial catalog, this command starts the selected project,
verifies the V1–V10 schema, writes a mode-0600 custom-format PostgreSQL backup,
restores it into a temporary database, and writes
`tools/catalog-import/output/demo-setup/<project>/review-evidence.json`.
It then returns `AUTHORIZATION_REQUIRED` without importing. Review that file,
the exact database target, the plan for each needed stage, and the backup.
The `stages` hashes are produced by `amazon_import.py` from its actual plans;
the catalog manifest file hash is not an import authorization hash.

An operator who approves a stage must create a private
`.private/catalog-bootstrap-authorizations/stage100.json`, `stage1000.json`,
or `stage1500.json` for each needed stage. Each record must contain:

```json
{
  "decision": "APPROVED",
  "target": "backend@127.0.0.1:55433",
  "stage": 100,
  "manifest_sha256": "<matching review-evidence stages.100 value>",
  "backup_sha256": "<matching review-evidence backup_sha256 value>",
  "restore_tested": true,
  "approved_by": "<actual operator>",
  "evidence_reference": "<actual review record>"
}
```

The approval must be an actual operator decision, never an automatically
generated record. Run the same entry point with the private directory only
after every needed stage is authorized:

```bash
bash scripts/setup-demo.sh --data-only \
  --authorization-dir .private/catalog-bootstrap-authorizations
```

The first run of a fresh database needs Stage 100, Stage 1000, and Stage 1500.
A compatible 100-Product database needs the latter two; a compatible
1,000-Product database needs Stage 1500. Reruns skip completed stages.
The expected final state is 1,500 Products, 1,500 Variants, 10 Categories,
1,500 DRAFT Products, 428 Variants with nonempty specifications, and zero
ProductImages. The verifier checks exact Product and Variant UUIDs, unique SKUs,
approved row content, and successful Flyway V1–V10 history.

For failures, inspect the returned blocker, `docker compose` logs for the
selected project, and the private `review-evidence.json`. Do not run
`docker compose down -v`. Correct configuration or approval records, then
rerun the same command. A changed backup, wrong target, unexpected identities,
or incompatible rows stops the import; preserve the volume for review.

Full media bootstrap remains separate. It requires a licensed real-image
manifest, explicit permission bound to the target, file SHA-256 checks,
Product UUID mapping, Admin login after Backend startup, and LocalStack object
verification. `rights_status=UNKNOWN` does not authorize upload or distribution.
Existing ProductImages require separately verified replacement semantics.

For a fresh-clone test, clone Backend-SWE and Catalog-Data as siblings, use
the example unique project and unused ports above, then run the two commands
above. The first setup command stops at `AUTHORIZATION_REQUIRED`; after an
actual operator reviews and creates the three private records, run the setup
command with `--authorization-dir`. Rerun it once more to prove idempotency.
