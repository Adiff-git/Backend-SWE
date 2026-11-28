# Line-ending normalization verification — 2026-11-28

## Scope and preserved content

Backend-SWE `draft/version-1`, Catalog-Data `main`, and Catalog-Data `bootstrap`
were audited recursively using tracked-file inventories. Historical Git commits
were preserved. No force push or history rewrite was performed.

The initial Backend working-tree audit identified 1,093 CRLF, 186 LF, 18 mixed,
17 newline-free text files, and 87 binary files. The audited, backed-up plan made
1,112 newline-only working-tree changes. Most Backend blobs were already LF in
Git; the permanent changes primarily establish consistent checkout attributes
and the new release fingerprints. Original Git-object audits are also retained.

| Final fresh-clone inventory | LF | CRLF | Newline-free text | Binary |
|---|---:|---:|---:|---:|
| Backend including this report | 1,301 | 1 | 17 | 87 |
| Catalog source | 12 | 0 | 0 | 1,500 |
| Catalog bootstrap | 432 | 0 | 13 | 3,003 |

There are no mixed-ending text files. The one CRLF exception is `demo/mvnw.cmd`.
Only `.bat`/`.cmd` patterns are allowed CRLF; no migration or lockfile exception
remains. All 87 Backend binary files, 1,500 source images, and 3,003 bootstrap
binary files retain their original SHA-256. All three Backend BOM-bearing files
retain their BOMs. No LFS-managed files were present; Git LFS settings/content
were not changed, and the audit utility explicitly excludes LFS-managed content.
All 1,500 approved CSV rows/values and product/variant/specification records are
unchanged. The six artifact versions have identical approved dataset payloads.

## Fingerprints and releases

See [the complete checksum and lineage map](line-ending-release.json), including
all 30 old/new migration SHA-256 values, six artifact digests, importer and lock
digests, startup hashes, exact schema hashes, and the new source/manifest pin.
Artifacts were frozen to new output directories through existing candidate review,
fresh PostgreSQL validation, freeze, and detached approval commands. Approvals
record the owner's explicit delegated normalization authorization; independent
human inspection of final digests is not claimed. Original private artifacts
and `.env.local` remain untouched. Fresh configuration selects `demo-v6`.

No checksum, source check, startup fingerprint, migration fingerprint, schema
fingerprint, ownership check, or approval gate was removed or relaxed.

## Completed verification

| Check | Result |
|---|---|
| Independent fresh clones under `core.autocrlf=true` and `false`, all three repositories | PASS: every tracked file byte-identical |
| Native Ubuntu Git clones compared with Windows clones | PASS: every tracked file byte-identical |
| Raw committed Git blobs compared with normalized fresh checkout bytes | PASS, except intentional batch checkout conversion |
| PowerShell parser and native Ubuntu Bash syntax | PASS |
| Normalizer tests: encoding/BOM, UTF-16, Latin-1 bytes, binary/LFS exclusions, batch, backups, stale-plan refusal | 2 tests PASS |
| Source package validator, all 1,500 products/images | PASS, no issues |
| Helper image build with pinned wheel hashes | PASS |
| Backend Java 25 image build | PASS; test sources compile, Maven tests skipped in image build |
| Genuine Flyway migration of a new isolated PostgreSQL database | PASS |
| Existing database Flyway history (read-only) | All 30 logical checksums unchanged; no repair |
| Existing versus fresh schema definitions (read-only) | All 912 records match after newline normalization; 20 newline-only function definitions |
| Candidate PostgreSQL option/price/schema validation | PASS |
| Fresh PowerShell setup download and artifact verification | PASS |
| Fresh native Ubuntu/WSL setup download and artifact verification | PASS after tested DrvFS bind-handle fix |
| Repeat native Ubuntu setup with installed artifact cache | PASS; existing versions preserved |
| Unchanged runtime integrity gate on all six actually downloaded versions | PASS |
| Full isolated business import with all deferred constraints enabled | PASS: 14 invariant checks, zero errors |
| Isolated backend restart, Flyway validation, API readiness | PASS |
| Normal repeat of the real import stage | PASS: skipped; no business reimport |

Setup integration fixtures used an independent local Catalog Git mirror because
the normalized commits were not yet published. They changed only the test URL,
audit image tag, and final launch into an explicit no-op; all artifact validation
remained real. Full native `start-dev` service orchestration was not run against
the existing fixed production project. Instead, backend migration and import
components run against a separate network, new database, new S3 namespace, and
new checkpoint directory with no published ports or original volumes mounted.

## Full isolated import and recovery

The complete isolated import committed successfully: 1,500 products, 2,257
variants, 500 users, 178,538 orders, 578,080 order items, 83,561 reviews, 300 carts,
and 3,500 wishlist entries. All 14 persisted invariant checks report zero errors.
Actual review-count groups are `[300,300,525,300,75]` and average-rating groups
are `[60,120,300,420,300]`, matching the exact approved distributions.

The successful business transaction took 2,034.235 seconds, including 1,678.240
seconds flushing deferred constraints. The existing 1,800-second per-statement
timeout and every constraint remained enabled. The isolated backend then passed
Flyway startup validation and a real `/api/products` readiness request; its
readiness report records `backend_ready=true`.

Normal repeat returned `action=skipped`, verified all 1,500 existing images, and
did not reimport business data. Before/after snapshots match exactly for business
table counts, password digests, sequence values, anchor, attempt ID, artifact ID,
completion state, and completion time.

Enabling Ubuntu Docker integration restarted Docker Desktop during the first
verification transaction. The importer reported `DATABASE_UNAVAILABLE`; the
transaction was not adopted as completed. Only audit infrastructure was restarted,
all 1,500 trusted image checkpoints were restored, and normal recovery resumed
the same saved attempt/anchor without reset. The resumed transaction completed
and passed all constraints. No database reset was used to recover the test.

## Existing installations and publication

No existing database was reset, no Flyway history repaired, and no existing
business records were modified by verification. Original PostgreSQL was restored
to its pre-Docker-restart running state for SELECT-only schema inspection. Other
original services were not restarted by this audit. Disposable test data and
backups are retained for inspection.

Existing completed installations remain bound to their original artifact,
importer, and exact schema fingerprints. This task does not silently rewrite
completion metadata. Continue such installations with their matching prior
release; do not use `--fresh` to resolve a byte mismatch.

Remote branch-tip checks confirmed that normalized Catalog main
`89e3b6c5bc95b533386a349c65305613617d8709`, Catalog bootstrap
`0690953c4a0efc950768d4ff2b89f1e05383ca7b`, and Backend draft `13a3572` were
already published externally while verification was interrupted. This audit did
not perform those pushes. Additional verification-report/lineage changes are
local until explicitly published. The previous instruction reserving data
publication to the owner is preserved.

Detailed per-file inventories, content hashes, original Git-object audits, backups,
fresh-clone reports, and isolated verification evidence are retained under ignored
`.private/lf-audit/` and `.private/lf-release/`.
