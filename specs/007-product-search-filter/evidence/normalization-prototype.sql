-- T013 disposable prototype only. NOT an applied migration or approved vocabulary.
CREATE FUNCTION catalog_search_normalize_v1(input text) RETURNS text
LANGUAGE plpgsql IMMUTABLE PARALLEL SAFE AS $$
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
    RETURN lower(output COLLATE "und-x-icu");
END $$;

CREATE FUNCTION catalog_search_alnum_v1(ch text) RETURNS boolean
LANGUAGE sql IMMUTABLE STRICT PARALLEL SAFE
AS $$ SELECT ch COLLATE "und-x-icu" ~ '^[[:alpha:][:digit:]]$' $$;

CREATE FUNCTION catalog_search_digit_v1(ch text) RETURNS boolean
LANGUAGE sql IMMUTABLE STRICT PARALLEL SAFE
AS $$ SELECT ch COLLATE "und-x-icu" ~ '^[[:digit:]]$' $$;

CREATE FUNCTION catalog_search_decimal_start_v1(value text, at_pos integer) RETURNS boolean
LANGUAGE sql IMMUTABLE STRICT PARALLEL SAFE
AS $$ SELECT substr(value,at_pos,1) = '.' AND catalog_search_digit_v1(substr(value,at_pos+1,1)) $$;

CREATE FUNCTION catalog_search_tokens_v1(input text)
RETURNS TABLE(token text, structurally_protected boolean)
LANGUAGE plpgsql IMMUTABLE PARALLEL SAFE AS $$
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
        structurally_protected := NOT (token COLLATE "und-x-icu" ~ '^[[:alpha:]]+$');
        RETURN NEXT;
    END LOOP;
END $$;
