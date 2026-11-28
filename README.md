# Backend-SWE

## Collaborator setup

Run the commands below from the repository root, where `compose.dev.yaml` is located.

All tracked text uses LF except Windows batch scripts. See
[the line-ending policy](docs/line-ending-policy.md) for the normalized source
pin, artifact release lineage, audit command, and existing-database guidance.

### 1. Prepare your environment

- Install Git and Docker with Docker Compose v2, and start the local Docker daemon.
- Windows: use Windows PowerShell 5.1+ or PowerShell 7 with Docker Desktop in
  Linux-container mode. WSL2/Bash is not required for the PowerShell scripts.
- Linux: use Bash with a local Docker daemon. Ubuntu/WSL2 Bash can use the Linux
  scripts when Docker Desktop integration is enabled for that distribution.
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

In PowerShell, use `Copy-Item .env.example .env.local` instead.

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
DEMO_BOOTSTRAP_ARTIFACT=.private/demo-bootstrap/artifacts/demo-v6
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

### 3. Configure the demo inputs

The setup scripts clone the pinned `Catalog-Data` source if missing, then fetch
its `bootstrap` branch and install all missing `demo-v...` artifact directories.
No separate artifact download or manual copy is required. Git access to
`https://github.com/Adiff-git/Catalog-Data.git` and its `bootstrap` branch is required.
The local layout is:

```text
data/Catalog-Data/                         # Source Git checkout, including .git
.private/demo-bootstrap/artifacts/demo-v6/ # Complete approved artifact directory
```

The source checkout stays at commit
`89e3b6c5bc95b533386a349c65305613617d8709`. The artifact must include its
`manifest.json`, `release-approval.json` and all referenced files. Keep source
files/manifests and the frozen artifact unchanged; do not regenerate them during
ordinary setup. Setup exports artifact files from one fetched Git commit without
checkout line-ending conversion. Existing local artifact versions are never
overwritten. Only the version selected in `DEMO_BOOTSTRAP_ARTIFACT` is verified
for startup; downloading older versions does not select or approve them for the
current importer. If that version is absent from the branch, setup stops.

No AI API key or runtime AI call is needed. Dataset preparation and review are
separate maintainer activities.

### 4. Run the setup script for your terminal

Windows PowerShell:

```powershell
.\scripts\setup-dev.ps1
```

Linux terminal:

```bash
bash scripts/setup-dev.sh
```

These are the two setup entry points. Each builds the helper, obtains the pinned
source if missing, downloads missing artifact versions from `Catalog-Data` branch
`bootstrap`, verifies the selected artifact, then invokes its platform's
`start-dev` orchestrator. Synthetic data is downloaded as frozen files, never
generated during setup. PowerShell also requires Windows' `tar.exe`; Linux needs `tar`.

Use the approved `demo-v6` artifact matching this implementation. It preserves
the `demo-v3` dataset and records the current importer and both startup script
hashes. If compatibility is rejected, preserve the artifact and ask the maintainer
for the matching release; do not edit its manifest or approval.

An existing database imported with `demo-v3` retains that release's identity.
Normal startup with `demo-v6` will refuse to adopt or overwrite it. To replace
that database, use the optional `--fresh` procedure below only when you intend
to delete its records and import again. Updating the artifact setting alone
does not migrate an existing installation.

For an already prepared installation, the helper can also be built directly:

```bash
docker build -t backend-swe-demo-bootstrap:local tools/demo-bootstrap
```

This installs the pinned helper dependencies inside its Docker image. You do not
need to install Python packages on your host. Use the implementation that matches
the approved artifact; changing importer code or artifact contents can cause a
compatibility rejection.

### 5. Start the backend and import demo data

Setup already performs this step. On later runs, use the command for your terminal:

```powershell
.\scripts\start-dev.ps1
```

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

```powershell
.\scripts\start-dev.ps1
```

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

```powershell
.\scripts\start-dev.ps1 --fresh
```

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
