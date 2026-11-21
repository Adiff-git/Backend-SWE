# Implementation decisions

2026-11-21: The user explicitly approved all proposals (C1), including P-01–P-05, MANAGE_ORDER plus active-account/ownership checks, HTTP/update/error contracts, safe media fallback and active-author summaries. Q1–Q3 remain confirmed. No further approval is required for these policies.

Deletion, moderation, replies, votes, shop ratings and rating distribution remain excluded. Preserve existing identifier types and media lifecycle. See contracts/product-reviews.md and data-model.md.

## Implementation refinements

- Safe user author summaries expose only sanitized display data and optional avatar references internally. Review orchestration combines avatar IDs and review image IDs into one media batch, so the public response uses one prepared signer rather than separate avatar/review signing passes. Converters only enrich already-prepared values; they do not fetch.
- Review orchestration rejects an ambient transaction, following the existing media/catalog entry-boundary convention. This keeps upload-object preparation before writer locks and makes the writer commit boundary explicit.
- Order eligibility uses the existing order.lock-timeout-ms setting, with the established 5-second default.
- Input comments containing NUL are rejected as 400 because PostgreSQL TEXT cannot represent that character; the 2,000 Unicode code-point limit remains unchanged.
