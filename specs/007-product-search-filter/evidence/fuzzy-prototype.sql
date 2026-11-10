-- Reproduce guarded pair scoring only on an explicitly disposable V19 test database.
-- Full frozen-pair/source-token oracle is FuzzyCalibrationIT;this does not create relevance labels.
BEGIN;
SELECT set_config('pg_trgm.strict_word_similarity_threshold','0.32',true);
WITH pairs(q,c) AS (VALUES
 ('samsnug','samsung'),('appel','apple'),('lenvo','lenovo'),
 ('samsung','samson'),('sony','sonic'),('lenovo','lenox'),
 ('canon','canyon'),('intel','inter'))
SELECT q,c,similarity(q,c),catalog_search_typo_v1(q,c) AS one_edit,
 q <<% c AND similarity(q,c)>0.32 AND catalog_search_typo_v1(q,c) AS fuzzy_candidate
FROM pairs;
ROLLBACK;
