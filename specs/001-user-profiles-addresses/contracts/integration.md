# Prerequisite Integration Contracts

These describe required capabilities, not implemented interfaces or permission to expand this feature. Actual service/package/type names must reuse the prerequisite implementation rather than create duplicates.

## Authentication / User lifecycle

- Spring Security validates local-credential-issued Bearer JWTs and supplies immutable Long user ID to feature controllers/services. Username is not the account lookup identity for existing tokens.
- Authentication remains valid after a username change until ordinary expiration or other established auth failure. Do not add blacklist, refresh-token or token-reissue behavior.
- All feature routes require authentication; owners alone mutate self-profile/addresses. No extra role/permission/status restriction is added here.
- Authentication entry point and access-denied handler use the shared ApiError factory. A controller advice must not replace them.
- The identity prerequisite and feature must share exactly one User entity/table and agree on encoded password, status initialization, user ID generation and immutable joinedAt creation. No registration/deletion/password endpoint is added in this feature.
- Current repository has no implementation of this contract. Domain/service tests may use fixtures, but a mocked principal is not proof of a real JWT API flow. Full API acceptance cannot complete until the prerequisite is implemented and integrated.

## Media

- Reuse the media entity's actual durable ID type and JSON representation. Do not assume UUID or Long; no media entity/service/schema currently exists.
- A media-owned service accepts candidate ID and authenticated user ID, validates ownership/availability using its approved state rules and returns a usable entity/reference or a safe domain validation failure mapped to 400 for invalid profile image references.
- The profile service does not read the media repository. No fake validation, upload, object deletion, presigning or durable URL storage is introduced.
- Ownership/availability validation should be local DB work in the participating transaction. No network/S3 calls under the user coordination lock. If the media implementation requires them, reconcile integration ordering before implementation rather than holding database locks over remote calls.
- Establish an acyclic service direction (user -> media), actual FK target and any media-reference lifecycle guarantees before implementing avatar persistence. Media must not introduce a reverse user-service dependency for this flow; use owner IDs or an agreed acyclic interface.
- This feature only changes references; removal/replacement never cascades deletion to independently owned media. Concurrent media-state changes follow the real media domain's contract, not invented storage lifecycle rules.

## Common errors

The common ApplicationException base, ApiError subclass/factory and GlobalExceptionHandler do not exist. Create only the shared error support needed by this authorized feature, integrating rather than duplicating any prerequisite support that lands first. Security handlers call the same safe factory. Domain exceptions cover missing resources, identity conflicts and business validation; framework/driver exceptions remain framework exceptions mapped centrally, not wrapped merely for naming.

## Approval and sequencing gates

1. Reconcile Boot 4.1.1 in pom.xml with constitutional target 4.1.2 through an approved change.
2. Obtain approval for actually missing validation/Lombok dependencies and Failsafe plugin before adding them; inspect effective dependencies first.
3. Integrate authentication/User lifecycle before authenticated API completion; integrate real media before avatar contract completion. Do not silently reduce feature scope when dependencies are unavailable.
4. Reinspect migration history before assigning versions; coordinate User schema ownership and media FK ordering. No new globally applicable ID policy, roles or storage lifecycle is established by this design.

## Current core integration status (2026-10-26)

The reviewer confirmed all checklists and approved validation starter, Lombok, Maven Failsafe and Boot 4.1.2 during implementation. Those build changes are applied. The previous prerequisite ordering is superseded by engineering.md FR-018/FR-019 and plan.md's core-first design.

Core User/Address code exists independently of Authentication/Media. The identity boundary is `demo/src/main/java/com/example/demo/common/identity/CurrentUserIdProvider.java`; no production implementation is registered. Services accept a trusted actor ID; test-only identity providers and fixtures cannot activate production endpoints. Protected controllers, JWT parsing/validation and filter integration remain deferred.

Core User intentionally has no media column/entity/FK or guessed durable media ID. Response image state is null; request image binding accepts only omission/null and fails closed for non-null input as unavailable. Typed ID binding, actual validation, association/reference changes and S3 integration remain deferred to real Media. No dummy production provider is supplied.
