-- New final matcher vocabulary v1,approved by owner mission. V1-V15 remain immutable.
-- Indexed candidate expressions continue using structural V15 functions,not this vocabulary.
CREATE FUNCTION public.catalog_search_protected_v1(value text, context_tokens text) RETURNS boolean
LANGUAGE sql IMMUTABLE PARALLEL SAFE
SET search_path = pg_catalog,public AS $$
SELECT NOT (value COLLATE pg_catalog."und-x-icu" ~ '^[[:alpha:]]+$')
    OR value = ANY(ARRAY['galaxy','iphone','ipad','xperia','redmi','thinkpad','ideapad','inspiron','vivobook','zenbook','macbook','chromebook','elitebook','celeron','pentium','ryzen','snapdragon','geforce','radeon'])
    OR CASE value
    WHEN 'pro' THEN strpos(context_tokens,' iphone 11 pro ') > 0 OR strpos(context_tokens,' iphone 12 pro ') > 0 OR strpos(context_tokens,' iphone 13 pro ') > 0 OR strpos(context_tokens,' iphone 14 pro ') > 0 OR strpos(context_tokens,' ipad pro ') > 0 OR strpos(context_tokens,' microsoft surface pro ') > 0 OR strpos(context_tokens,' redmi note 8 pro ') > 0 OR strpos(context_tokens,' redmi note 9 pro ') > 0 OR strpos(context_tokens,' redmi note8 pro ') > 0 OR strpos(context_tokens,' huawei p20 pro ') > 0 OR strpos(context_tokens,' huawei p30 pro ') > 0 OR strpos(context_tokens,' oneplus 10 pro ') > 0 OR strpos(context_tokens,' iphone pro ') > 0
    WHEN 'plus' THEN strpos(context_tokens,' chromebook plus ') > 0 OR strpos(context_tokens,' moto e7 plus ') > 0 OR strpos(context_tokens,' tab m10 plus ') > 0 OR strpos(context_tokens,' tab p11 plus ') > 0 OR strpos(context_tokens,' iphone 14 plus ') > 0 OR strpos(context_tokens,' galaxy plus ') > 0 OR strpos(context_tokens,' galaxy s8 plus ') > 0 OR strpos(context_tokens,' galaxy s9 plus ') > 0 OR strpos(context_tokens,' galaxy s20+ plus ') > 0 OR strpos(context_tokens,' galaxy s21+ plus ') > 0 OR strpos(context_tokens,' lg k22+ plus ') > 0
    WHEN 'max' THEN strpos(context_tokens,' iphone 11 pro max ') > 0 OR strpos(context_tokens,' iphone 12 pro max ') > 0 OR strpos(context_tokens,' iphone 13 pro max ') > 0 OR strpos(context_tokens,' iphone 14 pro max ') > 0 OR strpos(context_tokens,' iphone xs max ') > 0 OR strpos(context_tokens,' walkerfit m2 max ') > 0 OR strpos(context_tokens,' iphone pro max ') > 0
    WHEN 'core' THEN strpos(context_tokens,' intel core ') > 0 OR strpos(context_tokens,' galaxy a03 core ') > 0
    WHEN 'air' THEN strpos(context_tokens,' macbook air ') > 0 OR strpos(context_tokens,' ipad air ') > 0
    WHEN 'note' THEN strpos(context_tokens,' galaxy note ') > 0 OR strpos(context_tokens,' redmi note ') > 0
    WHEN 'surface' THEN strpos(context_tokens,' microsoft surface ') > 0
    WHEN 'pavilion' THEN strpos(context_tokens,' hp pavilion ') > 0
    WHEN 'aspire' THEN strpos(context_tokens,' acer aspire ') > 0
    WHEN 'envy' THEN strpos(context_tokens,' hp envy ') > 0
    WHEN 'spectre' THEN strpos(context_tokens,' hp spectre ') > 0
    WHEN 'pixel' THEN strpos(context_tokens,' google pixel ') > 0
    WHEN 'nord' THEN strpos(context_tokens,' oneplus nord ') > 0
    WHEN 'vega' THEN strpos(context_tokens,' amd radeon vega ') > 0
    WHEN 'legion' THEN strpos(context_tokens,' lenovo legion ') > 0
    ELSE false END
$$;

-- Literal phrase comparison with D2-aware boundary protection;never interpret user regex syntax.
CREATE FUNCTION public.catalog_search_phrase_v1(haystack text, needle text) RETURNS boolean
LANGUAGE plpgsql IMMUTABLE PARALLEL SAFE
SET search_path=pg_catalog,public AS $$
DECLARE
    at_pos integer := 1;
    found integer;
    before_ch text;
    after_ch text;
    left_ok boolean;
    right_ok boolean;
BEGIN
    IF needle IS NULL OR needle='' THEN RETURN false; END IF;
    LOOP
        found := strpos(substr(haystack,at_pos),needle);
        IF found=0 THEN RETURN false; END IF;
        found := found+at_pos-1;
        before_ch := CASE WHEN found=1 THEN '' ELSE substr(haystack,found-1,1) END;
        after_ch := substr(haystack,found+char_length(needle),1);
        left_ok := before_ch='' OR NOT catalog_search_alnum_v1(before_ch);
        right_ok := after_ch='' OR NOT catalog_search_alnum_v1(after_ch);
        IF before_ch IN ('-','_','/','.',chr(39),'+') AND found>2
                AND catalog_search_alnum_v1(substr(haystack,found-2,1)) THEN left_ok:=false; END IF;
        IF after_ch IN ('-','_','/','.',chr(39),'+')
                AND (catalog_search_alnum_v1(substr(haystack,found+char_length(needle)+1,1)) OR after_ch='+') THEN right_ok:=false; END IF;
        IF left_ok AND right_ok THEN RETURN true; END IF;
        at_pos:=found+1;
    END LOOP;
END $$;
