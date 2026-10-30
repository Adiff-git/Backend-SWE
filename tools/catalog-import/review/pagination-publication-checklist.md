# Final local pagination publication checklist

The exact 45 UUIDs in [pagination-45-publication-proposal.md](pagination-45-publication-proposal.md) were verified against `backend@127.0.0.1:55432` using a read-only transaction. All 45 exist, remain DRAFT, have unique UUIDs, and match the immutable v1 snapshot's names, category UUIDs/names, one-variant representation, SKUs and approved whole-VND demo prices. No data discrepancy was found in those checks. Historical source prices remain demo data; no current retailer price claim is made.

No approval or status was changed. This document is a checklist for a later separately authorized operation:

- Obtain explicit authorization for the exact 45 UUIDs, the local target and intended 50 PUBLISHED / 950 DRAFT split.
- Recheck the original five PUBLISHED IDs and all 45 DRAFT records against the reviewed proposal; stop on drift.
- Verify Compose project, loopback target, persistent volume and exact Flyway V1–V9 history; create a fresh backup before writing.
- Apply any authorized status transition transactionally to only the approved UUIDs, preserving all prices, variants and other products.
- Verify exactly 50 PUBLISHED and 950 DRAFT, public pagination at size 20 (three pages), detail contracts and DRAFT concealment.
- Preserve seed v1 and its archive hash. Export changed states as a separately implemented and reviewed seed v2; the v1 loader intentionally requires the five-publication split and will not silently update statuses.
- Run the relevant tests and record execution results. Any dataset distribution still requires the content-rights and private-delivery checks in [SEED_DISTRIBUTION.md](../SEED_DISTRIBUTION.md).
