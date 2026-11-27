# Private educational demo bootstrap

This is a private, non-commercial university course dataset, restricted to team
members and the instructor for education and private instructor submission.
Preserve source provenance. Public redistribution and commercial use are prohibited.
Copyright clearance and reuse permissions remain **UNVERIFIED**. Public redistribution
verification is **NOT_APPLICABLE_CURRENT_SCOPE**. Verify applicable permissions and
explicitly reassess scope before any future public redistribution or commercial use.

The approved helper dependencies are pinned with hashes in requirements.lock; the
approval is recorded in ../../specs/014-demo-data-bootstrap/dependency-proposal.md.
Build with `docker build -t backend-swe-demo-bootstrap:local tools/demo-bootstrap`.
The helper runs as UID 10001, never installs packages during startup, and makes no
runtime AI API calls. Startup supports Linux/WSL2 Bash and a local Docker daemon.
Native host verification is deferred under the user's implementation instruction;
Docker-isolated checks do not establish host lifecycle acceptance.

## Offline preparation and genuine review

Use prepare.py inside the approved helper. All outputs belong under ignored
`.private/demo-bootstrap/`; mount original source and exact Git snapshot read-only.
`analyze` emits one evidence packet per source product, a separate deterministic
conservative proposal workspace, original hashes and all-product accounting.
These are candidates, not approved artifacts. Automated rule analysis is explicitly
identified; it never masquerades as a separately invoked model or human review.
External offline AI responses can replace candidate proposals using the versioned
instructions in rules/enrichment-prompt.md, retaining actual model/prompt/response
identity. The source package and its manifests must never be edited.

`candidate` validates evidence-backed proposals and creates separate catalog and
commerce choices. `review-template` emits the exact required human review sample.
Review all flags and at least 10% enriched products across enriched categories.
Record actual reviewer decisions against the payload digest; no automatic approval.
`freeze` refuses missing/stale/rejected human decisions and requires migrated
PostgreSQL validation evidence bound to this candidate digest. Changed content
requires a new candidate and review. Do not edit an already frozen artifact.

Normal user startup is `bash scripts/start-dev.sh`; fresh startup is
`bash scripts/start-dev.sh --fresh`. Fresh requires exact typed
`RESET backend_demo`. No automatic confirmation bypass is supported. Only the
dedicated database and exact ledger-owned assets may be reset; PostgreSQL volume
and unrelated objects/checkpoints are retained. Never use broad prune/down -v.

The orchestration and reviewed release are implemented. Full import, exact distributions, normal preservation and recovered
fresh/commit/readiness checks passed. Startup refuses an unapproved or changed artifact. See tasks.md for
completed work and outstanding acceptance.

## Maintainer sequence

1. Create an exact source snapshot using `git -c core.autocrlf=false archive` at
   the approved source revision and the helper's `snapshot-source` stage. Verify
   it against the unchanged working source with `verify-source`. The archive
   override is essential on Windows: ordinary archive can apply CRLF conversion.
2. Run `prepare.py analyze` and `candidate` inside the approved helper, mounting
   the exact source read-only and a separate private preparation directory read/write.
   Outputs must be new directories. Preserve earlier candidates for audit.
3. Optional external offline model responses use `capture-proposals` with actual
   `--model`, `--prompt`, `--reference`, and `--input` JSONL; it makes no AI call.
   Rebuild the candidate after any changes. The default conservative assistant-authored
   rule candidates explicitly record that no separate model invocation occurred.
4. Establish/verify the empty target's migrations with the host stdlib-only adapter:

   ```text
   python tools/demo-bootstrap/prepare.py migrate --workspace .private/demo-bootstrap/preparation/<candidate> --env-file .env.local
   ```

   It uses the approved helper and existing unpublished backend, exact same daemon
   lock, fixed target ownership checks and baseline classification. It does not
   require an approved final artifact and never imports business data or resets a
   database. It works on Windows for offline preparation; it is not another startup
   operating mode. `--compose-overlay` is restricted to ignored private verification
   files and remains subject to effective target checks.
5. Run `review-package` and `review-template`. Review every required record in
   `review/index.html`, then record actual decisions/reviewer/reference in the
   generated decision JSONL. On Linux, give the helper the host supplementary GID
   and create private output directories with group permissions/setgid (`2770`);
   the review template grants only its review directory/file group write access.
6. Run `database-validate` inside the helper with the reviewed candidate, decisions,
   private environment and read-only current migrations. It validates actual
   PostgreSQL option maps/prices and records the schema/protection fingerprint.
7. Run `freeze` to a new private artifact directory. It refuses stale decisions,
   changed bytes and absent database validation. It emits a final digest that
   still needs a genuine detached human release decision. Record that specific
   decision using `approve-artifact --digest ... --reviewer ... --reference ...`.
   `release-approval.json` is the detached record and intentionally lies outside
   the manifest payload to avoid a digest cycle. Never manufacture approval.
8. Set DEMO_BOOTSTRAP_ARTIFACT to that release and configure a private demo password.
   Use only the two Bash startup commands above. Passwords/hashes never appear in
   summaries. Startup binds source/artifact/migrations read-only and exposes no
   helper or temporary backend ports.

Runtime source/importer/migration/schema/daemon identity mismatches stop startup.
Completed normal starts preserve activity and skip import/migration. Only surviving
owned image references may be repaired. Unused owned checkpoint inputs are retained
under `.bootstrap/retained/` so the existing root loader cannot restore deleted
references; fresh cleanup includes those exact ledger-owned files. Non-owned
objects/checkpoints and the PostgreSQL volume are preserved.

Failed business imports retain their anchor and tracked external assets. Uncertain
commit state requires authoritative inspection. A stale sentinel is never reclaimed
by age: prove matching orchestrator/helpers/migrator have stopped before exact
token-checked removal. Post-commit readiness/report failures do not authorize reset.

## Current evidence

The private candidate `demo-v6-candidate` contains 1,500 products, 2,257 variants,
83,561 reviews and 178,538 orders (175,538 completed). The user approved its 711
required review decisions and continued implementation. Actual database validation
passed. Private release `demo-v3` has digest
`a393fc633883079bc4795a72a490ce3e6b9d63588f588447ef43a86cc1b0058c`.
Its approved payload files are byte-identical to `demo-v1`; tooling fixes retire
the old attempt atomically after fresh cleanup and resume saved confirmed resets
before classifying a replaced database. The first full import passed independent
persisted checks with exact review/rating distributions and zero invariant errors.
All twelve logins and 103 API checks passed, and normal repeat preserved every
public-table digest, sequence and anchor while importing zero rows. First import
took 1,761 seconds, including 1,477 seconds of enabled deferred constraints on the
verified local environment; this is a measurement, not a performance guarantee.
Controlled fresh recovery, commit acknowledgement/report failures and final healthy recovery passed. The complete graph passed two offline replays excluding
intentional BCrypt salt differences. Actual V1–V30 migration history, ICU/extension,
candidate canonical options, empty-staging SQL rollback, isolated S3 recovery and
Bash syntax checks passed. Unrestricted `mvnw.cmd verify` passed 724 unit/slice and
758 integration tests with zero failures/errors/skips. No new suites were added.
