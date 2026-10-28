# Product Image Upload handoff

The public catalog owner removed upload-only implementation from `feature/product-catalog` on 2026-10-28. Recover any removed source or test from original commit `62980e03e86018a52731c405a5e5d32987a7096c` (backup branch `backup/product-catalog-before-upload-removal`). The approved [specification](spec.md), [data model](data-model.md), and [OpenAPI contract](contracts/openapi.yaml) remain authoritative. Generic user-owned Media Upload on `origin/feature/user-address` is **not a drop-in replacement** for Product Image Upload.

## Removed production files

- `demo/src/main/java/com/example/demo/catalog/entity/ProductImageUpload.java`
- `demo/src/main/java/com/example/demo/catalog/entity/ProductImageUploadState.java`
- `demo/src/main/java/com/example/demo/catalog/repository/ProductImageUploadRepository.java`
- `demo/src/main/java/com/example/demo/catalog/storage/ProductImageUploadStorage.java`
- `demo/src/main/java/com/example/demo/catalog/validator/ProductImageContentValidator.java`
- `demo/src/main/java/com/example/demo/catalog/exception/InvalidProductImageException.java`
- `demo/src/main/java/com/example/demo/catalog/exception/UploadPreparerMismatchException.java`
- Upload PUT, inspect, copy, verify, list, and delete methods from `S3ProductImageStorage.java`; upload-only repository queries and the WebP decoder dependency.

## Removed test files

- `demo/src/test/java/com/example/demo/catalog/repository/ProductImageUploadRepositoryIT.java`
- `demo/src/test/java/com/example/demo/catalog/service/ProductImageRecoveryServiceTest.java` — contains the **11 prepared T059 recovery scenarios**. They were disabled pending a real recovery service; do not count them as executed passing tests.
- `demo/src/test/java/com/example/demo/catalog/validator/ProductImageContentValidatorTest.java`
- `demo/src/test/java/com/example/demo/catalog/controller/ProductImageUploadControllerTest.java`

Upload-only portions were also removed from `S3ProductImageStorageTest.java`, `CatalogEntityMappingsIT.java`, `CatalogConstraintsIT.java`, `CatalogFixtures.java`, and `CatalogSecurityBaselineTest.java`. The latter still checks that both product upload POST routes are denied. Recover prior versions from the original commit when integrating.

## Integration requirements

- Preserve applied catalog Flyway migrations V1–V3, including `product_image_uploads`, `product_images`, their foreign keys, uniqueness rules, and deferred gallery constraints. Do not reuse the teammate's separate Long-ID `image_uploads` table as though it were product upload persistence.
- Implement the approved product-scoped prepare and complete routes, UUID upload IDs, verified ADMIN preparer identity, product ownership, image metadata, gallery count/order/primary limits, actual-byte validation, fifteen-minute session deadline, PostgreSQL-side final eligibility, claim fencing, idempotent replay, and safe recovery/cleanup. Keep S3 work outside database locks and ensure private objects and unsigned object-key access stay denied.
- Reconcile the teammate's S3/LocalStack and authentication infrastructure deliberately. Their generic Media route uses user ownership and fifteen-minute download URLs; public product reads require **five-minute signed GET URLs** and PUBLISHED-only visibility. Keep the public signer and read projections working during integration.
- Rebuild upload-specific tests and verify real LocalStack PUT/copy/delete, conditional copy, signature expiry, anonymous direct-object denial, failure recovery, races, and ADMIN authorization. T051–T062 remain teammate-owned or pending as shown in [tasks](tasks.md). T063, T065, and T066 remain shared final integration gates.

This checkout intentionally has no product upload service or active upload controller. Existing security denies those POST routes until verified integration is complete.

## Draft version 1 integration

See [integration decisions](../draft-version-1.md) for the shared bucket, storage configuration, CORS and deferred product-upload scope that supersede the earlier handoff assumptions.
