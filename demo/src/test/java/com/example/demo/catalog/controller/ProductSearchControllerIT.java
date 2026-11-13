package com.example.demo.catalog.controller;

import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.filter;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.example.demo.catalog.CatalogFixtures;
import com.example.demo.catalog.entity.Brand;
import com.example.demo.catalog.entity.Category;
import com.example.demo.catalog.entity.Product;
import com.example.demo.catalog.entity.ProductStatus;
import com.example.demo.catalog.storage.ProductImageStorage;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ProductSearchControllerIT.Storage.class)
class ProductSearchControllerIT {
    @Autowired MockMvc mvc;
    @Autowired EntityManager em;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;
    private final JsonMapper json=JsonMapper.builder().build();
    private UUID laptop;
    record Fixture(UUID product,UUID variant){}
    @BeforeEach void setup(){clean();laptop=tx.execute(status->{var c=CatalogFixtures.category();c.setName("Laptop");em.persist(c);em.flush();return c.getId();});}
    @AfterEach void clean(){jdbc.execute("TRUNCATE users,categories,brands CASCADE");}
    Fixture product(String name,String sku,int price,int ram){return tx.execute(status->{var f=CatalogFixtures.persistProduct(em,ProductStatus.PUBLISHED);f.product().setName(name);f.product().setCategory(em.find(Category.class,laptop));f.variant().setSku(sku);f.variant().setSellingPrice(BigDecimal.valueOf(price));f.variant().setTechnicalSpecifications(Map.of("schema_version",1,"values",Map.of("physical_ram",Map.of("value",ram,"unit","GB"))));em.persist(CatalogFixtures.image(f.product(),0,true));em.flush();return new Fixture(f.product().getId(),f.variant().getId());});}
    JsonNode request(Map<String,String> parameters)throws Exception{
        var req=get("/api/products");parameters.forEach(req::param);
        return json.readTree(mvc.perform(req).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    List<String> ids(JsonNode page){var result=new ArrayList<String>();page.get("items").forEach(x->result.add(x.get("id").asString()));return result;}
    @Test void search_shouldExposeSevenTierOrderingAndAllSortModes()throws Exception{
        var sku=product("Other","sony alpha",10,8);var name=product("Sony Alpha","n",20,8);var phrase=product("The Sony Alpha device","p",30,8);var token=product("Alpha quality Sony","t",40,8);var prefix=product("Sonya Alphabeta","pre",50,8);var partial=product("Xsony Xalpha","part",60,8);var fuzzy=product("Sonny Alpha","f",70,8);
        var expected=List.of(sku,name,phrase,token,prefix,partial,fuzzy).stream().map(x->x.product().toString()).toList();
        assertThat(ids(request(Map.of("q","sony alpha")))).containsExactlyElementsOf(expected);
        mvc.perform(get("/api/products").param("q","sony alpha")).andExpect(status().isOk()).andExpect(header().string("X-Catalog-Search-Mode","fuzzy-enabled"));
        var all=new ArrayList<String>();
        for(int page=0;page<4;page++){var response=request(Map.of("q","sony alpha","page",Integer.toString(page),"size","2"));assertThat(response.get("totalItems").asLong()).isEqualTo(7);all.addAll(ids(response));}
        assertThat(all).containsExactlyElementsOf(expected);
        assertThat(ids(request(Map.of("q","sony alpha","sort","price-desc")))).containsExactlyElementsOf(expected.reversed());
        for(String sort:List.of("price-asc","name-asc","name-desc","relevance")){assertThat(request(Map.of("q","sony alpha","sort",sort)).get("totalItems").asLong()).isEqualTo(7);}
        var fixtures=List.of(sku,name,phrase,token,prefix,partial,fuzzy);
        var names=List.of("other","sony alpha","the sony alpha device","alpha quality sony","sonya alphabeta","xsony xalpha","sonny alpha");
        var positions=new ArrayList<Integer>();for(int i=0;i<7;i++)positions.add(i);
        positions.sort(Comparator.comparing(names::get));
        var nameOrder=positions.stream().map(index->fixtures.get(index).product().toString()).toList();
        for(String sort:List.of("relevance","price-asc","price-desc","name-asc","name-desc")){
            var complete=new ArrayList<String>();
            for(int page=0;page<4;page++)complete.addAll(ids(request(Map.of("q","sony alpha","sort",sort,"size","2","page",Integer.toString(page)))));
            var oracle=switch(sort){case "price-desc"->expected.reversed();case "name-asc"->nameOrder;case "name-desc"->nameOrder.reversed();default->expected;};
            assertThat(complete).as(sort).containsExactlyElementsOf(oracle);
        }
        var last=request(Map.of("q","sony alpha","page","99"));assertThat(ids(last)).isEmpty();assertThat(last.get("totalItems").asLong()).isEqualTo(7);
    }
    @Test void configurations_shouldExcludeRank7Variant_beforeRankingAndDisplay()throws Exception{
        var p=product("Alpha quality Sony","sony alpha",10,8);
        UUID large=tx.execute(status->{var v=CatalogFixtures.variant(em.find(Product.class,p.product()),Map.of("choice","large"),BigDecimal.valueOf(30));v.setSku("large");v.setTechnicalSpecifications(Map.of("schema_version",1,"values",Map.of("physical_ram",Map.of("value",16,"unit","GB"))));em.persist(v);em.flush();return v.getId();});
        var competitor=product("The Sony Alpha device","comp",25,16);
        assertThat(ids(request(Map.of("q","sony alpha")))).containsExactly(p.product().toString(),competitor.product().toString());
        String filter="[{\"key\":\"physical_ram\",\"alternatives\":[{\"op\":\"gte\",\"value\":16,\"unit\":\"GB\"}]}]";
        for(var extra:List.of(Map.of("minPrice","20"),Map.of("categoryId",laptop.toString(),"specFilters",filter),Map.of("categoryId",laptop.toString(),"specFilters",filter,"minPrice","20"))){
            var q=new HashMap<>(extra);q.put("q","sony alpha");var result=request(q);
            assertThat(ids(result)).containsExactly(competitor.product().toString(),p.product().toString());
            assertThat(result.get("items").get(1).get("representativeVariantId").asString()).isEqualTo(large.toString());
            assertThat(result.get("items").get(1).get("displayPrice").get("amount").intValue()).isEqualTo(30);
            assertThat(result.toString()).doesNotContain("objectKey","class_sum","similarity_sum");
            // Combined and filter-only pages use independent fixture ID/price/name oracles.
            for(boolean withKeyword:List.of(false,true))for(String sort:List.of("price-asc","price-desc","name-asc","name-desc","relevance")){
                if(!withKeyword&&sort.equals("relevance"))continue;
                var params=new HashMap<>(extra);if(withKeyword)params.put("q","sony alpha");params.put("size","1");params.put("sort",sort);
                var all=new ArrayList<String>();
                for(int page=0;page<2;page++){params.put("page",Integer.toString(page));var one=request(params);assertThat(one.get("totalItems").asLong()).isEqualTo(2);all.addAll(ids(one));}
                var oracle=sort.equals("price-desc")||sort.equals("name-asc")?List.of(p.product().toString(),competitor.product().toString()):List.of(competitor.product().toString(),p.product().toString());
                assertThat(all).as(sort+" keyword="+withKeyword).containsExactlyElementsOf(oracle);
                params.put("page","2");assertThat(ids(request(params))).isEmpty();
            }

        }
    }
    @Test void validation_shouldReturn400ForInvalidJsonUnitsAndUnsupportedKeys_but200ForNoMatch()throws Exception{
        product("Điện thoại","device",10,8);
        for(String raw:List.of("[]","null","[{\"key\":\"physical_ram\",\"key\":\"physical_ram\",\"alternatives\":[]}]","[{\"key\":\"physical_ram\",\"alternatives\":[{\"op\":\"eq\",\"value\":16,\"value\":16,\"unit\":\"GB\"}]}]","[{\"key\":\"battery_capacity_mah\",\"alternatives\":[{\"op\":\"eq\",\"value\":1,\"unit\":\"mAh\"}]}]","[{\"key\":\"physical_ram\",\"alternatives\":[{\"op\":\"eq\",\"value\":16,\"unit\":\"MB\"}]}]")){
            mvc.perform(get("/api/products").param("categoryId",laptop.toString()).param("specFilters",raw)).andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith("application/problem+json")).andExpect(jsonPath("$.status").value(400));
        }
        String valid="[{\"key\":\"physical_ram\",\"alternatives\":[{\"op\":\"eq\",\"value\":99,\"unit\":\"GB\"}]}]";
        assertThat(request(Map.of("categoryId",laptop.toString(),"specFilters",valid)).get("totalItems").asLong()).isZero();
        assertThat(request(Map.of("q","DIEN THOAI")).get("totalItems").asLong()).isOne();
    }
    @Test void remainingGoldenCases_shouldProtectIdentifiersAndKeepRankSeparateFromDisplay()throws Exception{
        var approximate=product("Samsung ID-X","first",10,8);
        var exact=product("Unrelated","second",20,8);
        tx.executeWithoutResult(st->em.find(Product.class,exact.product()).setDescription("ID-X and samsnug"));
        assertThat(ids(request(Map.of("q","ID-X samsnug")))).containsExactly(exact.product().toString(),approximate.product().toString()); // RK05
        var identifier=product("Sony WH-1000XM5","anchor",30,8);
        assertThat(ids(request(Map.of("q","WH-1000XM5")))).containsExactly(identifier.product().toString());
        assertThat(ids(request(Map.of("q","WH-1000XM6")))).isEmpty(); // RK06
        var a=product("Repeated Sony Sony","a",40,8);var b=product("Sony","b",50,8);
        var repeated=ids(request(Map.of("q","sony sony")));assertThat(repeated.getFirst()).isEqualTo(a.product().toString());assertThat(repeated).containsExactlyInAnyOrder(a.product().toString(),b.product().toString(),identifier.product().toString()); // RK09 phrase vs dedup token
        var tieA=product("Stable identical","tie-a",60,8);var tieB=product("Stable identical","tie-b",60,8);
        assertThat(ids(request(Map.of("q","stable identical")))).containsExactlyElementsOf(List.of(tieA.product().toString(),tieB.product().toString()).stream().sorted().toList()); // RK08
        var ranked=product("Alpha quality Sony","cheap",70,8);
        tx.executeWithoutResult(st->{var v=CatalogFixtures.variant(em.find(Product.class,ranked.product()),Map.of("choice","rank7"),BigDecimal.valueOf(100));v.setSku("sony alpha");em.persist(v);});
        var response=request(Map.of("q","sony alpha"));
        assertThat(ids(response)).containsExactly(ranked.product().toString());
        assertThat(response.get("items").get(0).get("representativeVariantId").asString()).isEqualTo(ranked.variant().toString()); // RK10
    }

    @Test void phraseInDescription_shouldOutrankScatteredBrandAndSku_butNotWholeName()throws Exception{
        var whole=product("Sony Alpha","whole",10,8);
        var phrase=product("Other","description",20,8);
        var scattered=product("Different","alpha",30,8);
        tx.executeWithoutResult(st->{em.find(Product.class,phrase.product()).setDescription("This Sony Alpha device");var brand=new Brand("sony","Sony");em.persist(brand);em.find(Product.class,scattered.product()).setBrand(brand);});
        assertThat(ids(request(Map.of("q","sony alpha")))).containsExactly(whole.product().toString(),phrase.product().toString(),scattered.product().toString()); // RK02,RK03
    }

    @Test void categoryAndBrands_shouldApplySubtreeAndOrGroups_withAndKeyword()throws Exception{
        var a=product("Laptop Alpha","a",10,8);var b=product("Laptop Beta","b",20,16);var hidden=product("Laptop Secret","hidden",30,8);
        var brandIds=tx.execute(st->{
            var first=new Brand("alpha-brand","First");var second=new Brand("beta-brand","Second");em.persist(first);em.persist(second);
            em.find(Product.class,a.product()).setBrand(first);em.find(Product.class,b.product()).setBrand(second);
            em.find(Product.class,hidden.product()).setStatus(ProductStatus.DRAFT);
            var parent=em.find(Category.class,laptop);
            for(int depth=0;depth<3;depth++){var next=CatalogFixtures.category(parent);next.setName("Level "+depth);em.persist(next);parent=next;}
            em.find(Product.class,b.product()).setCategory(parent);return List.of(first.getId(),second.getId());
        });
        var response=mvc.perform(get("/api/products").param("categoryId",laptop.toString()).param("brandId",brandIds.get(0).toString(),brandIds.get(1).toString(),UUID.randomUUID().toString())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(ids(json.readTree(response))).containsExactlyInAnyOrder(a.product().toString(),b.product().toString());
        assertThat(ids(request(Map.of("q","beta","categoryId",laptop.toString(),"brandId",brandIds.get(1).toString())))).containsExactly(b.product().toString());
        assertThat(request(Map.of("categoryId",UUID.randomUUID().toString())).get("totalItems").asLong()).isZero();
    }

    @TestConfiguration(proxyBeanMethods=false) static class Storage{
        @Bean @Primary ProductImageStorage localSigner(){return()->new ProductImageStorage.PreparedReadSigner(){public String signReadUrl(String key){return "https://images.test/signed?X-Amz-Expires=300";}public void close(){}};}
    }
}
