-- Same frozen lexical normalization as V15,implemented with immutable Unicode primitives.
-- Avoid per-character PL/pgSQL work when scanning actual1500 source descriptions.
CREATE FUNCTION public.catalog_search_normalize_v2(input text) RETURNS text
LANGUAGE plpgsql IMMUTABLE PARALLEL SAFE
SET search_path = pg_catalog,public AS $$
DECLARE value text;
BEGIN
    IF input COLLATE pg_catalog."und-x-icu" ~ ('['||chr(1)||'-'||chr(8)||chr(14)||'-'||chr(31)||chr(127)||'-'||chr(132)||chr(134)||'-'||chr(159)||']') THEN
        RAISE EXCEPTION 'Invalid control character in search text' USING ERRCODE='22023';
    END IF;
    value:=translate(normalize(coalesce(input,''),NFD),
        chr(273)||chr(272)||chr(768)||chr(769)||chr(771)||chr(777)||chr(803)||chr(770)||chr(774)||chr(795),'dd');
    value:=regexp_replace(value COLLATE pg_catalog."und-x-icu",
        '['||chr(9)||'-'||chr(13)||chr(32)||chr(133)||chr(160)||chr(5760)||chr(8192)||'-'||chr(8202)||chr(8232)||chr(8233)||chr(8239)||chr(8287)||chr(12288)||']+',' ','g');
    RETURN lower(btrim(value,' ') COLLATE pg_catalog."und-x-icu");
END;
$$;

-- Stored malformed text contributes no search evidence;the original value is never rewritten.
-- Query validation continues using the strict Java normalizer and still rejects invalid controls.
CREATE FUNCTION public.catalog_search_safe_normalize_v2(input text) RETURNS text
LANGUAGE plpgsql IMMUTABLE PARALLEL SAFE
SET search_path = pg_catalog,public AS $$
BEGIN
    RETURN public.catalog_search_normalize_v2(input);
EXCEPTION WHEN invalid_parameter_value THEN
    RETURN '';
END;
$$;
