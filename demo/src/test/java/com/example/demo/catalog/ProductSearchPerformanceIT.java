package com.example.demo.catalog;

import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.filter;
import com.example.demo.catalog.dto.request.ProductSearchRequest;
import com.example.demo.catalog.service.ProductCatalogService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
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

@SpringBootTest
@ActiveProfiles("test")
@Import(CatalogQueryCountIT.FakeStorageConfiguration.class)
class ProductSearchPerformanceIT {
    @Autowired ProductCatalogService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate tx;
    @Autowired jakarta.validation.Validator validator;
    @BeforeEach @AfterEach void clear(){jdbc.execute("TRUNCATE users,categories,brands CASCADE");}
    @Test void benchmark_shouldMeasureFullServiceAndAllJdbcStatements()throws Exception{
        UUID category=UUID.randomUUID();jdbc.update("INSERT INTO categories(id,name) VALUES (?,'Laptop')",category);
        tx.executeWithoutResult(status->{
            jdbc.update("INSERT INTO products(id,category_id,name,description,status) SELECT md5('search-product-'||i)::uuid,?, CASE WHEN i%4=0 THEN 'Samsung Galaxy A51' WHEN i%4=1 THEN 'Lenovo ThinkPad Laptop' WHEN i%4=2 THEN 'Sony Alpha Camera' ELSE 'Điện thoại Alpha' END||' '||i,repeat('electronics display quality portable ',3),'PUBLISHED' FROM generate_series(1,1500)i",category);
            jdbc.execute("INSERT INTO product_variants(id,product_id,sku,option_combination,selling_price,technical_specifications) SELECT md5('search-variant-'||i)::uuid,md5('search-product-'||i)::uuid,'DEMO-'||i,'{}'::jsonb,i*1000,jsonb_build_object('schema_version',1,'values',jsonb_build_object('physical_ram',jsonb_build_object('value',16,'unit','GB'))) FROM generate_series(1,1500)i");
        });jdbc.execute("ANALYZE products");jdbc.execute("ANALYZE product_variants");
        var observations=new ArrayList<Map<String,Object>>();
        for(String keyword:List.of("","samsnug","sony alpha","DEMO-1499","missingresult","combined")){
            var r=new ProductSearchRequest();r.setQ(keyword);
            if(keyword.equals("combined"))r=new com.example.demo.catalog.search.ProductSearchParameterParser(validator).parse(Map.of("q",List.of("sony alpha"),"categoryId",List.of(category.toString()),"minPrice",List.of("100000"),"maxPrice",List.of("1300000"),"specFilters",List.of("[{\"key\":\"physical_ram\",\"alternatives\":[{\"op\":\"gte\",\"value\":16,\"unit\":\"GB\"}]}]")));
            long cold=System.nanoTime();service.listProducts(r);cold=System.nanoTime()-cold;
            for(int i=0;i<20;i++)service.listProducts(r);
            long[] durations=new long[100];int maxData=0,maxSet=0;
            for(int i=0;i<100;i++){
                CatalogQueryCountIT.SqlCapture.begin();long start=System.nanoTime();
                var result=service.listProducts(r);durations[i]=System.nanoTime()-start;
                var sql=CatalogQueryCountIT.SqlCapture.end();int set=(int)sql.stream().filter(x->x.contains("set_config")).count();
                maxSet=Math.max(maxSet,set);maxData=Math.max(maxData,sql.size()-set);
                assertThat(result.getItems()).hasSizeLessThanOrEqualTo(20);
            }
            Arrays.sort(durations);double p95=durations[94]/1e6;
            observations.add(Map.of("query",keyword,"p95Ms",p95,"coldMs",cold/1e6,"maxDataStatements",maxData,"maxSetStatements",maxSet));
            assertThat(maxData).isLessThanOrEqualTo(keyword.isEmpty()?6:8);
            // Keep actual measurements even if a latency assertion fails.
            write(observations);
            assertThat(p95).as("p95 for %s",keyword).isLessThanOrEqualTo(1000.0);
        }
        try(var workers=Executors.newFixedThreadPool(10)){
            var calls=new ArrayList<Callable<Long>>();for(int i=0;i<20;i++)calls.add(()->{long start=System.nanoTime();var r=new ProductSearchRequest();r.setQ("sony alpha");assertThat(service.listProducts(r).getTotalItems()).isEqualTo(375);return System.nanoTime()-start;});
            var times=new ArrayList<Long>();for(var f:workers.invokeAll(calls))times.add(f.get());
            times.sort(Long::compare);observations.add(Map.of("diagnosticReaders",10,"journeys",20,"p95Ms",times.get(18)/1e6));
        }
        write(observations);
    }
    private void write(Object observations)throws Exception{
        var output=Path.of("target","search-performance");Files.createDirectories(output);
        Files.writeString(output.resolve("results.json"),JsonMapper.builder().build().writeValueAsString(Map.of("products",1500,"variants",1500,"warmups",20,"samplesPerQuery",100,"database",jdbc.queryForObject("SELECT version()",String.class),"observations",observations)));
    }
}
