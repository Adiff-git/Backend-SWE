# Frozen Artifact Contract

**Version**: demo-bootstrap-artifact-v1. Separate from the unchanged source dataset and from per-import resolved timestamps/password hashes.

**R-40 confirmed volume**: Freeze exact review/rating group assignments, seeded uniform per-product count draws and feasible actual integer ratings. Record resulting R reviews and sufficient C >= 9,000 completed orders; total orders C + 3,000 with other statuses fixed at 900/750/750/400/200. Freeze completed/total item counts and actual exact statuses. No obsolete fixed review/order targets, aggregate substitutes, count resampling/clipping or review-distribution tolerance.

## Planned layout

```text
<artifact>/
  manifest.json
  catalog/products.jsonl
  catalog/variants.jsonl
  catalog/categories.json
  catalog/brands.json
  assets/images.json
  provenance/fields.jsonl
  provenance/source-links.jsonl
  scenarios/recipe.json
  scenarios/demo-accounts.json
  rules/options.json
  rules/pricing.json
  rules/distributions.json
  review/decisions.jsonl
  review/summary.json
```

Images reference unchanged source files with strict expected hashes rather than duplicate or download unapproved media. Private educational packaging/submission is restricted to team members and the instructor under F-39; public redistribution and commercial use are prohibited. Preserve original provenance; neither educational scope nor private submission asserts verified permissions. Human review/AI raw workspaces stay outside the approved artifact until sanitized and intentionally included. Reject absolute/parent-traversal/symlink-escape references; all file paths resolve within approved source or artifact roots.

## Manifest fields

| Field | Required value/semantics |
|---|---|
| contract_version | demo-bootstrap-artifact-v1 |
| dataset_id, dataset_version | Stable named profile and reviewed release version; no latest-only identity |
| source | Repository revision, original manifest identity and hash, exact tracked-blob checksums and newline-equivalence report |
| files | Relative path -> SHA-256/byte size for every payload file; no unlisted consumed data |
| artifact_digest | SHA-256 of canonical manifest payload excluding the digest field itself; payload includes file digests and review/rule identity |
| importer_compatibility | Supported importer contract versions plus exact migration-history/file/Flyway-checksum expectations |
| rules | Option vocabulary, Decimal arithmetic/rounding, dimension factors, sampling/PRNG and temporal recipe versions |
| generation | Seed 20261126, tool/runtime/dependency-lock digest; AI model identity, prompt hashes and sanitized response references |
| scope | Expected initial counts/statuses, variant disposition totals and allowed bounds, eight voucher scenarios |
| review | Reviewed catalog/rule payload digest excluding review files and manifest, reviewer references, flags/fallback decisions, sampled coverage/category accounting and unresolved_error_count=0 |
| approvals | Local-only publication and interview decision references, original price approvals, reviewed enrichment release |
| usage_scope | scope_decision_status=COMPLETE; usage_scope=PRIVATE_NONCOMMERCIAL_UNIVERSITY_COURSE; access_audience=TEAM_MEMBERS/INSTRUCTOR; private instructor submission only |
| future_use_permissions | public_redistribution_verification=NOT_APPLICABLE_CURRENT_SCOPE; public_redistribution_approved=false; commercial_use_allowed=false; copyright_clearance_status=UNVERIFIED; permission_evidence_status=UNVERIFIED; future_permission_verification_required=true |

Canonical JSON is UTF-8 with sorted object keys, deterministic record sorting and no nonfinite numbers. JSONL record order is stable by entity logical ID. File checksums are strict byte checksums; normalize only when explicitly creating derived output, not when validating an arbitrary changed source/artifact. Store precise money/quantity decimals without floating-point ambiguity. Avoid digest cycles: review records bind the catalog/rule payload digest; the final manifest includes review-file hashes, and a detached final approval report binds the resulting full artifact digest.

## Product/variant provenance

Every product links to original source_identity/ASIN and original UUID. Original variant records carry original UUID/SKU/base_price exactly and a source origin flag. Synthetic siblings carry stable namespace-derived UUID, unique SKU, product UUID, reviewed option dimensions/map, typed specification document, approved price factors/base price and synthetic origin flag. Field-level provenance identifies extracted evidence versus generated values; model confidence is not approval.

To enrich, every original dimension/value must have evidence; otherwise keep the product's original single variant with empty options and report fallback. Do not put an empty original variant beside nonempty synthetic combinations. Use the same dimension set for every sibling; actual database canonicalization is final validation. Allow sparse valid combinations rather than materializing a full Cartesian product. Do not silently rename/merge source products or invent filterable fields.

## Relative scenario recipe

Record fixed logical identities, product/variant selections, statuses, quantities, ratings and relative calendar/time expressions or frozen deterministic choices. It contains no real personal data or auth secrets. One persisted attempt anchor resolves all expressions together. Calendar subtraction specifies end-of-month clamping and ordered lifecycle offsets; timezone UTC, timestamps microsecond precision. A replay of the same artifact+anchor+runtime lock yields the same business graph, excluding fresh BCrypt salt/operational timestamps and harmless identity-sequence gaps. Fresh runs intentionally use a new anchor; normal never rewrites existing dates.

Initial exact scope is 500 customers/12 logins/512 addresses, artifact-frozen R eligible reviews and C + 3,000 orders with approved derived/fixed status counts, 300 populated carts, 3,500 wishlist entries and 360 events. R/C and item totals are derived after sampled per-product review demand and purchase/popularity feasibility, then become exact artifact acceptance values. Variants remain data-dependent 1–6/product under the approved fallback policy. Pin each artifact's actual final variant count for exact import verification.

## Acceptance/rejection

Accept only supported contract, exact declared source, valid hashes/review coverage, no unresolved errors, declared compatible schema and all approved count/rule constraints. Reject unsafe paths, duplicate IDs/SKUs/canonical options, invalid price/specification relationships, silent source modifications, incomplete provenance masquerading as complete source links, or source/publication approval overreach.

Changed payload/checksum/rules/model-reviewed output is a new artifact version and requires review, not normal startup reseeding. Missing genuine source-link fields omit those application rows and report them without omitting catalog products. AI calls occur only in offline maintainer preparation and never in bootstrap/import/startup.
