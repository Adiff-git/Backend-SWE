# Local image storage trust boundary

Use `compose.yaml` together with `compose.catalog-images.yaml` for the Catalog image workflow. The base Compose file alone is a generic local emulator setup, not this hardened image-storage configuration.

The hardened topology is:

```text
Host backend/browser → 127.0.0.1:14566 → signed gateway :4567
                                          │
                        shared trusted network namespace
                                          │
                                127.0.0.1:4566 → raw S3
```

Only LocalStack and the trusted gateway share that namespace. PostgreSQL/application containers stay on the default network; raw storage is on a separate `storage-private` bridge. Raw S3 binds **127.0.0.1 inside its own namespace**, with no host port mapping. The gateway binds :4567 and only that port is loopback-published. Backend signed S3 operations and presigned URLs retain the same external endpoint. Changing the raw port number is not the protection: the loopback-only bind rejects bridge connections even from an ordinary container deliberately attached to the storage bridge.

The pinned LocalStack 4.14 Community Twisted runtime ignores IPv4 `GATEWAY_LISTEN` interface addresses. The override explicitly selects its supported **Hypercorn** runtime, which honors `127.0.0.1:4566`. Disposable attacker tests enforce this distinction: a configuration that merely sets the address under Twisted fails the raw-access assertion.

The private bridge is separate, not Docker `internal: true`: on the tested Docker setup an internal network also prevented access to the loopback-published signed endpoint. Namespace loopback binding remains the enforced raw-storage boundary. No untrusted workload should share the LocalStack/gateway network namespace. Do not mount a Docker socket, use host networking, or grant privileged Docker access to untrusted applications.

`/_gateway/health` returns only a readiness status; it exposes no object bytes or credentials. It verifies the upstream S3 service and **completed, successful ready hooks**, so a partially restored checkpoint inventory cannot be declared ready. LocalStack uses the same upstream-readiness logic. The gateway has its own Python health command, replacing the inherited LocalStack CLI check that could never correctly assess a gateway process. Ready-hook failures remain unhealthy; the checks are not disabled.

LocalStack Community is an emulator, not a production IAM or durability boundary. Private ACL/PublicAccessBlock alone did not reject raw unsigned reads in this pinned version. The gateway verifies full SigV4 signatures, payloads and expiry; the network namespace prevents bypass of that gateway by ordinary containers. The documented test/test keys are local development credentials, not secrets providing production identity assurance. This design does **not** protect against Docker administrators, privileged containers, host-root access, Docker-socket access, or someone deliberately joining the trusted namespace. Production must use real private object storage and its own IAM/network controls.

On restart, existing verified Product Image checkpoints rehydrate ephemeral LocalStack objects; this is local storage recovery, not a crawler/source import. Keep the checkpoint directory and project volumes. Both health checks wait for that recovery to finish. Only generated authorized bytes are used in topology/upload/portable-seed tests.
