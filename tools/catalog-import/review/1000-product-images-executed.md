# Completed local import: 1,000 products with images

Verified on 2026-10-29 against `backend@127.0.0.1:55432`, Compose project `catalog-pilot-20261029-01`, API `http://127.0.0.1:18080`, signed storage gateway `http://127.0.0.1:14566`.

The user explicitly instructed this session to use the existing image sources and begin the import. The private execution record identifies that conversation instruction as its evidence, with local download/private storage scope only. It does not assert a separate retailer license or authorize redistribution.

## Final measurements

| Measurement | Verified result |
|---|---:|
| Products / variants / categories | 1,000 / 1,000 / 31 |
| Products with usable stored images | 1,000 |
| Products without images | 0 |
| ProductImages / verified unique stored objects | 1,096 / 1,096 |
| Invalid galleries / duplicate object keys | 0 / 0 |
| PUBLISHED / DRAFT | 5 / 995 |
| Total referenced image bytes | 25,963,954 |
| Completed batches / failed batches | 40 / 0 |

The inventory contains 1,130 source URLs. The existing ten-image-per-product limit selected 1,096 entries; 34 entries beyond that limit were intentionally excluded. Every selected entry completed and its actual stored bytes passed image validation and SHA-256 comparison. Each product has exactly one primary image. Product UUID/source identity mappings were validated against seed v1 by the importer; existing product, variant, category, price and publication data passed the importer's seed comparison.

## Execution and acceptance evidence

The existing persistent containers were stopped at the start of execution and were restarted using the existing environment and volumes. A new pre-write backup is retained as ignored `output/catalog-before-authorized-images.dump`, mode 0600. No existing product was reimported or published.

The five PUBLISHED products passed the initial pilot. The remaining inventory ran in batches of 25, with at most four concurrent batches on disjoint products and separate private checkpoints. All writes used the Product upload prepare/PUT/complete workflow. The final checkpoint merges completed entries preferentially and contains 1,096 completed entries across 1,000 products, with no errors.

Acceptance checks completed:

- The image-import unit suite ran 8 tests successfully.
- Full rerun in resume mode reported 1,096 completed, 1,096 verified unique images and zero errors. Database metadata and counts remained unchanged.
- LocalStack and the signed gateway were restarted. All 1,096 durable object checkpoints restored successfully; full verification again reported 1,096 verified images and zero errors.
- Before/after restart metadata SHA-256 was identical: `fedefdabb63f4447ef2d95e855ee8d1cb2fa2d2d58cd0ca311937aeb7df1a69d`.
- All five public listing thumbnails returned HTTP 200 with real image bytes; their detail galleries each contained a primary image. Unsigned object requests returned HTTP 403, including after restart.
- Database audit found zero missing-image products, zero invalid galleries and zero duplicate object keys.

Private, Git-ignored evidence is under `tools/catalog-import/output/`: `approved-media-permission.json`, `parallel-images-results.json`, individual batch checkpoints/logs, `real-image-checkpoint.json`, `product-object-checkpoints/`, `before-storage-restart-audit.json`, `final-catalog-image-audit.json`, and `public-image-verification.json`. Keep these checkpoints with the existing project volumes: they provide the documented local storage restoration mechanism.

## Scope

The local catalog image import is complete. Publication remains five PUBLISHED and 995 DRAFT, so the anonymous catalog still lists five products, now with images. This workspace's Frontend-SWE contains only its README; browser/frontend rendering was not verified. Public API/image delivery was verified. Seed v1 was not modified; no image-bearing portable seed or external distribution was performed. No new full Maven run, commit or push was performed during this execution.
