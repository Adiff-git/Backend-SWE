# Startup Contract

**Version**: demo-bootstrap-startup-v1. This describes future implementation; the commands do not exist yet at planning time.

## User interface

Exactly two operating modes, from repository root:

```bash
bash scripts/start-dev.sh
bash scripts/start-dev.sh --fresh
```

No third import/reset/resume operating mode. Reject unknown/duplicate arguments nonzero before resource changes. Private helper stages and maintainer offline preparation are internal tools, not additional startup modes. Fresh is interactive and requires exact input `RESET backend_demo`; EOF/mismatch stops before deletion. No automatic confirmation flag or environment bypass.

## Effective configuration

| Field | Required contract |
|---|---|
| Compose files/project | Existing compose.dev.yaml plus minimal compose.bootstrap.yaml helper overlay; project fixed backend-swe-demo |
| Database | LOCAL_DB_NAME=backend_demo; approved LOCAL_DB_USER/password; postgres service internal port 5432; loopback host port follows existing contract |
| Storage | LOCAL_STORAGE_BUCKET=backend-demo-images; localstack service signed S3 port 4567, us-east-1/path-style/test local credentials; gateway signature enforcement unchanged |
| Backend | Existing local profile/container image; normal host binding remains loopback through Compose; temporary instance binds container 127.0.0.1 and publishes no ports |
| Local secrets | Existing LOCAL_DB_PASSWORD/JWT_SECRET and new DEMO_BOOTSTRAP_PASSWORD supplied only through uncommitted configuration; no real secret example values |
| Artifact | DEMO_BOOTSTRAP_ARTIFACT: canonical local directory under .private/demo-bootstrap/artifacts; no URL/download/executable input; default approved artifact pointer documented by implementation |
| Reports | .private/demo-bootstrap/reports/<attempt>/; private logs; no credential/hash/token contents |
| Lock | backend-swe-demo-bootstrap-lock on same verified local Docker daemon; invocation token labels |
| Asset ownership | images/demo-bootstrap/ plus durable exact ledger in existing checkpoint volume .bootstrap/ownership.json |

Validate configuration as data, never `source` an arbitrary `.env.local` into Bash. Credentials travel through private environment/stdin/config files with restricted permissions, not shell command text. Redact before output. A missing password blocks first/fresh import; matching completed normal startup must not rewrite password hashes or require a new password merely to skip seeding. Runtime dependency versions and Docker image digests belong to implementation's reviewed lock, not dynamically selected latest packages.

Reject remote/cloud database or object endpoints, ambient AWS profiles, mismatched effective backend settings, non-owned resource labels, unexpected stateful-service recreation and unsafe resource paths. Local SDK config is explicit, including endpoint/credentials/region; no cloud fallback. Bucket existence alone is not ownership proof.

## Ordered lifecycle

1. Parse arguments and perform read-only prerequisite/local-identity preflight. In fresh mode obtain exact typed confirmation, then reserve the exclusive daemon name; normal mode reserves after read-only preflight. No resource mutation, including lock-container creation, before applicable local identity and fresh confirmation checks; source/artifact hash failure stops before reset.
2. After lock reservation, stop normal backend traffic before infrastructure startup/restoration or initialization effects. Inspect checkpoint volume existence/labels before mounting it; do not implicitly create an absent volume with a supposedly read-only inspection helper. Inspect existing checkpoint contents and any live owned objects; root restore can overwrite, so conflict check must precede recreation/start. Stop normal backend before repair/reset/import and keep stopped on incomplete initialization. Retain healthy infrastructure; start required postgres/localstack/image-storage/mailpit only after preflight.
3. Classify database by live metadata/history and business tables. Only migration-owned baseline roles/permissions can coexist with unseeded state; auth/upload/business activity without completion is foreign. Exact allowed baseline reflects current migration fingerprint.
4. Fresh saves reset/owned-asset intention durably, resets only backend_demo through maintenance connection, preserves PostgreSQL volume, cleans exact ledger-owned objects/checkpoints and recreates database. No broad Docker prune/down -v or wildcard bucket cleanup.
5. Only unseeded/fresh runs unpublished temporary existing backend with optional workers disabled; verify compatible Flyway completion and internal readiness, stop it. Completed compatible normal path skips migration instance and refuses pending/mismatched history before normal backend can auto-migrate.
6. Ensure verified required asset objects/checkpoints, then resolve graph/import/verify in one business transaction and atomically complete operational metadata. Matching completed state skips import and preserves later user activity.
7. Start ordinary backend, verify actual readiness, emit result and release owned lock only after all children exit. Post-commit failure remains a committed dataset plus failed application readiness, not a rolled-back seed.

Timeout defaults for implementation: infrastructure 180 seconds, temporary/backend readiness 600 seconds, individual storage operations 60 seconds. They are bounded failure controls, not performance promises. Any override must be finite/positive and documented; DB import has a logged bounded statement-timeout policy chosen after measuring real constraint cost, never automatic trigger disablement.

## Initialization state/output semantics

| Condition | Result | Exit |
|---|---|---|
| New successful import and backend ready | initialized=true, action=imported | 0 |
| Matching completed dataset, backend ready | initialized=true, action=skipped (or assets_repaired) | 0 |
| Configuration/argument/confirmation failure | initialized reflects actual known state; no reset | 2 |
| Lock occupied | action=blocked, reason=LOCK_HELD | 3 |
| Foreign/changed/incompatible state or object conflict | action=blocked, explicit sanitized reason | 4 |
| Infrastructure/migration/import/verification failure | action=failed, actual commit state and safe recovery | 5 |
| Backend readiness fails after commit | initialized=true, backend_ready=false; no reset | 6 |
| SIGINT/SIGTERM | best-effort supervised shutdown; actual commit outcome reread/report where possible | conventional nonzero signal exit |

Every failed import exits nonzero, never claims application ready, and includes report path and a recovery action. If state cannot be established after lost commit response, report UNKNOWN and stop; do not claim rollback or repeat blindly. Error-code mapping is tooling-only and does not change HTTP errors.

## Nonsecret final summary

Dataset/version/digest, source/importer identity, import anchor, action/state, per-domain expected/actual/imported/skipped/rejected counts, object/checkpoint results, invariant results, twelve demo usernames and assigned journeys, nonsecret configured backend/Mailpit URLs and frontend URL only if actually known. Password comes from the user's private configuration; never print it or its BCrypt hash. Do not emit sessions, tokens, OTPs or secret-bearing URLs.

## Recovery boundaries

Retry normal only for same-identity attempt with rolled-back business graph and trusted ownership; reuse exact matching objects/anchor. Foreign data or lost ownership does not authorize repair-by-delete. Changed identity requires compatible tooling/artifact or explicitly confirmed fresh. Uncertain stale lock requires proving previous orchestrator/helpers/migrator are gone and verifying token/resource labels before exact manual removal. No automatic stale-age takeover, force reset, volume deletion or adoption of foreign data.
