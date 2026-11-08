# Wishlist Implementation Validation

**Date**: 2026-10-31
**Status**: Complete; all 44 tasks implemented and verified

## Delivered scope

Three permission-protected current-user endpoints support saved-item creation/duplicate replay, visible paginated listing, and idempotent removal. V10__create_wishlist_items.sql adds private Long membership identity, immutable Instant/TIMESTAMPTZ saving time, named pair uniqueness/FKs, dependent cleanup, pagination indexes and the default USER permission grant. Existing migrations are unchanged.

Duplicate addition uses targeted conflict-ignore insertion plus a separate timestamp lookup, with no failed transaction or new pessimistic duplicate-resolution lock. Actual named FK errors are mapped safely after rollback. Catalog service extensions prepare eligible references and bounded summaries using existing publication-safe signing. Listing is non-mutating in the approved write-capable transaction. No new frontend, dependency, plugin, security chain, image pipeline or public catalog response fields.

## Requirement evidence

Paths below are relative to demo/src/test/java/com/example/demo/.

| Requirements | Verification evidence |
| --- | --- |
| FR-001; SC-001 | wishlist/controller/WishlistControllerIT.java: real registration, save, logout, login, duplicate replay and persisted original time |
| FR-002/003; SC-002 | WishlistControllerTest and WishlistControllerIT: trusted identity, owner-input rejection, current permission revocation/restoration, custom-role denial, inactive/expired/revoked-session denial |
| FR-004 | catalog/service/ProductCatalogServiceImplTest.java and WishlistControllerIT: missing/DRAFT/ARCHIVED rejection and no product disclosure |
| FR-005/006; SC-005 | wishlist/repository/WishlistRepositoryIT.java: direct unique protection and usable conflict-ignore transaction; WishlistControllerIT: synchronized API additions and observed database unique-constraint contention, original timestamp and one row |
| FR-007/008 | wishlist/service/WishlistServiceIT.java: hidden retention/restoration, final eligibility failure and non-partial safe conflict |
| FR-009/010; SC-003 | WishlistRepositoryIT and WishlistControllerIT: pre-pagination visibility, 120 visible/30 hidden entries, sizes 1/20/100, equal-time UUID ordering, exact totals and beyond-last pages |
| FR-011; SC-006 | wishlist/converter/WishlistConverterTest.java and WishlistControllerIT: exact item/product allowlists, null image, stable mapping and copied page collection |
| FR-012/013; SC-004 | WishlistServiceImplTest, WishlistServiceIT and WishlistControllerIT: hidden/absent/foreign-only removal, other-user/parent survival, fresh re-add sampling including equal samples and new private identity |
| FR-014 | WishlistRepositoryIT: named FKs, required columns, schema indexes, user/product-dependent deletion without deleting independent parents |
| FR-015 | WishlistServiceIT: real rollback after signing, final eligibility, FK and removal failure; common/exception/WishlistPersistenceErrorTest.java: only recognized FK identity translated, unknown failures remain safe |
| FR-016 | WishlistServiceIT: pages of 1/100 imaged items remain within six feature SQL statements; response serialization after service completion issues no additional query |
| FR-017 | Existing catalog controller/contract/read/query integration regressions included in final full verification; existing HTTP contracts unchanged |

Migration upgrade coverage in WishlistRepositoryIT starts at V9 with existing assignments and checks that V10 adds only the USER permission relationship. Real new-account registration remains in the application API integration class, not the repository slice.

## Checkpoint results

- Foundation: 19 unit checks and nine PostgreSQL schema/mapping/upgrade checks passed.
- Save: initial 41 unit/MVC checks and 17 PostgreSQL checks passed; subsequent expanded API checks and real-failure rollback cases passed before listing began. An expired-session fixture was corrected to preserve the existing exact seven-day session constraint.
- Listing: 58 unit/MVC checks and 28 PostgreSQL checks passed, including traversal and query budgets.
- Removal/all wishlist stories: 63 unit/MVC checks and 35 PostgreSQL checks passed, zero failures/errors/skips. This includes the catalog extension and error mapping unit checks as well as wishlist checks.
- Final full-suite result: BUILD SUCCESS, 238 Surefire checks across 56 classes plus 206 Failsafe integration checks across 51 classes (444 total), zero failures/errors/skips. Compilation, JAR packaging and actual integration discovery passed. The final run includes the central service-level 401 Bearer challenge correction and its focused MVC assertion.

## Build environment and reproducibility

Java 25.0.4.1 and Maven 3.9.16 match the configured target. The Windows wrapper fails before launch on its existing null Target indexing; verification uses the matching cached Maven distribution with an explicit repository path. Commands from demo/:

```powershell
$wishlistMaven = 'C:\Users\TrinhThanh\.m2\wrapper\dists\apache-maven-3.9.16\0daed3be3ebd1c706f0e69e8b07c6b73f5cc4ea3dfce72a8d0ec2e849ca2ddb0\bin\mvn.cmd'
& $wishlistMaven -o '-Dmaven.repo.local=C:\Users\TrinhThanh\.m2\repository' '-Dtest=Wishlist*Test,ProductCatalogServiceImplTest' '-Dit.test=WishlistRepositoryIT,WishlistServiceIT,WishlistControllerIT' verify
& $wishlistMaven -o '-Dmaven.repo.local=C:\Users\TrinhThanh\.m2\repository' verify
```

Normal host access is required for the cached dependencies and Docker Desktop named pipe. Despite no Docker CLI on PATH, Testcontainers starts disposable PostgreSQL 17.11 successfully. No developer/production database or real AWS is used. Initial stale target migration resources were removed with Maven clean; no source migration was deleted.

Results are recorded in demo/target/surefire-reports/ and demo/target/failsafe-reports/; ignored diagnostic logs live in demo/wishlist-*.log. Existing exact authority/count assertions were updated for the approved MANAGE_WISHLIST grant and tenth migration. Existing controller-only test doubles implement the two new internal catalog methods without changing their public endpoint assertions.

## Review and limits

Architecture review confirms acyclic service boundaries, only own-domain repository access, pure converters, LAZY entity references, no parent ORM cascades, no automatic retries and no new broad locks. New tests follow constitution XIII domain/layer packages and class responsibilities. Reviewer-owned checklist markers are unchanged. The final central error-handler adjustment preserves WWW-Authenticate: Bearer for service-level 401 outcomes; security-filter handlers already supplied that challenge.

The intended Spotless/Checkstyle/SpotBugs/PMD toolchain is not configured in the current POM; no static-analysis success is claimed and no unapproved plugins were added. Manual browser/local-server walkthrough is optional and not claimed; disposable full-service/API checks exercise the guide's server-side workflow. No deployment, push or commit was performed. Existing README and constitution edits are preserved.

## Completion and remaining limitations

T001-T044 are complete. Both requirements checklists remain fully checked as reviewed by the user (16/16 and 32/32); implementation did not change their markers. There are no remaining wishlist implementation blockers. The wrapper startup issue and absent optional static-analysis configuration are recorded environment/tooling limitations, not silently represented as passing checks. Optional manual browser validation and deployment were not performed or claimed.
