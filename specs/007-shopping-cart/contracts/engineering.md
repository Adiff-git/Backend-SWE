# Cart engineering contract

Current confirmed requirements are in ../spec.md (2026-11-17). The previous 1,000-unit and target-plus-request switch semantics have been replaced, not retained as alternative rules.

Preserve single-vendor scope, UUID catalog IDs, Long owner/cart/item IDs, PUBLISHED/current valid-price eligibility, lazy persistent cart creation, safe ownership, snapshots, complete ordered responses, exact VND calculations and transaction guarantees. No inventory, orders, checkout, voucher or guest implementation is added.

The seven-route contract uses productVariantId on add/variant requests, separate quantity/variant PUTs, strict token shapes, 200 add and totalItemCount in the complete response. Variant-only requests never carry quantity. New target quantity is 1; existing target remains entirely unchanged; current variant is no-op. All requested and final added quantities remain 1..999.

Test responsibilities: validator arithmetic/bounds; pure calculator/converter totals/snapshots; MVC binding/security/status; facade preparation and transaction mutation unit tests; PostgreSQL mapping/migration constraints; committed service rollback/concurrency; real authenticated HTTP/later-session flow. Retain independent transactions and bounded barriers. Required regressions include 998+2 unchanged, same-variant quantity preservation, source reset, entire target preservation, unknown/foreign parity, signing rollback, selected/total row distinction and legacy-1000 migration rejection/retry. Do not weaken unrelated migration coverage when assigning new Flyway versions.
