# Research: Image Storage

**Date**: 2026-10-27. Sources are primary documentation checked during planning. Decisions describe the intended implementation; no runtime compatibility test was performed.

## R1 — S3 integration and identity protection

**Decision**: Use synchronous AWS SDK Java v2 S3Client and S3Presigner behind one storage service. HEAD staging, conditionally GET using its opaque ETag, validate returned Content-Type and decoded content, then CopyObject with `copySourceIfMatch` using that ETag. Replace final metadata explicitly. Fully consume the SDK copy result before treating publication as successful.

**Rationale**: Source conditions protect the validated bytes against ordinary replacement between validation and promotion. A destination condition is not a source condition. ETag is not a locally calculated MD5 and does not prove metadata identity; validate metadata from the conditional GET and never inherit later client metadata. A metadata-only change need not change ETag, but cannot alter the approved final metadata. This uses the ETag safeguard explicitly permitted by the specification; do not claim cryptographic uniqueness or immutable object versions.

**Alternatives considered**: Unconditional copy permits a replacement race. Mandatory versioning adds bucket policy/lifecycle complexity not required here. Copying locally downloaded bytes back would change the specified copy flow.

Sources: [Conditional reads](https://docs.aws.amazon.com/AmazonS3/latest/userguide/conditional-reads.html), [CopyObject conditions and limits](https://docs.aws.amazon.com/AmazonS3/latest/API/API_CopyObject.html), [ETag semantics](https://docs.aws.amazon.com/AmazonS3/latest/API/API_Object.html). Single-call copy has an S3 service limit of 5 GB; this is not a newly imposed application image-size cap. Do not promise arbitrary-size service support or silently add multipart publication infrastructure; report an actual service-limit failure safely.

## R2 — Concurrency and external consistency

**Decision**: Add a technical `@Version` column. Each attempt copies to its own unpredictable final key, then flushes the version-checked state change. Optimistic conflicts become 409 without automatic retry. Return a typed EXPIRED outcome from the transactional command, then throw the domain error only after its commit. Keep the public completion facade nontransactional, following the existing auth facade/outcome pattern.

**Rationale**: Competing or rolled-back copies cannot overwrite the winner's image. No pessimistic media lock spans decoding or network operations. Expiration cannot be lost to the exception that reports it. A typed completion result is ordinary branching, not an exception for control flow.

**Alternatives considered**: Pessimistic locking across storage calls unnecessarily serializes expensive work. A new PROCESSING state and leases require additional recovery policy. REQUIRES_NEW solely to preserve expiry is unnecessary and changes approved propagation defaults.

Source: [Spring programmatic transactions](https://docs.spring.io/spring-framework/reference/data-access/transaction/programmatic.html). Repository evidence: existing `auth/service/AuthOutcome.java` and `auth/service/impl/AuthServiceImpl.java`. Choose a separate proxied MediaCommandService with REQUIRED transactions, rather than needing a new transaction framework.

## R3 — Post-commit cleanup and retry

**Decision**: Register synchronous after-commit staging cleanup with caught/sanitized failures; response signing occurs after successful completion commit. No event bus, async worker or scheduled media cleanup. Operator reconciliation handles leftovers and possible orphans.

**Rationale**: Cleanup cannot roll back published state, and signing failure can be safely retried against COMPLETED without another copy. A transaction synchronization callback is sufficient; transaction-bound events are an available alternative, not another required layer.

**Alternatives considered**: Deleting staging before commit destroys retry content after rollback. Automatic compensation after uncertain commit risks deleting valid images. An outbox/background worker exceeds approved scope.

Sources: [Spring transaction-bound phases](https://docs.spring.io/spring-framework/reference/data-access/transaction/event.html), [AWS resource/timeouts guidance](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/best-practices.html).

## R4 — Real WebP decoding

**Decision**: Propose TwelveMonkeys `com.twelvemonkeys.imageio:imageio-webp:3.15.0` alongside JDK JPEG/PNG ImageIO. Discover readers from actual bytes, canonicalize reported format and decode actual pixels. Fail closed for missing provider, unsupported format, null result, decoding errors or corruption warnings indicating incomplete data. Close streams and dispose readers.

**Rationale**: ImageIO SPI avoids a native platform dependency. The upstream Java baseline does not prove Java 25 or executable-JAR compatibility; these require fixture and packaged-runtime smoke tests after dependency approval. Use temporary files for downloaded bytes rather than loading the entire object into one byte array; decoded pixel memory still needs operational consideration, without inventing a size cap.

**Alternatives considered**: JDK-only ImageIO does not establish WebP support. Sejda's native WebP integration has platform deployment considerations. MIME/magic-byte checks alone do not validate an image.

Sources: [TwelveMonkeys project and version](https://github.com/haraldk/TwelveMonkeys/blob/master/README.md), [Java 25 ImageIO](https://docs.oracle.com/en/java/javase/25/docs/api/java.desktop/javax/imageio/ImageIO.html), [native alternative](https://github.com/sejda-pdf/webp-imageio). Include actual lossy/lossless/alpha WebP, JPEG/PNG and corrupt/truncated fixtures; do not silently add a static-only image policy or claim rejection of every possible malformed suffix.

## R5 — LocalStack parity and signed hosts

**Decision**: Separate optional service endpoint and optional signing endpoint, using identical region/credentials/path-style settings. Host-run backend may use localhost for both; container backend uses internal LocalStack for operations and browser-reachable localhost for signing. Configure `S3_SKIP_SIGNATURE_VALIDATION=0` in local/test LocalStack so signature/expiration tests are meaningful.

**Rationale**: Presigning is local, so a browser host need not resolve within the backend container. Host rewriting invalidates signatures. LocalStack can otherwise skip presigned checks and conceal broken header/expiration behavior. Verify selected pinned image actually honors these options.

**Alternatives considered**: A single externally resolvable endpoint works when topology permits; changing signed URLs afterwards does not. Public buckets and permissive backend CORS do not solve storage CORS.

Sources: [LocalStack S3](https://docs.localstack.cloud/aws/services/s3/), [SDK endpoint configuration](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/endpoint-config.html), [presigner/browser compatibility](https://docs.aws.amazon.com/java/api/latest/software/amazon/awssdk/services/s3/presigner/S3Presigner.html), [default credentials](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/credentials-chain.html).

## R6 — Repository adaptation and approval boundary

**Decision**: Store scalar ownerUserId with database FK and scalar profileImageId with FK; consumers call MediaService for state/ownership/access rather than dereferencing media repositories. Preserve existing profile lock, strict field handling, ApiError and header-only JWT transport. New dependencies need approval before edits; exact pinned SDK/container versions belong in that concrete dependency selection, not an unsupported assertion of tested compatibility.

**Rationale**: Scalar foreign keys avoid cycles, lazy loading in converters and cross-domain repository access. No new media permission or role seeding is needed. Research is complete; dependency approval and runtime tests are implementation gates, not unresolved feature decisions.

**Alternatives considered**: Back-references for every consumer entangle independent aggregates. Reusing profile permission contradicts the confirmed Q1 decision. Altering all profile coordination or security is unrelated scope.


## Confirmed content identity clarification (2026-10-27)

Authoritative content-identity requirements and acceptance outcomes are defined in [spec.md FR-008 and Content Identity Decision](spec.md#content-identity-decision-2026-10-27). This document references that definition rather than restating it.


## T001 — Concrete Dependency Proposal (2026-10-27)

**Status**: Approved by the user on 2026-10-27; dependencies installed. The proposal below records the approved selection.

| Artifact/image | Proposed version | Scope and reason |
| --- | --- | --- |
| software.amazon.awssdk:bom | 2.57.2 | Import in dependencyManagement to align SDK modules; this exact BOM is already available in the local Maven cache |
| software.amazon.awssdk:s3 | 2.57.2 via BOM | Production synchronous S3 operations and S3Presigner |
| software.amazon.awssdk:url-connection-client | 2.57.2 via BOM | Explicit synchronous JDK HTTP transport; avoid adding a native/async transport requirement |
| com.twelvemonkeys.imageio:imageio-webp | 3.15.0 | Production ImageIO WebP decoding; JPEG/PNG remain JDK readers |
| org.testcontainers:testcontainers-localstack | 2.0.5 via existing Spring Boot BOM | Test-only LocalStack support, aligned with existing PostgreSQL Testcontainers; no Testcontainers major-version override |
| localstack/localstack | 4.14.0 | Pinned community-era image candidate for S3 development and isolated integration tests; no floating latest tag |
| postgres | 17-alpine (existing test convention) | Preserve existing PostgreSQL test image convention; select an immutable digest before adding Compose configuration |

Boot 4.1.2's locally cached dependency BOM declares testcontainers.version=2.0.5. Current project already uses testcontainers-postgresql under that management. SDK BOM 2.57.2 is locally cached and has an official release; this is a concrete pinned candidate, not a claim that it is the latest or has passed compatibility/security review. Preserve Boot dependency management for shared transitive libraries and inspect the resolved dependency tree before accepting convergence. Exclude unwanted default HTTP transports if necessary when selecting UrlConnectionHttpClient explicitly; list any resulting artifact additions in the POM review.

Sources: [AWS SDK 2.57.2 release](https://github.com/aws/aws-sdk-java-v2/releases/tag/2.57.2), [TwelveMonkeys dependency documentation](https://github.com/haraldk/TwelveMonkeys/blob/master/README.md), [Testcontainers LocalStack module](https://java.testcontainers.org/modules/localstack/), [LocalStack 4.14.0 release](https://github.com/localstack/localstack/releases/tag/v4.14.0). The LocalStack repository is archived; this historical pin is chosen to avoid silently introducing newer distribution/account requirements, not as a guarantee of maintenance. Confirm image availability and signature-enforcement behavior before approving runtime results; if this candidate cannot satisfy the contract, bring a replacement image proposal rather than silently changing storage requirements.

No new build plugin is proposed. Retain existing Surefire/Failsafe and Spring Boot packaging configuration; adjust discovery only if actual execution shows a feature-related need.

### Compatibility and environment evidence

- Java reports 25.0.4.1 on this machine.
- Docker command is not found in the current PATH. Docker-backed verification cannot currently run; this is not evidence that Docker is absent from every installed location. Recheck installation/runtime availability before T003/T010 integration execution.
- No SDK/decoder dependency has been installed or executed in this step; Java 25 reader discovery, executable-JAR packaging, SDK convergence and LocalStack source-condition/signature behavior remain required verification after approval.
- Existing .gitignore already covers target/class/jar/log/local environment files. No .dockerignore was found; create it during approved project setup before introducing Docker build context files, preserving existing ignore edits. No Docker files or dependencies were introduced in this proposal step.
- Approval request covers the exact SDK modules, WebP reader, test-only LocalStack module and pinned LocalStack image above. Approval does not establish test success or authorize deployment.

### Approval and dependency entry check

The user approved the concrete T001 proposal on 2026-10-27. Added the approved AWS SDK BOM/S3/URL-connection transport, WebP reader and Boot-managed LocalStack test module to demo/pom.xml. Excluded default Apache/Netty S3 transports; no new plugin introduced. Maven 3.9.16 clean test-compile succeeded on Java 25 after explicitly selecting the existing local repository and using the required filesystem access. Wrapper launch in this session fails with Cannot index into a null array; the configured Maven 3.9.16 distribution was invoked directly as a diagnostic workaround. This proves dependency compile compatibility only, not decoded-format or integration acceptance.


Final environment correction: Docker CLI was absent from PATH, but Docker Desktop was available through its named pipe. PostgreSQL/LocalStack integration and isolated Compose verification succeeded. Inspected image digests were pinned in compose.yaml. Upload presigning returns its exact signed expiry; the creation timestamp is the signed start instant at second precision, preventing a subsecond lifetime truncation mismatch. No production resources were accessed.


## Native persistence acceptance failure (2026-10-27)

Observed twice with approved community image digest 3ebc37595918b8accb852f8048fef2aff047d465167edd655528065b07bc364a: a stored S3 object was absent after restart despite PERSISTENCE=1 and the named volume. The second run also used ON_REQUEST/ON_STARTUP and waited 20 seconds before restart; POST /_localstack/state/s3/save returned 404. All isolated verification resources were removed. This is a real unresolved local persistence requirement, not evidence of an application upload defect.

[Official persistence documentation](https://docs.localstack.cloud/aws/developer-tools/snapshots/persistence/) uses a Pro image and LOCALSTACK_AUTH_TOKEN. Read-only registry inspection confirmed alternative localstack/localstack-pro:4.14.0@sha256:5f1fb6c73ccf053e3748d57d5c43b9cd3dba109bb2ae5b7096d2cf5155082a6c. Do not switch images, assume an entitlement or introduce backup infrastructure without the user's decision. Core application tests pass, but T044/T046 remain open.


## User-directed CORS and packaging change (2026-10-27)

The user explicitly requested allow-all CORS, deferred bucket initialization and exclusion of docker/ plus the fixture media README from Git. API CORS now allows every origin, method and request header, exposes Authorization and retains allowCredentials=false and all JWT/authorization checks. This latest instruction overrides earlier explicit-origin requirements for this change; it does not make buckets public. The future bucket CORS example allows all origins/headers and all five S3-supported methods. Bucket configuration remains separate and is not automatically applied.

Compose no longer mounts docker/ or checks bucket existence; it checks LocalStack HTTP health. A fresh checkout therefore has no dependency on ignored files, and no bucket is automatically created. Bucket initialization and its persistence verification remain deferred; upload requests require the bucket to be configured later. The root README had pre-existing unrelated edits and is intentionally excluded from this commit.

Fixture attribution (the media README is ignored at the user's request): JPEG/WebP samples are public Google WebP gallery assets, Creative Commons Attribution 3.0; sources https://www.gstatic.com/webp/gallery/1.jpg, https://www.gstatic.com/webp/gallery/1.webp, https://www.gstatic.com/webp/gallery3/1_webp_ll.webp and https://www.gstatic.com/webp/gallery3/1_webp_a.webp. See https://developers.google.com/speed/webp/gallery and https://developers.google.com/speed/webp/gallery2. PNG was generated; invalid fixtures are text/truncated derivatives for validation tests.
