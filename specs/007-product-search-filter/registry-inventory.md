# Specification registry evidence inventory

Inspected 2026-11-08. Source: sibling Catalog-Data/data/specifications.jsonl, variants.jsonl, category_mapping.json and data/approvals/amazon_import_approved_1500.csv. These local paths are evidence inputs, not a new runtime dependency or proof of live DB contents.

All 1,500 specification records carry approval=UNVERIFIED. Category mapping version1 has APPROVED entries; import CSV approves import/category/variant/SKU-policy/price but has no specification search/filter approval columns. Publication remains PENDING_APPROVAL there. No authoritative approved filter-registry artifact was found in the inspected Backend-SWE/Catalog-Data sources.

| Observed key | Shape / unit | Observed category labels | Source rows containing key | Activation state |
| --- | --- | --- | --- | --- |
| battery_capacity_mah | quantity / mAh | Charger & Power Bank | 54 | Observed, NOT APPROVED |
| cpu_family | text | Laptop | 64 | Observed, NOT APPROVED |
| cpu_model | text | Laptop, Smartphone | 100 | Observed, NOT APPROVED |
| display_diagonal | quantity / inch | Laptop, Smartphone, Tablet | 361 | Observed, NOT APPROVED |
| display_resolution | text | Laptop | 105 | Observed, NOT APPROVED |
| gpu_model | text | Laptop | 137 | Observed, NOT APPROVED |
| installed_storage_gb | quantity / GB | Laptop | 148 | Observed, NOT APPROVED |
| internal_storage | quantity / GB | Smartphone, Tablet | 217 | Observed, NOT APPROVED |
| physical_ram | quantity / GB | Laptop, Smartphone, Tablet | 349 | Observed, NOT APPROVED |

Counts overlap, are not product totals, and do not establish semantic correctness. Numeric and boolean scalar shapes are structurally supported by TechnicalSpecificationValidator, but no active approved numeric/boolean key is demonstrated by this source inventory. Synthetic numeric/boolean registry fixtures may test the engine, clearly labelled test-only; they never establish production keys.

Observed category UUID bindings: Laptop: 31549723-c1a2-56a6-a1a1-64da36e16f65; Smartphone: 6d50755f-c3bd-521d-9eb8-2abaac52fdf6; Tablet: 5793dbd1-a3e4-514e-bf1d-ef50a31acc04; Charger & Power Bank: f065e70e-4abe-5b7f-9112-264bd82c2930. Labels/UUIDs are evidence from the package, to reconcile against target taxonomy before activation. Other categories have no observed keys in this table, which does not prove their permanent lack of specifications.

Proposed/illustrative only in docs/catalog-enhancement/architecture.md: installed_storage, refresh_rate, nfc_supported and its sample typed document. Do not rename installed_storage_gb to installed_storage, unify it with internal_storage, or add these illustrative keys without reviewed semantic mapping. Ranges/lists discussed there are not implemented value capabilities in the current scalar validator.

Approval record must identify registry version, canonical key meaning, category UUIDs, type, canonical unit, allowed operators, searchable/filterable flags, identifier handling, source references/hashes, approver and evidence reference. A planned runtime file may contain only activated APPROVED definitions. Unsupported/unapproved requests return400; an incomplete registry must never enable permissive fallback. B1 remains open.

Design correction note2026-11-08: D1 typed keyword representations do not activate any key, translate units or create approval evidence. D5 unsupported-category400 is determined by an approved registry/taxonomy, not by these observed counts. B1/CHK005 remains NEEDS VERIFICATION; all nine inventory entries retain NOT APPROVED.


## Current owner-authorized execution revision

Definition approval is now recorded in evidence/registry-approval.json under the owner MISSION. Four keys/five bindings only;all source facts remain UNVERIFIED. Runtime UUID reconciliation uses actual unique Backend Laptop/Tablet rows;missing/ambiguous bindings are disabled. The disposable-fixture result is not deployment taxonomy verification. See mission-scope.md and evidence/local-data-policy-audit.json.
