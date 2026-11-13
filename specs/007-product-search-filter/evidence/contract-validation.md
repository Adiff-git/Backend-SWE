# T006 — OpenAPI 3.1 semantic validation

**Status: PASS — approved isolated verification executed on 2026-11-09.** Dependency T001 is complete. This is preimplementation contract acceptance,not endpoint/deployment acceptance.

## Tool authorization and isolation

The current user explicitly authorized openapi-spec-validator==0.9.0,openapi-schema-validator==0.9.0,OAS31Validator and required URI-format dependencies in a temporary virtual environment. Tooling was installed only in `/tmp/backend-swe-t006-validator`,using Python3.14.9;include-system-site-packages=false. jsonschema==4.26.0 with format-nongpl and PyYAML==6.0.3 were used. URI dependencies include rfc3986-validator==0.1.1 and rfc3987-syntax==1.1.0. No application/CI dependency was added.

[openapi-semantic-verification.json](openapi-semantic-verification.json) records versions,commands,stdout/stderr,exit codes,checker results,artifact hashes and downloaded wheel hashes. [t006-validator-freeze.txt](t006-validator-freeze.txt) pins the complete temporary environment. Wheel downloads are under /tmp only.

## Actual results

| Check | Result |
| --- | --- |
| Feature and baseline Catalog documents | Both OpenAPI3.1 validator CLI executions exit0;both OpenAPIV31SpecValidator results valid |
| Local references | 60 references resolve across feature/base documents |
| Schema examples | 41 positive/negative cases pass with Draft202012Validator and all41 replay successfully with explicit OAS31Validator |
| Negative document controls | Missing info,duplicate query parameter and duplicate operationId rejected |
| allOf / unevaluatedProperties | Valid composed responses accepted;missing/wrong/unknown fields rejected |
| Nullable responses | Explicit null unions and paired-null listing accepted;mixed/missing pair rejected;legacy nullable:true alone does not admit null |
| URI checking | URI checker actually registered;invalid sentinel rejected;nullable image URL retained |
| Query definitions | page,size,UUID,brand array,price,sort positive/negative cases;form/explode assertion and URL-encoded specFilters roundtrip |
| Responses/errors | Listing/detail,400/401/500/503 schema cases,exact-only mode header;no listing409 |

Detailed inputs,expectations and actual outcomes are in [openapi-capability-results.json](openapi-capability-results.json). The previous limited probe is preserved as [openapi-capability-results-pre-restoration.json](openapi-capability-results-pre-restoration.json);its missing-URI/tool limitations are historical,not current results.

## Authorized compatibility restoration

Inspected baseline constraints and applied [contract-compatibility-proposal.patch](contract-compatibility-proposal.patch) only to the feature contract:

- ProductListingItem.name:minLength1.
- CategorySummary.name:minLength1.
- ProductListingItem.primaryImageUrl:format uri.

A deep parsed-document comparison proves exactly these three additions;all null unions,paired-null composition and other semantics remain unchanged. [contract-restoration-results.json](contract-restoration-results.json) records before/after hashes. The baseline Catalog contract was not modified during this task. T006-C01 is resolved;negative examples reject empty names/invalid URI. The patch is now APPLIED and must not be reapplied.

## Prose-only constraints and verification boundaries

- specFilters without categoryId is invalid;unsupported keys for a valid category yield400;valid supported no-match yields200 empty. Schema shape/serialization is verified here;registry applicability/type/unit/operator approval remains T003 and later semantic integration work.
- Equal duplicate raw JSON members are invalid,including nested members. JSON Schema receives decoded objects and cannot detect duplicates. Existing T012 parser/source and [parser-listing-results.json](parser-listing-results.json) provide separate syntax/duplicate/null/typed operand evidence;T012 was not rerun or modified. URL examples are transport roundtrips,not live HTTP requests.
- Unknown parameters,raw limits before dedup,range/category relationships and missing/null operands require parser/semantic checks beyond generic OpenAPI document validation. The existing contract and T012 records retain these requirements.
- Mode-header emission,required capability-loss503 and HTTP/body status correspondence need runtime tests. Static headers/error schemas were checked;no endpoint,registry approval or deployed capability is claimed.
- Paired-null fields do not establish orphan eligibility;no listing409 is introduced and detail behavior stays unchanged. T064 still revalidates actual responses later.

## Reproduction

Run from repository root in a fresh temporary environment:

```sh
python3 -m venv /tmp/backend-swe-t006-validator-repro
/tmp/backend-swe-t006-validator-repro/bin/python -m pip install -r specs/007-product-search-filter/evidence/t006-validator-freeze.txt
/tmp/backend-swe-t006-validator-repro/bin/python -m openapi_spec_validator specs/007-product-search-filter/contracts/openapi.yaml
/tmp/backend-swe-t006-validator-repro/bin/python -m openapi_spec_validator specs/001-product-catalog/contracts/openapi.yaml
/tmp/backend-swe-t006-validator-repro/bin/python specs/007-product-search-filter/evidence/probe-openapi-capabilities.py --approved-tools
```

The probe writes its scoped evidence output. This authorization does not approve permanent tooling adoption. No application code,migration,shared DB,commit or push was involved.
