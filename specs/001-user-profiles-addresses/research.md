# Research: User Profiles and Address Management

Date: 2026-10-26. Repository evidence: demo/pom.xml, production/test inventories, application/test properties, .github/workflows/test.yml and the constitution/specification. No production/test code or databases were changed.

## 1. Pessimistic coordination and timeouts

**Decision**: Lock the existing owning users row using PESSIMISTIC_WRITE before current-state reads for every profile/address mutation. Use READ COMMITTED, REQUIRED transactions and PostgreSQL transaction-local lock_timeout (configurable; proposed default 5s). Let failures leave the transaction and map recognized lock timeout to 409 after rollback. No automatic retry.

**Rationale**: A user row exists even when no address rows exist, so it coordinates first creates and the capacity check. All participating writers use the same mutex; a parent lock is not a child-row lock. Read fresh state after the lock; do not merge detached stale entity snapshots.

**Alternatives considered**: Address-only locks miss empty books; global locks serialize unrelated users; optimistic versions contradict approved last-successful-write behavior; SERIALIZABLE is unnecessary and unapproved. Provider timeout hints alone are not sufficient proof of a real PostgreSQL bound.

Sources: [Spring Data locking](https://docs.spring.io/spring-data/jpa/reference/jpa/locking.html), [PostgreSQL explicit locks](https://www.postgresql.org/docs/17/explicit-locking.html), [PostgreSQL client settings](https://www.postgresql.org/docs/17/runtime-config-client.html).

## 2. Database uniqueness and normalized comparison

**Decision**: Preserve username text; define database-authoritative generated lowercase username/email keys with named UNIQUE constraints. Email is stored lowercase. Query existing keys using PostgreSQL LOWER of the normalized request operand using the same collation; exclude current user ID. Use database LOWER for canonical email storage as well, avoiding a Java/database Unicode case-fold mismatch. No ASCII-only username restriction is invented. Case mapping is the selected PostgreSQL database collation's LOWER semantics, not full Unicode case folding or accent-insensitivity.

**Rationale**: Database-generated keys cannot drift from their source and protect concurrent writes. Multiple null emails remain allowed. Generated columns allow constitutional `uk_...` UNIQUE-constraint naming rather than pretending a functional index is a normal constraint. Internal keys never appear in responses. Test non-ASCII cases on the selected PostgreSQL image and keep collation consistent across environments.

**Alternatives considered**: Case-sensitive unique constraints violate approved rules; Java-only existence checks race; citext adds an extension unnecessarily; lower-expression unique indexes are valid but generated keys better express named constraints. No duplicate plain-string unique indexes are needed.

Sources: [Generated columns](https://www.postgresql.org/docs/17/ddl-generated-columns.html), [expression indexes](https://www.postgresql.org/docs/17/indexes-expressional.html), [string functions](https://www.postgresql.org/docs/17/functions-string.html).

## 3. Default enforcement and flush order

**Decision**: Use a unique partial index on addresses(user_id) where is_default is true, plus parent-lock transactional transitions for the at-least-one rule and the count limit. Explicitly flush clearing/deletion before a new default is flushed.

**Rationale**: The partial index gives final at-most-one protection; it cannot alone ensure a non-empty book has a default or enforce the 20-address cross-row bound. Explicit phase ordering avoids ORM statement reordering causing transient unique-index failure. Intermediate zero-default state is uncommitted and rolls back on failure.

**Alternatives considered**: Checking only in validators races; a deferred ordinary unique constraint cannot directly express the partial predicate; adding address timestamps is unnecessary; schema triggers duplicating the business state machine add complexity without a requirement for uncontrolled external writers.

Source: [PostgreSQL partial indexes](https://www.postgresql.org/docs/17/indexes-partial.html).

## 4. Request presence and normalization

**Decision**: Use DTO setters/binding with explicit presence flags for fields that distinguish omission/null, and trim textual inputs before Bean Validation. Use Java String.strip and locale-independent treatment where Java case conversion is needed; database-generated keys remain authoritative for identity comparison. Preserve internal whitespace, reject supplied blanks, apply limits after stripping, and explicitly reject protected fields.

**Rationale**: Ordinary nullable properties collapse omitted and explicit null. Validating before normalization incorrectly rejects padded values at maximum length. No new wrapper library is required. Pin normalization tests including Unicode whitespace handled by String.strip; do not add accent or compatibility normalization.

**Alternatives considered**: Generic maps weaken typed contracts; Optional alone is not a reliable presence contract; global mutation of every request can change unrelated endpoints. Use normalization only for this feature's request DTOs.

## 5. Error representation

**Decision**: ApiError extends Spring ProblemDetail, with standard status and top-level timestamp/message/path/fieldErrors extensions. Use one safe factory from MVC advice and Security handlers, never duplicate filter handling in advice.

**Rationale**: The approved decision preserves the constitution and exact client structure. Verify serialization against the actual Boot/Jackson dependency tree rather than assuming prior-version APIs.

**Alternatives considered**: Standalone ApiError violates the confirmed choice; wrapping successes is unnecessary; leaking framework exception messages is prohibited.

Source: [Spring MVC error responses](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html).

## 6. Absent infrastructure and dependency approval

**Decision**: Define integration prerequisites, not replacement auth/media components. JWT must expose immutable user ID, real filter handlers must emit the common error structure, and a media service must validate existing owned/available references. Media validation should be local database work through its service, not a network/S3 call while holding the user lock. Stop avatar implementation until the media entity ID/type and schema exist. Keep auth/media service dependencies acyclic.

**Rationale**: No such infrastructure exists in the current source tree. Pretending that a media stub validates ownership or bypassing security would produce a falsely complete feature. Interfaces can be documented now without approving unrelated feature implementation.

**Alternatives considered**: New login/storage implementations expand scope; rejecting every avatar ID or returning fake successes fails the contract. Validation starter/Lombok/Failsafe are proposed approvals, not silently installed dependencies. Boot 4.1.1 versus constitutional 4.1.2 is an explicit reconciliation gate.

All research questions have design decisions. External integration and build approval gates remain explicit prerequisites, not unresolved feature behavior.
