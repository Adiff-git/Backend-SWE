-- Conservative natural-name typo guard;does not enable fuzzy for identifiers or technical values.
-- Inputs are normalized tokens. One insertion,deletion,substitution or adjacent transposition.
CREATE FUNCTION public.catalog_search_typo_v1(a text,b text) RETURNS boolean
LANGUAGE plpgsql IMMUTABLE STRICT PARALLEL SAFE
SET search_path = pg_catalog,public AS $$
DECLARE
    na integer:=char_length(a); nb integer:=char_length(b);
    i integer:=1; j integer:=1; edits integer:=0;
BEGIN
    IF abs(na-nb)>1 THEN RETURN false; END IF;
    IF na=nb THEN
        WHILE i<=na AND substr(a,i,1)=substr(b,i,1) LOOP i:=i+1; END LOOP;
        IF i>na THEN RETURN true; END IF;
        IF substr(a,i+1)=substr(b,i+1) THEN RETURN true; END IF;
        RETURN i<na AND substr(a,i,1)=substr(b,i+1,1)
            AND substr(a,i+1,1)=substr(b,i,1) AND substr(a,i+2)=substr(b,i+2);
    END IF;
    WHILE i<=na AND j<=nb LOOP
        IF substr(a,i,1)=substr(b,j,1) THEN i:=i+1;j:=j+1;
        ELSE
            edits:=edits+1;
            IF edits>1 THEN RETURN false; END IF;
            IF na>nb THEN i:=i+1; ELSE j:=j+1; END IF;
        END IF;
    END LOOP;
    RETURN true;
END;
$$;
