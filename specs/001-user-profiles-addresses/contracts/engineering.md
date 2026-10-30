# Engineering Constraints and Decision Record

**Updated**: 2026-10-26

Technical decisions relocated from spec.md at the user's request. These constraints remain binding; relocation grants no dependency approval and makes no implementation claim. Business requirements and FR/SC identifiers remain in [spec.md](../spec.md). Use [HTTP contract](http.md) for wire behavior and [plan.md](../plan.md) for implementation. The core-first decisions below supersede older broad prerequisites in ancillary documents. References to the previous edit scope are historical, not a restriction on this relocation.

## Identity and concurrency decisions

### Session 2026-10-26

- Q: When a user changes their username, should their existing authentication token continue to work? ? A: Existing tokens remain valid; authentication identifies the account through its immutable user ID.
- Q: If two devices update the same profile or address at nearly the same time, how should conflicting changes be handled? ? A: Later successful writes win, and omitted profile fields preserve the latest stored values. Use pessimistic locking to coordinate conflicting writes.
- Q: How many addresses may each user save? ? A: Maximum 20 addresses per user; reject additional creates with 400. Enforce the limit under the same per-user pessimistic coordination lock.
- Q: What should happen if a write cannot acquire its pessimistic lock before the lock wait times out? ? A: Return 409 with a safe concurrent-update/retry message, roll back the operation, and do not retry automatically; the client decides whether to retry.

## Implementation Readiness and Future Integration

These confirmed decisions supersede earlier prerequisites in this feature's ancillary research, data-model, HTTP/integration contracts and quickstart where those documents require Authentication or Media before independent work. Only spec.md, plan.md, tasks.md and checklists are revised in this update; those ancillary files remain historical design context, not blocking authority for core work.

| Category | Scope | Current status |
| --- | --- | --- |
| Ready for immediate implementation | User/Address schema, entities, repositories, DTOs, validation, services, ownership/default rules, locking/transactions, unit and repository/service integration tests | Planned, not implemented; existing dependency approval rules still apply |
| Implemented but awaiting external integration | Core components may enter this category once their own tests pass | None currently implemented; record component evidence before using this status |
| Blocked until Authentication | Production authenticated HTTP endpoint activation, JWT adapter/validation, genuine security-filter errors and real token-continuity/API acceptance | Future Authentication feature; never a blocker for core components |
| Blocked until Media | Actual media-ID binding, entity association/FK, non-null reference acceptance and ownership/existence/availability validation; reference removal/replacement integration | Future Media feature; optional image state remains absent/null in the core increment |

- **FR-018 ? Implementation sequencing and identity boundary**: Implement independent User/Address components before separate Authentication and Media features. Define a minimal CurrentUserIdProvider contract resolving an immutable Long user ID or failing for unavailable identity, with no JWT/Spring Security types. Production business services receive a trusted actor ID from a future boundary adapter, never JWTs, SecurityContext or a client-provided ownership claim. The interface is not an identity source and has no temporary production implementation. No JWT parsing, bypass, hardcoded identity or fake authentication is permitted. Until the real adapter/security exists, do not register or activate these protected HTTP routes; no anonymous alternative is introduced. Unit and service/repository integration tests may supply controlled IDs exclusively through test fixtures/constructors or test-only adapters, preserving all ownership checks.
- **FR-019 ? Future media boundary**: Define a service contract for later media resolution/ownership/existence/availability and reference management. Do not choose a durable media ID type before the real contract exists, create dummy production media services or persist unvalidated non-null references. Core schema/entities have no guessed media table/FK; add the optional association through a later additive migration once Media exists. Profile DTOs may declare the absent/null image contract without introducing a placeholder non-null ID type. Core profile operations with the image field omitted or null remain independently testable; an explicit null on an absent reference is a no-op. Non-null media-dependent operations remain unavailable and must fail closed before any mutation if invoked internally, without falsely reporting the candidate as valid or invalid. Actual stored-reference removal/replacement remains deferred with Media. S3-compatible storage is future Media scope: LocalStack S3 locally, AWS S3 remotely; no S3 implementation in this feature.

Core acceptance: profile/address service flows work with test-controlled actor IDs and null images, including foreign-address 404, all default/capacity transitions and real rollback/concurrency boundaries. No production-ready HTTP claim follows from these tests. Future integration acceptance retains the original authenticated and avatar scenarios unchanged; anonymous access yields 401 through real Authentication, whereas any authenticated user may view the limited public profile without extra feature-specific permissions/status restrictions.

## Endpoint Contracts

| Method | Endpoint | Request | Success body/status |
| --- | --- | --- | --- |
| GET | /api/me | None | Private profile, 200 |
| PUT | /api/me | Editable fields using presence table | Updated private profile, 200 |
| GET | /api/users/{userId} | None | Limited public profile, 200; authentication required |
| GET | /api/me/addresses | None | Ordered address array, 200 |
| POST | /api/me/addresses | Required recipient fields, optional isDefault | Address, 201; Location `/api/me/addresses/{id}` |
| GET | /api/me/addresses/{addressId} | None | Owned address, 200 |
| PUT | /api/me/addresses/{addressId} | Required recipient fields, optional isDefault | Updated address, 200 |
| DELETE | /api/me/addresses/{addressId} | None | No body, 204 |

Missing current-user records yield 404 after successful authentication. Malformed identifier values yield 400; well-formed missing IDs yield 404.

## Error Representation and Mapping

| Status | Trigger | Acceptance condition |
| --- | --- | --- |
| 400 | Invalid shape/field, business validation, invalid default change, address capacity exceeded or invalid image reference | Safe message and applicable field errors; no partial mutation |
| 401 | Unauthenticated protected request | Existing/approved authentication entry point handles filter failure |
| 403 | Authenticated caller lacks approved permission | Access-denied handler handles genuine filter failures; no additional feature restrictions are introduced |
| 404 | Missing user or missing/foreign address | No ownership disclosure |
| 409 | Username/email uniqueness conflict or pessimistic lock wait timeout | Safe conflict-specific message, no SQL/locking internals and full rollback; lock timeout indicates the client may retry without automatic retry |

ApiError extends ProblemDetail, preserving constitutional compatibility. All feature errors use `application/problem+json` with standard fields type, title, status, detail and instance, plus custom timestamp (UTC ISO-8601 with Z), message (safe explanation matching detail), path (request path matching instance) and fieldErrors (field-to-message-list mapping, `{}` for non-field errors). Standard numeric status supplies the requested status without duplication. Use safe status-appropriate titles and `about:blank` when no approved problem-type identifier exists.

For each 400/401/403/404/409 category, acceptance verification must check content type, standard/custom field presence, HTTP/body status agreement, message/detail agreement, path/instance agreement and applicable fieldErrors. Check the same structure for MVC and security-filter errors. The 403 mapping does not introduce additional role/permission/status restrictions for this feature.

Never expose rejected credentials, stack traces, SQL, tokens or internal exception messages. Infrastructure failures must not be disguised as validation successes. GlobalExceptionHandler handles MVC/application errors; the authentication entry point and access-denied handler emit the same structure for filter errors without duplicating them in controller advice.

## Explicit Engineering Constraints Supplied by the User

These confirmed user constraints have been moved out of the business specification without changing their force.

- Use `com.example.demo.user` with controller, service interfaces/ServiceImpl, entity, repository, converter, validator, dto/request, dto/response, enums and exception packages as applicable. Shared errors belong to `common`.
- Activate Bean Validation with `@Valid` at the boundary. Domain validators own business rules; controllers delegate to services; services access their own repositories and cross-domain service interfaces only.
- Entity-returning getters use `get...Entity()` and required retrieval throws not-found; DTO getters use `get...()`. Dedicated converters map nested DTOs through appropriate converters without validation, repository access or hidden queries.
- Prefer derived Spring Data queries for simple conditions; use `@Query` for genuine complexity. Avoid redundant methods and unnecessary abstractions. Reads use suitable read-only service transactions; mutations use complete write transactions.
- Use LAZY associations with use-case fetch plans. Do not expose JPA entities. Persist enum strings; Long IDs are specific to User/Address, not a global ID policy.
- Use LocalDate for dateOfBirth and Instant for joinedAt; absolute JSON timestamps use UTC ISO-8601 Z and PostgreSQL TIMESTAMPTZ. Use new Flyway migrations with constraints and constitutional naming, never ddl-auto=update or rewriting applied migrations.
- Use GlobalExceptionHandler and ApiError extending ProblemDetail with the standard and custom fields above. Reuse common exception bases where available; categories cover missing resources, identity conflicts, default changes and image validation without excessive exception classes.
- Future tests: UserServiceImplTest/AddressServiceImplTest, UserValidatorTest/AddressValidatorTest, UserConverterTest/AddressConverterTest; controller slices; meaningful RepositoryIT queries/fetch plans; ServiceIT invariants/rollback; API IT across HTTP, controller, service and database. Use approved JUnit/Mockito/AssertJ/MockMvc facilities, PostgreSQL Testcontainers and Flyway. No tests are written in this task.
- Test private/public allowlists, security and ownership, every presence-table cell, unchanged/conflicting identities including races, avatar preserve/remove/replace, CRUD/phone validation, every default transition, missing/foreign resources, concurrent first creation and mutation, atomicity and rollback. Test accepted limits at the boundary and one beyond, dates today/tomorrow, all invalid phone classes, bad types and enums.
- Use reusable domain TestData/fixtures for repeated graphs, keep scenario inputs/expectations visible and parameterize only equivalent behaviors. Verify actual Test/IT discovery when implemented; never infer it from filenames or a successful build.

## Repository Evidence and Dependencies

Inspected `demo/src/main/java/com/example/demo/DemoApplication.java`, the complete production/test file inventory, `demo/pom.xml`, application/test properties, `.github/workflows/test.yml`, `Database-Design/accounts.puml`, `master.puml` and `.specify/memory/constitution.md` before drafting.

- The application has no User/Address/media entities, authentication implementation/security policy, controllers, validators, converters, migrations, shared exception base, ApiError or GlobalExceptionHandler. Conceptual diagrams are not implemented contracts and specify no lengths or normalization rules.
- Security, JPA, Flyway, PostgreSQL and web/test dependencies exist. The sole test is a SpringBootTest context load using the test profile and a disposable PostgreSQL Testcontainers JDBC URL. No domain test fixtures or explicit Failsafe configuration exist. No tests or infrastructure were executed for this documentation task.
- Maven declares Java 25 and Spring Boot 4.1.1; the constitution targets 4.1.2. This discrepancy is recorded, not corrected or presented as an implemented upgrade.
- Authentication and Media are separate future features. User/Address owns its core schema/model now; Authentication later reuses the same User rather than blocking its creation. Password remains encoded-only and non-null, accountStatus remains non-null, and joinedAt is assigned at creation; tests provide explicit fixture values, with no production registration, password encoder workflow or default account provisioning introduced. Future integrations do not block independent work; only the readiness table gates their dependent operations.
- No feature numbering override or extension hooks are configured. The resolved project spec template is `.specify/templates/spec-template.md`; sequential feature directory numbering is independent of the existing branch.
- UTC defines the date boundary for birth-date validation. No-op profile updates succeed; prohibited-field mutations fail rather than silently disappear. These explicit assumptions can be revised during clarification.

## Additional Preserved Technical Decisions

- **FR-002 — Identity**: Derive identity from the authenticated principal; request-body user IDs never establish identity or ownership. All feature endpoints require authentication, including GET `/api/users/{userId}`. No additional role, permission or account-status restrictions apply. Only the profile owner may modify their profile; `/api/me` targets the principal exclusively. Authentication resolves the immutable user ID rather than treating a mutable username as account identity. Changing username does not invalidate otherwise valid tokens or require signing in again; normal token expiration and other established authentication rules still apply.

- **FR-006 — Uniqueness**: Username is nonblank and unique; email is optional in stored records but valid and unique when present. Reject blank username/email updates. Exclude the current user from conflict checks, and enforce final uniqueness constraints during persistence; map conflicts including races to 409. Apply the confirmed normalization rules below. Username/email uniqueness is case-insensitive and enforced at database level, including concurrent writes. Exclude the current user using the same normalized comparison. Phone numbers are not unique.

- **FR-007 — Avatar**: Distinguish omitted profileImageId from explicit null. Use durable media IDs when an approved media-ID contract exists, never stored expiring presigned URLs. Validate ownership and availability through the media owner's service. No reference change may delete an independent media resource. The media entity/service does not yet exist; its contract is a future integration dependency only; it must not block independent User/Address components. Apply FR-019 until it exists.

- **FR-009 — Address validation**: recipientName and addressText are required and nonblank; recipientPhone is required and matches exactly `0[0-9]{9}`. Update requires all three fields. Persist isDefault as non-null. No additional constraint on profile phoneNumber is implied by the address-phone rule.

- **FR-010 — Defaults**: An empty book has no default; every non-empty book has exactly one. Apply all creation, update and deletion transitions in Story 2. Highest remaining ID is the fallback; add no timestamp solely for default selection.

- **FR-011 — Atomicity**: Coordinate all address mutations for the same owner, including when there are zero addresses. Locking existing address rows alone is insufficient. Check, clear, promote and persist operations form one atomic unit. Different users must not share a global address-book lock. Use pessimistic locking to coordinate conflicting profile and address writes per user, including first-address creation when no address rows exist. Acquire coordination before reading the state used for validation or mutation and hold it through transaction completion; all competing write paths must participate. Merely locking existing address rows is insufficient. A parent lock does not implicitly lock child rows. Planning must define the concrete lock targets and consistent acquisition order and verify real transaction rollback.

- **FR-012 — Responses**: Use separate private-profile, public-profile and address DTOs. Address responses contain id, recipientName, recipientPhone, addressText and isDefault only. No entity bodies or generic success wrapper are required. List order is default first, then ID descending; return an unpaginated array under this contract.

- **FR-013 — Errors**: Use the status/error table below and a consistent safe error body. Preserve centralized application error handling and security-filter entry-point/access-denied responsibilities. Existing shared exception infrastructure is absent; design only the necessary common base and reusable domain exception categories, not a class per validation branch.

- **FR-014 — Persistence**: Enforce nullability, agreed lengths, uniqueness and ownership foreign keys. Preserve user lifecycle and existing applied migration history when dependencies are introduced. No developer/production database access during verification. No cascade into independent media resources.

- **FR-015 ? Concurrent edits**: For overlapping profile or address updates, the later successful write in the coordinated transaction order wins for supplied fields; omitted profile fields preserve the latest committed values observed after acquiring the lock. Address updates still require all recipient fields and preserve an omitted/null default flag from the latest state. Do not add stale-edit version requirements or rejection solely because a client read older values. Revalidate ownership, uniqueness and default rules against the coordinated current state; these constraints may still reject a write.

- **FR-017 ? Lock wait failure**: If a write cannot acquire its pessimistic lock before the lock wait times out, return 409 with a safe concurrent-update message indicating that the client may retry. Roll back the entire operation with no partial profile, address or default changes. Do not automatically retry; the client decides whether to retry.

All maximum string lengths count Unicode code points after trimming, not UTF-16 code units, bytes or grapheme clusters. A supplementary-plane character counts as one; combining code points count separately with no Unicode normalization. Use code-point-aware boundary validation rather than assuming ordinary string @Size matches this contract; PostgreSQL character-length constraints must agree. Boundary criteria include supplementary characters at the maximum and one code point beyond. Trim leading and trailing whitespace from all supplied string fields in profile/address requests. Preserve internal whitespace. Reject blank strings for both required and optional fields. Optional null/omission remains governed by the operation-specific tables; it does not override the rejection of explicit null username/email. Preserve other text casing and case-sensitive comparisons. Password handling is outside this update contract.

Email is stored and compared lowercase. Username retains supplied display casing but is compared using its lowercase form. Database constraints must enforce the same case-insensitive uniqueness semantics, protecting against races. Request limits and persisted column limits must agree.

- **User**: Long id; displayName; unique nonblank username; encoded password only; optional unique valid email; phoneNumber; accountStatus (ACTIVE, INACTIVE, SUSPENDED, BANNED); optional gender (MALE, FEMALE, OTHER); optional dateOfBirth; immutable joinedAt assigned at creation; optional profileImage reference. No new account-creation workflow is included. Account-status initialization and lifecycle belong to the identity dependency.

- **Address**: Long id; required recipientName, recipientPhone and addressText; non-null Boolean isDefault; exactly one owning User. Owned by the user's address book.

Normalization uses String.strip as recorded in research.md; Unicode code-point length uses String.codePointCount, with PostgreSQL VARCHAR/CHAR_LENGTH agreement and supplementary/combining boundary tests per plan.md and tasks.md T019. ApiError extends ProblemDetail remains the confirmed Q3 decision. Java 25, Boot baseline reconciliation, validation/Lombok/Failsafe approvals, package conventions, identity-provider contract, no guessed media ID/FK, later additive media migration, LocalStack S3 locally and AWS S3 remotely remain unchanged.
