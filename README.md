# Backend-SWE

## Collaborator setup

Run the commands below from the repository root, where `compose.dev.yaml` is located.

### 1. Prepare your environment

- Install Git and Docker with Docker Compose v2, and start the local Docker daemon.
- To run the bootstrap script, use Linux Bash or Ubuntu/WSL2. On Windows, enable
  Docker Desktop integration for your WSL distribution and open its terminal.
  PowerShell and Git Bash are not supported runtimes for this script.
- A host Java installation is not required for Docker startup. Java 25 is needed
  only if you run the Maven application/build directly on your computer.

The bootstrap instructions require `scripts/start-dev.sh`, `compose.bootstrap.yaml`
and `tools/demo-bootstrap/` from the maintainer's bootstrap implementation. If your
checkout does not contain these files, obtain that implementation first; this
README alone does not install the tooling. Likewise, Swagger access requires the
Swagger security/configuration changes, not only the existing Springdoc dependency.

### 2. Create `.env.local`

For a **new setup**, copy the example configuration:

```bash
cp .env.example .env.local
```

If `.env.local` already exists, preserve it instead of overwriting your credentials.
Edit it and supply three distinct private values:

| Variable | What to configure |
|---|---|
| `LOCAL_DB_PASSWORD` | A private password for your local PostgreSQL instance |
| `JWT_SECRET` | A random secret of at least 32 bytes |
| `DEMO_BOOTSTRAP_PASSWORD` | The password for the 12 demo logins; use at most 72 UTF-8 bytes |

Use the following dedicated demo settings. Add any missing entries if your
configuration example predates the bootstrap implementation:

```dotenv
COMPOSE_PROJECT_NAME=backend-swe-demo
LOCAL_DB_NAME=backend_demo
LOCAL_DB_USER=backend
LOCAL_STORAGE_BUCKET=backend-demo-images
DEMO_BOOTSTRAP_ARTIFACT=.private/demo-bootstrap/artifacts/demo-v3
SERVER_PORT=18081
LOCAL_DB_PORT=55433
LOCAL_STORAGE_PORT=14567
LOCAL_STORAGE_SIGNING_ENDPOINT=http://127.0.0.1:14567
LOCAL_MAILPIT_PORT=8025
GOOGLE_ENABLED=false
EMAIL_VERIFICATION_ENABLED=false
```

Keep `.env.local` private. Do not commit passwords, JWT secrets or tokens.
Google login and email verification are optional features and need their own
configuration before enabling them.

### 3. Obtain the private demo inputs

Ask the maintainer for the approved source checkout and frozen artifact through
the team's private sharing channel. They are not supplied by a normal Git clone:

```text
data/Catalog-Data/                         # Source Git checkout, including .git
.private/demo-bootstrap/artifacts/demo-v3/ # Complete approved artifact directory
```

The source checkout must be at commit
`c5dcfaabdf61b244783d46a1403107e19cd2b02c`. The artifact must include its
`manifest.json`, `release-approval.json` and all referenced files. Keep source
files/manifests and the frozen artifact unchanged; do not regenerate them during
ordinary setup. Extract private bundles as your normal user and ensure their
files are readable by the helper through your host group.

No AI API key or runtime AI call is needed. Dataset preparation and review are
separate maintainer activities.

### 4. Build the bootstrap helper

```bash
docker build -t backend-swe-demo-bootstrap:local tools/demo-bootstrap
```

This installs the pinned helper dependencies inside its Docker image. You do not
need to install Python packages on your host. Use the implementation that matches
the approved artifact; changing importer code or artifact contents can cause a
compatibility rejection.

### 5. Start the backend and import demo data

```bash
bash scripts/start-dev.sh
```

The script checks configuration and ownership, starts the dedicated infrastructure,
uses the existing backend to apply migrations when needed, imports the approved
dataset, verifies it, and starts the normal backend after successful initialization.
Do not use plain `docker compose up` as a substitute for the full demo import.

The first full business import took approximately 29 minutes in the verified
environment; image checks/uploads and service startup take additional time. Let
the command finish. It must report initialization and backend readiness before
you begin using the API.

The approved dataset contains 1,500 products, 2,257 variants, 500 customers,
178,538 orders and 83,561 reviews. Later normal starts preserve your activity and
skip the business import; asset verification can still take time.

### 6. Open Swagger UI and query the API

| Service | Local address |
|---|---|
| Swagger UI | http://localhost:18081/swagger-ui/index.html |
| OpenAPI JSON | http://localhost:18081/v3/api-docs |
| Public product query | http://localhost:18081/api/products?page=0&size=20 |
| Mailpit | http://localhost:8025 |
| PostgreSQL | `localhost:55433`, database `backend_demo`, user `backend` |

For public catalog requests, open Swagger UI, expand `GET /api/products`, click
**Try it out**, set `page`/`size` if desired, then click **Execute**.

For authenticated requests:

1. Expand `POST /api/auth/login` and click **Try it out**.
2. Enter a demo username and the `DEMO_BOOTSTRAP_PASSWORD` configured in your own
   `.env.local`:

   ```json
   {
     "identifier": "demo_browse",
     "password": "YOUR_PRIVATE_DEMO_PASSWORD"
   }
   ```

3. Click **Execute**. Copy the JWT from the response's **Authorization header**;
   the access token is not returned as an `accessToken` body field.
4. Click **Authorize** at the top of Swagger UI and paste only the token, without
   its `Bearer ` prefix.
5. Try `GET /api/me`, `/api/me/addresses`, `/api/me/cart`, `/api/me/orders` or
   `/api/me/wishlist`.

Example demo usernames include `demo_browse`, `demo_variants`, `demo_cart`,
`demo_checkout`, `demo_stock`, `demo_vouchers`, `demo_orders`, `demo_cancellation`,
`demo_reviews`, `demo_wishlist`, `demo_history` and `demo_behavior`. They share
the locally configured demo password. The other 488 synthetic customers have no
password login. Customer APIs return the current user's records and enforce the
existing permissions; authorizing Swagger does not grant administrator access.

Springdoc 3.1.0 is already included in the project; no additional dependency is
needed. If the UI returns 401/403, confirm you have built and started the backend
version containing the Swagger integration.

### 7. Daily startup, code changes and shutdown

For normal use, run the same command again:

```bash
bash scripts/start-dev.sh
```

After changing Java code, rebuild the backend image before normal startup:

```bash
docker compose --project-name backend-swe-demo --env-file .env.local -f compose.dev.yaml build backend
bash scripts/start-dev.sh
```

To stop the dedicated services while keeping their volumes:

```bash
docker compose --project-name backend-swe-demo --env-file .env.local -f compose.dev.yaml stop
```

Keep the original database/demo password settings for an existing installation.
Changing `.env.local` does not automatically change database credentials or reset
stored demo-login passwords.

## Optional: reset the demo dataset

Use this only when you intentionally want to delete **all records in
`backend_demo`, including your later demo activity**, and import again:

```bash
bash scripts/start-dev.sh --fresh
```

At the prompt, type exactly:

```text
RESET backend_demo
```

Fresh mode recreates only the dedicated database and cleans importer-owned
objects/checkpoints. It retains the PostgreSQL volume and non-owned uploaded
objects/checkpoints. It does not provide an automatic backup or undo. Do not use
`docker compose down -v` or broad Docker pruning as a replacement.

## Troubleshooting and verification

- **Missing source/artifact:** obtain the complete approved private bundle from
  the maintainer; do not edit checksums or invent an approval record.
- **Compatibility or ownership rejection:** preserve the existing data and use
  matching tooling/configuration. See the recovery guide before attempting a reset.
- **Lock already held:** let the active invocation finish. Do not remove a lock
  merely because it looks old; recovery requires proving its workers have stopped.
- **Port conflict:** check ports `18081`, `55433`, `14567` and `8025`. Do not stop
  unrelated projects or change an existing database's identity to work around it.
- **Login fails after editing the demo password:** use the password originally
  used for that database, or deliberately reset the dedicated demo if appropriate.

For code verification on a host with Java 25:

```bash
cd demo
./mvnw verify
```

Keep review checkouts and submissions outside `demo/target`, which Maven treats
as generated build output. No `clean` is required for the command above.

The bootstrap has Windows/Docker operational evidence; native Linux/WSL2 host
and native cross-checkout verification remain deferred. Commerce frontend
journeys remain unexecuted without a supplied frontend. Swagger UI is the API
documentation/testing interface, not the storefront frontend.

When the bootstrap implementation is available, see its detailed
[usage/recovery guide](specs/014-demo-data-bootstrap/quickstart.md) and
[maintainer preparation instructions](tools/demo-bootstrap/README.md).

## Recommendation MVP

See [recommendation documentation](docs/recommendation.md).

## Restricted educational scope

This is a private, non-commercial university course project for team members,
the instructor and private instructor submission. Preserve source provenance.
Public redistribution and commercial use are prohibited. Public redistribution
verification is not applicable to the current scope; copyright clearance and reuse
permissions remain **UNVERIFIED**. Verify applicable permissions and reassess scope
before any future public redistribution or commercial use.
