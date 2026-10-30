# Backend-SWE

Spring Boot **4.1.2 / Java 25** backend for the electronics Product Catalog. The application uses PostgreSQL 17, Flyway, Spring Data JPA, Spring Security/JWT and private S3-compatible image storage. Existing modules include public product listing/detail, Product image uploads, registration/login/refresh sessions, user profiles, addresses, roles/permissions and user-owned media uploads.

Frontend clients call the HTTP APIs. `AI-SWE` is a separate FastAPI project with placeholder recommendation/agent features; this repository does not start AI or contain an automatic Backend-to-AI integration. When running AI locally, its `BACKEND_API_BASE_URL` must point at the actual backend address below rather than its default port 8000. No AI service is required to run the Catalog.

**Private local development only:** the existing retailer-image dataset is authorized for private local use, not public/shared hosting or redistribution. Keep the API and image gateway loopback-bound. Do not share the existing pilot through a public server or tunnel. Use authorized fixture media for separately approved shared development.

## Prerequisites

- JDK **25**, matching `demo/pom.xml` (tested OpenJDK 25.0.4.1).
- Maven (tested **3.8.7**), or the supplied Maven Wrapper, configured for **3.9.16**. On Linux the wrapper is not executable in this checkout: use `bash demo/mvnw -f demo/pom.xml ...` instead of `./demo/mvnw`.
- Docker Engine and the **Docker Compose plugin**, with support for `!override`, `!reset` and `--wait` (tested Compose **5.1.4**). Legacy `docker-compose` v1 is unsuitable. The current user must be able to access Docker.
- PostgreSQL **17**, supplied by Docker; no native database installation is needed.
- Python **3.12**, `venv`/`pip`, curl and `sha256sum` for dataset tooling, examples and checks.
- Internet access for first-time Maven/Python dependencies and pinned Docker images. Integration tests require a working Docker daemon.

Commands below assume Bash on Linux/macOS, executed from the repository root. Native Windows users should adapt paths/shell activation or use WSL2.

## Clone and inspect

```bash
git clone --branch feature/product-catalog https://github.com/Adiff-git/Backend-SWE.git
cd Backend-SWE
git status --short
java -version
mvn -version
docker compose version
```

Obtain the maintainer's reviewed revision containing the gateway, seed tools and these instructions. Local uncommitted/untracked changes are not downloaded by cloning; this task does not commit or push them. No dataset is bundled with a clone. Repository access/clone credentials and publication of the latest working-tree revision are maintainer responsibilities; a fresh remote clone was not performed in this verification.

## Configure a fresh local environment

The safe template is [`.env.example`](.env.example). The following generator copies its values into a **new**, ignored, mode-0600 `.env.local`, generating private database/JWT secrets and an absolute checkpoint path. It refuses to overwrite an existing environment file.

```bash
python3 - <<'PY'
from pathlib import Path
import os, secrets
values = dict(line.split('=', 1) for line in Path('.env.example').read_text().splitlines()
              if line and not line.startswith('#') and '=' in line)
values['LOCAL_DB_PASSWORD'] = secrets.token_urlsafe(36)
values['JWT_SECRET'] = secrets.token_urlsafe(48)
checkpoint = Path('tools/catalog-import/output') / values['COMPOSE_PROJECT_NAME'] / 'object-checkpoints'
checkpoint.mkdir(parents=True, exist_ok=True, mode=0o700)
values['CATALOG_IMAGES_CHECKPOINT_DIRECTORY'] = str(checkpoint.resolve())
values['CATALOG_IMPORT_DSN'] = ('postgresql://' + values['LOCAL_DB_USER'] + ':' +
    values['LOCAL_DB_PASSWORD'] + '@' + values['LOCAL_DB_HOST'] + ':' +
    values['LOCAL_DB_PORT'] + '/' + values['LOCAL_DB_NAME'])
fd = os.open('.env.local', os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
with os.fdopen(fd, 'w') as stream:
    stream.write(''.join(k + '=' + v + '\n' for k, v in values.items()))
PY
set -a
source .env.local
set +a
```

Only source private configuration you trust. Do not print/commit/share environment files, authentication files or database backups.

| Setting | Fresh onboarding example | Existing pilot |
|---|---|---|
| `COMPOSE_PROJECT_NAME` | `backend-swe-local` | `catalog-pilot-20261029-01` |
| `LOCAL_DB_HOST` / `LOCAL_DB_PORT` / `LOCAL_DB_NAME` | `127.0.0.1` / `55433` / `backend` | `127.0.0.1` / `55432` / `backend` |
| `LOCAL_DB_USER` / `LOCAL_DB_PASSWORD` | private local database identity | preserve existing configuration |
| `JWT_SECRET` / `JWT_ISSUER` | generated secret / `urn:backend-swe:local` | preserve existing configuration |
| `SERVER_ADDRESS` / `SERVER_PORT` | `127.0.0.1` / `18081` | `127.0.0.1` / `18080` |
| `LOCAL_STORAGE_PORT` | `14567` | `14566` |
| `LOCAL_STORAGE_ENDPOINT` | `http://127.0.0.1:14567` | `http://127.0.0.1:14566` |
| `LOCAL_STORAGE_SIGNING_ENDPOINT` | same local gateway address | same local gateway address |
| `LOCAL_STORAGE_BUCKET` | `backend-images` | `backend-images` |
| `CATALOG_IMAGES_CHECKPOINT_DIRECTORY` | generated absolute, project-specific path | existing private checkpoint directory |
| `CATALOG_IMPORT_DSN` | generated private loopback DSN; Python tools only | existing private loopback DSN |

The `local` Spring profile maps the `LOCAL_DB_*` and `LOCAL_STORAGE_*` settings. Outside that profile the application expects `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` and separately configured storage; those are not needed for this local setup. `JWT_SECRET` must contain at least 32 UTF-8 bytes. Local emulator signing uses its documented dummy test/test identity internally; never replace that with production credentials.

Before starting, check `docker ps -a`, `docker compose ls --all` and your listening ports. Choose a different project/ports **before first startup** if the example is already in use; update both storage endpoint URLs and the private DSN consistently. Changing a password in an env file does not change credentials inside an initialized PostgreSQL volume.

## Start PostgreSQL, private storage and Spring Boot

Use **both** Compose files. The image override isolates raw S3 inside the trusted storage/gateway namespace and supplies the real gateway health checks. Raw LocalStack must not be published directly.

```bash
docker compose --env-file .env.local -f compose.yaml -f compose.catalog-images.yaml up -d --wait postgres
docker compose --env-file .env.local -f compose.yaml -f compose.catalog-images.yaml up -d --wait --wait-timeout 240 localstack image-storage
docker compose --env-file .env.local -f compose.yaml -f compose.catalog-images.yaml ps
curl --fail "http://127.0.0.1:${LOCAL_STORAGE_PORT}/_gateway/health"
```

Start the backend in the foreground:

```bash
mvn -B -ntp -f demo/pom.xml spring-boot:run -Dspring-boot.run.profiles=local
```

For a log file, use this alternative command instead of starting a second application:

```bash
mvn -B -ntp -f demo/pom.xml spring-boot:run -Dspring-boot.run.profiles=local \
  > /tmp/backend-swe-local.log 2>&1
```

Normal application startup applies **Flyway V1–V9** and validates Hibernate mappings. Do not run manual schema SQL, Flyway clean/repair/baseline, or edit migration checksums to force startup. The application does not import development products automatically.

In another terminal, load the same env file and check the running backend:

```bash
set -a
source .env.local
set +a
curl --fail "http://127.0.0.1:${SERVER_PORT}/api/products?page=0&size=20"
curl --fail "http://127.0.0.1:${LOCAL_STORAGE_PORT}/_gateway/health"
```

A fresh database returns a successful **empty** catalog, not 1,000 products. The project does not configure an Actuator health endpoint; use the Catalog GET and container health checks above. Springdoc dependencies are present, but `/v3/api-docs` and `/swagger-ui/index.html` currently return **401** under the deny-by-default security chain. They are not public readiness endpoints; use the documented Catalog GETs and the API contracts under `specs/`.

To use the existing pilot, load its **existing** `.env.catalog-pilot.local` instead and use that filename with the same Compose commands. Keep `SERVER_ADDRESS=127.0.0.1` exported before starting Spring Boot (the pilot private env now includes it). Verify `COMPOSE_PROJECT_NAME`, ports and volumes before doing so; do not generate replacement secrets or run seed loaders against that already populated database.

## Development data and publication

### Existing local pilot

The existing machine's isolated pilot contains **1,000 Products, 1,000 Variants, 31 Categories and 1,096 images**. All **1,000 are PUBLISHED** for local Frontend testing. No price, SKU, category, variant or image was changed by publication. This state belongs to that machine's database/checkpoint directory, not the Git checkout.

The guarded status-only operation, backup receipt, idempotent rerun and rollback procedure are documented in [LOCAL_PUBLICATION.md](tools/catalog-import/LOCAL_PUBLICATION.md). Do not rerun seed v1 on the published pilot: v1 intentionally records the original five-PUBLISHED state and correctly rejects conflicting publication states.

### Fresh developer data

Obtain an **approved** Catalog-only seed from the maintainer; there is no published download URL. Put its `catalog.json`, `manifest.json` and `SHA256SUMS` in `tools/catalog-import/output/catalog-dev-v1/`, and verify the delivered checksums. These files are Git-ignored. Cloning the repository does not supply the crawler, dataset or retailer images.

```bash
python3 -m venv /tmp/backend_catalog_import_venv
/tmp/backend_catalog_import_venv/bin/pip install -r tools/catalog-import/requirements-images.txt
(cd tools/catalog-import/output/catalog-dev-v1 && sha256sum -c SHA256SUMS)
```

Seed v1 provides 1,000 Products, 1,000 Variants and 31 Categories, **five PUBLISHED / 995 DRAFT**, with **no images**. On the **fresh onboarding target only**, preview and then load it after normal Flyway startup:

```bash
/tmp/backend_catalog_import_venv/bin/python tools/catalog-import/portable_seed.py \
  --seed tools/catalog-import/output/catalog-dev-v1 \
  --target-kind local-development --expect-database "$LOCAL_DB_NAME" \
  --expect-host "$LOCAL_DB_HOST" --expect-port "$LOCAL_DB_PORT" \
  --confirm-target "${LOCAL_DB_NAME}@${LOCAL_DB_HOST}:${LOCAL_DB_PORT}"
# Add --execute to the same command only after reviewing that fresh local target.
```

For images, use an **authorized fixture-backed v2 package**. The v2 workflow was tested with 1,096 generated fixture images and full fresh restoration; it preserves the original five-PUBLISHED publication split. The real retailer-image seed cannot currently be packaged or redistributed. Do not copy the pilot checkpoint directory or database dump to teammates as a substitute.

To generate an authorized fixture v2 package locally, first obtain the approved v1 Catalog data, then run the seed v2 test listed below. Its output reports `fixture_seed_path` for the generated, ignored package. The test uses disposable PostgreSQL/S3 environments. Alternatively obtain an independently approved fixture package from the maintainer.

After offline verification, restore the selected package into a fresh matching target. Set `SEED_V2_DIR` to the actual supplied/generated directory (it is not an automatically created Git artifact):

```bash
/tmp/backend_catalog_import_venv/bin/python tools/catalog-import/portable_seed_v2.py verify --seed "$SEED_V2_DIR"
/tmp/backend_catalog_import_venv/bin/python tools/catalog-import/portable_seed_v2.py restore \
  --seed "$SEED_V2_DIR" --execute \
  --checkpoint-directory "$CATALOG_IMAGES_CHECKPOINT_DIRECTORY" \
  --s3-endpoint "$LOCAL_STORAGE_ENDPOINT" \
  --target-kind local-development --expect-database "$LOCAL_DB_NAME" \
  --expect-host "$LOCAL_DB_HOST" --expect-port "$LOCAL_DB_PORT" \
  --confirm-target "${LOCAL_DB_NAME}@${LOCAL_DB_HOST}:${LOCAL_DB_PORT}"
```

The checkpoint path must match the directory mounted by **that target's** Compose project. Identical repeat restores create zero rows and upload zero objects; conflicting data fails. Stop/restart does not require another import. A real image-bearing package could use the same loader only after its separate media-use/distribution approval and integrity review. See [portable seed v2](tools/catalog-import/PORTABLE_SEED_V2.md), [seed v1 onboarding](tools/catalog-import/FRONTEND_SEED.md) and [import tools](tools/catalog-import/README.md).

### Product APIs

For the current published pilot:

```bash
curl --fail 'http://127.0.0.1:18080/api/products?page=0&size=20'
curl --fail 'http://127.0.0.1:18080/api/products/204aa314-9d7e-5df5-9984-02e1d46db7f4'
```

The pilot returns 20 products on page 0, `totalItems=1000` and `totalPages=50`. Pages are zero-based; `size` is 1–100. Lists contain only PUBLISHED products; DRAFT or nonexistent product details return 404. The fresh v1/v2 fixture seeds list only five products until a separately reviewed local publication operation changes their statuses.

Details include variants/SKUs/VND selling prices, categories, `primaryImageUrl` and ordered `images`. URLs are signed private GET capabilities valid for **five minutes (300 seconds)**. Frontend should use the returned URL unchanged as its image source, without appending API tokens/query parameters or constructing S3 keys. Refresh Catalog data to obtain new URLs after expiry; show an image placeholder while refreshing. An unsigned or altered object URL returns 403.

## Frontend and AI development integration

**Option 1 — local backend:** each developer runs PostgreSQL, storage and Spring Boot locally with an approved seed. For the fresh example the API base is `http://127.0.0.1:18081`; for the existing pilot it is `http://127.0.0.1:18080`. Configure the real Frontend client's API base URL accordingly, or proxy `/api` through its dev server. Frontend-SWE in this workspace currently contains only a README, so there is no verified framework-specific env variable to prescribe.

```javascript
const apiBase = 'http://127.0.0.1:18081';
const response = await fetch(`${apiBase}/api/products?page=0&size=20`);
const page = await response.json();
// Render page.items using each item's returned primaryImageUrl.
```

The current `SharedCorsConfiguration` allows all origins, methods and headers, exposes `Authorization`, and sets `allowCredentials=false`. Anonymous Catalog GETs need no bearer token or cookies; omit `credentials: 'include'`. Authenticated endpoints use the real Auth flow and Bearer header. The local S3 gateway forwards the bucket's configured browser CORS rules. Both API and signed image addresses must be reachable from the browser.

**Option 2 — approved shared development backend:** a separately authorized fixture-backed environment can provide a reachable API URL so Frontend developers do not run databases themselves. The service owner must configure reachable API **and signing/storage** URLs, network access and suitable CORS for that environment. No shared backend is deployed by these instructions.

`127.0.0.1` on the backend developer's machine is not reachable as that backend from another developer's machine; it refers to the caller's own host (or a container's own namespace). Do not expose this retailer-image pilot to solve that. Request an approved fixture-backed shared service or use Option 1. AI clients running directly on the same machine can set `BACKEND_API_BASE_URL=http://127.0.0.1:18081`; container clients need an explicitly reachable host/service address instead of their own loopback. Actual AI feature integration is outside this backend's tested startup path.

## Tests and troubleshooting

Full Java verification (unit + disposable integration tests):

```bash
cd demo
mvn -B -ntp clean verify
cd ..
```

Catalog/Image/S3/authentication and Listing/Detail tests are included. Additional Python tests require the approved Catalog seed described above:

```bash
/tmp/backend_catalog_import_venv/bin/pip install 'testcontainers[postgres,localstack]==4.15.0'
mvn -B -ntp -f demo/pom.xml test-compile dependency:build-classpath \
  -Dmdep.outputFile=/tmp/backend_catalog_import_classpath.txt
/tmp/backend_catalog_import_venv/bin/python -m unittest discover -s tools/catalog-import -p 'test_*.py' -v
```

The discovery includes normalizer, product/image importers, v1/v2 seeds, real Product upload, S3 gateway, topology isolation and publication/rollback tests. Writes occur only against disposable targets. To generate/test only fixture seed v2, run `/tmp/backend_catalog_import_venv/bin/python tools/catalog-import/test_portable_seed_v2.py`. Test infrastructure uses separate random ports/projects and must not inherit a production/staging DSN. Generated artifacts stay ignored; do not disable failing tests to make setup appear successful.

Useful local checks, after sourcing the matching private environment:

```bash
docker compose --env-file .env.local -f compose.yaml -f compose.catalog-images.yaml ps
docker compose --env-file .env.local -f compose.yaml -f compose.catalog-images.yaml logs --tail=80 localstack image-storage
docker compose --env-file .env.local -f compose.yaml -f compose.catalog-images.yaml exec -T postgres \
  sh -c 'pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
docker compose --env-file .env.local -f compose.yaml -f compose.catalog-images.yaml exec -T postgres \
  sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "SELECT version,success FROM flyway_schema_history ORDER BY installed_rank"'
curl --fail "http://127.0.0.1:${LOCAL_STORAGE_PORT}/_gateway/health"
curl --fail "http://127.0.0.1:${SERVER_PORT}/api/products?page=0&size=20"
tail -n 80 /tmp/backend-swe-local.log
```

- **Empty list:** fresh databases have no dataset; seed defaults expose only PUBLISHED products. Inspect the actual target before loading anything.
- **Missing images:** v1 has none; v2 needs both metadata and object bytes. Check the signed gateway, returned hostname/port and URL expiry. Keep the project-specific checkpoint directory. Both storage checks wait for successful ready-hook restoration; do not bypass them or expose raw S3.
- **403 image response:** obtain a fresh API URL; do not alter the signature. A working unsigned image URL is a security defect, not a fix.
- **Database startup/authentication failure:** verify the local profile/env, port ownership and original volume credentials. Startup migrations must match exact V1–V9; never reset an existing database, rewrite migration history or delete a volume to bypass errors.
- **Port/project conflict:** choose unused values for a new project; preserve existing projects. For startup logs, use the foreground console or the redirected log alternative above.
- **Java compilation failure:** check `JAVA_HOME`/`java -version` is JDK 25. **Integration test Docker failure:** check Docker daemon access and image availability, not the pilot DSN.

Stop the foreground backend with Ctrl-C. Stop infrastructure while retaining containers, volumes and dataset checkpoints:

```bash
docker compose --env-file .env.local -f compose.yaml -f compose.catalog-images.yaml stop
```

Start again using the same env/project and earlier `up -d --wait` commands. Never use `down -v`, `docker volume rm`, or prune commands on this environment. Do not delete the checkpoint directory.

## Project structure and verification scope

| Path | Responsibility |
|---|---|
| `demo/pom.xml`, `demo/mvnw*` | Maven application/build/wrapper |
| `demo/src/main/java/com/example/demo/` | Catalog, Auth, User/Address, Media, Storage and shared security |
| `demo/src/main/resources/` | Profiles and immutable Flyway V1–V9 migrations |
| `demo/src/test/` | Unit and disposable integration tests |
| `compose.yaml`, `compose.catalog-images.yaml` | PostgreSQL and hardened local S3/gateway topology |
| `docker/localstack/` | Bucket initialization, checkpoint recovery, gateway and trust-boundary documentation |
| `tools/catalog-import/` | Normalization/import/seed tools, local publication and acceptance reports |
| `tools/catalog-import/output/` | Ignored private datasets, fixture packages, backups and evidence |
| `specs/` | API contracts, implementation specs and handoff notes |

Runtime commands were checked against this checkout. The fresh `.env.local`/Compose/Spring Boot path was smoke-tested with a separate empty local project, normal Flyway startup and healthy storage; full seed restoration and publication/rollback were tested on disposable fixtures. The remote clone, Maven Wrapper distribution download, unpublished revision delivery, actual browser Frontend rendering and real retailer-image seed redistribution were not verified or performed. System Maven was used for the tested commands.

See [latest local publication report](tools/catalog-import/review/1000-products-local-publication.md), [previous technical acceptance](tools/catalog-import/review/1000-products-final-verification.md), [storage trust boundary](docker/localstack/gateway/README.md), [seed v2](tools/catalog-import/PORTABLE_SEED_V2.md), and [distribution restrictions](tools/catalog-import/SEED_DISTRIBUTION.md).
