# Storage Configuration Contract

Proposed property names are implementation targets, not existing settings. Validate required values at startup; no active profile hardcoded in shared properties.

| Property | Local/test | Production |
| --- | --- | --- |
| storage.s3.region | us-east-1 | Explicit configured AWS region |
| storage.s3.bucket | backend-images | Explicit private bucket |
| storage.s3.endpoint | http://localhost:4566 for host backend; http://localstack:4566 inside Compose | Omitted for normal AWS endpoint |
| storage.s3.signing-endpoint | Browser-reachable http://localhost:4566; defaults to service endpoint if omitted | Omitted for standard AWS endpoint |
| storage.s3.path-style-access | true | false unless deployment explicitly requires otherwise |
| storage.s3.local-access-key / local-secret-key | Dummy test/test only, local profile | Must be absent; default SDK provider chain/IAM role |
| storage.s3.upload-url-lifetime | 900s | 900s |
| storage.s3.download-url-lifetime | 300s | 300s |
| storage.s3.api-call-timeout | Proposed 60s | Explicit finite operational setting, initially 60s |
| storage.s3.api-call-attempt-timeout | Proposed 30s | Explicit finite operational setting, initially 30s |

Lifetime values implement confirmed Q2; changes alter the approved expiry contract and must be reviewed. Network timeouts bound operation duration, not an image-size policy. Disable automatic SDK retries for this first synchronous workflow so ambiguous copy failures remain explicit client retries; no automatic database retry. Use singleton clients/presigner with shutdown close and close every response stream. Approve and pin exact dependency/image versions before adding them.

Local Compose must include health-checked PostgreSQL and LocalStack S3, optional named persistence volume, and a documented bucket/CORS bootstrap under docker/localstack/init/. Set `S3_SKIP_SIGNATURE_VALIDATION=0` and verify incorrect presigned headers/expired URLs are rejected with the selected LocalStack version. Testcontainers supplies isolated storage/buckets and must not connect to developer Compose or AWS.

Bucket CORS allows explicit local frontend origins (example http://localhost:3000), PUT and GET, and required Content-Type. HEAD is backend-only and needs no browser CORS unless a later browser flow requires it. Keep backend API CORS separate, permitting existing Authorization/Content-Type; no wildcard production origins or credentials cookies. S3 requests never need application JWTs. Never rewrite signed URL hosts; use a distinct presigner endpoint where topology requires it.

Production keeps bucket private with public access blocked and server credentials authorized for only required object read/write/copy/delete operations in staging/final prefixes. Default provider chain supports IAM roles; no production keys in repository/env examples. Do not give clients final-prefix PUT capabilities. No production provisioning or AWS test resources are created during planning.


### Latest user override ? 2026-10-27

Allow-all API CORS (origins/methods/headers), retaining Bearer JWT authorization and allowCredentials=false, supersedes earlier explicit-origin restrictions. Bucket initialization is deferred; Compose must not reference ignored docker/ files or require an initialized bucket for health. docker/ and the fixture media README are excluded from Git. Future bucket CORS allows all origins/headers and all S3-supported methods while the bucket remains private. See research.md for the decision and fixture attribution. Existing persistence work remains deferred/open.
