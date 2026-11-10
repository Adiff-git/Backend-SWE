-- Additive search primitives only. No catalog rows, registry approval or search indexes.
-- ICU153.136/pg_trgm1.6 are the capabilities actually proved by T002/T013.
-- An unverified target must fail rather than silently use another collation/version.
DO $$
DECLARE
    verified_collation boolean;
BEGIN
    IF current_setting('server_encoding') <> 'UTF8' THEN
        RAISE EXCEPTION 'Catalog search requires UTF8' USING ERRCODE = '0A000';
    END IF;
    SELECT coalesce(bool_and(collprovider = 'i' AND collisdeterministic
            AND collversion = '153.136'
            AND pg_collation_actual_version(oid) = collversion),false)
        INTO verified_collation
        FROM pg_collation
        WHERE collnamespace = 'pg_catalog'::regnamespace AND collname = 'und-x-icu';
    IF NOT verified_collation THEN
        RAISE EXCEPTION 'Catalog search requires verified deterministic ICU collation und-x-icu version153.136'
            USING ERRCODE = '0A000';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_available_extension_versions WHERE name='pg_trgm' AND version='1.6') THEN
        RAISE EXCEPTION 'Catalog search required pg_trgm package is unavailable (version1.6 required)'
            USING ERRCODE = '0A000';
    END IF;
    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname='pg_trgm'
            AND (extversion <> '1.6' OR extnamespace <> 'public'::regnamespace)) THEN
        RAISE EXCEPTION 'Catalog search requires pg_trgm1.6 in public; existing extension is incompatible'
            USING ERRCODE = '0A000';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_extension WHERE extname='pg_trgm')
            AND NOT has_database_privilege(current_user,current_database(),'CREATE') THEN
        RAISE EXCEPTION 'Catalog search pg_trgm installation requires CREATE privilege on current database'
            USING ERRCODE = '42501';
    END IF;
    IF EXISTS (SELECT 1 FROM pg_proc WHERE oid = ANY(ARRAY[
            'pg_catalog.lower(text)'::regprocedure,'pg_catalog.normalize(text,text)'::regprocedure,
            'pg_catalog.substr(text,integer,integer)'::regprocedure,'pg_catalog.char_length(text)'::regprocedure,
            'pg_catalog.ascii(text)'::regprocedure,'pg_catalog.chr(integer)'::regprocedure,
            'pg_catalog.textregexeq(text,text)'::regprocedure,'pg_catalog.textcat(text,text)'::regprocedure,
            'pg_catalog.string_agg(text,text)'::regprocedure]) AND provolatile <> 'i') THEN
        RAISE EXCEPTION 'Catalog search requires immutable PostgreSQL primitives' USING ERRCODE = '0A000';
    END IF;
END $$;

CREATE EXTENSION IF NOT EXISTS pg_trgm WITH SCHEMA public VERSION '1.6';

CREATE FUNCTION public.catalog_search_normalize_v1(input text) RETURNS text
LANGUAGE plpgsql IMMUTABLE PARALLEL SAFE
SET search_path = pg_catalog, public AS $$
DECLARE
    decomposed text := normalize(coalesce(input, ''), NFD);
    output text := '';
    ch text;
    cp integer;
    pending_space boolean := false;
BEGIN
    FOR i IN 1..char_length(decomposed) LOOP
        ch := substr(decomposed, i, 1);
        cp := ascii(ch);
        IF cp BETWEEN 9 AND 13 OR cp = ANY(ARRAY[32,133,160,5760,8232,8233,8239,8287,12288])
                OR cp BETWEEN 8192 AND 8202 THEN
            pending_space := output <> '';
        ELSIF cp < 32 OR cp BETWEEN 127 AND 159 THEN
            RAISE EXCEPTION 'Invalid control character in search text' USING ERRCODE = '22023';
        ELSIF cp <> ALL(ARRAY[768,769,771,777,803,770,774,795]) THEN
            IF pending_space THEN output := output || ' '; pending_space := false; END IF;
            output := output || CASE WHEN cp IN (273,272) THEN 'd' ELSE ch END;
        END IF;
    END LOOP;
    RETURN lower(output COLLATE pg_catalog."und-x-icu");
END $$;

CREATE FUNCTION public.catalog_search_alnum_v1(ch text) RETURNS boolean
LANGUAGE sql IMMUTABLE STRICT PARALLEL SAFE
SET search_path = pg_catalog, public AS $$ SELECT ch COLLATE pg_catalog."und-x-icu" ~ '^[[:alpha:][:digit:]]$' $$;

CREATE FUNCTION public.catalog_search_digit_v1(ch text) RETURNS boolean
LANGUAGE sql IMMUTABLE STRICT PARALLEL SAFE
SET search_path = pg_catalog, public AS $$ SELECT ch COLLATE pg_catalog."und-x-icu" ~ '^[[:digit:]]$' $$;

CREATE FUNCTION public.catalog_search_decimal_start_v1(value text, at_pos integer) RETURNS boolean
LANGUAGE sql IMMUTABLE STRICT PARALLEL SAFE
SET search_path = pg_catalog, public AS $$ SELECT substr(value,at_pos,1) = '.' AND catalog_search_digit_v1(substr(value,at_pos+1,1)) $$;

CREATE FUNCTION public.catalog_search_tokens_v1(input text)
RETURNS TABLE(token text, structurally_protected boolean)
LANGUAGE plpgsql IMMUTABLE PARALLEL SAFE
SET search_path = pg_catalog, public AS $$
DECLARE
    value text := catalog_search_normalize_v1(input);
    cursor_pos integer := 1;
    start_pos integer;
    end_pos integer;
    length integer := char_length(value);
    ch text;
BEGIN
    WHILE cursor_pos <= length LOOP
        start_pos := cursor_pos;
        ch := substr(value,cursor_pos,1);
        IF ch IN ('+','-') AND (catalog_search_digit_v1(substr(value,cursor_pos+1,1))
                OR catalog_search_decimal_start_v1(value,cursor_pos+1)) THEN
            cursor_pos := cursor_pos+1;
        END IF;
        IF catalog_search_decimal_start_v1(value,cursor_pos) THEN cursor_pos := cursor_pos+1; END IF;
        IF cursor_pos > length OR NOT catalog_search_alnum_v1(substr(value,cursor_pos,1)) THEN
            cursor_pos := start_pos+1;
            CONTINUE;
        END IF;
        cursor_pos := cursor_pos+1;
        WHILE cursor_pos <= length LOOP
            ch := substr(value,cursor_pos,1);
            IF catalog_search_alnum_v1(ch) THEN cursor_pos := cursor_pos+1;
            ELSIF ch IN ('-','_','/','.',chr(39),'+')
                    AND catalog_search_alnum_v1(substr(value,cursor_pos-1,1))
                    AND catalog_search_alnum_v1(substr(value,cursor_pos+1,1)) THEN
                cursor_pos := cursor_pos+1;
            ELSIF ch = '+' AND catalog_search_alnum_v1(substr(value,cursor_pos-1,1)) THEN
                end_pos := cursor_pos;
                WHILE substr(value,end_pos,1) = '+' LOOP end_pos := end_pos+1; END LOOP;
                IF end_pos-cursor_pos <= 2 THEN cursor_pos := end_pos; END IF;
                EXIT;
            ELSE EXIT;
            END IF;
        END LOOP;
        token := substr(value,start_pos,cursor_pos-start_pos);
        structurally_protected := NOT (token COLLATE pg_catalog."und-x-icu" ~ '^[[:alpha:]]+$');
        RETURN NEXT;
    END LOOP;
END $$;

-- Structural candidate view only; approved alphabetic exclusions remain query-time gates.
-- Reuse the verified tokenizer; no mutable dictionary, table or registry lookup.
CREATE FUNCTION public.catalog_search_natural_name_v1(input text) RETURNS text
LANGUAGE sql IMMUTABLE PARALLEL SAFE
SET search_path = pg_catalog, public AS $$
    SELECT coalesce(string_agg(token, ' ' ORDER BY position), '')
    FROM public.catalog_search_tokens_v1(input) WITH ORDINALITY AS words(token, protected, position)
    WHERE NOT protected
$$;
