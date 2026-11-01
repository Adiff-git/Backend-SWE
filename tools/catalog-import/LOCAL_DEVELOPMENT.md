# Isolated local Catalog database and API

Verified on 2026-10-29 with PostgreSQL 17, the `local` Spring profile, and an empty Catalog. The importer requires the local-development database name `backend`; isolation comes from a unique Compose project, project-scoped volume, and loopback host port. Use a new project name and free ports on another machine. Never reuse an unidentified database or run `docker compose down -v`.

The named pilot instance received one approved local-only import later on 2026-10-29. Its actual counts and API results are recorded in [the import execution report](review/local-development-29-import-report.md). The fresh-database workflow below still applies to a new Compose project and volume.

## Start a fresh persistent PostgreSQL

From the Backend-SWE root, inspect Docker first:

```bash
docker ps -a
docker compose ls --all
docker volume ls
```

Check that your chosen project, volume, and host ports are unused. The verified instance uses project `catalog-pilot-20261029-01`, volume `catalog-pilot-20261029-01_postgres-data`, database `backend`, and `127.0.0.1:55432`. Create a private, Git-ignored `.env.catalog-pilot.local` with mode `0600`, setting at least `LOCAL_DB_NAME=backend`, `LOCAL_DB_USER=backend`, `LOCAL_DB_PASSWORD`, `LOCAL_DB_HOST=127.0.0.1`, `LOCAL_DB_PORT`, `JWT_SECRET` (at least 32 UTF-8 bytes), `JWT_ISSUER`, and `SERVER_PORT`. Use a newly generated password and JWT secret. The importer also needs a private `CATALOG_IMPORT_DSN` URI with that same database, user, password, loopback host, and port. URI-encode the password in the DSN if necessary. Never commit or print this file.

```bash
docker compose --env-file .env.catalog-pilot.local -p catalog-pilot-20261029-01 up -d postgres
docker compose --env-file .env.catalog-pilot.local -p catalog-pilot-20261029-01 ps postgres
```

Only the `postgres` service is needed to start and read the empty Catalog. The volume persists across container and Spring restarts. Do not remove it when stopping the service.

## Initialize and verify schema and API

The `local` profile connects to `LOCAL_DB_HOST:LOCAL_DB_PORT/LOCAL_DB_NAME`. Normal application startup runs Flyway V1–V9 and Hibernate `ddl-auto=validate`. Run with the private environment loaded in the process; the verified command is:

```bash
set -a
source .env.catalog-pilot.local
set +a
mvn -B -ntp -f demo/pom.xml spring-boot:run -Dspring-boot.run.profiles=local
```

In another terminal, verify the schema without writing data:

```bash
docker compose --env-file .env.catalog-pilot.local -p catalog-pilot-20261029-01 exec -T postgres psql -X -v ON_ERROR_STOP=1 -U backend -d backend -c 'SELECT installed_rank, version, script, checksum, success FROM public.flyway_schema_history ORDER BY installed_rank;'
docker compose --env-file .env.catalog-pilot.local -p catalog-pilot-20261029-01 exec -T postgres psql -X -v ON_ERROR_STOP=1 -U backend -d backend -c 'SELECT (SELECT count(*) FROM categories) AS categories, (SELECT count(*) FROM products) AS products, (SELECT count(*) FROM product_variants) AS variants, (SELECT count(*) FROM product_images) AS images;'
curl -i 'http://127.0.0.1:18080/api/products?page=0&size=20'
```

Use the actual `SERVER_PORT` in the URL. A fresh database has no imported products, so the anonymous GET returns `200` with `items: []` and `totalItems: 0`. Point your frontend's API base URL at `http://127.0.0.1:18080` for this instance; configure the equivalent frontend setting in the frontend project. Docker does not download crawler data. Only a later approved importer run will create Catalog products. Imported DRAFT products remain hidden from public GET APIs; only independently approved PUBLISHED products appear. Restarting Spring Boot or PostgreSQL with the same volume does not require another import.

## Review-only importer checks

The 50-row pilot manifest remains unapproved. The 29 reviewed identities and historical price proposals are in `review/first-29-human-approval-summary.md`. These commands do not write database rows:

```bash
python3 tools/catalog-import/import_postgres.py --dry-run --manifest tools/catalog-import/review/demo-products-approval.csv
python3 -m venv /tmp/backend_catalog_import_venv
/tmp/backend_catalog_import_venv/bin/pip install -r tools/catalog-import/requirements-import.txt
set -a
source .env.catalog-pilot.local
set +a
/tmp/backend_catalog_import_venv/bin/python tools/catalog-import/import_postgres.py \
  --check-db --target-kind local-development \
  --expect-database backend --expect-host 127.0.0.1 --expect-port 55432 \
  --confirm-target backend@127.0.0.1:55432
```

Substitute the actual port if different. With zero approved rows, `--check-db` validates the target and Flyway history but has no eligible product rows for per-product conflict checks. The database row counts independently show this new Catalog is empty.

## Later execution checkpoint

After the owner explicitly approves each selected product's SKU, local demo selling price, category, variant mapping, import permission, and any independent publication permission, update a review manifest while keeping the other 21 rows unapproved. Repeat both checks and inspect the target database immediately before execution. The documented write command is below **for that later approved step only**:

```bash
/tmp/backend_catalog_import_venv/bin/python tools/catalog-import/import_postgres.py \
  --execute --target-kind local-development \
  --expect-database backend --expect-host 127.0.0.1 --expect-port 55432 \
  --confirm-target backend@127.0.0.1:55432 \
  --report tools/catalog-import/output/local-import.json
```

Do not run this command against an old teammate database or before explicit approval. The importer never imports images.
