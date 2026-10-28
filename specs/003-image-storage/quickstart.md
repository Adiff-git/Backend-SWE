# Validation Guide: Image Storage

The media routes, Compose setup and environment profiles are implemented. Validation evidence and operational limitations are recorded below. See [HTTP contract](contracts/http.md), [configuration](contracts/configuration.md) and [consistency](contracts/consistency.md) for authoritative expectations.

## Prerequisites

Java 25, Docker Desktop/Compose, PowerShell and the repository Maven wrapper. The dependencies were approved on 2026-10-27 and pinned in demo/pom.xml; Compose image digests are pinned in compose.yaml. Use only dummy local storage credentials; set JWT_SECRET and JWT_ISSUER externally per existing authentication requirements, never use a production secret. Set CORS_ALLOWED_ORIGINS to the actual browser origin.

## Local setup

From repository root:

```powershell
docker compose up -d postgres localstack
docker compose ps
```

Bucket initialization is deferred by the user. Compose starts infrastructure only; docker/ is ignored and is not mounted. Configure a private bucket and its CORS later before attempting the upload flow. The future bucket CORS policy allows all origins/headers and GET/PUT/POST/DELETE/HEAD; this policy is separate from API CORS and does not make the bucket public. LocalStack must enforce presigned signatures; inspect effective configuration for S3_SKIP_SIGNATURE_VALIDATION=0. Optional persistent local data is not used by automated tests.

Before starting Compose, set LOCAL_DB_PASSWORD in an uncommitted environment or ignored .env file. Export the same value to the application process. Set JWT_SECRET (at least 32 UTF-8 bytes), JWT_ISSUER and CORS_ALLOWED_ORIGINS externally; Compose does not export its .env values into your shell. Optional LOCAL_DB_PORT/LOCAL_STORAGE_PORT select host ports; if the storage port changes, also set LOCAL_STORAGE_ENDPOINT and LOCAL_STORAGE_SIGNING_ENDPOINT to the matching browser URL. Use the local profile defaults for region, bucket and dummy storage credentials; then from `demo/`:

```powershell
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=local'
```

Expected: Flyway applies V5__image_uploads.sql and V6__user_profile_images.sql without losing existing users; startup validates schema/properties and all supported decoder providers are available.

## Browser flow

Authenticate with existing register/login endpoints and retain the returned Authorization header as a token in memory. Do not put tokens/signed URLs in shell histories, logs or committed fixtures. Example browser-console actions below assume `token` contains the Bearer value and `file` is a File from a file input; API calls use `http://localhost:8080`.

```javascript
const prepared = await fetch('http://localhost:8080/api/image-uploads', {
  method: 'POST', headers: { Authorization: token, 'Content-Type': 'application/json' },
  body: JSON.stringify({ contentType: file.type })
});
const upload = await prepared.json();
const put = await fetch(upload.uploadUrl, {
  method: 'PUT', headers: { 'Content-Type': file.type }, body: file
});
if (!put.ok) throw new Error('Storage upload failed');
const finished = await fetch(`http://localhost:8080/api/image-uploads/${upload.id}/complete`, {
  method: 'POST', headers: { Authorization: token }
});
const image = await finished.json();
```

Expected prepared=201, finished=200, same stable ID; use image.finalUrl as an image source without an application JWT. Repeat separately for actual JPEG, PNG and WebP. Complete again: same ID, fresh URL and no copy. Attach with existing PUT /api/me JSON `{"profileImageId":123}` and required profile permission; omit/null/replace to verify semantics. Read another user's limited profile with a second authenticated account: avatar viewing works but owner renewal/attachment of that image returns 404.

Do not log the response objects. Confirm the browser's signed hostname resolves and PUT is allowed by storage CORS. Confirm an authenticated account without MANAGE_USER_PROFILE can still create/complete/renew its own media but cannot edit its profile.

## Automated verification

From the repository root, after implementation and with Docker available (change into `demo/` before invoking Maven):

```powershell
cd demo
./mvnw.cmd verify
Get-ChildItem target/surefire-reports,target/failsafe-reports -Filter 'TEST-*.xml'
```

Verify actual discovered counts/classes include media service/validator/converter Test, MVC Test, PostgreSQL repository IT and LocalStack flow/consistency IT; a successful Maven exit alone does not prove discovery. Existing package conventions include domain testdata fixtures and `methodName_shouldExpectedBehavior_whenCondition`.

Mandatory cases: all supported real formats (including lossy/lossless/alpha WebP), corrupt/truncated/non-image/empty bytes, MIME mismatches, absent/foreign objects, changed source before read and before promotion, expired pending uploads with durable EXPIRED despite 409, completed use after original deadline, idempotence, fresh URL renewal, profile atomicity, rollback after copy and post-commit staging cleanup failure. Real LocalStack signed Content-Type and expiry checks must be enforced. Use controllable time for backend deadline boundaries; test provider expiry with an intentionally short test-only signed URL without changing application 900-second configuration.

Simultaneous-completion integration testing is deferred. Do not omit the object-replacement interleaving test or claim the deferred case passed.

Package with Maven verify and launch the packaged JAR using the local profile, then repeat WebP upload/completion to verify ImageIO provider discovery outside the test classpath. Expected packaged Java 25 runtime actually decodes WebP; an absent reader fails closed. No real AWS credentials/resources or developer database may be used by automated tests.

## Recovery validation

Inject a copy-success/database-failure path; expect rollback and possible unreferenced final object with staging retained. Inject post-commit delete failure; expect 200 and COMPLETED retained. Inject post-commit signing failure; expect 503 then successful idempotent retry. Follow the manual reconciliation section only with quiescent operations and rechecked references; never delete based solely on object age.


## Confirmed content identity clarification (2026-10-27)

Authoritative content-identity requirements and acceptance outcomes are defined in [spec.md FR-008 and Content Identity Decision](spec.md#content-identity-decision-2026-10-27). This document references that definition rather than restating it.


Equivalent command for a POSIX shell, from the repository root:

```sh
cd demo
./mvnw verify
```

After changing into demo, report paths in commands are relative to that directory (`target/surefire-reports/` and `target/failsafe-reports/`). Repository-relative file references elsewhere in the task list retain their `demo/` prefix.


## Runtime/tooling notes

On this Windows machine Docker Desktop is installed under the user profile and its CLI is not on PATH. The CLI was located at `C:/Users/TrinhThanh/AppData/Local/Programs/DockerDesktop/resources/bin/docker.exe`; either add its bin directory to your shell PATH or invoke that path. Testcontainers independently discovers the Docker named pipe.

The wrapper launch in this agent session fails with `Cannot index into a null array`. Validation used the exact configured Maven 3.9.16 distribution from `.m2/wrapper/dists`, with `-Dmaven.repo.local=C:/Users/TrinhThanh/.m2/repository`, while working in `demo/`. The initial sandboxed cache access also failed; normal filesystem access was required. No wrapper or global Maven/user configuration was changed.

The executable-JAR acceptance test `PackagedImageFlowIT` starts a separate Java 25 process against isolated PostgreSQL/LocalStack, performs raw HTTP upload/completion/view for JPEG/PNG/WebP and checks downloaded bytes. This exercises packaged ImageIO discovery, not just a test-classpath reader. `StorageCorsIT` covers explicit browser-origin preflights. The automated browser contract uses HTTP clients; no separate frontend UI was introduced.

## Manual reconciliation runbook

Use this runbook only as an authorized storage/database operator. It is not an application deletion endpoint or background job.

1. Pause application writes/completions using the deployment's existing maintenance procedure; wait for all in-flight requests and transactions to finish. Record the bucket/environment being inspected. Never infer quiescence from object age alone.
2. Inventory staging/final keys with `awslocal s3api list-objects-v2 --bucket backend-images` locally, or the equivalent authenticated AWS CLI against the configured production bucket. Do not generate or log presigned URLs for inventory.
3. Read the authoritative database references, for example:

```sql
SELECT i.id, i.status, i.staging_key, i.final_key, i.expires_at,
       COUNT(u.id) AS profile_references
FROM image_uploads i
LEFT JOIN users u ON u.profile_image_id = i.id
GROUP BY i.id, i.status, i.staging_key, i.final_key, i.expires_at;
```

4. An abandoned staging key is a candidate only after its PUT capability deadline, with no active completion. Time-expired PENDING rows may still be stored as PENDING because no scheduler is included. A final key is a candidate only if it is absent from every committed final reference and from every consuming reference. Preserve COMPLETED final keys even if currently unattached, because standalone access/reuse is supported.
5. Re-read database lifecycle/references immediately before each explicit operator deletion, while quiescence remains in force. Preserve every uncertain match/commit. Use the normal provider delete-object command only for the individually reviewed key; never delete a prefix or bucket wholesale. A failed copy or rollback does not itself authorize deletion of a possibly committed object.
6. Record sanitized counts/categories of cleanup failures, retry failed deletions manually, and restore normal traffic only after the inspection window closes. Removing an avatar never triggers this procedure automatically.

## Production access requirements

Use a private bucket with public access blocked. Application IAM requires GetObject for validation/view signing, PutObject for staging capabilities and final copy, and DeleteObject only on the staging prefix. Copy requires source read and destination write permissions; there is no separate CopyObject IAM action. Operator inventory/cleanup and bucket/CORS administration use separately authorized operator permissions. Keep production credentials external and let the SDK default chain use the deployment role. No production resources were provisioned or used in verification.

## Implementation validation record ? 2026-10-27

- Java 25.0.4.1; Maven 3.9.16; Docker Desktop daemon discovered through its named pipe.
- Actual JPEG/PNG and lossy/lossless/alpha WebP fixtures decode successfully. Corrupt/truncated/non-image/MIME mismatch cases reject publication.
- PostgreSQL migration/constraint, stale-version, durable-expiration, copy/rollback, post-commit cleanup/signing recovery, and different/identical-content/metadata-only replacement scenarios have integration coverage.
- Avatar preserve/clear/replace, foreign viewing versus attachment/renewal, profile permission retention and rollback on invalid selection/signing failure passed JWT/API integration.
- LocalStack enforces wrong signed Content-Type and expired capability rejection with S3_SKIP_SIGNATURE_VALIDATION=0; browser CORS is separately validated.
- Compose + executable JAR was exercised in an isolated project on ports 25432/24566/28080 with temporary local credentials. Raw PUT/completion/view passed for all three formats; the test project's containers, network and volumes were removed afterwards. Developer databases/services were not used.
- Simultaneous-completion integration testing remains deferred. Required optimistic version checks and distinct attempt keys remain implemented. No live AWS verification or frontend UI test is claimed.
- Final test counts and discovery evidence are recorded after the final Maven run below. Static-analysis tools absent from the existing build were not added or represented as passing.


### Final verification counts

| Evidence | Result |
| --- | --- |
| Default Maven verify | 140 unit/MVC/context tests + 117 integration tests; passed |
| Added exact-expiry and copy/deadline-boundary cases | Focused rerun passed; latest configuration class 5 tests and consistency class 8 tests |
| Latest per-class Surefire XML | 43 classes, 141 tests, 0 failures/errors/skips |
| Latest per-class Failsafe XML | 34 classes, 119 tests, 0 failures/errors/skips |
| Executable JAR flow | Actual separate JVM, JPEG/PNG/WebP upload/complete/view passed |
| Compose local flow | Separate project/ports/credentials; all three formats passed; resources removed |
| Optional native persistence | FAILED; unresolved T044/T046 acceptance, not an automated-test pass |

### Local persistence limitation requiring a decision

Default LOCALSTACK_PERSISTENCE=0 is disposable local storage. Do not assume that setting it to 1 makes the pinned community image durable: two isolated restart checks lost the test object, including ON_REQUEST/ON_STARTUP and a flush-interval wait. The snapshot endpoint also returned 404. A named volume is necessary storage wiring but is not proof that the emulator supports state restoration. Application/database state must not be represented as restart-safe under this configuration.

The [official persistence instructions](https://docs.localstack.cloud/aws/developer-tools/snapshots/persistence/) show a Pro image and an external LOCALSTACK_AUTH_TOKEN. The alternative localstack/localstack-pro:4.14.0 manifest was checked; exact manifest-list digest is sha256:5f1fb6c73ccf053e3748d57d5c43b9cd3dba109bb2ae5b7096d2cf5155082a6c. This different image has not been approved, installed or verified. Set any approved token only through your local environment/secret store, never in chat or committed configuration. Keep T044/T046 open until persistence passes with an approved supported configuration or the user explicitly defers the requirement. No paid plan/account was created.


## Latest user decision ? CORS and repository packaging (2026-10-27)

API CORS allows all origins, methods and request headers, with Authorization exposed and credentials cookies disabled. JWT authentication and domain authorization remain required. CORS_ALLOWED_ORIGINS is no longer consumed by the application. This supersedes prior explicit-origin descriptions in the design/history. Storage CORS remains a separate manual bucket configuration; bucket initialization is deferred. docker/ and demo/src/test/resources/media/README.md are ignored and are not required by the checked-in Compose file. The persistence limitation above remains documented; it was not silently declared fixed.

Latest CORS change validation: unit/MVC regression and AuthCorsApiIT/StorageCorsIT passed via Maven verify; Compose config validation passed without the ignored docker directory. No bucket initializer was run or committed for this deferred setup.
