package com.example.demo.catalog.repository;
import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.in;
import static org.assertj.core.api.Assertions.not;
import com.example.demo.catalog.search.ProductSearchCriteria;
import com.example.demo.catalog.search.ProductSearchNormalizer;
import com.example.demo.catalog.search.ProductSearchParameterParser;
import com.example.demo.catalog.search.ProductSearchSort;
import com.example.demo.catalog.search.SpecificationFilterRegistry;
import jakarta.persistence.EntityManager;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(properties="spring.datasource.url=jdbc:tc:postgresql:17-alpine:///search_plans")
@ActiveProfiles("test")
class SearchQueryPlanIT {
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate tx;
    @Autowired jakarta.validation.Validator validator;
    @org.junit.jupiter.api.BeforeEach void clear(){jdbc.execute("TRUNCATE users,categories,brands CASCADE");}
    @Test void plans_shouldRecordActualExhaustiveAndCandidateQueries_beforeAddingIndexes()throws Exception{
        UUID category=UUID.randomUUID();jdbc.update("INSERT INTO categories(id,name) VALUES (?,'Laptop')",category);
        tx.executeWithoutResult(status->{
            jdbc.update("INSERT INTO products(id,category_id,name,description,status) SELECT md5('plan-product-'||i)::uuid,?,'Sony Alpha Camera '||i,repeat('Electronics portable photography ',3),'PUBLISHED' FROM generate_series(1,1500)i",category);
            jdbc.execute("INSERT INTO product_variants(id,product_id,sku,option_combination,selling_price,technical_specifications) SELECT md5('plan-variant-'||i)::uuid,md5('plan-product-'||i)::uuid,'PLAN-'||i,'{}'::jsonb,i*1000,'{\"schema_version\":1,\"values\":{\"physical_ram\":{\"value\":16,\"unit\":\"GB\"}}}'::jsonb FROM generate_series(1,1500)i");
        });jdbc.execute("ANALYZE products");jdbc.execute("ANALYZE product_variants");
        var output=Path.of("target","search-plans");Files.createDirectories(output);
        var normalizer=new ProductSearchNormalizer();
        for(String query:List.of("sony alpha","PLAN-1499","missingresult","combined","legacy","short","prefix","partial","fuzzy","brand","sort","deep")){
            var criteria=ProductSearchCriteria.builder().keyword(normalizer.normalizeQuery(query)).categoryId(category).minPrice(BigInteger.ZERO).build();
            if(query.equals("combined")){
                var r=new ProductSearchParameterParser(validator).parse(Map.of("categoryId",List.of(category.toString()),"q",List.of("sony alpha"),"minPrice",List.of("100000"),"maxPrice",List.of("1300000"),"specFilters",List.of("[{\"key\":\"physical_ram\",\"alternatives\":[{\"op\":\"gte\",\"value\":16,\"unit\":\"GB\"}]}]")));
                criteria=ProductSearchCriteria.builder().keyword(normalizer.normalizeQuery(r.getQ())).categoryId(category).minPrice(r.getMinPrice()).maxPrice(r.getMaxPrice()).specificationGroups(r.getSpecificationGroups()).build();
            }
            if(List.of("legacy","short","prefix","partial","fuzzy","sort","deep").contains(query)){
                String q=switch(query){case "legacy"->null;case "short"->"so";case "prefix"->"son";case "partial"->"lpha";case "fuzzy"->"sonny";default->"sony alpha";};
                criteria=ProductSearchCriteria.builder().keyword(normalizer.normalizeQuery(q)).categoryId(category).page(query.equals("deep")?50:0).sort(query.equals("sort")?ProductSearchSort.PRICE_DESC:null).build();
            }
            if(query.equals("brand")){
                UUID brand=UUID.randomUUID();jdbc.update("INSERT INTO brands(id,code,name) VALUES (?,'source-brand','Source Brand')",brand);jdbc.update("UPDATE products SET brand_id=?",brand);
                criteria=ProductSearchCriteria.builder().keyword(normalizer.normalizeQuery("source" )).categoryId(category).brandIds(List.of(brand)).build();
            }
            var statement=new CatalogSearchSql(new SpecificationFilterRegistry()).compile(criteria,.32,true);
            Files.writeString(output.resolve(query.replace(' ','-')+"-parameters.json"),JsonMapper.builder().build().writeValueAsString(statement.parameters()));
            Files.writeString(output.resolve(query.replace(' ','-')+".sql"),statement.sql());
            var plan=tx.execute(st->{em.createNativeQuery("SELECT set_config('pg_trgm.strict_word_similarity_threshold','0.32',true)").getSingleResult();var q=em.createNativeQuery("EXPLAIN(ANALYZE,BUFFERS,VERBOSE,SETTINGS,FORMAT JSON) "+statement.sql());statement.parameters().forEach(q::setParameter);return q.getSingleResult().toString();});
            assertThat(plan).contains("Plan");Files.writeString(output.resolve(query.replace(' ','-')+".json"),plan);
            if(criteria.getKeyword()==null || criteria.getKeyword().getTokens().isEmpty())continue;
            // Same SQL with superset disabled is a slow exhaustive oracle,not a different matcher.
            int start=statement.sql().indexOf("NOT EXISTS(SELECT 1 FROM query_tokens q WHERE NOT(");
            int end=statement.sql().indexOf(", query_tokens(idx",start);
            String candidate=statement.sql().substring(start,end);
            // The predicate is last in eligible_base:remove it while preserving its closing CTE paren.
            String oracle=statement.sql().substring(0,start)+"true)"+statement.sql().substring(end);
            var equal=tx.execute(st->{var a=em.createNativeQuery(statement.sql());var b=em.createNativeQuery(oracle);statement.parameters().forEach((k,v)->{a.setParameter(k,v);b.setParameter(k,v);});return Arrays.deepEquals(a.getResultList().toArray(),b.getResultList().toArray());});
            assertThat(equal).as("candidate membership and ranking vs exhaustive: %s",query).isTrue();
        }
    }
    @Test void exploratoryTenThousandAndVariantSkew_shouldKeepSelectiveResultsAndRecordPlans()throws Exception{
        UUID category=UUID.randomUUID();jdbc.update("INSERT INTO categories(id,name) VALUES (?,'Laptop')",category);
        tx.executeWithoutResult(st->{
            jdbc.update("INSERT INTO products(id,category_id,name,status) SELECT md5('scale-p-'||i)::uuid,?,'Scale Laptop '||i,'PUBLISHED' FROM generate_series(1,10000)i",category);
            jdbc.execute("INSERT INTO product_variants(id,product_id,sku,option_combination,selling_price) SELECT md5('scale-v-'||i||'-0')::uuid,md5('scale-p-'||i)::uuid,'SCALE-'||i,jsonb_build_object('option','0'),i*1000 FROM generate_series(1,10000)i");
        });
        var output=Path.of("target","search-plans");Files.createDirectories(output);
        for(int fanout:List.of(1,5,20)){
            if(fanout>1)tx.executeWithoutResult(st->jdbc.update("INSERT INTO product_variants(id,product_id,sku,option_combination,selling_price) SELECT md5('scale-v-'||i||'-'||n)::uuid,md5('scale-p-'||i)::uuid,'SCALE-'||i||'-'||n,jsonb_build_object('option',n::text),i*1000+n FROM generate_series(1,1000)i CROSS JOIN generate_series(?,?)n",fanout==5?1:5,fanout-1));
            jdbc.execute("ANALYZE products");jdbc.execute("ANALYZE product_variants");
            var criteria=ProductSearchCriteria.builder().keyword(new ProductSearchNormalizer().normalizeQuery("SCALE-1499")).build();
            var statement=new CatalogSearchSql(new SpecificationFilterRegistry()).compile(criteria,.32,true);
            var plan=tx.execute(st->{em.createNativeQuery("SELECT set_config('pg_trgm.strict_word_similarity_threshold','0.32',true)").getSingleResult();var q=em.createNativeQuery("EXPLAIN(ANALYZE,BUFFERS,VERBOSE,SETTINGS,FORMAT JSON) "+statement.sql());statement.parameters().forEach(q::setParameter);return q.getSingleResult().toString();});
            Files.writeString(output.resolve("scale-"+fanout+".json"),plan);
            var actual=tx.execute(st->{var q=em.createNativeQuery(statement.sql());statement.parameters().forEach(q::setParameter);return (Object[])q.getResultList().getFirst();});
            assertThat(actual[0]).isEqualTo(jdbc.queryForObject("SELECT md5('scale-p-1499')::uuid",UUID.class));assertThat(((Number)actual[3]).longValue()).isOne();
            Files.writeString(output.resolve("scale-"+fanout+"-metadata.json"),JsonMapper.builder().build().writeValueAsString(Map.of("products",10000,"variants",jdbc.queryForObject("SELECT count(*) FROM product_variants",Long.class),"skewedProducts",1000,"fanoutOnSkewedProducts",fanout,"query","SCALE-1499")));
        }
    }
}
