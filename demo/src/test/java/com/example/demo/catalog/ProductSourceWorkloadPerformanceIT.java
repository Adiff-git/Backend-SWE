package com.example.demo.catalog;

import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.filter;
import static org.assertj.core.api.Assertions.not;
import com.example.demo.catalog.dto.request.ProductSearchRequest;
import com.example.demo.catalog.search.ProductSearchParameterParser;
import com.example.demo.catalog.service.ProductCatalogService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/** Actual1500 source texts and1:1 source fanout;owned disposable fixtures,not target import evidence. */
@SpringBootTest
@ActiveProfiles("test")
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
@Import(CatalogQueryCountIT.FakeStorageConfiguration.class)
class ProductSourceWorkloadPerformanceIT {
    @Autowired ProductCatalogService service;
    @Autowired org.springframework.test.web.servlet.MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate tx;
    @Autowired jakarta.validation.Validator validator;
    @Autowired jakarta.persistence.EntityManager em;
    @BeforeEach @AfterEach void clear(){jdbc.execute("TRUNCATE users,categories,brands CASCADE");}
    @Test void actualSourceTexts_shouldHaveBoundedQueriesAndRepeatedMeasurements()throws Exception{
        var json=JsonMapper.builder().build();var seeded=seed();var frozen=seeded.frozen();var categories=seeded.categories();var brands=seeded.brands();
        var parser=new ProductSearchParameterParser(validator);var observations=new ArrayList<Map<String,Object>>();
        var queries=new LinkedHashMap<String,Map<String,List<String>>>();
        queries.put("browse",Map.of());queries.put("broad-laptop",Map.of("q",List.of("laptop")));queries.put("fuzzy",Map.of("q",List.of("samsnug")));
        queries.put("selective",Map.of("q",List.of("thinkpad")));
        queries.put("brand-category",Map.of("categoryId",List.of(categories.get("Laptop").toString()),"brandId",List.of(brands.get("Lenovo").toString())));
        queries.put("combined",Map.of("q",List.of("laptop"),"categoryId",List.of(categories.get("Laptop").toString()),"minPrice",List.of("4000000"),"maxPrice",List.of("150000000"),"specFilters",List.of("[{\"key\":\"physical_ram\",\"alternatives\":[{\"op\":\"gte\",\"value\":8,\"unit\":\"GB\"}]}]")));
        queries.put("price-specification",Map.of("categoryId",List.of(categories.get("Laptop").toString()),"minPrice",List.of("4000000"),"maxPrice",List.of("150000000"),"specFilters",List.of("[{\"key\":\"physical_ram\",\"alternatives\":[{\"op\":\"gte\",\"value\":8,\"unit\":\"GB\"}]}]")));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM products",Integer.class)).isEqualTo(1500);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product_variants",Integer.class)).isEqualTo(1500);
        for(var entry:queries.entrySet()){
            ProductSearchRequest request=parser.parse(entry.getValue());long start=System.nanoTime();var cold=http(entry.getValue());double coldMs=(System.nanoTime()-start)/1e6;
            observations.add(Map.of("scenario",entry.getKey(),"coldMs",coldMs,"total",cold.get("totalItems").asLong(),"phase","cold"));write(json,frozen,observations);
            for(int i=0;i<20;i++)http(entry.getValue());
            long[] times=new long[100];int maxData=0,maxSet=0;
            for(int i=0;i<100;i++){
                CatalogQueryCountIT.SqlCapture.begin();start=System.nanoTime();var result=http(entry.getValue());times[i]=System.nanoTime()-start;var sql=CatalogQueryCountIT.SqlCapture.end();
                int set=(int)sql.stream().filter(x->x.contains("set_config")).count();maxSet=Math.max(set,maxSet);maxData=Math.max(maxData,sql.size()-set);
                assertThat(result.get("totalItems").asLong()).isEqualTo(cold.get("totalItems").asLong());assertThat(result.get("items").size()).isLessThanOrEqualTo(20);
            }
            Arrays.sort(times);double p95=times[94]/1e6;
            double budget=switch(entry.getKey()){
                case "browse","brand-category","price-specification","combined" -> 500.0;
                case "selective" -> 1000.0;
                case "broad-laptop","fuzzy" -> 1500.0;
                default -> throw new IllegalArgumentException("Missing scenario budget: "+entry.getKey());
            };
            Map<String,Object> measurement=new LinkedHashMap<>();
            measurement.put("scenario",entry.getKey());measurement.put("p50Ms",times[49]/1e6);
            measurement.put("p95Ms",p95);measurement.put("maxMs",times[99]/1e6);
            measurement.put("timeoutCount",0);measurement.put("samples",100);measurement.put("warmups",20);
            measurement.put("maxDataStatements",maxData);measurement.put("maxSetStatements",maxSet);
            measurement.put("budgetMs",budget);measurement.put("phase","warm");
            measurement.put("sortedSamplesMs",Arrays.stream(times).mapToDouble(n->n/1e6).toArray());
            observations.add(measurement);write(json,frozen,observations);
            assertThat(maxData).isLessThanOrEqualTo(request.getQ()==null?6:8);
            // Owner-approved project-specific MVP budgets;all request errors still fail immediately.
            assertThat(p95).as(entry.getKey()).isLessThan(budget);
        }
    }
    private tools.jackson.databind.JsonNode http(Map<String,List<String>> parameters)throws Exception{
        var request=org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/products");parameters.forEach((key,values)->request.param(key,values.toArray(String[]::new)));
        var response=mvc.perform(request).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andReturn().getResponse();
        if(parameters.containsKey("q"))assertThat(response.getHeader("X-Catalog-Search-Mode")).isEqualTo("fuzzy-enabled");
        return JsonMapper.builder().build().readTree(response.getContentAsString());
    }
    record Seeded(tools.jackson.databind.JsonNode frozen,Map<String,UUID> categories,Map<String,UUID> brands){}
    private Seeded seed()throws Exception{
        var json=JsonMapper.builder().build();var frozen=json.readTree(getClass().getResourceAsStream("/catalog/search/source-workload-v1.json"));
        var categories=new HashMap<String,UUID>();var brands=new HashMap<String,UUID>();
        var productRows=new ArrayList<Object[]>();var variantRows=new ArrayList<Object[]>();int position=0;
        for(var r:frozen.get("records")){
            String category=r.get("category").asString();if(!categories.containsKey(category)){UUID id=UUID.randomUUID();jdbc.update("INSERT INTO categories(id,name) VALUES (?,?)",id,category);categories.put(category,id);}
            String brand=r.get("brand").isNull()?null:r.get("brand").asString();if(brand!=null&&!brands.containsKey(brand)){UUID id=UUID.randomUUID();jdbc.update("INSERT INTO brands(id,code,name) VALUES (?,?,?)",id,"fixture-"+brands.size(),brand);brands.put(brand,id);}
            UUID product=UUID.randomUUID();productRows.add(new Object[]{product,categories.get(category),brands.get(brand),r.get("name").asString(),r.get("description").isNull()?null:r.get("description").asString()});
            variantRows.add(new Object[]{UUID.randomUUID(),product,"SOURCE-FIXTURE-"+(++position),position*100000L,json.writeValueAsString(r.get("specifications"))});
        }
        tx.executeWithoutResult(st->{jdbc.batchUpdate("INSERT INTO products(id,category_id,brand_id,name,description,status) VALUES (?,?,?,?,?,'PUBLISHED')",productRows);jdbc.batchUpdate("INSERT INTO product_variants(id,product_id,sku,option_combination,selling_price,technical_specifications) VALUES (?,?,?,'{}'::jsonb,?,?::jsonb)",variantRows);});
        jdbc.execute("ANALYZE products");jdbc.execute("ANALYZE product_variants");
        return new Seeded(frozen,categories,brands);
    }
    @Test void sourceTextsBaselinePlan_shouldRecordActualSourceQueries()throws Exception{
        seed();var output=Path.of("target","source-workload-plans");Files.createDirectories(output);
        for(String keyword:List.of("laptop","samsnug")){
            var r=new ProductSearchRequest();r.setQ(keyword);
            String plan=tx.execute(st->com.example.demo.catalog.repository.SearchPlanCapture.explain(em,r));
            Files.writeString(output.resolve(keyword+".json"),plan);assertThat(plan).contains("Execution Time");
        }
    }
    private void write(JsonMapper json,tools.jackson.databind.JsonNode source,Object observations)throws Exception{
        var directory=Path.of("target","source-workload-performance");Files.createDirectories(directory);
        Files.writeString(directory.resolve("results.json"),json.writeValueAsString(Map.of("runtime",Map.of("java",System.getProperty("java.version"),"pid",ProcessHandle.current().pid(),"processors",Runtime.getRuntime().availableProcessors(),"hikariMaxPool",2,"serial",true,"failurePolicy","Any HTTP/request/timeout exception fails the test"),"products",1500,"variants",1500,"sourceProductSha256",source.get("sourceProductSha256").asString(),"targetDistributionVerified",false,"measurementBoundary","MockMvc HTTP controller/security/parser/service/SQL/serialization plus client JSON parse","database",jdbc.queryForObject("SELECT version()",String.class),"observations",observations)));
    }
}
