package com.example.demo.catalog.service;

import com.example.demo.catalog.dto.request.AdminProductRequests;
import com.example.demo.catalog.dto.request.AdminVariantRequests;
import com.example.demo.catalog.dto.request.CatalogField;
import com.example.demo.catalog.dto.response.AdminCatalogResponses.ProductDetail;
import com.example.demo.catalog.storage.ProductImageStorage;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** Real committed Catalog service tests. Local signing is explicitly not storage evidence. */
@SpringBootTest
@ActiveProfiles("test")
@Import(AdminCatalogIntegrationSupport.LocalSigning.class)
public abstract class AdminCatalogIntegrationSupport {
    @Autowired protected AdminProductService products;
    @Autowired protected AdminVariantService variants;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected TestSigner testSigner;
    protected UUID category;
    @BeforeEach void cleanAndSeedCategory(){
        testSigner.reset();
        jdbc.execute("TRUNCATE users,categories,brands RESTART IDENTITY CASCADE");
        category=UUID.randomUUID();jdbc.update("INSERT INTO categories(id,name) VALUES (?,'Laptop')",category);
    }
    protected ProductDetail draft(){return products.create(new AdminProductRequests.Create("Prepared product",category,CatalogField.absent(),null,CatalogField.absent()));}
    protected long version(ProductDetail detail){return Long.parseLong(detail.getVersion());}
    protected long taxonomy(ProductDetail detail){return Long.parseLong(detail.getTaxonomyVersion());}
    protected ProductDetail transition(UUID id,AdminProductRequests.Action action){ProductDetail d=products.get(id);return products.transition(id,version(d),taxonomy(d),action);}
    protected UUID variant(UUID product,String sku,String color,BigDecimal price){
        ProductDetail d=products.get(product);
        return variants.create(product,new AdminVariantRequests.Create(version(d),taxonomy(d),sku,price,Map.of("color",color),Map.of("schema_version",1,"values",Map.of()),true)).getVariant().getId();
    }
    protected UUID image(UUID product,int order,boolean primary){
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO product_images(id,product_id,object_key,alt_text,content_type,byte_size,width,height,display_order,is_primary,created_at) VALUES (?,?,?,'Prepared image','image/png',100,10,10,?,?,CURRENT_TIMESTAMP)",id,product,"images/synthetic-service/"+id,order,primary);
        return id;
    }
    protected UUID ready(){UUID id=draft().getId();variant(id,"SKU-"+id,"black",BigDecimal.valueOf(1000));image(id,0,true);transition(id,AdminProductRequests.Action.PUBLISH);return id;}
    /** Instrumented local signer; never presented as object-storage verification. */
    static class TestSigner implements ProductImageStorage {
        volatile Runnable beforePrepare=()->{};
        final java.util.List<String> signedKeys=new java.util.concurrent.CopyOnWriteArrayList<>();
        void reset(){beforePrepare=()->{};signedKeys.clear();}
        public PreparedReadSigner prepareReadSigner(){
            beforePrepare.run();
            return new PreparedReadSigner(){
                public String signReadUrl(String key){signedKeys.add(key);return "https://images.test/catalog-service-read";}
                public void close(){}
            };
        }
    }
    @TestConfiguration(proxyBeanMethods=false)
    public static class LocalSigning {
        @Bean @Primary TestSigner preparedCatalogSigner(){return new TestSigner();}
    }
}
