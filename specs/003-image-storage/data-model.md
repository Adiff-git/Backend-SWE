# Data Model: Image Storage

## ImageUpload / image_uploads

| Java field | PostgreSQL column/type | Integrity rule |
| --- | --- | --- |
| id: Long | id BIGINT identity | Primary key |
| ownerUserId: Long | owner_user_id BIGINT | NOT NULL, FK users(id), immutable |
| stagingKey: String | staging_key VARCHAR(512) | NOT NULL, nonblank, UNIQUE uk_image_uploads_staging_key |
| finalKey: String | final_key VARCHAR(512) | Nullable, nonblank when present, UNIQUE uk_image_uploads_final_key |
| contentType: String | content_type VARCHAR(100) | NOT NULL; CHECK exact three allowed MIME values |
| status: ImageUploadStatus | status VARCHAR(16) | NOT NULL; CHECK PENDING/COMPLETED/EXPIRED; JPA EnumType.STRING |
| expiresAt: Instant | expires_at TIMESTAMPTZ | NOT NULL; later than created_at |
| createdAt: Instant | created_at TIMESTAMPTZ | NOT NULL, immutable |
| version: Long | version BIGINT | NOT NULL, initial zero; technical optimistic @Version |

CHECK state/key coherence: COMPLETED requires nonblank final_key; PENDING and EXPIRED require final_key IS NULL. A failed copy's candidate key is never persisted as a published key. Unique final_key permits multiple SQL NULLs. Add `idx_image_uploads_owner_user_id` and `idx_image_uploads_status_expires_at` for owner operations and manual reconciliation. Do not add updatedAt indiscriminately. No URLs or bytes reside in this table.

## Existing User / users

Add nullable `profile_image_id BIGINT` FK to image_uploads(id) with `idx_users_profile_image_id`. Existing rows remain null, existing fields/IDs unchanged. Scalar ID avoids a domain dependency from media back to user; database ownership linkage does not imply a Java service dependency. No cascade remove or orphanRemoval, and no automatic object deletion. Restrict deletion of referenced users/images rather than cascading independently owned data.

The FK proves existence only. Media validators additionally enforce caller ownership, COMPLETED, valid final key and available object. Responses use prepared ID/URL values, never JPA traversal or URL generation in converters. No review/product relationships are introduced.

## State transitions

| State/input | Result |
| --- | --- |
| Create at T | PENDING, expiresAt = T + 900 seconds |
| Owned PENDING before deadline, successful validated copy and version-checked commit | COMPLETED, finalKey set |
| Owned PENDING at/after deadline | Commit EXPIRED with null finalKey, then return 409 |
| PENDING validation/infrastructure failure before transition | Remain PENDING; no published key |
| COMPLETED repeated completion | Same state/key/ID; fresh 900-second URL |
| EXPIRED completion | 409; no outgoing transition |
| COMPLETED after original deadline | Still usable; never time-expired by upload deadline |

Check deadline on admission and immediately before transition to COMPLETED. If validation crosses the deadline, commit EXPIRED and treat any attempt copy as an orphan; return 409 after commit. This defines the application's authoritative deadline check; it does not claim database commit wall-clock precision.

## Binding and lifecycle rules

Keep presence-aware UpdateUserRequest handling. A supplied non-null value must be an integral positive Long JSON number; reject coercion from strings/booleans/fractions/arrays or overflow. Omitted preserves latest stored reference under the existing user update coordination; null clears; valid explicit value replaces. Every other profile edit rolls back on failed image selection. No duplicate-ID or ordering rules are needed for the scalar field.

Use safe Lombok entity annotations, protected no-argument constructor, no @Data/unsafe equality. Create a new migration, tentatively V5__image_uploads.sql after existing V4; recheck numbering before implementation. Do not edit applied migrations or use ddl-auto update. Test migration with existing user rows and constraints via PostgreSQL Testcontainers.
