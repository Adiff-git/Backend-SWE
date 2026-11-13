# Local image storage trust boundary

Use `compose.dev.yaml` for the local development stack, including the backend, PostgreSQL and signed image gateway.

The hardened topology is:

```text
Host backend/browser → 127.0.0.1:14567 → signed gateway :4567
                                          │
                        shared trusted network namespace
                                          │
                                127.0.0.1:4566 → raw S3
```

Only LocalStack and the trusted gateway share that namespace. PostgreSQL stays on the default network. The backend also joins `storage-private` to reach the gateway at `localstack:4567`; raw S3 remains bound to namespace loopback. Raw S3 binds **127.0.0.1 inside its own namespace**, with no host port mapping. The gateway binds :4567 and only that port is loopback-published. Backend signed S3 operations and presigned URLs retain the same external endpoint. Changing the raw port number is not the protection: the loopback-only bind rejects bridge connections even from an ordinary container deliberately attached to the storage bridge.

The pinned LocalStack 4.14 Community Twisted runtime ignores IPv4 `GATEWAY_LISTEN` interface addresses. The Compose configuration explicitly selects its supported **Hypercorn** runtime, which honors `127.0.0.1:4566`. Disposable attacker tests enforce this distinction: a configuration that merely sets the address under Twisted fails the raw-access assertion.

The private bridge is separate, not Docker `internal: true`: on the tested Docker setup an internal network also prevented access to the loopback-published signed endpoint. Namespace loopback binding remains the enforced raw-storage boundary. No untrusted workload should share the LocalStack/gateway network namespace. Do not mount a Docker socket, use host networking, or grant privileged Docker access to untrusted applications.

`/_gateway/health` returns only a readiness status; it exposes no object bytes or credentials. It verifies the upstream S3 service and **completed, successful ready hooks**, so a partially restored checkpoint inventory cannot be declared ready. LocalStack uses the same upstream-readiness logic. The gateway has its own Python health command, replacing the inherited LocalStack CLI check that could never correctly assess a gateway process. Ready-hook failures remain unhealthy; the checks are not disabled.

LocalStack Community is an emulator, not a production IAM or durability boundary. Private ACL/PublicAccessBlock alone did not reject raw unsigned reads in this pinned version. The gateway verifies full SigV4 signatures, payloads and expiry; the network namespace prevents bypass of that gateway by ordinary containers. The documented test/test keys are local development credentials, not secrets providing production identity assurance. This design does **not** protect against Docker administrators, privileged containers, host-root access, Docker-socket access, or someone deliberately joining the trusted namespace. Production must use real private object storage and its own IAM/network controls.

## Project-wide image durability

The shared `ObjectStorageService.copyIfMatch` checkpoints every final `images/...` object in the local profile when `storage.s3.checkpoint-directory` is configured. This covers completed user media (including future review attachments using the same upload flow), product copies, banners and future image modules. Module names are not allowlisted. All final image writes must use the shared storage promotion API; raw staging uploads are deliberately excluded. New image formats/size policies still belong in the shared validators.

The backend writes verified bytes synchronously before completion metadata can commit. Checkpoint write failure fails completion and retains staging for retry. Files use SHA-256 key identities, bounded image sizes, checksums, atomic rename and filesystem sync. Non-local profiles do not write local checkpoints. Existing catalog checkpoint callers/cleanup delegate to the same writer; legacy JSON manifests and the named `product-checkpoints` volume remain compatible. The legacy property `catalog.images.checkpoint-directory` is supported as fallback; Compose uses `STORAGE_S3_CHECKPOINT_DIRECTORY`.

The historical `restore_product_images.py` filename is retained for compatible mounts, but restores **all project images**, not just products. LocalStack reads the existing volume read-only; the backend writes it. Startup prevalidates every manifest before restoring, verifies restored bytes/content type, rejects malformed/checksum-invalid/path-traversal manifests, ignores unfinished `.tmp` files, and keeps readiness unhealthy on error. No database references are rewritten. Checkpoints can contain orphan objects from rolled-back transactions; private objects require authorized references/signed access. Checkpoints contain private image bytes and must remain outside Git and public hosting.

Before the first rollout, freeze uploads (stop backend), then capture any final image objects that predate automatic checkpointing **before** restarting/recreating LocalStack. For the default project name:

```sh
docker compose --env-file .env.local -f compose.dev.yaml stop backend
docker compose --env-file .env.local -f compose.dev.yaml run --rm --no-deps --volume backend-swe-local_product-checkpoints:/catalog-product-checkpoints:rw --entrypoint python3 localstack /opt/catalog/restore_product_images.py --capture-existing --endpoint http://localstack:4567
docker compose --env-file .env.local -f compose.dev.yaml up -d --build --wait --wait-timeout 300
```

Use the actual volume name if COMPOSE_PROJECT_NAME differs. If capture fails, keep storage running and restart the old backend while diagnosing; do not recreate storage. Images already missing from S3 with no checkpoint cannot be recovered and need reupload. The backfill reads existing image bytes and does not delete objects or touch PostgreSQL.

On subsequent restarts, the ready hook automatically restores images before storage is healthy. Keep the named volume; `down -v` removes it. Pending staging uploads can expire/disappear after restart and should be retried; successful completed images are the durability guarantee. This remains local emulator recovery, not a production storage/backup strategy.

Tests: `python -m unittest discover -s docker/localstack/init -p test_restore_images.py`; Java `LocalImageCheckpointTest`/`LocalImageCheckpointIT` cover atomic replacement, all namespaces, failure rollback and S3 restore roundtrip. Only generated authorized bytes are used in storage tests.


Validated 2026-11-12: six Python restore/backfill tests and 29 targeted Java tests passed (including shared checkpoint write failure rollback, avatar restore roundtrip, existing avatar/upload/product behavior). After the final filename-extension adjustment, all five checkpoint unit/integration tests were rerun successfully. The actual development Compose stack was rebuilt; a fresh test avatar was uploaded, completed and attached. Both `restart localstack image-storage` and forced container recreation preserved the exact image bytes and `/api/me` returned 200 after each operation. PostgreSQL and checkpoint volumes were retained. The initial backfill found zero surviving final objects; this cannot recover images already lost before checkpointing. All five Compose services were healthy afterward. No full unrelated regression run or production durability guarantee is claimed.
