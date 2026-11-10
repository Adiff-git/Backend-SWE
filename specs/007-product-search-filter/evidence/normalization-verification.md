# T013 — SQL/Java primitive parity and immutable indexing prerequisites

Status: PASS for the recorded disposable environment/corpus. T002/T007 prerequisites complete. Not vocabulary approval,B3 sign-off,fuzzy calibration,Flyway migration,production index approval or deployment proof.

## Actual results

[normalization-parity-results.json](normalization-parity-results.json) records actual context-free Java outputs and PostgreSQL outputs for53 inputs:all16 original D2 corpus cases plus37 boundary probes (NFC/NFD,Vietnamese/đ/case,all25 specified whitespace code points,empty/null/control handling,repetitions,supplementary letter case,Greek/Turkish case and Arabic decimal digits). Normalized strings,ordered tokens and structural flags match53/53. Controls reject on both sides. This compares Java normalize/tokenize primitives,not normalizeQuery HTTP limits;nonblank zero-token query rejection is separately tested by T007/T012. Original normalization-cases.json is unchanged;fixture expected tokens/flags were checked against independently specified D2 expectations before SQL comparison.

The compiled production ProductSearchNormalizer was executed directly by [NormalizationParityProbe.java](NormalizationParityProbe.java),using the existing test runtime classpath;no Spring context/DB/mock oracle. SQL was evaluated independently by psql against [normalization-prototype.sql](normalization-prototype.sql). The report contains source/prototype hashes. A second fresh container reproduced53/53 with the checked-in verifier;its identity/outcome is recorded under reproduction.

## Environment and function audit

- PostgreSQL17.11,UTF8,pre-existing postgres:17-alpine image digest recorded in report;network none,no ports,no mounted project/catalog volumes,PGDATA tmpfs256MiB. Both task-owned containers removed after collection. No shared/development DB or Flyway lifecycle was touched.
- Unicode lowercase/classification is explicitly pinned to deterministic ICU und-x-icu. Observed collprovider i,collisdeterministic true,collversion=actual_version=153.136. Existing name-sort COLLATE C requirement is unchanged:lowercase normalization and final ordering collation are separate operations.
- pg_proc audit lists all five prototype functions and8 directly used text primitives:each provolatile i and proparallel s. Bodies use NFD,explicit mark/whitespace mapping,character scanning and pinned collation,with no mutable registry/dictionary/table lookup. Verification JSON serialization is outside indexed function bodies.
- A two-row disposable parity_index_probe table accepted CREATE INDEX on catalog_search_normalize_v1(value);pg_index.indisvalid=true. It is a throwaway feasibility probe,not a catalog migration/index benchmark or T015/T059 execution. No immutable normalized-column fallback was needed.

This is bounded corpus evidence,not an exhaustive Unicode equivalence claim. ICU/Java Unicode version upgrades or function/grammar changes require renewed parity evidence and versioned function/index review. T015 must use proven immutable primitives and explicitly require this collation capability/version compatibility;T063 must verify the actual target. Do not silently use lower(... COLLATE C),install ICU,change vocabulary or mark target readiness based on this image.

## Reproduction (fresh disposable instance only)

First run the documented focused Maven tests so ProductSearchNormalizer and its Surefire runtime-classpath report exist. From repository root,create a fresh uniquely named container with no host port/volume and a T013 label:

```sh
docker run -d --pull=never --name speckit007-t013-reproduce --label speckit.task=T013 --network none --tmpfs /var/lib/postgresql/data:rw,noexec,nosuid,size=256m -e POSTGRES_PASSWORD=disposable-t013-only -e POSTGRES_DB=normalization_parity postgres:17-alpine
docker exec speckit007-t013-reproduce pg_isready -U postgres -d normalization_parity
```

Wait for pg_isready success before loading the prototype,then:

```sh
docker exec -i speckit007-t013-reproduce psql -X -v ON_ERROR_STOP=1 -U postgres -d normalization_parity < specs/007-product-search-filter/evidence/normalization-prototype.sql
python3 specs/007-product-search-filter/evidence/verify-normalization-parity.py speckit007-t013-reproduce
docker rm -f speckit007-t013-reproduce
```

Use a fresh unique name if occupied;never reuse an unknown container. Cleanup the newly created instance even if verification fails. The verifier asserts T013 label,network isolation,tmpfs,no mounts/ports;it refuses ordinary target containers. It compiles the Java probe under /tmp,checks stored inputs against the D2 fixtures,compares real results,audits volatility and creates only its tiny proof table/index. Output goes to /tmp/t013-reproduction-results.json;recorded evidence is not overwritten. Missing collation/non-immutable primitives/mismatches fail this gate;do not approve an automatic alternative.
