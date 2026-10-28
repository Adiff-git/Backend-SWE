# Domain Service Contracts

Dependency direction: user → media → storage. Authentication continues supplying trusted current identity; media does not call user services or repositories. Database foreign keys do not create a Java dependency cycle.

## MediaService / MediaServiceImpl

Public operations: createImageUpload(actorId, request), completeImageUpload(actorId, imageId), getImageDownload(actorId, imageId). Use response DTOs for public operations, not JPA entities. Controller supplies actorId only through CurrentUserIdProvider. Completion facade is nontransactional and delegates to separately proxied MediaCommandService; enforce its no-ambient-transaction entry contract as existing auth facades do.

Consumer operations: validateImageForAttachment(actorId, imageId) returns prepared immutable media data only after owner/state/final-object checks; getAttachedImage(imageId) returns prepared access for a reference already authorized by the consuming service. The latter is internal and never exposed as a generic owner-bypassing controller route. Its caller must establish profile visibility first. Response preparation may include id/finalUrl; no caller receives a repository handle or accepts arbitrary storage keys.

UserService stores only the validated scalar image ID. It calls media for explicit replacement, and prepares image access for persisted profile responses. UserConverter receives prepared image response data as an argument and performs no storage/database access. MediaConverter likewise maps supplied values; validators receive loaded metadata/decoded content instead of initiating hidden persistence work.

## MediaCommandService / MediaCommandServiceImpl

Owns write transactions and ImageUploadRepository. create persists/signs coherently; completion returns a typed outcome carrying COMPLETED data or EXPIRED after successful transaction. Facade throws UploadExpiredException only after the command has returned and committed. Follow existing auth outcome pattern; do not rely on self-invocation or REQUIRES_NEW.

Repository retrieval names distinguish `getImageUploadEntity` and response retrieval consistently with the constitution. Repository lookups scope by id+owner for indistinguishable not-found behavior. @Version is checked on transition flush/commit; no automatic optimistic retry. Read-only operations use appropriate read transactions.

## ObjectStorageService / ObjectStorageServiceImpl

One concrete S3-backed infrastructure implementation exposes prepareUpload(key, contentType, deadline), inspect(key), readIfMatch(key, etag), copyIfMatch(source, etag, target, approvedContentType), verifyFinalExists(key), prepareDownload(key, lifetime), and deleteStaging(key). prepareUpload returns PreparedUpload containing the URL and exact signed expiration (second precision); creation persists that expiration and derives its immutable creation instant 900 seconds earlier. Signing precedes persistence so a signing failure creates no record; a persistence failure returns no capability. SDK-specific objects stay within storage; media receives explicit metadata/content handles with close semantics. This is a narrow service boundary, not a provider strategy/factory framework.

Source ETag is opaque. Copy uses source precondition and REPLACE metadata with approved type/headers. LocalStack and AWS use the same implementation; configuration supplies endpoints/credentials. Dispose temporary content even on failure and configure bounded calls. Do not enable HTTP wire logging of signed requests.

## Exceptions

Plan domain-specific ImageUploadNotFoundException (404), UploadExpiredException/UploadStateConflictException/ObjectChangedException (409), InvalidImageContentException (422). Boundary mapping covers unavailable storage (503), malformed IDs/declarations (400), optimistic conflicts (409), unexpected database errors (500). Do not repurpose generic user MediaUnavailableException as a permanent rejection of non-null images; replace the deferred binding boundary only in the approved avatar integration.


## Confirmed content identity clarification (2026-10-27)

Authoritative content-identity requirements and acceptance outcomes are defined in [spec.md FR-008 and Content Identity Decision](../spec.md#content-identity-decision-2026-10-27). This document references that definition rather than restating it.
