# Standalone V27 Auditor — T008 Operations

**Implemented OFFLINE in Controlled Batch 2. No target audit was executed.** Real PostgreSQL17/V27 behavior, SELECT-only privileges, unchanged fingerprints/history, shared projection parity, and both SQL preflight executions remain **T086 NOT RUN**. T009/T010 and G1 do not close from compiling this CLI or passing its isolated tests.

## Entry point and classpath

`com.example.demo.catalog.audit.CatalogCompatibilityAudit` is a plain Java main. It never invokes SpringApplication, an ApplicationContext, Hibernate/JPA, Flyway, repositories, scheduling or application configuration. Only source migration resource bytes are read to validate the explicitly supplied manifest; no migration library/API is called. Keep normal Flyway resources present: their presence does not execute them.

From `Backend-SWE/demo`, after authorized compilation/test verification:

```bash
bash ./mvnw -o compile
bash ./mvnw -o dependency:build-classpath -DincludeScope=runtime \
  -Dmdep.outputFile=target/catalog-audit-runtime.cp -Dmdep.regenerateFile=true
AUDIT_CLASSPATH="target/classes:$(cat target/catalog-audit-runtime.cp)"
java -cp "$AUDIT_CLASSPATH" com.example.demo.catalog.audit.CatalogCompatibilityAudit --help
```

The already cached Maven dependency plugin3.10.0 resolves the existing runtime dependency graph; no dependency or pom/plugin configuration is added. `includeScope=runtime` is the plugin's correct property (not `mdep.includeScope`); the resulting graph includes the existing PostgreSQL driver and AWS URL-connection client but excludes test-scoped JUnit/Testcontainers. This classpath procedure and `--help` were verified without database/storage connections. It is a normal classes/jars classpath, not an executable Spring Boot JAR.

Do not run the following audit invocation until the T086 safety gate passes and an operator explicitly supplies an authorized identified restored copy and read-only storage access under T009:

```bash
# Set private environment values first; do not echo them or enable shell tracing.
java -cp "$AUDIT_CLASSPATH" com.example.demo.catalog.audit.CatalogCompatibilityAudit
```

No other argument/mode is accepted. `--repair`, `--migrate`, output paths and SQL arguments fail before connecting. Standard output is JSON Lines; capture it privately with restrictive file permissions. No file output or target mutation mode exists. Never include credentials in command arguments, logs or evidence artifacts.

## Required private operator configuration

| Environment variable | Contract |
| --- | --- |
| AUDIT_JDBC_URL | Plain `jdbc:postgresql://host:port/database`; no embedded credentials, URL query/options, fragments, alternate driver, jdbc:tc, multi-host or application startup. |
| AUDIT_DB_USER / AUDIT_DB_PASSWORD | Explicit least-privilege SELECT-only account, never a superuser; no Flyway/DDL/DML privileges. Transaction READ ONLY is additional enforcement, not a replacement for permissions. |
| AUDIT_EXPECTED_DATABASE | Exact expected CURRENT_DATABASE(), checked against the actual server. |
| AUDIT_EXPECTED_SCHEMA | Must be `public`, the approved V27 schema. No arbitrary schema fallback. |
| AUDIT_EXPECTED_SERVER_ADDRESS / AUDIT_EXPECTED_SERVER_PORT | Exact INET_SERVER_ADDR()/INET_SERVER_PORT() values for the identified copy, not an inferred production target. NAT/proxy/container host ports can differ: obtain server identity out of band, never silently substitute it after a mismatch. |
| AUDIT_MANIFEST | Private path to the operator-reviewed V1–V27 TSV manifest described below. |
| AUDIT_SSL_MODE | Optional: `verify-full` default; `require` or `disable` only when explicitly selected by the operator for the authorized environment. Use verified TLS in deployed environments and separately managed driver trust configuration. |
| AUDIT_MAX_RECORDS | Optional1–10000000, default1000000, per streamed relation/variant/image phase. Exceeding the operational limit is INCOMPLETE, never a partial clean audit. |
| AUDIT_MAX_SECONDS | Optional1–3600, default600. Checked at phase/row boundaries. An in-flight operation additionally has its own bounded timeout; this is not a hard process kill timer. |
| AUDIT_S3_BUCKET / AUDIT_S3_REGION | Required when images are encountered. Explicit read-only authorized bucket context, not application profile configuration. Empty galleries do not initialize an S3 client. |
| AUDIT_S3_ENDPOINT | Optional explicit HTTP(S) endpoint without credentials/query/fragment, path-style access for authorized local/test storage. Otherwise standard regional AWS S3. No service is started. |

S3 credentials use the existing AWS default credential provider chain (standard environment/profile/role mechanisms), with an independently restricted HEAD/GetObject policy. No copy/PutObject/delete/checkpoint privilege is needed. Network access itself must be authorized; this Batch2 invocation only used mocks and local image bytes, not AWS/LocalStack.

Manifest: exactly27 lines ordered1..27, UTF-8, no header; each line is `version<TAB>signed-Flyway-CRC32<TAB>script-filename`. For example the baseline V1 entry is `1<TAB>1465354838<TAB>V1__create_catalog_core.sql`. Use real tab characters. Obtain all entries from the reviewed T001 migration inventory/current approved resources; this example is not a complete manifest. Maximum manifest size64KiB. Each checksum is rechecked against the packaged V27 resource using Flyway-compatible line-wise UTF-8 CRC32 (BOM/newlines excluded). The live history must contain exactly successful SQL V1–V27, matching script/checksum/order. Missing, failed, repeatable, baseline/repair or future entries fail closed; no repair is attempted. This check is intentionally conservative.

Source manifests do not prove live applied history or unmodified schema objects. T003 still inspects actual schema, constraints, indexes, functions, history and target provenance. The CLI probes required V27 columns with SELECT LIMIT0; missing columns/permissions/functions produce INCOMPLETE. It reads no future version/enabled/active/restore/taxonomy-gate column. The server must identify as PostgreSQL17, and current role must report non-superuser. Other baselines require separate review.

## Read boundary and data checks

Before data reads: JDBC readOnly=true, REPEATABLE_READ, autocommit=false, explicit PostgreSQL `SET TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY`; then transaction-local statement60s/lock2s timeouts, fixed public search path, UTC/ISO timestamp formatting. Verify server read-only/isolation/identity before history/catalog queries. JDBC connect/login timeouts10s, socket timeout65s. Every statement has query timeout60s and cursor fetch size100. There is no commit; rollback runs on success, invalid data and failure. A rollback/close failure means INCOMPLETE.

The same T081 registry CTE supplies globally unambiguous named-family/ancestry context in the JDBC query. PersistedSpecificationValidator delegates generic shape checks and applies the existing four registered type/exact-unit rules only where applicable. Empty optional facts and false/zero remain valid; nonapplicable/unknown canonical facts stay generic. Missing/null/malformed persisted document/envelope is invalid; **missing individual optional facts is not invalid**. No universal mandatory fact, conversion or duplicate Java tree resolver is added. SQL preflight remains responsible for SKU/options/price/lifecycle/relationship/uniqueness checks; this CLI's COMPLETE is only its named scope, not global catalog readiness.

Specification text and image metadata have1MiB operational transfer/reader bounds. Exceeding a bound, including parser nesting/number/string limits, yields INCOMPLETE, not a new business validation rule. Results/findings stream immediately; no full catalog/finding list is retained. A very large valid dataset must be processed with a reviewed adequate budget, never a silent LIMIT/truncated PASS.

Images reuse ObjectStorageServiceImpl.inspect/readBoundedIfMatch and ProductImageContentValidator. Only HEAD/If-Match bounded GET are called. The adapter deliberately supplies no unused signer/checkpoint dependency and exposes only inspection, so upload/copy/checkpoint/delete is not invoked or initialized. Existing bounded reads use temporary local files, which are closed/deleted; this is local scratch, not an object/database write. Metadata, ETag, actual type/bytes/dimensions and SHA-256 are checked; legacy null source_image_id does not imply missing ownership. Stable missing objects or invalid content produce findings; changed GET/ETag, access/timeouts and uncertain content produce INCOMPLETE. No storage rollback or physical deletion is inferred.

Catalog/source/order/recipient/item/receipt/allocation row fingerprints are streamed without payloads/keys. They are comparison aids, not cryptographic provenance or proof of historical immutability. Counts and object SHA-256 accompany the report. T009 must retain exact target/copy provenance and compare controlled evidence; both SQL scripts and the typed/object audit are separate mandatory gate evidence.

## Output and exit contract

Each JSON line has runId. Types: FINDING (fixed code, entity and identity only), FINGERPRINT (relation/identity/MD5), OBJECT_FINGERPRINT (identity/SHA-256), COUNT, and final SUMMARY. No raw fact, name/recipient/address/receipt payload, object key, bucket, password, token, JDBC URL or exception/SQL text is printed. Findings distinguish missing specification document, invalid specifications and invalid image metadata/content. Infrastructure errors use fixed safe failure codes.

| Exit | Final status | Interpretation |
| --- | --- | --- |
|0 | COMPLETE | This typed-fact/object/fingerprint scope completed with zero findings and successful rollback/connection close. **Not G1 PASS.** |
|2 | INVALID | All phases completed but data findings exist; retain identities, stop and request a separate remediation decision. |
|3 | INCOMPLETE | Configuration/identity/history/schema/permission/connection/storage/output failure, timeout/limit or failed rollback. Partial findings/fingerprints are not a successful audit. |

Require process exit code AND final SUMMARY for the same runId. Missing/malformed/truncated output, broken pipe, interruption or killed JVM is INCOMPLETE. Never combine unrelated snapshots/runs or relabel zero observed findings before a failure as clean. Connection/driver/SDK exception messages are suppressed at the CLI boundary. An operator must protect report files and private configuration.

## Deferred real proof

T086 must still execute the auditor on disposable PostgreSQL17 pinned to V27 with SELECT-only privileges, validate actual identity/CRC/JSONB mapping/cursor/read-only enforcement, absence of startup/migration/write effects, unchanged schemas/history/rows, shared applicability parity, each SQL preflight violation and legitimate retained history. Storage integration must prove actual conditional bounded reads and IAM/object behavior. Mocks prove only orchestration and failure contracts. No target database, audit, migration, remediation or production security verification was executed in Batch2.
