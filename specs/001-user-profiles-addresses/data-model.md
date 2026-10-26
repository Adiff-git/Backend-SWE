# Data Model

## User - users

| Attribute / column | Java / PostgreSQL | Constraints |
| --- | --- | --- |
| id | Long / BIGINT | PK, non-null, immutable; preserve prerequisite identity generation |
| displayName / display_name | String / VARCHAR(100) | Nullable; supplied blank rejected after trim |
| username | String / VARCHAR(50) | Non-null, nonblank, trimmed; casing preserved |
| usernameKey / username_key | Database-generated lowercase username | Non-null unique key, not an editable/entity business field |
| password | String / TEXT | Non-null encoded credential only; lifecycle owned by authentication prerequisite; no response/update binding |
| email | String / VARCHAR(254) | Nullable; valid nonblank when supplied; trimmed lowercase |
| emailKey / email_key | Database-generated lowercase email | Nullable unique key; multiple nulls permitted |
| phoneNumber / phone_number | String / VARCHAR(20) | Nullable, trimmed/nonblank when supplied, not unique |
| accountStatus / account_status | AccountStatus / VARCHAR(16) | Non-null string enum; lifecycle initializes through approved identity contract |
| gender | Gender / VARCHAR(6) | Nullable string enum |
| dateOfBirth / date_of_birth | LocalDate / DATE | Nullable; not future using UTC clock at validation time |
| joinedAt / joined_at | Instant / TIMESTAMPTZ | Non-null, set on creation and immutable to profile writes |
| profileImage / profile_image_id | LAZY media entity reference / media-owned ID type | Nullable FK; schema/type comes from actual media prerequisite; no invented Long/UUID media policy |

AccountStatus: ACTIVE, INACTIVE, SUSPENDED, BANNED. Gender: MALE, FEMALE, OTHER. Use CHECK constraints for enum values and nonblank stored text; length columns enforce maxima. Do not use volatile current-date birth-date CHECKs as substitutes for request validation. User creation is not an endpoint in this feature.

Unique constraints: `uk_users_username_key`, `uk_users_email_key`. Generated key expressions use PostgreSQL LOWER and inherit the approved database collation. Do not add case-sensitive uniqueness on username/email. Protect trimmed/nonblank stored values; service email canonicalization uses the same database lower semantics. No phone uniqueness.

## Address - addresses

| Attribute / column | Java / PostgreSQL | Constraints |
| --- | --- | --- |
| id | Long / BIGINT | PK, generated; highest remaining value determines fallback |
| recipientName / recipient_name | String / VARCHAR(100) | Non-null, trimmed, nonblank |
| recipientPhone / recipient_phone | String / VARCHAR(10) | Non-null; ASCII pattern `^0[0-9]{9}$` after trim; not unique |
| addressText / address_text | String / VARCHAR(1000) | Non-null, trimmed, nonblank; internal whitespace preserved |
| isDefault / is_default | Boolean / BOOLEAN | Non-null |
| user / user_id | LAZY User / BIGINT | Non-null FK to users.id, exactly one owner |

Indexes: `idx_addresses_user_id_is_default_id` supports scoped ordered reads; `idx_addresses_user_id_default` is UNIQUE on user_id WHERE is_default (partial unique index, not an ordinary UNIQUE constraint). Avoid redundant user-only indexes unless query plans show a need. No createdAt column solely for fallback selection. Do not add cascading deletes into media. User deletion semantics must reuse the eventual identity lifecycle; no user-deletion capability is implemented here and conservative FK rejection avoids inventing a cascade.

## Aggregate invariants and transitions

Every mutation locks users.id before address state reads. The lock coordinates all feature writers; it does not implicitly lock addresses. All transitions are transactional and the count must never exceed 20.

| Operation | Result |
| --- | --- |
| First create, any default input | New sole default |
| Later create, omitted/null/false | New non-default, old default retained |
| Later create, true | Clear/flush old default then insert new default |
| Create at count 20 | 400, no change |
| Update, omitted/null default | Preserve latest flag; require all recipient fields |
| Update, true | Clear/flush old default, promote target |
| Update current default, false | 400, no change |
| Update non-default, false | Allowed |
| Delete non-default | Existing default retained |
| Delete default with remaining rows | Delete/flush, promote highest remaining ID |
| Delete final row | Empty book, no default |
| Lock wait timeout | 409, rollback, no automatic retry |

At-most-one is protected by the partial index. At-least-one and maximum capacity are protected by the shared lock and business transactions. External writers must not bypass this protocol.

## Update and response model

Use separate UpdateUserRequest, CreateAddressRequest and UpdateAddressRequest, and separate PrivateUserResponse, PublicUserResponse and AddressResponse. Public allowlist is id/username/displayName/profileImageId/joinedAt only. No internal generated keys, passwords, entity relations or user_id appear in public/address DTOs. Private response includes the agreed personal details but never credentials/roles/permissions.

Presence-aware selective profile PUT preserves omitted fields, rejects explicit null username/email, preserves null optional scalars and removes profileImageId only on explicit null. No string clearing by blank input. Avatar IDs use the actual media-ID JSON contract. Image validity/ownership is checked through the media service; replacing/removing a reference never deletes storage.

## Migration sequencing

Inspect prerequisite schema/migration history at implementation time. If User already exists, extend it with a new migration; otherwise coordinate a single shared User schema with the identity prerequisite. Never create duplicate users tables/entities. User creation/lifecycle owns password/status initialization. Add address schema and constraints in new versioned migrations only. Add the media FK after the real media migration, preserving existing data; no destructive rewrite or guessed reference type. Existing case-colliding data requires a separately approved remediation plan, never silent deletion. No migrations are executed during planning.
