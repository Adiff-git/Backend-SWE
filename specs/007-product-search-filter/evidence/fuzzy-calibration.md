# Fuzzy calibration v2

Threshold0.32 plus conservative one insertion/deletion/substitution/adjacent-transposition guard. All original24 labels remain author-defined controlled typo/distinct-name diagnostics,not manually reviewed catalog relevance judgments.

Original threshold-only:15TP/5FP/0FN,precision75%,recall100%. Guarded:15TP/2FP/0FN,precision88.24%,recall100%. Multi-edit Samsung/Samson,Sony/Sonic,Lenovo/Lenox are rejected. Canon/Canyon and Intel/Inter remain one-edit ambiguous pairs and are explicitly retained false positives;no ad-hoc blacklist. Typos with multiple edits can lose recall by design.

Grid0.15–0.50 includes0.30/0.32/0.33;0.32 is a margin below the weakest required positive similarity≈0.3333,not a claim of global optimum or largest possible threshold. Increasing the threshold enough to remove all diagnostics loses required typo positives. Source-token oracle uses1500 frozen titles;no prefilter loss at the chosen policy,no top-K truncation. Natural-name only≥4;protected family/model/technical/SKU values never fuzzy. Transaction-local threshold reset is tested on a reused pool connection.
