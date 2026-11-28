## Resetting Demo Data (`--fresh`)

The bootstrap system supports two startup modes:

- **Normal mode:** Preserves existing data and imports the dataset only when necessary.
- **Fresh mode (`--fresh`):** Deletes the existing demo database records and reimports the selected approved dataset.

### Available Commands

| Command | Purpose | Resets Database |
|---|---|---|
| `setup-dev` | Build helper, fetch source and artifacts, and start the backend | No |
| `start-dev` | Start an existing compatible installation | No |
| `setup-dev --fresh` | Prepare source and artifacts, reset the database, and import again | Yes |
| `start-dev --fresh` | Reset the database using the existing prepared source and artifact | Yes |

### When to Use `--fresh`

| Scenario | Use `--fresh`? |
|---|---|
| First-time setup on a machine without an existing demo database | No |
| Normal daily startup | No |
| Restarting Docker Desktop or your computer | No |
| Pulling ordinary Java source code changes | No |
| Rebuilding the backend Docker image | No |
| Changing the JWT secret | No |
| Switching to a new approved dataset that is incompatible with the existing database | Yes, if data loss is acceptable |
| Encountering `DATASET_INCOMPATIBLE` after verifying the new release | Yes, if data loss is acceptable |
| Restoring the original demo dataset after testing | Yes |
| Removing all accounts, orders, reviews and other activity created during testing | Yes |
| Applying a new demo account password to all designated demo accounts through reimport | Yes, if a full reset is intended |
| Recovering from an interrupted import | No, try normal startup first |
| Encountering artifact checksum or compatibility errors | No, resolve the compatibility issue first |
| Encountering Docker or network errors | No |

> **WARNING**
>
> Fresh mode is destructive. Do not use it as the default startup mode or as a
> general solution to every bootstrap error.

### Option 1 — Full Setup With a Fresh Database

Use this option when:

- You have pulled a new backend implementation.
- The maintainer has published a new compatible demo artifact.
- You need to download missing source data or artifacts.
- You intentionally want to replace the existing demo database.

#### Windows PowerShell

```powershell
.\scripts\setup-dev.ps1 --fresh
```

#### Linux / Ubuntu / WSL2

```bash
bash scripts/setup-dev.sh --fresh
```

The setup script will:

1. Validate the local environment.
2. Build the bootstrap helper Docker image.
3. Prepare the pinned Catalog-Data source if missing.
4. Fetch missing demo artifact versions.
5. Verify the selected artifact and its compatibility.
6. Invoke the startup script in fresh mode.
7. Request explicit confirmation before resetting the database.
8. Reset and reinitialize the dedicated demo database.
9. Import the approved dataset and required images.
10. Start the backend and verify readiness.

Existing source checkouts and artifact directories are not automatically
overwritten, even when `--fresh` is supplied.

If they are incompatible, follow the instructions in
[Updating From an Older Dataset Release](#updating-from-an-older-dataset-release)
before running this command.

### Option 2 — Fresh Startup Using Existing Artifacts

Use this option when:

- You have already completed the setup process.
- The bootstrap helper image is available.
- The required Catalog-Data source and approved artifact are available locally.
- You want to restore the original demo dataset without downloading everything again.

#### Windows PowerShell

```powershell
.\scripts\start-dev.ps1 --fresh
```

#### Linux / Ubuntu / WSL2

```bash
bash scripts/start-dev.sh --fresh
```

Unlike `setup-dev --fresh`, this command does not fetch missing source data or
artifacts or rebuild the bootstrap helper image.

It uses the existing prepared local environment.

### Confirming the Reset

Both fresh-mode commands require explicit confirmation.

When prompted, type exactly:

```text
RESET backend_demo
```

The confirmation is case-sensitive.

If the confirmation does not match, the reset is cancelled.

### What Happens During Fresh Mode?

Fresh mode performs a controlled reset of the dedicated local demo environment:

1. Verify the environment configuration and resource ownership.
2. Verify the approved artifact, source revision, importer and migration fingerprints.
3. Reset the dedicated `backend_demo` database.
4. Clean importer-owned storage objects and checkpoints.
5. Recreate the database schema using Flyway migrations.
6. Import the approved product images into LocalStack.
7. Import the approved business records into PostgreSQL.
8. Initialize the designated demo accounts using the configured demo password.
9. Verify the imported dataset.
10. Start the Spring Boot backend.
11. Verify backend readiness.

The reset is restricted to the dedicated local demo environment.

### What Is Deleted and What Is Preserved?

| Resource | Behavior During Fresh Mode |
|---|---|
| Existing PostgreSQL business records | Deleted |
| User-created accounts | Deleted |
| User-created orders and shopping carts | Deleted |
| User-created reviews and wishlists | Deleted |
| Existing database schema | Recreated through Flyway |
| Bootstrap initialization metadata | Recreated |
| Importer-owned product image objects | Cleaned and reimported |
| Importer-owned image checkpoints | Cleaned and recreated |
| Non-importer-owned storage objects | Preserved |
| PostgreSQL Docker volume | Preserved |
| Catalog-Data source checkout | Preserved |
| Downloaded demo artifacts | Preserved |
| `.env.local` | Preserved |
| Backend source code | Preserved |
| Git commit history | Preserved |
| Unrelated Docker resources | Preserved |

Although the PostgreSQL Docker volume is preserved, all records inside the
dedicated `backend_demo` database are deleted.

Fresh mode does not provide automatic backups or an undo mechanism.

### Switching Between Demo Artifact Versions

The selected demo artifact is configured in `.env.local`:

```dotenv
DEMO_BOOTSTRAP_ARTIFACT=.private/demo-bootstrap/artifacts/demo-v6
```

Changing this value alone does not replace the existing database.

The database records the identity of the artifact and importer used during
its original initialization.

If the newly selected artifact has a different identity, normal startup
may report:

```json
{
  "code": "DATASET_INCOMPATIBLE",
  "message": "Existing dataset/importer identity differs; normal startup never reseeds it",
  "result": "FAIL"
}
```

This is intentional behavior designed to prevent accidental data loss.

To switch to another approved and compatible artifact:

1. Stop the current backend.
2. Back up any important database records.
3. Update `DEMO_BOOTSTRAP_ARTIFACT` in `.env.local`.
4. Ensure the selected artifact is compatible with the current backend.
5. Ensure the required source and artifact are available.
6. Run `setup-dev --fresh` or `start-dev --fresh`.
7. Confirm the database reset.
8. Verify backend readiness.

Do not assume that every downloaded artifact version is compatible with
the current backend implementation.

### Upgrading From an Older Demo Release to Demo-v6

Collaborators who previously initialized an older demo release may encounter
`DATASET_INCOMPATIBLE` after updating the repository.

If you do not need to preserve your previous demo activity:

1. Update Backend-SWE to the current implementation.
2. Preserve your existing `.env.local` and private credentials.
3. Set the selected artifact to `demo-v6`:

   ```dotenv
   DEMO_BOOTSTRAP_ARTIFACT=.private/demo-bootstrap/artifacts/demo-v6
   ```

4. Back up incompatible source checkouts or artifact directories if necessary,
   following the dataset update instructions.
5. Run the full setup in fresh mode.

Windows PowerShell:

```powershell
.\scripts\setup-dev.ps1 --fresh
```

Linux / Ubuntu / WSL2:

```bash
bash scripts/setup-dev.sh --fresh
```

When prompted, enter:

```text
RESET backend_demo
```

After successful initialization, use normal startup for subsequent runs.

If you need to preserve the existing database, do not use fresh mode.
Continue using its matching backend, importer and artifact release until
an approved upgrade procedure is available.

### Important Notes About Environment Variables

Fresh mode uses the current `.env.local` configuration.

The `DEMO_BOOTSTRAP_PASSWORD` value is used when initializing the designated
demo accounts again.

However, fresh mode does not recreate the PostgreSQL Docker volume or
automatically change the existing PostgreSQL user's credentials.

Keep the original `LOCAL_DB_PASSWORD` and `LOCAL_DB_USER` for an existing
installation unless you have explicitly migrated its database credentials.

Changing `JWT_SECRET` may invalidate previously issued access tokens.
A database reset is not required solely because the JWT secret changes.

### Handling Errors During Fresh Mode

If fresh mode reports one of the following errors:

- `ARTIFACT_CHECKSUM`
- `SOURCE_REVISION_MISMATCH`
- `MIGRATION_INCOMPATIBLE`
- Importer compatibility errors
- Startup script fingerprint errors
- `TARGET_MISMATCH`

Resolve the underlying compatibility or configuration problem first.

Fresh mode does not bypass checksum, compatibility, release approval or
resource ownership verification.

If the reset or import has already started and subsequently fails, inspect
the bootstrap report before retrying.

Bootstrap reports are available under:

```text
.private/demo-bootstrap/reports/
```

Do not repeatedly run fresh mode without understanding the previous
failure. Normal startup may be able to resume an interrupted initialization.

### After a Successful Reset

Once fresh initialization has completed successfully, return to normal
startup commands.

Windows PowerShell:

```powershell
.\scripts\start-dev.ps1
```

Linux / Ubuntu / WSL2:

```bash
bash scripts/start-dev.sh
```

Subsequent normal starts preserve existing data and skip the full business
import when the existing dataset remains compatible.

### Prohibited Reset Alternatives

Do not use broad Docker cleanup commands as substitutes for the guarded
fresh-mode reset:

```bash
docker compose down -v
docker volume prune
docker system prune -a --volumes
```

These commands may remove persistent data or resources outside the intended
demo reset scope.

Always prefer the guarded `--fresh` workflow when intentionally resetting
the dedicated demo database.
