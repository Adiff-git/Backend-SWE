package com.example.demo.catalog.repository;

import com.example.demo.catalog.search.ProductIdentifierVocabulary;
import com.example.demo.catalog.search.ProductSearchCriteria;
import com.example.demo.catalog.search.ProductSearchNormalizer;
import com.example.demo.catalog.search.ProductSearchSort;
import com.example.demo.catalog.search.SpecificationFilterRegistry;
import com.example.demo.catalog.dto.request.ProductSearchRequest.NumericValue;
import com.example.demo.catalog.dto.request.ProductSearchRequest.SpecificationOperator;
import com.example.demo.catalog.dto.request.ProductSearchRequest.TextValue;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.math.BigDecimal;

/** Trusted SQL composition;all client values bound. One eligibility relation drives count,rank and display. */
final class CatalogSearchSql {
    record Statement(String sql,Map<String,Object> parameters){}
    private final SpecificationFilterRegistry registry;
    CatalogSearchSql(SpecificationFilterRegistry registry){this.registry=registry;}
    Statement compile(ProductSearchCriteria c,double threshold,boolean fuzzy){
        Map<String,Object> params=new LinkedHashMap<>();
        List<String> where=new ArrayList<>(List.of("p.status='PUBLISHED'"));
        String categoryRoot=c.getCategoryId()==null?"SELECT id FROM categories":"SELECT id FROM categories WHERE id=:category";
        if(c.getCategoryId()!=null){params.put("category",c.getCategoryId());where.add("p.category_id IN(SELECT id FROM category_scope)");}
        if(c.getBrandIds()!=null){params.put("brands",c.getBrandIds());where.add("p.brand_id IN(:brands)");}
        StringBuilder sql=new StringBuilder("""
            WITH RECURSIVE category_scope(id) AS (
            """);
        sql.append(categoryRoot).append(" UNION SELECT c.id FROM categories c JOIN category_scope s ON c.parent_category_id=s.id),");
        sql.append("""
            registry_roots AS (
                SELECT catalog_search_safe_normalize_v2(name) AS family,min(id::text)::uuid AS id FROM categories
                WHERE catalog_search_safe_normalize_v2(name) IN ('laptop','tablet')
                GROUP BY catalog_search_safe_normalize_v2(name) HAVING count(*)=1
            ), registry_categories(family,id) AS (
                SELECT family,id FROM registry_roots UNION
                SELECT r.family,c.id FROM registry_categories r JOIN categories c ON c.parent_category_id=r.id
                WHERE catalog_search_safe_normalize_v2(c.name)=r.family OR catalog_search_safe_normalize_v2(c.name) NOT IN ('laptop','tablet','smartphone','computer accessories','headphone & earbuds','charger & power bank','speaker','phone accessories','smartwatch')
            ), selected_products AS MATERIALIZED (
                SELECT p.id,p.category_id,p.name,catalog_search_safe_normalize_v2(p.name) AS normalized_name,
                    catalog_search_safe_normalize_v2(coalesce(b.name,'')) AS brand_name,
                    catalog_search_safe_normalize_v2(coalesce(b.code,'')) AS brand_code,p.description,p.catalog_description_tokens_v1
                FROM products p LEFT JOIN brands b ON b.id=p.brand_id WHERE
            """).append(String.join(" AND ",where)).append(")");
        List<String> factViews=new ArrayList<>();
        int definitionIndex=0;
        for(var d:registry.definitions().values().stream().sorted(Comparator.comparing(SpecificationFilterRegistry.Definition::key)).toList()){
            String n="definition"+definitionIndex++;params.put(n,d.key());
            String raw="(v.technical_specifications->'values'->:"+n+")";
            String envelope="v.technical_specifications->'schema_version'='1'::jsonb AND jsonb_typeof(v.technical_specifications->'values')='object'";
            String num="CASE WHEN "+envelope+" AND jsonb_typeof("+raw+")='object' THEN CASE WHEN ("+raw+" - 'value' - 'unit')='{}'::jsonb AND jsonb_typeof("+raw+"->'value')='number' AND "+raw+"->>'unit'="+literal(d.unit())+" THEN ("+raw+"->>'value')::numeric END END";
            String text="CASE WHEN "+envelope+" AND jsonb_typeof("+raw+")='string' THEN catalog_search_safe_normalize_v2("+raw+"#>>'{}') END";
            String numVal=d.type().equals("quantity")?num:"NULL::numeric";
            String txtVal=d.type().equals("text")?text:"NULL::text";
            List<String> families=d.categoryRoots().stream().map(x->literal(x.toLowerCase(Locale.ROOT))).toList();
            String quality=switch(d.key()){
                case "display_diagonal" -> " AND f.amount IS DISTINCT FROM 6088";
                case "installed_storage_gb" -> " AND NOT(coalesce(f.amount=65,false) AND p.normalized_name ~ '(^|[^[:alnum:]])64[ ]*gb([^[:alnum:]]|$)')";
                case "display_resolution" -> " AND NOT(coalesce(f.text_value ~ '^1600[ ]*x[ ]*600$',false) AND strpos(p.normalized_name,'16:10')>0)";
                default -> "";};
            factViews.add("SELECT p.id AS product_id,v.id AS variant_id,:"+n+" AS key,f.amount,"+literal(d.unit())+"::text AS unit,f.text_value FROM selected_products p JOIN product_variants v ON v.product_id=p.id CROSS JOIN LATERAL(SELECT "+numVal+" AS amount,"+txtVal+" AS text_value) f WHERE EXISTS(SELECT 1 FROM registry_categories rc WHERE rc.id=p.category_id AND rc.family IN("+String.join(",",families)+")) AND (f.amount IS NOT NULL OR coalesce(f.text_value,'')<>'')"+quality);
        }
        sql.append(", safe_facts AS MATERIALIZED (").append(String.join(" UNION ALL ",factViews)).append(")");
        boolean keyword=c.getKeyword()!=null&&!c.getKeyword().getTokens().isEmpty();
        List<String> variantConditions=new ArrayList<>();
        if(c.getMinPrice()!=null){variantConditions.add("v.selling_price>=:minPrice");params.put("minPrice",c.getMinPrice());}
        if(c.getMaxPrice()!=null){variantConditions.add("v.selling_price<=:maxPrice");params.put("maxPrice",c.getMaxPrice());}
        List<String> support=new ArrayList<>();
        if(c.getSpecificationGroups()!=null){int g=0;
            for(var group:c.getSpecificationGroups()){
                var d=registry.get(group.getKey());String prefix="spec"+g++;params.put(prefix+"key",d.key());
                List<String> alternatives=new ArrayList<>();int i=0;
                for(var a:group.getAlternatives()){
                    String p=prefix+"a"+i++;
                    if(a.getOp()==SpecificationOperator.BETWEEN){params.put(p+"min",a.getMin());params.put(p+"max",a.getMax());alternatives.add("(f.amount>=:"+p+"min AND f.amount<=:"+p+"max)");}
                    else if(a.getValue() instanceof NumericValue number){params.put(p,number.getAmount());String op=switch(a.getOp()){case EQ->"=";case GT->">";case GTE->">=";case LT->"<";case LTE->"<=";default->throw new IllegalArgumentException("Unvalidated numeric operator");};alternatives.add("f.amount"+op+":"+p);}
                    else if(a.getValue() instanceof TextValue text){params.put(p,new ProductSearchNormalizer().normalize(text.getText()));alternatives.add(switch(a.getOp()){case EQ->"f.text_value=:"+p;case PREFIX->"left(f.text_value,char_length(:"+p+"))=:"+p;case CONTAINS->"strpos(f.text_value,:"+p+")>0";default->throw new IllegalArgumentException("Unvalidated text operator");});}
                }
                variantConditions.add("EXISTS(SELECT 1 FROM safe_facts f WHERE f.variant_id=v.id AND f.key=:"+prefix+"key AND ("+String.join(" OR ",alternatives)+"))");
                support.add("EXISTS(SELECT 1 FROM registry_categories rc WHERE rc.id=cs.id AND rc.family IN("+String.join(",",d.categoryRoots().stream().map(x->literal(x.toLowerCase(Locale.ROOT))).toList())+"))");
            }
        }
        if(keyword){
            // Recall-preserving superset only:the exact token classifier still decides membership/rank.
            // Avoid tokenizing descriptions/fields for products that cannot satisfy every query token.
            String fuzzyCandidate=fuzzy?" OR (NOT q.protected AND char_length(q.token)>=4 AND (q.token <<% p.normalized_name OR q.token <<% p.brand_name))":"";
            variantConditions.add("NOT EXISTS(SELECT 1 FROM query_tokens q WHERE NOT("
                    +"strpos(p.normalized_name,q.token)>0 OR strpos(p.brand_name,q.token)>0 OR strpos(p.brand_code,q.token)>0 "
                    +"OR strpos(catalog_search_safe_normalize_v2(coalesce(p.description,'')),q.token)>0 "
                    +"OR strpos(catalog_search_safe_normalize_v2(coalesce(v.sku,'')),q.token)>0 "
                    +"OR EXISTS(SELECT 1 FROM safe_facts f WHERE f.variant_id=v.id AND (strpos(f.text_value,q.token)>0 OR f.amount=q.numeric_value OR lower(f.unit)=q.token))"
                    +fuzzyCandidate+"))");
        }
        String unsupported=support.isEmpty()?"false":"EXISTS(SELECT 1 FROM categories WHERE id=:category) AND NOT EXISTS(SELECT 1 FROM category_scope cs WHERE "+String.join(" AND ",support)+")";
        sql.append(", validation AS(SELECT ").append(unsupported).append(" AS unsupported),eligible_base AS MATERIALIZED(SELECT p.id AS product_id,v.id AS variant_id,v.selling_price AS display_amount,p.normalized_name FROM selected_products p LEFT JOIN product_variants v ON v.product_id=p.id");
        if(!variantConditions.isEmpty()){sql.append(" WHERE ").append(String.join(" AND ",variantConditions));}sql.append(")");
        if(keyword){
            params.put("phrase",c.getKeyword().getOriginalNormalized());params.put("phraseLength",c.getKeyword().getSequence().size());if(fuzzy){params.put("threshold",threshold);}
            List<String> queryRows=new ArrayList<>();var vocabulary=new ProductIdentifierVocabulary();int n=0;
            for(var token:c.getKeyword().getTokens()){
                String p="q"+n;params.put(p,token.getText());params.put(p+"protected",vocabulary.isProtected(token,c.getKeyword().getOriginalNormalized()));
                BigDecimal numeric=null;if(token.getText().matches("[+-]?(?:[0-9]+(?:\\.[0-9]+)?|\\.[0-9]+)")){numeric=new BigDecimal(token.getText());}
                params.put(p+"number",numeric);queryRows.add("("+(n++)+",cast(:"+p+" AS text),cast(:"+p+"protected AS boolean),cast(:"+p+"number AS numeric))");
            }
            params.put("tokenCount",n);
            sql.append(", query_tokens(idx,token,protected,numeric_value) AS(VALUES ").append(String.join(",",queryRows)).append(")");
            sql.append("""
                ,field_views AS MATERIALIZED (
                    SELECT p.id AS product_id,NULL::uuid AS variant_id,x.view,x.source,x.fuzzy,x.context_prefix,CASE WHEN x.source=1 THEN p.catalog_description_tokens_v1 END AS cached_tokens
                    FROM selected_products p CROSS JOIN LATERAL(VALUES
                        (p.normalized_name,5,true,p.brand_name),
                        (p.brand_name,4,true,''),(p.brand_code,4,false,''),
                        (catalog_search_safe_normalize_v2(coalesce(p.description,'')),1,false,'')) x(view,source,fuzzy,context_prefix)
                    WHERE EXISTS(SELECT 1 FROM eligible_base e WHERE e.product_id=p.id)
                    UNION ALL SELECT v.product_id,v.id,catalog_search_safe_normalize_v2(v.sku),6,false,'',NULL::text[]
                        FROM product_variants v JOIN eligible_base e ON e.variant_id=v.id
                    UNION ALL SELECT f.product_id,f.variant_id,f.text_value,3,false,'',NULL::text[] FROM safe_facts f WHERE f.text_value IS NOT NULL
                    UNION ALL SELECT f.product_id,f.variant_id,trim_scale(f.amount)::text||' '||lower(f.unit),0,false,'',NULL::text[] FROM safe_facts f WHERE f.amount IS NOT NULL
                ),field_tokens AS MATERIALIZED (
                    SELECT f.*,coalesce(f.cached_tokens,ARRAY(SELECT t.token FROM catalog_search_tokens_v1(f.view) WITH ORDINALITY t(token,protected,pos) ORDER BY pos)) AS tokens
                    FROM field_views f WHERE f.source<>0 AND f.view<>'' AND EXISTS(SELECT 1 FROM eligible_base e WHERE e.product_id=f.product_id)
                ),word_views AS MATERIALIZED (
                    SELECT f.product_id,f.variant_id,f.source,f.fuzzy,w.token,
                        CASE WHEN f.fuzzy THEN ' '||f.context_prefix||' '||array_to_string(f.tokens,' ')||' ' ELSE '' END AS context_tokens
                    FROM field_tokens f CROSS JOIN LATERAL unnest(f.tokens) w(token)
                    WHERE f.source<>0 AND (f.fuzzy OR EXISTS(SELECT 1 FROM query_tokens q WHERE strpos(w.token,q.token)>0))
                ),word_hits AS (
                    SELECT w.product_id,w.variant_id,q.idx,w.source,
                        CASE WHEN q.token=w.token THEN 4
                             WHEN left(w.token,char_length(q.token))=q.token THEN 3
                             WHEN char_length(q.token)>=4 AND strpos(w.token,q.token)>0 THEN 2
                """);
            if(fuzzy){sql.append(" WHEN w.fuzzy AND char_length(q.token)>=4 AND char_length(w.token)>=4 AND NOT q.protected AND NOT catalog_search_protected_v1(w.token,w.context_tokens) AND q.token <<% w.token AND similarity(q.token,w.token)>:threshold AND catalog_search_typo_v1(q.token,w.token) THEN 1");}
            sql.append(" ELSE 0 END AS class, CASE WHEN w.fuzzy THEN round(similarity(q.token,w.token)::numeric,6) ELSE 0 END AS similarity FROM word_views w CROSS JOIN query_tokens q)");
            sql.append("""
                ,all_hits AS MATERIALIZED (
                    SELECT * FROM word_hits WHERE class>0
                    UNION ALL SELECT f.product_id,f.variant_id,q.idx,3,4,0 FROM safe_facts f CROSS JOIN query_tokens q
                        WHERE f.amount IS NOT NULL AND (f.amount=q.numeric_value OR lower(f.unit)=q.token)
                ),best_hits AS MATERIALIZED (
                    SELECT DISTINCT ON(e.product_id,e.variant_id,h.idx) e.product_id,e.variant_id,e.display_amount,e.normalized_name,h.idx,h.class,h.source,
                        CASE WHEN h.class=1 THEN h.similarity ELSE 0 END AS similarity
                    FROM eligible_base e JOIN all_hits h ON h.product_id=e.product_id AND (h.variant_id IS NULL OR h.variant_id=e.variant_id)
                    ORDER BY e.product_id,e.variant_id,h.idx,h.class DESC,h.source DESC,h.similarity DESC
                ),ranked_variants AS (
                    SELECT h.product_id,h.variant_id,h.display_amount,
                        CASE WHEN h.variant_id IS NOT NULL AND EXISTS(SELECT 1 FROM field_views f WHERE f.variant_id=h.variant_id AND f.source=6 AND f.view=:phrase) THEN 7
                             WHEN h.normalized_name=:phrase THEN 6
                             WHEN :phraseLength>=2 AND EXISTS(SELECT 1 FROM field_views f WHERE f.product_id=h.product_id AND (f.variant_id IS NULL OR f.variant_id=h.variant_id) AND catalog_search_phrase_v1(f.view,:phrase)) THEN 5
                             ELSE min(h.class) END AS tier,
                        sum(h.class) AS class_sum,sum(h.source) AS source_sum,sum(h.similarity) AS similarity_sum
                    FROM best_hits h
                    GROUP BY h.product_id,h.variant_id,h.display_amount,h.normalized_name HAVING count(*)=:tokenCount
                )
                """);
        }else{sql.append(", ranked_variants AS(SELECT e.*,0 AS tier,0 AS class_sum,0 AS source_sum,0 AS similarity_sum FROM eligible_base e)");}
        sql.append("""
            ,representatives AS(SELECT DISTINCT ON(product_id) product_id,variant_id,display_amount FROM ranked_variants ORDER BY product_id,display_amount ASC NULLS LAST,variant_id ASC NULLS LAST),
            best_ranks AS(SELECT DISTINCT ON(product_id) product_id,tier,class_sum,source_sum,similarity_sum FROM ranked_variants ORDER BY product_id,tier DESC,class_sum DESC,source_sum DESC,similarity_sum DESC),
            reduced AS(SELECT r.*,p.normalized_name,b.tier,b.class_sum,b.source_sum,b.similarity_sum FROM representatives r JOIN best_ranks b USING(product_id) JOIN selected_products p ON p.id=r.product_id),
            totals AS(SELECT count(*) AS total FROM reduced),candidate_page AS(SELECT * FROM reduced ORDER BY
            """);
        ProductSearchSort sort=c.getSort();String order=sort==null?(keyword?"tier DESC,class_sum DESC,source_sum DESC,similarity_sum DESC,product_id ASC":"product_id ASC"):switch(sort){case RELEVANCE->"tier DESC,class_sum DESC,source_sum DESC,similarity_sum DESC,product_id ASC";case PRICE_ASC->"display_amount ASC NULLS LAST,product_id ASC";case PRICE_DESC->"display_amount DESC NULLS LAST,product_id ASC";case NAME_ASC->"normalized_name COLLATE \"C\" ASC,product_id ASC";case NAME_DESC->"normalized_name COLLATE \"C\" DESC,product_id ASC";};
        params.put("size",c.getSize());params.put("offset",(long)c.getPage()*c.getSize());
        sql.append(order).append(" LIMIT :size OFFSET :offset) SELECT p.product_id,p.variant_id,p.display_amount,t.total,v.unsupported FROM totals t CROSS JOIN validation v LEFT JOIN candidate_page p ON true ORDER BY ").append(order);
        return new Statement(fuzzy ? sql.toString() : sql.toString().replace("CASE WHEN w.fuzzy THEN round(similarity(q.token,w.token)::numeric,6) ELSE 0 END", "0::numeric"),params);
    }
    private static String literal(String trusted){return trusted==null?"NULL":"'"+trusted.replace("'","''")+"'";}
}
