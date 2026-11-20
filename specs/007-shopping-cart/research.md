# Cart revision decisions

2026-11-17. The user supplied and approved replacement confirmed rules: quantity 1..999, absent variant target reset-to-1, existing target unchanged/source removed, same variant no-op, separate quantity and variant requests. These override the earlier 2026-11-11 decisions. No new business capabilities or dependencies are introduced.

Keep established cart parent locking and resource-free image capability boundaries; they protect all new operations without per-item UPSERT or broader isolation/retry. Whole VND uses BigInteger exact scale-zero arithmetic, preserving the existing SellingPrice convention; aggregate quantities use checked Long arithmetic.

Use exact productVariantId input and seven routes to avoid client quantity conflicting with variant reset policy. Keep internal AddCartResult for creation evidence but expose only CartResponse with 200 for both add outcomes, documented as a business-action POST exception. totalItemCount is the size of the complete captured item list, independent of eligible selected counts.

Applied migrations must not be edited. V22 tightens the quantity check and refuses incompatible legacy data instead of choosing an unapproved cap/delete conversion. Upgrade tests must preserve rows on failure and succeed after an explicit correction. Search's disposable successor fixture moves to V23 to avoid collision while retaining its rollback/retry assertions.

Verification notes belong in quickstart.md. Prior 2026-11-12 verification applied the superseded rules and is historical, not proof of this revision.
