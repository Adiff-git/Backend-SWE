package com.example.demo.catalog.controller.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.media.integration.SeededMediaTestSupport;
import com.example.demo.user.service.UserService;
import java.math.BigInteger;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Real login bearer, filter chain, services, PostgreSQL commits and isolated LocalStack. */
@SpringBootTest
@AutoConfigureMockMvc
@org.springframework.test.context.TestPropertySource(properties="spring.datasource.hikari.maximum-pool-size=6")
class AdminCatalogApiIT extends SeededMediaTestSupport {
    private static final String BASE="/api/admin/catalog";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserService userService;
    @Autowired PasswordEncoder passwords;
    @Autowired JsonMapper json;
    @Autowired org.springframework.security.oauth2.jwt.JwtEncoder encoder;
    @Autowired org.springframework.security.oauth2.jwt.JwtDecoder decoder;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean com.example.demo.catalog.repository.CatalogMutationRepository revisionBarrier;
    private UUID category;
    private Actor admin;
    record Actor(long id,String token,String refresh) { }

    @BeforeEach void prepareAdmin() throws Exception {
        jdbc.execute("TRUNCATE users,categories,brands RESTART IDENTITY CASCADE");
        category=UUID.randomUUID();jdbc.update("INSERT INTO categories(id,name) VALUES (?,'Laptop')",category);
        admin=actor(true);
    }
    private Actor actor(boolean elevated) throws Exception {
        String name="catalog-"+UUID.randomUUID();
        long id=userService.createVerifiedAccount(name+"@example.test",passwords.encode("Password 123"),Instant.now());
        if(elevated){
            jdbc.update("INSERT INTO roles(name) VALUES ('ADMIN') ON CONFLICT(name) DO NOTHING");
            jdbc.update("INSERT INTO user_roles(user_id,role_id) SELECT ?,id FROM roles WHERE name='ADMIN'",id);
        }
        var response=mvc.perform(request(HttpMethod.POST,"/api/auth/login").contentType("application/json")
                .content(json.writeValueAsString(Map.of("identifier",name,"password","Password 123"))))
                .andExpect(status().isOk()).andReturn().getResponse();
        return new Actor(id,response.getHeader("Authorization"),json.readTree(response.getContentAsString()).path("refreshToken").asText());
    }
    private JsonNode call(HttpMethod method,String path,Object body,int expected) throws Exception {
        var builder=request(method,path).header("Authorization",admin.token()).contentType("application/json");
        if(body!=null)builder.content(json.writeValueAsString(body));
        var response=mvc.perform(builder).andExpect(status().is(expected)).andReturn().getResponse();
        assertThat(response.getHeader("Cache-Control")).contains("no-store");
        String value=response.getContentAsString();return value.isBlank()?null:json.readTree(value);
    }
    private JsonNode create() throws Exception {return call(HttpMethod.POST,BASE+"/products",Map.of("name","HTTP product","categoryId",category),201);}
    private String product(JsonNode detail){return BASE+"/products/"+detail.path("id").asText();}
    private Map<String,Object> revisions(String path) throws Exception {
        var d=call(HttpMethod.GET,path,null,200);Map<String,Object> result=new LinkedHashMap<>();
        result.put("expectedVersion",d.path("version").asText());result.put("expectedTaxonomyVersion",d.path("taxonomyVersion").asText());return result;
    }
    private JsonNode variant(String path,String sku,String color) throws Exception {
        var body=revisions(path);body.put("sku",sku);body.put("sellingPrice",Map.of("amount",1000,"currency","VND"));body.put("options",Map.of("color",color));
        return call(HttpMethod.POST,path+"/variants",body,201).path("variant");
    }
    private UUID completed() {
        var upload=prepare(admin.id());stage(upload);return media.completeImageUpload(admin.id(),upload.getId()).getId();
    }
    private JsonNode attach(String path,UUID source) throws Exception {
        var body=revisions(path);body.put("imageId",source);body.put("altText","HTTP image");return call(HttpMethod.POST,path+"/images",body,201);
    }
    @Test void productsShouldFollowRealHttpLifecycleWithVariantlessDraftAndZeroStock() throws Exception {
        var d=create();String path=product(d);
        assertThat(d.path("status").asText()).isEqualTo("DRAFT");assertThat(d.path("variants").size()).isZero();assertThat(d.path("brand").isNull()).isTrue();
        call(HttpMethod.POST,path+"/publish",revisions(path),422);
        assertThat(call(HttpMethod.GET,path,null,200).path("version").asText()).isEqualTo("0");
        variant(path,"HTTP-READY","black");attach(path,completed());
        assertThat(jdbc.queryForObject("SELECT stock FROM inventory_balances",Long.class)).isZero();
        var published=call(HttpMethod.POST,path+"/publish",revisions(path),200);assertThat(published.path("status").asText()).isEqualTo("PUBLISHED");
        var current=revisions(path);assertThat(call(HttpMethod.POST,path+"/publish",current,200).path("version").asText()).isEqualTo(current.get("expectedVersion"));
        call(HttpMethod.POST,path+"/unpublish",current,200);call(HttpMethod.POST,path+"/unpublish",current,409);
        call(HttpMethod.POST,path+"/publish",revisions(path),200);call(HttpMethod.POST,path+"/archive",revisions(path),200);
        var forbidden=revisions(path);forbidden.put("name","Must not change");call(HttpMethod.PATCH,path,forbidden,409);
        var restored=call(HttpMethod.POST,path+"/restore",revisions(path),200);assertThat(restored.path("status").asText()).isEqualTo("DRAFT");assertThat(restored.path("lastRestoredAt").isTextual()).isTrue();
        assertThat(call(HttpMethod.GET,BASE+"/products?status=DRAFT",null,200).path("totalItems").asInt()).isOne();
    }
    @Test void variantsAndSpecificationsShouldUseSameRevisionAndPreserveExactPrices() throws Exception {
        String path=product(create());var first=variant(path,"HTTP-FIRST","black");var second=variant(path,"HTTP-SECOND","white");
        String owned=path+"/variants/"+first.path("id").asText();
        Map<String,Object> old=revisions(path),body=new LinkedHashMap<>(old);
        BigInteger amount=new BigInteger("123456789012345678901234567890");body.put("sellingPrice",Map.of("amount",amount,"currency","VND"));
        var changed=call(HttpMethod.PATCH,owned,body,200);assertThat(changed.path("variant").path("sellingPrice").path("amount").bigIntegerValue()).isEqualTo(amount);
        old.put("technicalSpecifications",Map.of("schema_version",1,"values",Map.of()));call(HttpMethod.PUT,owned+"/specifications",old,409);
        var facts=Map.of("schema_version",1,"values",Map.of("physical_ram",Map.of("value",16,"unit","GB")));
        body=revisions(path);body.put("technicalSpecifications",facts);call(HttpMethod.PUT,owned+"/specifications",body,200);
        assertThat(call(HttpMethod.GET,owned+"/specifications",null,200).path("technicalSpecifications").path("values").path("physical_ram").path("value").asInt()).isEqualTo(16);
        assertThat(call(HttpMethod.GET,path+"/variants/"+second.path("id").asText()+"/specifications",null,200).path("technicalSpecifications").path("values").size()).isZero();
        body=revisions(path);body.put("technicalSpecifications",Map.of("schema_version",1,"values",Map.of()));call(HttpMethod.PUT,owned+"/specifications",body,200);
        call(HttpMethod.POST,owned+"/disable",revisions(path),200);assertThat(call(HttpMethod.GET,path+"/variants",null,200).path("items").size()).isEqualTo(2);
        call(HttpMethod.POST,owned+"/enable",revisions(path),200);
        String foreign=product(create());call(HttpMethod.PUT,foreign+"/variants/"+first.path("id").asText()+"/specifications",Map.of("expectedVersion","0","expectedTaxonomyVersion",revisions(foreign).get("expectedTaxonomyVersion"),"technicalSpecifications",facts),404);
    }
    @Test void galleryAndLegacyAliasShouldShareReplayRevisionAndRetainObjectsOnDetach() throws Exception {
        String path=product(create());UUID source=completed();var attached=attach(path,source);String id=attached.path("image").path("id").asText();
        var body=revisions(path);body.put("imageId",source);body.put("altText","HTTP image");
        var replay=call(HttpMethod.POST,"/api/products/"+path.substring(path.lastIndexOf('/')+1)+"/images",body,200);assertThat(replay.path("replay").asBoolean()).isTrue();
        var primary=revisions(path);primary.put("imageId",id);call(HttpMethod.PUT,path+"/images/primary",primary,200);
        var order=revisions(path);order.put("imageIds",List.of(id));call(HttpMethod.PUT,path+"/images/order",order,200);
        var token=revisions(path);call(HttpMethod.DELETE,path+"/images/"+id+"?expectedVersion="+token.get("expectedVersion")+"&expectedTaxonomyVersion="+token.get("expectedTaxonomyVersion"),null,204);
        assertThat(jdbc.queryForObject("SELECT active FROM product_images WHERE id=?",Boolean.class,UUID.fromString(id))).isFalse();
        assertThat(s3.listObjectsV2(r->r.bucket(bucket).prefix("images/")).contents()).isNotEmpty();
        call(HttpMethod.POST,path+"/images",body,409);
        var reactivated=attach(path,source);assertThat(reactivated.path("image").path("id").asText()).isEqualTo(id);
        assertThat(call(HttpMethod.GET,path+"/images",null,200).path("items").size()).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product_images",Integer.class)).isOne();
    }
    @Test void taxonomyShouldPreserveReferencesOptionalBrandsAndNormalizedNames() throws Exception {
        String path=product(create());
        var created=call(HttpMethod.POST,BASE+"/categories",Map.of("expectedTaxonomyVersion",revisions(path).get("expectedTaxonomyVersion"),"name","Accessories"),201);
        String categoryPath=BASE+"/categories/"+created.path("category").path("id").asText();
        call(HttpMethod.POST,BASE+"/categories",Map.of("expectedTaxonomyVersion",created.path("taxonomyVersion").asText(),"name"," accessories "),409);
        var updated=call(HttpMethod.PATCH,categoryPath,Map.of("expectedVersion",created.path("category").path("version").asText(),"expectedTaxonomyVersion",created.path("taxonomyVersion").asText(),"parentCategoryId",category),200);
        assertThat(updated.path("category").path("parentCategoryId").asText()).isEqualTo(category.toString());
        call(HttpMethod.GET,categoryPath,null,200);
        call(HttpMethod.GET,BASE+"/categories?parentCategoryId="+category,null,200);
        call(HttpMethod.DELETE,categoryPath+"?expectedVersion="+updated.path("category").path("version").asText()+"&expectedTaxonomyVersion="+updated.path("taxonomyVersion").asText(),null,204);
        var brand=call(HttpMethod.POST,BASE+"/brands",Map.of("code","vendor","name","Vendor"),201);
        String brandPath=BASE+"/brands/"+brand.path("id").asText();
        call(HttpMethod.POST,BASE+"/brands",Map.of("code","other","name","  VENDOR  "),409);
        call(HttpMethod.PATCH,brandPath,Map.of("expectedVersion","0","code","forbidden","name","Changed"),400);
        brand=call(HttpMethod.PATCH,brandPath,Map.of("expectedVersion","0","name","Renamed Vendor"),200);
        assertThat(brand.path("code").asText()).isEqualTo("vendor");
        call(HttpMethod.GET,brandPath,null,200);call(HttpMethod.GET,BASE+"/brands",null,200);
        var assignment=revisions(path);assignment.put("brandId",brand.path("id").asText());assignment.put("expectedBrandVersion",brand.path("version").asText());
        call(HttpMethod.PATCH,path,assignment,200);call(HttpMethod.POST,path+"/archive",revisions(path),200);
        call(HttpMethod.DELETE,brandPath+"?expectedVersion="+brand.path("version").asText(),null,409);
        call(HttpMethod.DELETE,BASE+"/categories/"+category+"?expectedVersion=0&expectedTaxonomyVersion="+revisions(path).get("expectedTaxonomyVersion"),null,409);
        call(HttpMethod.POST,path+"/restore",revisions(path),200);
        var clear=revisions(path);clear.put("brandId",null);assertThat(call(HttpMethod.PATCH,path,clear,200).path("brand").isNull()).isTrue();
        call(HttpMethod.DELETE,brandPath+"?expectedVersion="+brand.path("version").asText(),null,204);
    }

    @Test void earlyErrorsShouldTraverseRealBearerAndTransactionBoundaries() throws Exception {
        String path=product(create());
        mvc.perform(request(HttpMethod.GET,BASE+"/products")).andExpect(status().isUnauthorized());
        Actor customer=actor(false);
        mvc.perform(request(HttpMethod.GET,BASE+"/products").header("Authorization",customer.token())).andExpect(status().isForbidden());
        mvc.perform(request(HttpMethod.GET,BASE+"/products").header("Authorization","Bearer invalid")).andExpect(status().isUnauthorized());
        call(HttpMethod.GET,BASE+"/products/"+UUID.randomUUID(),null,404);
        call(HttpMethod.PATCH,path,Map.of("expectedVersion","0","expectedTaxonomyVersion",revisions(path).get("expectedTaxonomyVersion")),400);
        var stale=revisions(path);stale.put("expectedVersion","999");stale.put("name","Stale");
        assertThat(call(HttpMethod.PATCH,path,stale,409).path("code").asText()).isEqualTo("CATALOG_STALE_VERSION");
        var wrongTaxonomy=revisions(path);wrongTaxonomy.put("expectedTaxonomyVersion","999");wrongTaxonomy.put("name","Wrong taxonomy");call(HttpMethod.PATCH,path,wrongTaxonomy,409);
        UUID brand=UUID.randomUUID();jdbc.update("INSERT INTO brands(id,code,name) VALUES (?,'observed-brand','Observed Brand')",brand);
        var staleBrand=revisions(path);staleBrand.put("brandId",brand);staleBrand.put("expectedBrandVersion","1");call(HttpMethod.PATCH,path,staleBrand,409);
        assertThat(call(HttpMethod.GET,path,null,200).path("version").asText()).isEqualTo("0");
        mvc.perform(request(HttpMethod.PATCH,path).header("Authorization",admin.token()).contentType("text/plain").content("{}"))
                .andExpect(status().isUnsupportedMediaType());
        var v=variant(path,"DUPLICATE","black");String second=product(create());
        var duplicate=revisions(second);duplicate.put("sku","duplicate");duplicate.put("sellingPrice",Map.of("amount",1,"currency","VND"));
        assertThat(call(HttpMethod.POST,second+"/variants",duplicate,409).path("code").asText()).isEqualTo("CATALOG_SKU_CONFLICT");
        var invalidPrice=revisions(path);invalidPrice.put("sellingPrice",Map.of("amount",1.25,"currency","VND"));
        call(HttpMethod.PATCH,path+"/variants/"+v.path("id").asText(),invalidPrice,422);
        var current=revisions(path);jdbc.update("UPDATE products SET version=? WHERE id=?",Long.MAX_VALUE,UUID.fromString(path.substring(path.lastIndexOf('/')+1)));
        current.put("expectedVersion",Long.toString(Long.MAX_VALUE));current.put("name","Overflow must not wrap");call(HttpMethod.PATCH,path,current,503);
        assertThat(call(HttpMethod.GET,path,null,200).path("version").asText()).isEqualTo(Long.toString(Long.MAX_VALUE));
    }
    @Test void unknownDeferredConstraintShouldReturnSafe500AndRollBackActualHttpMutation() throws Exception {
        String path=product(create());UUID id=UUID.fromString(path.substring(path.lastIndexOf('/')+1));
        jdbc.execute("CREATE TABLE admin013_late_fk(product_id UUID REFERENCES products(id) DEFERRABLE INITIALLY DEFERRED)");
        jdbc.execute("CREATE FUNCTION admin013_late_fk() RETURNS TRIGGER LANGUAGE plpgsql AS $$ BEGIN IF NEW.name='Synthetic late failure' THEN INSERT INTO admin013_late_fk VALUES ('00000000-0000-0000-0000-000000000000'); END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER admin013_late_fk AFTER UPDATE ON products FOR EACH ROW EXECUTE FUNCTION admin013_late_fk()");
        try{
            var body=revisions(path);body.put("name","Synthetic late failure");
            var failure=call(HttpMethod.PATCH,path,body,500);
            assertThat(failure.toString()).doesNotContain("admin013_late_fk","23503","INSERT INTO","Synthetic late failure");
            assertThat(jdbc.queryForObject("SELECT version FROM products WHERE id=?",Long.class,id)).isZero();
            assertThat(jdbc.queryForObject("SELECT name FROM products WHERE id=?",String.class,id)).isEqualTo("HTTP product");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin013_late_fk",Integer.class)).isZero();
        }finally{jdbc.execute("DROP TRIGGER admin013_late_fk ON products");jdbc.execute("DROP FUNCTION admin013_late_fk()");jdbc.execute("DROP TABLE admin013_late_fk");}
    }

    @Test void lateUniqueAndGalleryFailuresShouldNotReturnSuccessOrConsumeRevision() throws Exception {
        String path=product(create());UUID id=UUID.fromString(path.substring(path.lastIndexOf('/')+1));
        jdbc.execute("CREATE TABLE admin013_late_unique(value TEXT UNIQUE DEFERRABLE INITIALLY DEFERRED)");
        jdbc.update("INSERT INTO admin013_late_unique VALUES ('taken')");
        jdbc.execute("CREATE FUNCTION admin013_late_unique() RETURNS TRIGGER LANGUAGE plpgsql AS $$ BEGIN IF NEW.name='Late unique' THEN INSERT INTO admin013_late_unique VALUES ('taken'); END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER admin013_late_unique AFTER UPDATE ON products FOR EACH ROW EXECUTE FUNCTION admin013_late_unique()");
        try{
            var body=revisions(path);body.put("name","Late unique");var failure=call(HttpMethod.PATCH,path,body,500);
            assertThat(failure.toString()).doesNotContain("admin013_late_unique","23505","duplicate key");
            assertThat(jdbc.queryForObject("SELECT version FROM products WHERE id=?",Long.class,id)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin013_late_unique",Integer.class)).isOne();
        }finally{jdbc.execute("DROP TRIGGER admin013_late_unique ON products");jdbc.execute("DROP FUNCTION admin013_late_unique()");jdbc.execute("DROP TABLE admin013_late_unique");}
        UUID source=completed();int before=s3.listObjectsV2(r->r.bucket(bucket).prefix("images/")).contents().size();
        jdbc.execute("CREATE FUNCTION admin013_http_gallery_failure() RETURNS TRIGGER LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'Synthetic gallery failure' USING ERRCODE='23514'; END $$");
        jdbc.execute("CREATE CONSTRAINT TRIGGER admin013_http_gallery_failure AFTER INSERT ON product_images DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION admin013_http_gallery_failure()");
        try{
            var body=revisions(path);body.put("imageId",source);body.put("altText","Ready");call(HttpMethod.POST,path+"/images",body,500);
            assertThat(jdbc.queryForObject("SELECT version FROM products WHERE id=?",Long.class,id)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product_images",Integer.class)).isZero();
            assertThat(s3.listObjectsV2(r->r.bucket(bucket).prefix("images/")).contents().size()).isGreaterThan(before);
            assertThat(media.validateImageForAttachment(admin.id(),source).getId()).isEqualTo(source);
        }finally{jdbc.execute("DROP TRIGGER admin013_http_gallery_failure ON product_images");jdbc.execute("DROP FUNCTION admin013_http_gallery_failure()");}
    }

    static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> putSchedules(){
        return java.util.stream.Stream.of("price","disable").flatMap(kind->java.util.stream.Stream.of(
                org.junit.jupiter.params.provider.Arguments.of(kind,false),org.junit.jupiter.params.provider.Arguments.of(kind,true)));
    }
    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.MethodSource("putSchedules")
    void dedicatedPutShouldShareRevisionWithConcurrentHttpPriceOrDisable(String kind,boolean reverse) throws Exception {
        String path=product(create());var target=variant(path,"PUT-RACE","black");String owned=path+"/variants/"+target.path("id").asText();
        UUID id=UUID.fromString(path.substring(path.lastIndexOf('/')+1));var tokens=revisions(path);long before=Long.parseLong(tokens.get("expectedVersion").toString());
        var specification=new LinkedHashMap<String,Object>(tokens);specification.put("technicalSpecifications",Map.of("schema_version",1,"values",Map.of("physical_ram",Map.of("value",16,"unit","GB"))));
        var competing=new LinkedHashMap<String,Object>(tokens);if(kind.equals("price"))competing.put("sellingPrice",Map.of("amount",2000,"currency","VND"));
        var claimed=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);var first=new java.util.concurrent.atomic.AtomicBoolean(true);
        org.mockito.Mockito.doAnswer(call->{call.callRealMethod();if(first.compareAndSet(true,false)){claimed.countDown();assertThat(release.await(15,java.util.concurrent.TimeUnit.SECONDS)).isTrue();}return null;})
                .when(revisionBarrier).claim(org.mockito.ArgumentMatchers.eq(com.example.demo.catalog.repository.CatalogMutationRepository.Resource.PRODUCT),org.mockito.ArgumentMatchers.eq(id),org.mockito.ArgumentMatchers.anyLong());
        try(var executor=java.util.concurrent.Executors.newFixedThreadPool(2)){
            java.util.concurrent.Callable<JsonNode> winner=reverse?()->call(kind.equals("price")?HttpMethod.PATCH:HttpMethod.POST,owned+(kind.equals("price")?"":"/disable"),competing,200):()->call(HttpMethod.PUT,owned+"/specifications",specification,200);
            java.util.concurrent.Callable<JsonNode> loser=reverse?()->call(HttpMethod.PUT,owned+"/specifications",specification,409):()->call(kind.equals("price")?HttpMethod.PATCH:HttpMethod.POST,owned+(kind.equals("price")?"":"/disable"),competing,409);
            var a=executor.submit(winner);assertThat(claimed.await(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();var b=executor.submit(loser);
            try{
                long until=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(8);boolean waiting=false;
                while(System.nanoTime()<until){if(jdbc.queryForObject("SELECT COUNT(*) FROM pg_stat_activity WHERE datname=CURRENT_DATABASE() AND pid<>pg_backend_pid() AND wait_event_type='Lock'",Integer.class)>0){waiting=true;break;}Thread.sleep(10);}
                assertThat(waiting).isTrue();
            }finally{release.countDown();}
            a.get(15,java.util.concurrent.TimeUnit.SECONDS);b.get(15,java.util.concurrent.TimeUnit.SECONDS);
        }finally{release.countDown();org.mockito.Mockito.reset(revisionBarrier);}
        var facts=call(HttpMethod.GET,owned+"/specifications",null,200);
        assertThat(Long.parseLong(facts.path("productVersion").asText())).isEqualTo(before+1);
        assertThat(facts.path("technicalSpecifications").path("values").has("physical_ram")).isEqualTo(!reverse);
        assertThat(jdbc.queryForObject("SELECT selling_price FROM product_variants WHERE id=?",java.math.BigDecimal.class,UUID.fromString(target.path("id").asText()))).isEqualByComparingTo(kind.equals("price")&&reverse?"2000":"1000");
        assertThat(jdbc.queryForObject("SELECT enabled FROM product_variants WHERE id=?",Boolean.class,UUID.fromString(target.path("id").asText()))).isEqualTo(!(kind.equals("disable")&&reverse));
    }
    @Test void dedicatedPutShouldRejectBothStaleTokensAndRollBackFactsOnLateUniqueFailure() throws Exception {
        String path=product(create());var target=variant(path,"PUT-LATE","black");String owned=path+"/variants/"+target.path("id").asText();
        var before=call(HttpMethod.GET,owned+"/specifications",null,200);var tokens=revisions(path);
        var stale=new LinkedHashMap<String,Object>(tokens);stale.put("expectedVersion","0");stale.put("technicalSpecifications",Map.of("schema_version",1,"values",Map.of()));call(HttpMethod.PUT,owned+"/specifications",stale,409);
        stale.put("expectedVersion",tokens.get("expectedVersion"));stale.put("expectedTaxonomyVersion","999");call(HttpMethod.PUT,owned+"/specifications",stale,409);
        jdbc.execute("CREATE TABLE admin013_put_late(value TEXT UNIQUE DEFERRABLE INITIALLY DEFERRED)");jdbc.update("INSERT INTO admin013_put_late VALUES ('taken')");
        jdbc.execute("CREATE FUNCTION admin013_put_late() RETURNS TRIGGER LANGUAGE plpgsql AS $$ BEGIN IF NEW.technical_specifications->'values'->>'custom_text'='late' THEN INSERT INTO admin013_put_late VALUES ('taken'); END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER admin013_put_late AFTER UPDATE ON product_variants FOR EACH ROW EXECUTE FUNCTION admin013_put_late()");
        try{
            var body=new LinkedHashMap<String,Object>(tokens);body.put("technicalSpecifications",Map.of("schema_version",1,"values",Map.of("custom_text","late")));
            call(HttpMethod.PUT,owned+"/specifications",body,500);
            assertThat(call(HttpMethod.GET,owned+"/specifications",null,200)).isEqualTo(before);
            assertThat(jdbc.queryForObject("SELECT selling_price FROM product_variants WHERE id=?",java.math.BigDecimal.class,UUID.fromString(target.path("id").asText()))).isEqualByComparingTo("1000");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin013_put_late",Integer.class)).isOne();
        }finally{jdbc.execute("DROP TRIGGER admin013_put_late ON product_variants");jdbc.execute("DROP FUNCTION admin013_put_late()");jdbc.execute("DROP TABLE admin013_put_late");}
        var reloaded=revisions(path);reloaded.put("technicalSpecifications",Map.of("schema_version",1,"values",Map.of("physical_ram",Map.of("value",16,"unit","GB"))));
        call(HttpMethod.PUT,owned+"/specifications",reloaded,200);
    }

    @Test void allThirtyOperationsAndLegacyAliasShouldEnforceRealCurrentJwtAcrossInvalidIdentities() throws Exception {
        String path=product(create());var variant=variant(path,"SECURITY","black");var image=attach(path,completed()).path("image");
        var brand=call(HttpMethod.POST,BASE+"/brands",Map.of("code","security","name","Security brand"),201);
        String owned=path+"/variants/"+variant.path("id").asText();
        List<Map.Entry<HttpMethod,String>> routes=List.of(
            Map.entry(HttpMethod.GET,BASE+"/products"),Map.entry(HttpMethod.GET,path),Map.entry(HttpMethod.POST,BASE+"/products"),Map.entry(HttpMethod.PATCH,path),
            Map.entry(HttpMethod.POST,path+"/publish"),Map.entry(HttpMethod.POST,path+"/unpublish"),Map.entry(HttpMethod.POST,path+"/archive"),Map.entry(HttpMethod.POST,path+"/restore"),
            Map.entry(HttpMethod.GET,BASE+"/categories"),Map.entry(HttpMethod.GET,BASE+"/categories/"+category),Map.entry(HttpMethod.POST,BASE+"/categories"),Map.entry(HttpMethod.PATCH,BASE+"/categories/"+category),Map.entry(HttpMethod.DELETE,BASE+"/categories/"+category),
            Map.entry(HttpMethod.GET,BASE+"/brands"),Map.entry(HttpMethod.GET,BASE+"/brands/"+brand.path("id").asText()),Map.entry(HttpMethod.POST,BASE+"/brands"),Map.entry(HttpMethod.PATCH,BASE+"/brands/"+brand.path("id").asText()),Map.entry(HttpMethod.DELETE,BASE+"/brands/"+brand.path("id").asText()),
            Map.entry(HttpMethod.GET,path+"/variants"),Map.entry(HttpMethod.POST,path+"/variants"),Map.entry(HttpMethod.PATCH,owned),Map.entry(HttpMethod.POST,owned+"/enable"),Map.entry(HttpMethod.POST,owned+"/disable"),
            Map.entry(HttpMethod.GET,owned+"/specifications"),Map.entry(HttpMethod.PUT,owned+"/specifications"),Map.entry(HttpMethod.GET,path+"/images"),Map.entry(HttpMethod.POST,path+"/images"),Map.entry(HttpMethod.PUT,path+"/images/primary"),Map.entry(HttpMethod.PUT,path+"/images/order"),Map.entry(HttpMethod.DELETE,path+"/images/"+image.path("id").asText()),
            Map.entry(HttpMethod.POST,"/api/products/"+path.substring(path.lastIndexOf('/')+1)+"/images"));
        assertThat(routes).hasSize(31);String before=catalogFingerprint();Actor customer=actor(false);
        for(var route:routes){
            var request=request(route.getKey(),route.getValue()).header("Authorization",admin.token()).contentType("application/json");
            if(route.getKey()!=HttpMethod.GET)request.content("{}");
            var response=mvc.perform(request).andReturn().getResponse();
            assertThat(response.getStatus()).as(route.toString()).isNotIn(401,403,500);
            assertThat(response.getHeader("Cache-Control")).contains("no-store");
        }
        deniedRoutes(routes,null,401);deniedRoutes(routes,customer.token(),403);deniedRoutes(routes,"Bearer invalid",401);
        var valid=decoder.decode(admin.token().substring("Bearer ".length()));
        var claims=org.springframework.security.oauth2.jwt.JwtClaimsSet.builder().claims(values->values.putAll(valid.getClaims()))
                .issuedAt(Instant.now().minusSeconds(600)).expiresAt(Instant.now().minusSeconds(300)).build();
        String expired="Bearer "+encoder.encode(org.springframework.security.oauth2.jwt.JwtEncoderParameters.from(
                org.springframework.security.oauth2.jwt.JwsHeader.with(org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256).build(),claims)).getTokenValue();
        deniedRoutes(routes,expired,401);
        mvc.perform(request(HttpMethod.POST,"/api/auth/logout").header("Authorization",admin.token()).contentType("application/json").content(json.writeValueAsString(Map.of("refreshToken",admin.refresh())))).andExpect(status().isNoContent());
        deniedRoutes(routes,admin.token(),401);
        Actor inactive=actor(true);jdbc.update("UPDATE users SET account_status='INACTIVE' WHERE id=?",inactive.id());deniedRoutes(routes,inactive.token(),401);
        assertThat(catalogFingerprint()).isEqualTo(before);
    }
    private void deniedRoutes(List<Map.Entry<HttpMethod,String>> routes,String token,int expected) throws Exception {
        for(var route:routes){
            var builder=request(route.getKey(),route.getValue()).contentType("application/json");if(token!=null)builder.header("Authorization",token);
            if(route.getKey()!=HttpMethod.GET)builder.content("{}");
            var response=mvc.perform(builder).andExpect(status().is(expected)).andReturn().getResponse();
            assertThat(response.getHeader("Set-Cookie")).isNull();
            assertThat(response.getContentAsString()).doesNotContain("stagingKey","objectKey","password",admin.token());
        }
    }
    private String catalogFingerprint(){
        StringBuilder value=new StringBuilder();
        for(String table:List.of("products","product_variants","product_images","categories","brands","catalog_taxonomy_state"))
            value.append(jdbc.queryForObject("SELECT MD5(STRING_AGG(row_to_json(t)::text,',' ORDER BY row_to_json(t)::text)) FROM "+table+" t",String.class));
        return value.toString();
    }
    @Test void strictRequestsAndForeignGalleryReferencesShouldFailWithoutPartialMutation() throws Exception {
        String path=product(create());var attached=attach(path,completed());String other=product(create());
        call(HttpMethod.GET,BASE+"/products?unknown=1",null,400);call(HttpMethod.GET,BASE+"/products?size=101",null,400);
        call(HttpMethod.POST,BASE+"/products",Map.of("name","Bad state","categoryId",category,"status","PUBLISHED"),400);
        mvc.perform(request(HttpMethod.POST,BASE+"/products").header("Authorization",admin.token()).contentType("application/json")
                .content("{\"name\":\"a\",\"name\":\"b\",\"categoryId\":\""+category+"\"}" )).andExpect(status().isBadRequest());
        var token=revisions(other);String image=attached.path("image").path("id").asText();
        call(HttpMethod.DELETE,other+"/images/"+image+"?expectedVersion="+token.get("expectedVersion")+"&expectedTaxonomyVersion="+token.get("expectedTaxonomyVersion"),null,404);
        assertThat(call(HttpMethod.GET,other,null,200).path("version").asText()).isEqualTo("0");
        assertThat(call(HttpMethod.GET,path+"/images",null,200).path("items").size()).isOne();
    }

}
