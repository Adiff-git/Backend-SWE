-- Actual1500-source plans show description tokenization dominating read latency.
-- This generated column is transactionally derived from the canonical description;
-- no replica,sync job,source rewrite or speculative index is introduced.
CREATE FUNCTION public.catalog_search_description_tokens_v1(input text) RETURNS text[]
LANGUAGE sql IMMUTABLE PARALLEL SAFE
SET search_path = pg_catalog,public AS $$
    SELECT coalesce(array_agg(t.token ORDER BY t.position),ARRAY[]::text[])
    FROM public.catalog_search_tokens_v1(public.catalog_search_safe_normalize_v2(input))
    WITH ORDINALITY AS t(token,protected,position)
$$;

ALTER TABLE public.products ADD COLUMN catalog_description_tokens_v1 text[]
    GENERATED ALWAYS AS (public.catalog_search_description_tokens_v1(description)) STORED;
