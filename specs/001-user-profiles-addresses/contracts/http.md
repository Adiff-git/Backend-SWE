# HTTP Contract

Base prefix `/api`; JSON request/success bodies, `application/problem+json` failures. All eight endpoints require a valid Bearer JWT resolving an immutable user ID. No additional role, permission or account-status restriction is introduced. No client-provided owner ID is trusted.

## Routes

| Method and path | Input | Output |
| --- | --- | --- |
| GET /api/me | No body | 200 PrivateUserResponse |
| PUT /api/me | UpdateUserRequest | 200 persisted PrivateUserResponse |
| GET /api/users/{userId} | Long path ID | 200 PublicUserResponse |
| GET /api/me/addresses | No body | 200 AddressResponse array, default first then ID descending; empty `[]` |
| POST /api/me/addresses | CreateAddressRequest | 201 AddressResponse; Location `/api/me/addresses/{id}` |
| GET /api/me/addresses/{addressId} | Long path ID | 200 owned AddressResponse |
| PUT /api/me/addresses/{addressId} | Long path ID, UpdateAddressRequest | 200 persisted AddressResponse |
| DELETE /api/me/addresses/{addressId} | Long path ID | 204, no body |

Malformed/non-Long IDs produce 400; well-formed missing IDs produce 404. Missing and foreign address IDs are indistinguishable 404 on read/update/delete. A missing current-user record after valid authentication produces 404. No generic success wrapper or pagination envelope.

## Schemas

| Response | Fields |
| --- | --- |
| PrivateUserResponse | id: Long; username: string; displayName: nullable string; email: nullable string; phoneNumber: nullable string; accountStatus: ACTIVE/INACTIVE/SUSPENDED/BANNED; gender: nullable MALE/FEMALE/OTHER; dateOfBirth: nullable YYYY-MM-DD; joinedAt: UTC ISO-8601 Z; profileImageId: nullable media-owned ID |
| PublicUserResponse | Only id, username, displayName, profileImageId, joinedAt with the same types |
| AddressResponse | id: Long; recipientName: string; recipientPhone: string; addressText: string; isDefault: boolean |

Nullable response properties are emitted as null for a stable schema. Public profiles never expose email, phone, addresses, dateOfBirth, gender, status, passwords, authentication material, roles/permissions or internal/generated identifiers. Address responses do not expose owner relationships. The exact non-null profileImageId wire type must reuse the media prerequisite; it is deliberately not guessed.

## UpdateUserRequest

Allowed properties: username, email, displayName, phoneNumber, gender, dateOfBirth, profileImageId. All may be omitted. An empty object is a successful no-op. Reject id/password/accountStatus/joinedAt/roles/permissions mutations with 400; these are not writable fields.

| Property | Omitted | Explicit null | Value |
| --- | --- | --- | --- |
| username/email | Preserve latest | 400 | Normalize, validate, update |
| displayName/phoneNumber/gender/dateOfBirth | Preserve latest | Preserve latest | Normalize/validate/update |
| profileImageId | Preserve | Clear reference | Validate owned/available media then replace |

Do not assume ordinary nullable binding distinguishes omitted/null. Wrong JSON types, unsupported enums, malformed dates and future dates are 400. UTC determines today's date. Removal/replacement never deletes the stored image. Existing tokens remain valid after username changes; token identity remains the user ID. Later successful lock-ordered writes win only for supplied applicable fields.

## Text validation

Trim surrounding whitespace before validation/storage/comparison, preserve internal whitespace, reject every supplied blank string including optional strings. Use feature-local String.strip normalization; no accent/compatibility normalization. Lengths are measured after trimming; never truncate.

| Field | Maximum | Additional constraints |
| --- | --- | --- |
| displayName | 100 | Optional |
| username | 50 | Nonblank; case preserved for display, lowercase comparison |
| email | 254 | Valid email, lowercase storage/comparison |
| phoneNumber | 20 | Optional; no additional address-phone pattern; non-unique |
| recipientName | 100 | Required on create/update |
| recipientPhone | 10 | Required, exactly `0[0-9]{9}`; non-unique |
| addressText | 1000 | Required on create/update |

Database-generated lowercase identity keys enforce case-insensitive uniqueness; conflict checks exclude the current user's ID. Other text remains case-sensitive. Multiple absent emails and repeated phone numbers are allowed. Null semantics above take precedence over generic optionality.

## Address request contracts

CreateAddressRequest and UpdateAddressRequest both require non-null recipientName, recipientPhone and addressText. Neither accepts an authoritative user ID. isDefault is an optional nullable boolean.

| Operation | omitted/null | false | true |
| --- | --- | --- | --- |
| First create | Sole default | Sole default | Sole default |
| Later create | Non-default | Non-default | Sole default |
| Update non-default | Preserve | Remain non-default | Sole default |
| Update default | Preserve | 400 | Remain sole default |

Maximum 20 addresses per owner. Creating at capacity is 400 with no mutation, regardless of requested default. Updates/deletes remain allowed at capacity. Deleting a non-default preserves the default; deleting the default promotes the highest remaining ID; deleting the last leaves an empty book. Each successful transaction preserves exactly one default for a non-empty book. Lock/count checks also cover concurrent first creates and count-19 races.

## Error schema and status mapping

ApiError extends ProblemDetail. Required fields: type (URI, `about:blank` if no specific type), title (safe status title), status (integer matching HTTP), detail (safe explanation), instance (request path), timestamp (UTC ISO-8601 Z), message (same safe explanation as detail), path (same as instance), fieldErrors (object mapping field names to arrays of safe messages; `{}` for non-field errors).

| Status | Conditions |
| --- | --- |
| 400 | Shape/validation failures, protected-field changes, invalid default change, capacity exceeded, invalid image reference |
| 401 | Missing, invalid or expired authentication |
| 403 | Genuine existing authorization failure; no new feature restriction |
| 404 | Missing user or missing/foreign address |
| 409 | Username/email conflict including races; pessimistic lock wait timeout |
| 5xx | Unexpected infrastructure failure, safely represented rather than recast as invalid input |

Lock timeout detail/message indicates a concurrent update and that the client may retry. No automatic retry. All failed writes fully roll back. Classify recognized constraint/lock failures without exposing SQL, constraint names, exception class names, stack traces, credentials, rejected secrets or driver messages. Both MVC advice and security-filter handlers serialize the same contract.

## Acceptance references

Verify every presence/default-table cell, normalized boundary and case-conflict pair, public/private allowlist, owner/missing 404 pair, token continuity, capacity boundary/race, coordinated concurrent edits, flushed-default rollback and lock timeout. See [spec.md](../spec.md) for the complete acceptance scenarios and [quickstart.md](../quickstart.md) for execution prerequisites.


## Engineering decision reference

Binding implementation constraints relocated from spec.md are maintained in [engineering.md](engineering.md) and [plan.md](../plan.md). This HTTP contract retains the exact routes, status mappings and error representation. Core-first sequencing in plan.md and engineering.md supersedes older broad prerequisites; the business specification describes observable outcomes.
