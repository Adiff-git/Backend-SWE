# Backend-SWE

Spring Boot 4.1.2 / Java 25 backend using PostgreSQL 17, Flyway, JWT and private S3-compatible storage. Modules include product catalog/images, authentication, refresh sessions, profiles, addresses and user media.

## Development environment

Install Docker Engine or Docker Desktop with Linux containers and Docker Compose supporting `--wait`. Java and Maven are supplied by the backend build image.

Use the single `compose.dev.yaml` file. It starts the backend, PostgreSQL, LocalStack and the signed S3 gateway.

Copy the environment template from the repository root:

```powershell
Copy-Item .env.example .env.local
```

On Bash use `cp .env.example .env.local`. Edit `.env.local`, replacing `LOCAL_DB_PASSWORD` and `JWT_SECRET` with private random values. The JWT secret must contain at least 32 bytes. Generate each value with `python -c "import secrets; print(secrets.token_urlsafe(48))"`. `.env.local` is Git-ignored.

```sh
docker compose --env-file .env.local -f compose.dev.yaml up -d --build --wait --wait-timeout 300
docker compose --env-file .env.local -f compose.dev.yaml ps
```

Default host endpoints:

| Service | Address |
|---|---|
| Backend API | http://127.0.0.1:18081 |
| PostgreSQL | 127.0.0.1:55433 |
| Signed image gateway | http://127.0.0.1:14567 |

```sh
curl --fail "http://127.0.0.1:18081/api/products?page=0&size=20"
curl --fail "http://127.0.0.1:14567/_gateway/health"
```

The backend uses Spring's `local` profile, `postgres:5432` for its database and `localstack:4567` for storage within Docker. Browser image URLs use `LOCAL_STORAGE_SIGNING_ENDPOINT`; update this URL together with `LOCAL_STORAGE_PORT` when changing the host storage port. Host ports bind only to loopback. Raw S3 binds only to loopback inside the storage namespace; backend and browser requests go through the signed gateway.

Flyway applies migrations automatically. A fresh database returns an empty catalog. Compose does not import datasets or run seed tools. Frontend and AI services are separate projects and are not required to run this backend. Existing retailer-image datasets are for private local use only; do not expose them through public hosting or tunnels.

## Development workflow

After changing Java source, rerun the `up -d --build --wait --wait-timeout 300` command above to rebuild and update the backend. The container runs a packaged JAR and does not reload source automatically.

```sh
docker compose --env-file .env.local -f compose.dev.yaml logs -f backend
docker compose --env-file .env.local -f compose.dev.yaml logs --tail=80 localstack image-storage
docker compose --env-file .env.local -f compose.dev.yaml stop
```

PostgreSQL persists in `postgres-data`. Product image checkpoints persist in `product-checkpoints`; LocalStack restores these on startup. Avoid `down -v` when retaining data. Changing an env password does not change credentials in an initialized database. Keep `COMPOSE_PROJECT_NAME` consistent to reuse the same volumes. Checkpoints previously stored in host bind mounts are not automatically migrated into the new volume.

Choose unused host ports in `.env.local` if necessary and inspect service logs for startup errors. Swagger endpoints may require authentication; backend readiness uses the public catalog GET endpoint.

## Product image upload

Product images use the same upload flow as avatars:

1. `POST /api/image-uploads` with `{ "contentType": "image/png" }` using a Bearer token.
2. PUT image bytes to the returned `uploadUrl` with the matching Content-Type.
3. `POST /api/image-uploads/{imageId}/complete` using the returned upload `id`.
4. As that same user with `ROLE_ADMIN`, call `POST /api/products/{id}/images`:

```json
{
  "imageId": "00000000-0000-4000-8000-000000000001",
  "altText": "Front view",
  "isPrimary": true,
  "displayOrder": 0
}
```

`isPrimary` and `displayOrder` are optional. The first gallery image must be primary. Successful attachment returns 201; retrying the same image/metadata returns 200 and the same product image ID. Conflicting metadata, duplicate content, occupied order or a full ten-image gallery returns 409. Unknown or another user's upload returns 404; an unfinished/expired upload returns 409. Product image validation rejects invalid content with 422. Storage/checkpoint failure prevents gallery publication.

The product attachment creates and verifies its own durable copy, preserving shared source media for avatars. Only completed uploads are eligible; the upload deadline does not expire already completed media. Flyway V12 adds the source link without rewriting existing galleries. The old product-specific `/images/uploads` and `/images/uploads/{uploadId}/complete` routes have been removed; clients must use the shared flow above.

## Tests and documentation

For Maven tests on the host, install JDK 25 and provide Docker for integration tests:

```sh
cd demo
./mvnw clean verify
```

On Windows use `mvnw.cmd clean verify`. On Bash use `bash mvnw clean verify` if the wrapper is not executable.

- [API documentation](docs/README.md)
- [OpenAPI contract](docs/openapi.json)
- [Storage gateway](docker/localstack/gateway/README.md)
- `demo/src/main/`: application code, profiles and Flyway migrations.
- `demo/src/test/`: unit and integration tests.
- `specs/`: feature specifications and API contracts.
