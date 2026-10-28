# Frontend local Catalog dataset

The seed tooling is maintained on `feature/product-catalog`. Teammates need a shared revision containing these tools; local commits are not available from origin until the owner explicitly pushes them. No dataset upload was made. The reviewed dataset is packaged separately rather than bundled with the entire crawler snapshot.

## Obtain the seed and checkout

Clone `https://github.com/Adiff-git/Backend-SWE.git`, checkout the reviewed feature revision containing `portable_seed.py`, and install Java 25, Maven, Docker Compose and Python 3. From the repository root:

```bash
python3 -m venv /tmp/backend_catalog_import_venv
/tmp/backend_catalog_import_venv/bin/pip install -r tools/catalog-import/requirements-import.txt
```

Ask the project maintainer for the reviewed `catalog-dev-v1` directory containing `catalog.json`, `manifest.json` and `SHA256SUMS` (or the corresponding archive). The current local copy is `tools/catalog-import/output/catalog-dev-v1/`. Place those files in the same path on your checkout and verify `sha256sum -c SHA256SUMS` from that directory. These generated dataset files remain Git ignored pending distribution review. No published download URL exists yet. Loading requires no `electronics-crawler`, SQLite database or normalization output.

## New persistent local environment

Inspect `docker ps -a`, `docker volume ls`, `docker compose ls --all` and listening ports first. The example below uses a new project `frontend-catalog-v1`, database `backend`, PostgreSQL port `55433`, and API port `18081`; choose unused project/ports and never remove an existing volume. Create private configuration without displaying credentials:

```bash
python3 - <<'PY'
import os, secrets
values = dict(LOCAL_DB_NAME='backend', LOCAL_DB_USER='backend',
              LOCAL_DB_HOST='127.0.0.1', LOCAL_DB_PORT='55433',
              LOCAL_DB_PASSWORD=secrets.token_urlsafe(36),
              JWT_SECRET=secrets.token_urlsafe(48),
              JWT_ISSUER='urn:backend-swe:frontend-local',
              SERVER_ADDRESS='127.0.0.1', SERVER_PORT='18081')
values['CATALOG_IMPORT_DSN'] = 'postgresql://backend:' + values['LOCAL_DB_PASSWORD'] + '@127.0.0.1:55433/backend'
fd = os.open('.env.frontend.local', os.O_CREAT | os.O_EXCL | os.O_WRONLY, 0o600)
with os.fdopen(fd, 'w') as stream:
    stream.write(''.join(k + '=' + v + '\n' for k, v in values.items()))
PY
docker compose --env-file .env.frontend.local -p frontend-catalog-v1 up -d postgres
docker compose --env-file .env.frontend.local -p frontend-catalog-v1 ps postgres
set -a
source .env.frontend.local
set +a
mvn -B -ntp -f demo/pom.xml spring-boot:run -Dspring-boot.run.profiles=local
```

Wait for the database to be healthy and Spring Boot to start. Normal startup applies Flyway V1–V9 and Hibernate schema validation. No manual migrations, Flyway repair, baseline or clean are required. No LocalStack is needed for this image-free Catalog read demo.

## Load the seed

In another terminal at the repository root, export the same private configuration. First run the default read-only preview, then execute on this newly identified development database:

```bash
set -a
source .env.frontend.local
set +a
/tmp/backend_catalog_import_venv/bin/python tools/catalog-import/portable_seed.py \
  --seed tools/catalog-import/output/catalog-dev-v1 \
  --target-kind local-development --expect-database backend \
  --expect-host 127.0.0.1 --expect-port 55433 \
  --confirm-target backend@127.0.0.1:55433
/tmp/backend_catalog_import_venv/bin/python tools/catalog-import/portable_seed.py \
  --seed tools/catalog-import/output/catalog-dev-v1 --execute \
  --target-kind local-development --expect-database backend \
  --expect-host 127.0.0.1 --expect-port 55433 \
  --confirm-target backend@127.0.0.1:55433
```

The loader validates the version, SHA-256 data checksum, migration checksums, deterministic IDs, categories, SKU uniqueness, prices and publication split. It reuses the existing importer connection safety checks and transactional category/product/variant writer. Exact repeated loads skip matching products; differing fields or unrelated target records cause failure and rollback. It never updates existing records or publication states.

The seed contains exactly 31 categories, 1,000 Products and one variant per Product. Only five Products are PUBLISHED; 995 remain DRAFT. It includes no Users, Addresses, Authentication records, Media records or images. Historical prices are LOCAL DEMO prices only.

## Use the API

Set your frontend API base URL to `http://127.0.0.1:18081` using the frontend project's actual configuration convention.

```bash
curl 'http://127.0.0.1:18081/api/products?page=0&size=20'
curl 'http://127.0.0.1:18081/api/products/204aa314-9d7e-5df5-9984-02e1d46db7f4'
```

The public list has five items, `totalItems=5` and `totalPages=1`; DRAFT Products are hidden and return 404 by ID. Images are empty. The separate 45-product publication proposal needs explicit authorization before the demo can show 50 public products.

The Compose volume `frontend-catalog-v1_postgres-data` retains imported data across Docker and Spring Boot restarts. Do not import again after every restart or use `down -v`. A fresh project/volume starts empty until this seed is loaded. Docker does not fetch crawler data.

## Seed verification and review

Run the actual seed integration test on a fresh disposable PostgreSQL 17 container (requires the separately obtained dataset):

```bash
/tmp/backend_catalog_import_venv/bin/pip install 'testcontainers[postgres]==4.15.0'
mvn -B -ntp -f demo/pom.xml test-compile dependency:build-classpath -Dmdep.outputFile=/tmp/backend_catalog_import_classpath.txt
/tmp/backend_catalog_import_venv/bin/python tools/catalog-import/test_portable_seed.py
```

The test applies real Flyway migrations, loads twice, compares every Product and variant, checks category conflicts and tampered data, and starts Spring Boot against that disposable database to verify public APIs. It does not connect to the persistent pilot database.

Commit candidates: `portable_seed.py`, `export_portable_seed.py`, `test_portable_seed.py`, this guide, the local setup guide, reviewed execution reports and publication proposal. Preserve the pre-existing staged review document. Keep `.env.*`, `output/` datasets, approval manifests, backups, snapshots and generated evidence out of commits until the dataset is separately reviewed for distribution. The historical selection scripts under `output/` require the original crawler and pre-import state; they are audit artifacts, not frontend dependencies.

## Browser CORS and development proxy

The current integrated `SharedCorsConfiguration` allows all origins, methods and headers, exposes `Authorization`, and sets `allowCredentials=false`. Public Catalog GET requests need no token or cookies; use ordinary fetch without `credentials: 'include'`. This describes existing behavior and does not change security configuration.

Alternatively configure your frontend dev server to proxy `/api` to `http://127.0.0.1:18081`, and request relative `/api/products` URLs from the browser. Keep the proxy target in local configuration. A frontend running inside a container must reach the backend through its host/network configuration rather than assuming its own localhost is the backend.

See [SEED_DISTRIBUTION.md](SEED_DISTRIBUTION.md) for rights clearance, private delivery and archive verification. Seed v1 is immutable; any later publication changes require an independently reviewed v2 artifact.
