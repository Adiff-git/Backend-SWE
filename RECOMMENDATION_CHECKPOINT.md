# Recommendation checkpoint — locally complete
Current branch: draft/version-1. Feature branch retained: features/AIRecommendation.
Feature commit: 76aa0ef7861368b197bb169d1b92c817fe363f57
Local merge commit: 713b76df165607ee65ec6fe4401ee511bc6c8462
Contract v1 canonical AI-SWE/src/ai_service/recommendation/inference.py.
Completed components/files: docs/recommendation.md and docs/recommendation-verification.md.
Tests passed: AI 255, Ruff; complete Backend serial verify 752 distinct cases
(357 unit +395 IT), zero failures/errors/skips. Postmerge: AI255; Backend357 unit +167 selected
IT cases (154 +13 distinct), all passed, including actual two-service HTTP with1500 Backend
fixture products, owner/JWT/minimized payload, general/cold-start/similar, 401 and stopped-AI fallback.
Initial JDK25.0.4.1->25.0.5.0.3 in-place update caused one spawn-helper infrastructure error;
fresh-JVM full rerun and postmerge runs passed. No outstanding test failures or blockers.
Postmerge HTTP: cold164ms, warm75-95ms, AI48-52ms, hydration4-8ms, fallback40ms (local observations).
Next action: none required for this MVP; frontend integration uses the documented Backend APIs.
No push/deployment; Catalog-Data remains read-only at c5dcfaa. No migrations added/modified.
NOT VERIFIED: deployed/browser behavior, production load/network/TLS. DEFERRED: human-reviewed
relevance labels and specification features without trusted approved values.
