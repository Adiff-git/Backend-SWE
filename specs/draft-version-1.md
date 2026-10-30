# Draft version 1 integration

This branch combines feature/user-address and feature/product-catalog.

- Flyway V1-V3 are catalog; V4-V7 are user/address/auth; V8-V9 are UUID media and avatar references. No deployed database needs migration-history conversion.
- All API security chains use SharedCorsConfiguration: any origin, method and request header; Authorization exposed; browser credentials disabled. Existing endpoint authorization is preserved.
- Catalog image reads use the server storage.s3 configuration, bucket, credentials and browser signing endpoint. There are no separate catalog.images settings. Signed GET URLs last 300 seconds; upload sessions and PUT URLs last 900 seconds.
- Catalog retains a small domain adapter for its error contract. Shared storage prepares a credential snapshot before catalog row locks, then signs locally without S3 requests while locks are held.
- Generic image uploads and avatar references use canonical UUIDs. User IDs remain Long.
- Product-scoped upload/gallery services and POST endpoints remain deferred and denied, as agreed. Existing catalog schema is retained for later implementation.
- Bucket bootstrap is available in docker/localstack/init/10-init-s3.sh, with inline wildcard CORS. Compose does not execute it automatically; bucket initialization remains deferred.
- Native LocalStack persistence remains an open item recorded in the image-storage tasks.

Validate Java with `cd demo` then `./mvnw clean verify`. Validate the catalog import utility with `python -m unittest discover -s tools/catalog-import -p "test_*.py"` from the repository root.
