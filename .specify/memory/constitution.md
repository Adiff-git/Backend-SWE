<!--
Sync Impact Report (temporary review material; remove before committing)
Version: 1.1.0 -> 1.2.0 (MINOR: materially expanded verification guidance).
Modified principles:
- XIII. Meaningful Testing (title unchanged): integrated risk-based execution policy.
- Technology and Repository Baseline: clarified risk-triggered concurrency verification
  versus requiring concurrency infrastructure for every feature.
- Development Workflow and Quality Gates: linked feature plans/tasks to Principle XIII.
Added subsection: Risk-Based Verification and Regression Testing Policy (within XIII).
Removed sections: none. Existing test categories, organization and integrity rules retained.
Conflicts resolved:
- A full gate is not required after every isolated fix; reruns depend on regression risk.
- Documentation-only governance work retains its existing no-application-test exemption.
- Environmental inability is recorded as blocked/unverified, never a passing full gate.
Read-only feature review / follow-up (no feature files changed):
- specs/011-voucher-support/plan.md and tasks.md: add explicit risk/scope mapping,
  checkpoint selection, isolated-fix rerun criteria and environmental reporting;
  retain T058's unrestricted full gate and all affected security/concurrency checks.
- specs/010-inventory-management/plan.md and tasks.md: on future active revision,
  reference XIII; T069 already limits broad reruns to justified changes/uncertainty.
- specs/008-customer-order-management/tasks.md T047 and
  specs/009-customer-checkout/tasks.md T058 already distinguish focused reruns;
  no retrospective rewriting of completed evidence is required.
- specs/005-catalog-data-enhancement/tasks.md T009/T016: if reused, qualify repeated
  complete Maven/Python runs by regression risk; preserve historical execution records.
- specs/007-shopping-cart/plan.md: on future active revision, add targeted/checkpoint
  scope and conditional rerun rules to its full-verify instruction.
Templates/commands: read Constitution at runtime; no source changes in this amendment.
Deferred metadata: original ratification remains unverified; preserved date is not
proof of adoption. No new unresolved policy placeholders.
-->

# Backend-SWE Java Backend Constitution

## Core Principles

MUST and MUST NOT are mandatory. SHOULD denotes a preference; departures require a
recorded rationale. MAY denotes permission. These rules govern development and do not
assert that the current implementation satisfies them.

### I. Domain-Oriented Modular Monolith

The backend MUST use a domain-oriented modular monolith with layers inside each domain
under `com.example.demo`: `controller`, `service`, `repository`, `dto`, and `entity`,
plus applicable converters and validators. Controllers MUST handle HTTP concerns,
activate request validation, and delegate business operations to services. Each business
service MUST define an interface and corresponding `ServiceImpl`.

Services MUST access only their own domain's repositories. Cross-domain business operations
MUST use the owning domain's service interface. Domain dependencies MUST be acyclic.
Services MUST orchestrate business workflows, transactions, and applicable domain operations.
Repositories MUST focus on persistence and data access; they MAY enforce persistence-level
invariants through database constraints, locking, and atomic conditional updates.
Controllers, repositories, and converters MUST NOT orchestrate business workflows.
Converters MUST remain responsible for DTO mapping; domain validators MUST remain responsible
for applicable business validation. Responsibilities SHOULD remain clear without unnecessary
abstractions. Microservices, BFF architecture,
additional application frameworks, and unrelated architecture changes require explicit
requirements. Domain boundaries keep business ownership explicit.

### II. Naming and Java Conventions

Service retrieval methods MUST distinguish results: `getProductEntity(id)` returns one
entity, `getProductEntities(ids)` an entity collection, and `getProduct(id)` a DTO.
Equivalent naming MUST be consistent across domains. This rule does not automatically
apply to repository queries, framework callbacks, converter methods, or mutations.

Wildcard imports MUST NOT be used. Explicit types SHOULD be preferred over `var`.
Constructor injection SHOULD be preferred, using Lombok `@RequiredArgsConstructor` where
appropriate for Spring services. Safe Lombok usage SHOULD be preferred for entities,
DTOs, and services, subject to Principle VIII. Method names MUST communicate responsibility.
Methods SHOULD be cohesive and single-purpose. Meaningful business-logic duplication
SHOULD be removed; helpers SHOULD be extracted when they improve readability. Magic
numbers and strings SHOULD be replaced with meaningful constants or configuration.
New dependencies and build plugins MUST receive developer approval before introduction,
including additions for testing and quality tooling.

### III. Services, DTOs, Converters, and Validators

Services MUST orchestrate business operations, transaction boundaries, validation,
persistence, and DTO conversion within domain boundaries. Public business-facing methods
SHOULD return response DTOs when appropriate; internal methods MAY return entities.

DTOs MUST use Lombok-based classes organized in `dto/request` and `dto/response` packages.
Request DTOs MUST use Jakarta Bean Validation for applicable format, nullability, length,
range, and structural constraints. Nested response DTOs MAY be used. JPA entities MUST NOT
be exposed directly as HTTP response bodies.

Each applicable domain MUST have dedicated converters that construct response DTOs and
collections and delegate nested DTO mapping to the appropriate domain converter.
Converters MUST NOT access repositories, write to the database, execute business
validation, or cause hidden fetching or unintended lazy loading. Services MUST arrange
required data before conversion.

Each applicable domain MUST have a business validator. Bean Validation MUST cover request
shape and basic constraints; domain validators MUST cover applicable business rules,
uniqueness, ownership, resource state, and persisted-state constraints. Database constraints
MUST provide final integrity protection. Authoritative business calculations MUST NOT be
duplicated across validators, services, and converters.

### IV. REST and HTTP Contracts

Resource URLs MUST use plural nouns and kebab-case multiword segments. HTTP methods MUST
follow their semantics. Authenticated-user resources SHOULD use `/api/me/...` where
appropriate. URLs MUST avoid action verbs except for explicit business operations.

Each feature specification MUST define omitted, null, empty, and explicit-value semantics
where relevant. PATCH SHOULD support partial updates, preserving omitted fields and applying
explicit values. Explicit null MAY clear a field only when the endpoint contract permits
removal. PUT MUST follow its explicitly documented full-replacement or complete-update
contract. Confirmed endpoint-specific contracts MAY override preferred shared semantics
while respecting HTTP method semantics. Implementations MUST NOT assume ordinary Jackson
binding distinguishes omitted properties from explicit null; they MUST use an appropriate
presence-aware mechanism when that distinction is required.

Default success statuses are 200 for GET, 201 for resource-creating POST, 200 or 204 for
PUT/PATCH according to the response contract, and 204 for DELETE. Business-action POST
normally uses 200 or 202 according to processing semantics. Standards-compliant exceptions
MAY be used when appropriate, such as conditional GET returning 304, partial GET returning
206, or asynchronous deletion returning 202. Feature specifications MUST define exceptional
behavior where relevant. Errors MUST use meaningful codes, including 400, 401, 403, 404,
409, 422, and 500 where appropriate. Failed operations MUST NOT return 200 merely for
response uniformity. Feature contracts MUST determine applicable error mappings.

Suitable pagination MUST use Spring Data `Pageable` and application-defined pagination
response DTOs. Spring Data pagination implementation types MUST NOT define the required
public contract. Filtering and sorting MUST follow the documented endpoint contract.
Success DTOs MAY be returned directly; a generic success wrapper is not required.
HTTP errors MUST use Spring `ProblemDetail` following RFC 9457.

### V. Exceptions and Error Handling

Application business exceptions MUST be descriptive custom classes derived from a common
application exception base. Domain-specific types such as `ProductNotFoundException`
SHOULD be preferred. Applicable application and MVC exceptions MUST be handled centrally
with `@RestControllerAdvice`. Filter-chain authentication and authorization failures MUST
use appropriate Spring Security exception-handling components. Framework/infrastructure
exceptions MUST NOT be wrapped solely for naming compliance. HTTP errors MUST NOT expose
stack traces, SQL internals, credentials, tokens, or sensitive data. Exceptions SHOULD NOT
implement ordinary branching where clearer alternatives exist.

### VI. Authentication and Authorization

Authentication MUST use local username/password and JWT access tokens transmitted through
`Authorization: Bearer <token>`. Refresh tokens, social OAuth2 login, cookie-based JWTs,
and token blacklisting MUST NOT be assumed requirements or introduced without explicit
requirements. Additional authentication mechanisms require explicit requirements.

Authorization MUST combine RBAC, permissions, and applicable attribute-based and ownership
checks through Spring Security. Backend checks MUST enforce approved authorization and
business eligibility rules. Client-provided user IDs MUST NOT prove authenticated identity.
Specific roles, permissions, and policies MUST come from approved feature requirements.
Authentication and authorization MUST NOT be disabled to make tests pass.

Secrets, private keys, credentials, and JWT signing secrets MUST remain outside committed
source files. Production CORS origins MUST be explicit. Explicit Bearer headers do not
impose a cookie-specific CSRF-token workflow; CSRF protection MUST be reassessed if
transport changes.

### VII. PostgreSQL and Flyway

PostgreSQL MUST be the database and Flyway MUST manage schema changes. Tables MUST use
plural snake_case; columns snake_case; primary-key columns `id`; foreign-key columns
`<entity>_id`; indexes `idx_<table>_<columns>`; unique constraints
`uk_<table>_<columns>`. Absolute timestamps MUST use `TIMESTAMPTZ`. Existing ID contracts
MUST be preserved; a new global BIGINT-versus-UUID policy requires developer confirmation.
SQL keywords and data types MUST be uppercase in new or modified SQL.

Schema changes MUST use new Flyway migrations, conventionally under
`demo/src/main/resources/db/migration`. Migrations applied in shared environments MUST NOT
be edited. `ddl-auto=create` and `ddl-auto=update` MUST NOT substitute for migrations.
Destructive schema/data changes require explicit authorization; data MUST be preserved
unless approved requirements state otherwise. Appropriate NOT NULL, UNIQUE, CHECK, and
FOREIGN KEY constraints MUST protect integrity.

### VIII. JPA and Repositories

Associations SHOULD use LAZY fetching. Global EAGER fetching MUST NOT hide N+1 problems.
Fetch plans MUST fit use cases and account for pagination and performance; JOIN FETCH,
`@EntityGraph`, DTO projections, and other appropriate strategies MAY be used.

JPA entities MUST NOT use Lombok `@Data` or unsafe equality, hash-code, or string rendering,
including inappropriate `@EqualsAndHashCode` or `@ToString`. Safe `@Getter`, `@Setter`, and
appropriate protected no-argument constructors SHOULD be preferred. Enums MUST use
`EnumType.STRING` where applicable. Cascades and orphan removal MUST reflect lifecycle
ownership. Deletes MUST NOT cascade across independent aggregates without justification.

Simple queries SHOULD use Spring Data derived methods. `@Query` MAY be used for justified
complexity, performance, or database-specific behavior. Specification or Criteria API MAY
support suitable dynamic filtering. Controllers and converters MUST NOT access repositories.

### IX. Transactions

Transaction boundaries MUST primarily reside at the service layer. Database-backed reads
SHOULD use `@Transactional(readOnly = true)` where appropriate; writes MUST use suitable
read-write transactions covering the complete database operation requiring atomicity.
Participating methods need not duplicate outer annotations. Implementations MUST NOT rely
on Spring proxy self-invocation to activate transactions.

Propagation MUST default to `REQUIRED`; isolation MUST default to PostgreSQL's
`READ COMMITTED`. `REPEATABLE READ` MAY serve justified use cases. SERIALIZABLE or unusual
propagation requires an explicit technical requirement and approval. Database rollback
MUST NOT be assumed to reverse external service or S3 operations.

### X. Concurrency and Locking

Repository-level pessimistic locking, optimistic locking with `@Version`, and atomic
conditional updates MAY be used. Selection MUST follow analysis of shared mutable resources,
actual conflicts, and business invariants, protecting the relevant read/check/write sequence.
Multiple locks MUST follow a consistent documented order; resources of the same type MUST
be sorted by stable identifiers. Parent locks MUST NOT be assumed to lock children.

Holding locks across network calls SHOULD be avoided where an alternative exists.
Implementations MUST NOT lock every query or increase isolation by default. Automatic
application retries for optimistic conflicts, serialization failures, and deadlocks MUST
NOT become a general policy. Failures SHOULD surface through suitable application errors
so clients can decide whether to retry. A project-wide idempotency-key policy requires
explicit requirements.

### XI. S3 Object Storage

Deployed object storage MUST use AWS S3; local development MUST use LocalStack S3.
Uploads MUST follow: backend prepares a presigned URL; client uploads to staging; client
requests completion; backend validates the upload and applicable authorization; backend
publishes the validated object according to the defined storage workflow.

Upload states MUST be explicit. Required ownership and metadata MUST be validated before
publication. Features MUST define handling of expired/abandoned uploads and database/S3
consistency failures. JPA rollback MUST NOT be assumed to reverse S3 operations. Temporary
presigned URLs MUST NOT be durable identifiers. Configuration MUST be environment-specific;
AWS credentials MUST NOT be hardcoded. Standard AWS credential provider mechanisms SHOULD
be preferred in deployed environments. State names, expiry durations, metadata limits,
publication mechanics, and recovery details require approved storage requirements.

### XII. Audit Timestamps

Absolute timestamps MUST use Java `Instant` and UTC semantics. `createdAt` and `updatedAt`
are conventional fields where needed; both MUST NOT be required indiscriminately on every
entity. Creation timestamps SHOULD remain unchanged by ordinary updates. Spring Data JPA
Auditing MAY be used, avoiding invented entity-level requirements.

### XIII. Meaningful Testing

Applicable tests MUST use the approved stack: JUnit 5, Mockito, AssertJ, Spring MVC
Test/MockMvc, PostgreSQL Testcontainers, and Spring Boot integration facilities.
Test categories MUST be selected according to actual behavior and change scope; not every
feature requires every category. Tests MUST be deterministic and independent.

- Service, validator, and converter unit tests MUST NOT load the Spring ApplicationContext.
  Real DTOs/entities SHOULD be preferred; dependencies MAY be mocked where appropriate.
- Controller tests MUST use `@WebMvcTest` and MockMvc, with Spring-managed mocks such as
  `@MockitoBean`, verifying applicable binding, HTTP, validation, mapping, and security.
- Repository integration tests MUST use `@DataJpaTest` with PostgreSQL Testcontainers,
  testing relevant mappings, custom queries, and constraints.
- Service integration tests MUST use `@SpringBootTest`, PostgreSQL Testcontainers, and real
  internal dependencies. API integration tests MUST use `@SpringBootTest` and MockMvc across
  controller, service, and database layers. E2E/flow integration tests MUST use
  `@SpringBootTest` and appropriate infrastructure for complete user-facing workflows.

#### Risk-Based Verification and Regression Testing Policy

Verification MUST be selected from the affected code, observable behavior, dependencies
and regression risk. This policy governs execution scope and frequency; it MUST NOT
weaken the applicable test categories, integrity protections or evidence requirements
elsewhere in this Constitution.

- After each implementation change, targeted verification MUST be the default. Select
  compilation and applicable unit, MVC, repository, service, API, flow or other checks
  that exercise the changed behavior and affected dependency paths. Record the scope
  and risk rationale; passing a narrow test does not establish unaffected behavior.
- At integration checkpoints, relevant cross-domain regression tests MUST run for the
  contracts, persistence and workflows connected by the change. Checkpoints and their
  actual test scope MUST be specified in the feature plan and tasks.
- Near completion of a feature involving application implementation, at least one
  unrestricted full `mvn verify` MUST run from `demo/`, using the repository wrapper
  where available. This run MUST include normal configured test discovery and packaging,
  without selective test exclusions or skip flags. It does not replace risk-specific
  tests absent from normal discovery. Documentation-only work retains the existing
  document-consistency-check exemption; it does not require application tests.
- After an isolated fix, affected tests MUST be rerun first. The entire suite MUST NOT
  be rerun automatically merely because a fix occurred. Full verification MUST be repeated
  when the fix has broad impact, changes shared infrastructure, or leaves unresolved
  regression uncertainty. Record the trigger and rationale for a repeated full run,
  or why affected-test evidence is sufficient after a localized fix. Earlier full-run
  results MUST remain attributed to the revision tested; they MUST NOT be relabeled
  as a passing full run of later changes.
- Required security, transaction, concurrency, rollback, idempotency and performance
  tests MUST be preserved and executed whenever their associated risks are affected.
  Risk-based selection MUST NOT justify omitting a required scenario, weakening its
  assertions, disabling security/integrity, or substituting a mock for a required real
  transaction/concurrency boundary. This does not require every category for every fix.
- Unrelated expensive tests MUST NOT be repeatedly executed without a justified
  regression risk. Their presence in a required unrestricted full run is not a reason
  to repeat them at every targeted or integration checkpoint. Existing performance
  budgets and query/integrity assertions remain in force when applicable.
- Environmental limitations MUST be documented with the blocked commands/checks,
  concrete cause, affected verification scope and remaining uncertainty. Attempt the
  required full run when the environment permits; do not replace an unavailable full
  run with selective checks and label it unrestricted. Continue feasible independent
  verification, record the full gate as blocked or unverified when it cannot finish,
  and disclose that limitation before claiming feature completion. An application
  assertion failure is not an environmental waiver; diagnose and verify its fix under
  the same risk-based rerun rules.
- Verification records MUST contain exact commands and selectors, tested revision or
  identifiable working-state scope, relevant environment, results and discovered test
  counts, failures and fixes, skipped or unexecuted checks and environmental limitations.
  Inspect current-run reports rather than aggregating stale reports from earlier runs.
  Distinguish targeted success, integration-checkpoint success and full-suite success.
  Unexecuted, skipped, interrupted or failed tests MUST NOT be reported as passed;
  unresolved failures and incomplete gates MUST remain explicit.
- Feature-level `plan.md` and `tasks.md` MUST inherit this policy and specify affected
  risks and dependencies, targeted verification scope, cross-domain checkpoints,
  required full verification and conditional rerun criteria. They MUST retain the
  feature's mandatory risk-specific tests and evidence obligations. Where an older
  instruction requires unconditional repeated broad runs, align it with this policy
  before reuse; preserve historical execution evidence rather than rewriting it.

#### Test File and Package Organization

Tests MUST use Package by Feature/Domain and Test Class by Responsibility. All Java
tests MUST live under `demo/src/test/java` using the production root package
`com.example.demo`. Test packages MUST mirror the owning domain and relevant layer
(`service`, `validator`, `converter`, `controller`, `repository`, etc.); implementation
subpackages MAY be mirrored when used consistently in that domain. Test resources MUST
live under `demo/src/test/resources`.

Tests MUST NOT be grouped primarily into top-level `unit`, `integration`, or `api`
packages. A domain's unit tests MUST NOT be combined into one omnibus class. Each class
MUST have a clear component or workflow responsibility. Different test categories for
the same component MUST remain separate classes rather than mixing mocked unit tests,
MVC slices, and full application-context tests in one class.

| Responsibility | Class naming | Intended scope |
|---|---|---|
| Service implementation unit test | `<ServiceName>ImplTest` | Business rules with isolated dependencies |
| Validator unit test | `<ValidatorName>Test` | Domain validation rules |
| Converter/mapper unit test | `<ConverterName>Test` / `<MapperName>Test` | Meaningful mapping and calculation behavior |
| Controller MVC slice | `<ControllerName>Test` | HTTP contract with mocked services |
| Repository integration test | `<RepositoryName>IT` | Real PostgreSQL queries, mappings and constraints |
| Service integration test | `<ServiceName>IT` | Real service and internal persistence flow |
| API integration test | `<ControllerName>IT` | HTTP through controller, service and database |
| Domain flow integration test | `<FeatureName>FlowIT` | A complete user-facing scenario |

For example, `UserServiceImplTest` and `UserServiceIT` belong to the user service
layer; `UserControllerTest` and `UserControllerIT` belong to the user controller layer.
The naming table describes responsibilities, not a requirement to generate every class.
Where an existing approved convention uses `<ServiceName>ImplIT`, it MAY be retained;
both service IT names MUST NOT be created for the same responsibility.

A flow contained within one domain SHOULD remain under that domain. Cross-domain
end-to-end flows SHOULD live under `com.example.demo.e2e`. API integration tests MUST
NOT be mislabeled as complete business-flow tests merely because they use a full context.
Only real production components and meaningful workflows SHOULD receive test classes.
Do not create empty placeholder classes unless explicitly requested. Entity/DTO accessors,
framework-provided CRUD, and trivial delegation MUST NOT cause mechanical file generation.
Unit, repository, service, API, and flow tests MAY cover related behavior when each proves
a distinct boundary; assertions and setup MUST NOT be duplicated without added value.

#### Test Data, Fixtures, and Local Helpers

Reusable in-memory object factories SHOULD live under `com.example.demo.testdata.<domain>`.
They MUST return fresh independent objects and SHOULD provide clearly named valid/default
and business-state variants where these remove repeated setup. Unit tests MUST NOT use
persistent fixtures or load a database just to construct objects.

Persistent integration fixtures SHOULD live under `com.example.demo.fixture.<domain>`.
JPA/EntityManager SHOULD be preferred for ordinary entity graphs. JDBC MAY be used for
special database states, database-specific behavior, or justified bulk setup. Repository
IT setup SHOULD avoid depending on the repository method being tested. Fixture transaction
boundaries MUST remain separate from the service operation under test where transaction
behavior is being verified.

Small helpers shared only within one test class SHOULD remain local to that class. Shared
factories/fixtures MUST NOT become a single cross-domain catch-all or an opaque scenario
framework. Tests MUST make the preconditions and overrides relevant to their behavior clear.
Test methods SHOULD be grouped in the corresponding production-method order; `@Nested`
MAY group related behavior when it improves readability.

Shared infrastructure belongs under `com.example.demo.config` only when it removes real
duplication. Prefer Spring Boot test auto-configuration; do not manually redeclare all
production beans. PostgreSQL container configuration SHOULD be shared when safe, while
each test retains independent data. Shared fixture/config/e2e directories MUST NOT be
created solely to satisfy an illustrative tree. `application-test.properties` and SQL/JSON
resources SHOULD be added only when required by the selected tests.

Unit and slice classes MUST end in `Test`; integration classes MUST end in `IT`.
Repository integration tests use `IT` despite their slice annotation. Methods MUST follow
`methodName_shouldExpectedBehavior_whenCondition` or `methodName_shouldExpectedBehavior`.
Each test SHOULD target one meaningful behavior. Parameterized tests SHOULD cover the same
behavior with differing inputs. Assertions MUST verify meaningful results and state;
Mockito verification SHOULD target meaningful interactions and side effects. Tests MUST NOT
mock the component under test, over-test implementation details, or weaken production behavior.

Shared PostgreSQL container configuration SHOULD be used where appropriate; Flyway-created
schemas SHOULD be preferred. Repository tests MAY use rollback isolation. Service integration
tests MUST NOT default to test-level `@Transactional` when it conceals transaction behavior.
Relevant commit/rollback behavior MUST be verified at real boundaries; committed test data
MUST be cleaned up. Hardcoded generated IDs and shared mutable data MUST be avoided.
Reusable fixtures SHOULD be organized by domain; simple data MAY be created within tests.
Maven discovery MUST include `*Test` and `*IT` in their intended phases. Surefire/Failsafe
configuration and actual discovery MUST be verified rather than inferred from class names
or a successful `verify` invocation.

### XIV. Static Analysis and Code Quality

Formatting MUST be consistent, methods readable, and domain responsibilities clear.
The intended combined toolchain is Spotless with an appropriate Java formatter, Checkstyle,
SpotBugs, and PMD. These tools serve complementary purposes; selecting one does not fulfill
the combined standard. Adoption MAY proceed gradually through separately approved
implementation tasks under Principle II. This standard does not assert that tools or gates
are configured. Configuration and execution MUST be verified before claiming a check is
active or passing. Maven compilation and test verification MUST be used as applicable to
approved implementation changes.

### XV. Git and CI/CD

Commits MUST follow Conventional Commits, `<type>[(<scope>)][!]: <description>`, using `feat`,
`fix`, `refactor`, `test`, `docs`, `style`, `chore`, `build`, `ci`, `perf`, or `revert`.
Square brackets denote optional syntax, not literal characters. Scope MAY be omitted;
when present, it MUST be enclosed in parentheses, as in `feat(product): add product search`.
Breaking changes MUST use `!` before the colon or a
`BREAKING CHANGE` footer. Descriptions SHOULD be concise and English. Each commit MUST
focus on one cohesive change. Agents MUST NOT automatically commit or push.

Desired CI capabilities are compilation, unit/controller/repository/service/API tests,
JAR packaging, Docker builds, and static analysis. Checks MUST follow available CI
infrastructure and approved configuration. Missing checks MUST be reported rather than
represented as passing. CI changes require an authorized implementation scope.

### XVI. Docker and Environment Configuration

Containerization MUST use multi-stage builds, non-root runtime users, `.dockerignore`,
and externalized credentials/configuration. Minimal JRE runtime images SHOULD be preferred
where appropriate. Local infrastructure MUST use Docker Compose. Profiles such as local,
test, and prod MAY be used as appropriate; shared configuration MUST NOT hardcode an active
profile. Real secrets, private keys, active credentials, and local environment datasets
MUST NOT be committed. Image pushes and deployments require authorization.

### XVII. Scope and Change Safety

Work MUST remain within developer-approved scope. Relevant code MUST be read before
implementation decisions. Unrelated edits/refactoring, unapproved observable behavior
changes MUST NOT occur. Dependency and build-plugin approval follows Principle II.
Out-of-scope defects MUST be reported separately. Tests, security, and database integrity
MUST NOT be weakened to make
implementation appear successful. Constitution-only work MUST NOT modify production code,
tests, dependencies, migrations, Docker/CI configuration, or template sources.

## Technology and Repository Baseline

The confirmed target is Java 25 LTS, Spring Boot 4.1.2, Maven, PostgreSQL, Spring Data
JPA/Hibernate, Flyway, Docker, deployed AWS S3, and local LocalStack S3. Targets MUST guide
approved development; discrepancies MUST be reported rather than silently corrected.
S3 is object storage; no application compute deployment platform is specified.

The Maven application module is `demo/`, with production and test sources under
`demo/src/main/java` and `demo/src/test/java`. Spring MVC, Spring Security, and OpenAPI
tooling remain established choices. Conceptual database models do not establish applied
schemas or endpoint contracts. The domain-first architecture in Principle I takes precedence
over conflicting global-layer package proposals.

Global primary-key types, exact roles and permissions, token invalidation and refresh-token
strategy, endpoint update/error contracts, and storage lifecycle details remain open until
approved requirements resolve them. Principle XIII requires concurrency and idempotency
verification when their associated risks are affected; it does not require concurrency
infrastructure for every feature or establish a project-wide idempotency-key contract.
Developers MUST NOT invent defaults for these decisions. Missing approved capabilities
require separately authorized implementation work.

## Development Workflow and Quality Gates

Feature specifications MUST define applicable HTTP, update, authorization, persistence,
concurrency, and storage behavior. Plans and tasks MUST use actual repository paths and
select meaningful tests appropriate to changed behavior and inherit the risk-based
verification scope, checkpoints and rerun criteria in Principle XIII. Illustrative template
content MUST NOT override this constitution or imply implemented capabilities.

The installed Spec Kit planning workflow supplies constitution checks before research and
after design. These checks do not require additional developer approval at each stage.
Reviews MUST assess compliance with the principles applicable to the change. SHOULD
departures MUST record their technical rationale.

Build, test, integration-test discovery, and static-analysis claims MUST reflect verified
configuration and execution. Missing checks MUST be reported rather than represented as
passing. Constitution-only changes require document consistency checks, not application
builds or infrastructure provisioning.

## Governance

This constitution governs project-wide engineering decisions and supersedes conflicting
illustrative templates and unapproved proposals. Existing implementation is evidence, not
automatic architectural approval. Applying this constitution does not authorize unrelated
implementation changes.

Amendments MUST receive developer approval and document their rationale and affected rules.
Semantic versioning MUST use MAJOR for incompatible principle removals/redefinitions,
MINOR for new principles or materially expanded guidance, and PATCH for non-semantic
clarifications. The ratification date MUST preserve initial adoption; the amendment date
MUST reflect the latest amendment. Corrections to an unratified initial draft do not imply
a new ratification or amendment event. Compliance MUST be assessed through the normal
Spec Kit planning and review workflow.

Ratification status remains unverified. The testing-organization and risk-based verification
amendments were requested by the developer; retaining the original ratification date does
not verify initial adoption.

**Version**: 1.2.0 | **Ratified**: 2026-10-23 | **Last Amended**: 2026-11-20
