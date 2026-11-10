package com.example.demo.catalog.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.filter;
import static org.assertj.core.api.Assertions.from;
import com.example.demo.catalog.CatalogFixtures;
import com.example.demo.catalog.entity.Brand;
import com.example.demo.catalog.entity.Category;
import com.example.demo.catalog.entity.Product;
import com.example.demo.catalog.entity.ProductStatus;
import com.example.demo.catalog.entity.ProductVariant;
import com.example.demo.catalog.search.ProductCandidatePage;
import com.example.demo.catalog.search.ProductSearchCriteria;
import com.example.demo.catalog.search.ProductSearchNormalizer;
import com.example.demo.catalog.search.ProductSearchParameterParser;
import com.example.demo.catalog.dto.request.ProductSearchRequest;
import com.example.demo.catalog.exception.InvalidCatalogRequestException;
import jakarta.persistence.EntityManager;
import jakarta.validation.Validation;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class ProductSearchRepositoryIT {
    @Autowired ProductRepository products;
    @Autowired EntityManager em;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;
    private UUID laptop,child,tablet;
    private static final jakarta.validation.ValidatorFactory VALIDATION=Validation.buildDefaultValidatorFactory();
    @AfterAll static void closeValidation(){ VALIDATION.close(); }
    @BeforeEach void setup(){
        jdbc.execute("TRUNCATE product_image_uploads,product_images,catalog_source_links,wishlist_items,product_variants,products,brands,categories CASCADE");
        tx.executeWithoutResult(s->{var root=CatalogFixtures.category();root.setName("Laptop");em.persist(root);laptop=root.getId();
            var sub=CatalogFixtures.category(root);sub.setName("Gaming");em.persist(sub);child=sub.getId();
            var tab=CatalogFixtures.category();tab.setName("Tablet");em.persist(tab);tablet=tab.getId();});
    }
    @AfterEach void cleanup(){jdbc.execute("TRUNCATE product_image_uploads,product_images,catalog_source_links,wishlist_items,product_variants,products,brands,categories CASCADE");}
    record Owned(UUID product,UUID variant){}
    Owned seed(String name,String brand,String sku,int price,int ram,UUID cat){
        return tx.execute(s->{var f=CatalogFixtures.persistProduct(em,ProductStatus.PUBLISHED);f.product().setName(name);
            f.product().setCategory(em.find(Category.class,cat));f.variant().setSku(sku);f.variant().setSellingPrice(BigDecimal.valueOf(price));
            f.variant().setTechnicalSpecifications(Map.of("schema_version",1,"values",Map.of("physical_ram",Map.of("value",ram,"unit","GB"),"display_diagonal",Map.of("value",15.6,"unit","inch"),"display_resolution","1920x1080")));
            if(brand!=null){var b=new Brand(brand.toLowerCase()+"-"+UUID.randomUUID(),brand);em.persist(b);f.product().setBrand(b);}
            em.flush();return new Owned(f.product().getId(),f.variant().getId());});
    }
    ProductSearchCriteria criteria(Map<String,List<String>> query){var r=new ProductSearchParameterParser(VALIDATION.getValidator()).parse(query);
        return ProductSearchCriteria.builder().keyword(new ProductSearchNormalizer().normalizeQuery(r.getQ())).page(r.getPage()).size(r.getSize()).categoryId(r.getCategoryId()==null?null:UUID.fromString(r.getCategoryId())).brandIds(r.getBrandIds()==null?null:r.getBrandIds().stream().map(UUID::fromString).toList()).minPrice(r.getMinPrice()).maxPrice(r.getMaxPrice()).sort(r.getSort()).specificationGroups(r.getSpecificationGroups()).build();}
    ProductCandidatePage search(Map<String,List<String>> q){return products.findPublishedCandidates(criteria(q));}
    List<UUID> ids(ProductCandidatePage p){return p.getItems().stream().map(ProductCandidatePage.Candidate::getProductId).toList();}
    @Test void keywords_shouldNormalizeAndRequireEveryToken_acrossProductAndBrand(){
        var yes=seed("Điện thoại Alpha","Samsung","alpha-1",10,8,laptop);seed("Điện thoại Beta","Sony","beta-1",20,8,laptop);
        assertThat(ids(search(Map.of("q",List.of("DIEN THOAI samsung"))))).containsExactly(yes.product());
        assertThat(search(Map.of("q",List.of("dien missing"))).getTotalElements()).isZero();
    }
    @Test void ranking_shouldRespectSkuNamePhraseTokenPrefixPartialFuzzy(){
        var sku=seed("Other","Brand","sony alpha",10,8,laptop);
        var name=seed("Sony Alpha",null,"name-1",20,8,laptop);
        var phrase=seed("The Sony Alpha device",null,"phrase-1",30,8,laptop);
        var token=seed("Alpha quality Sony",null,"token-1",40,8,laptop);
        var prefix=seed("Sonya Alphabeta",null,"prefix-1",50,8,laptop);
        var partial=seed("Xsony Xalpha",null,"partial-1",60,8,laptop);
        var fuzzy=seed("Sonny Alpha",null,"fuzzy-1",70,8,laptop);
        assertThat(ids(search(Map.of("q",List.of("sony alpha"))))).containsExactly(sku.product(),name.product(),phrase.product(),token.product(),prefix.product(),partial.product(),fuzzy.product());
    }
    @Test void identifiers_shouldNeverFuzzyMatchFamiliesOrModels_butBrandTyposWork(){
        var samsung=seed("Galaxy WH-1000XM5","Samsung","technical-1",10,8,laptop);
        assertThat(ids(search(Map.of("q",List.of("samsnug"))))).containsExactly(samsung.product());
        assertThat(search(Map.of("q",List.of("galaxi"))).getTotalElements()).isZero();
        assertThat(search(Map.of("q",List.of("WH-1000XM6"))).getTotalElements()).isZero();
    }
    @Test void categories_shouldIncludeDescendants_andBrandsUseOr(){
        var a=seed("Alpha","Samsung","a",10,8,child);var b=seed("Beta","Sony","b",20,8,laptop);seed("Gamma",null,"c",30,8,tablet);
        var brands=jdbc.queryForList("SELECT id FROM brands",UUID.class).stream().map(UUID::toString).toList();
        assertThat(ids(search(Map.of("categoryId",List.of(laptop.toString()),"brandId",brands)))).containsExactlyInAnyOrder(a.product(),b.product());
        assertThat(search(Map.of("categoryId",List.of(UUID.randomUUID().toString()))).getTotalElements()).isZero();
    }
    @Test void configurations_shouldRequireSameVariant_andSelectMatchingPair(){
        var p=seed("Phone","Samsung","cheap",100,8,laptop);
        UUID expensive=tx.execute(s->{var v=CatalogFixtures.variant(em.find(Product.class,p.product()),Map.of("choice","large"),BigDecimal.valueOf(200));v.setSku("expensive");v.setTechnicalSpecifications(Map.of("schema_version",1,"values",Map.of("physical_ram",Map.of("value",16,"unit","GB"))));em.persist(v);em.flush();return v.getId();});
        String filters="[{\"key\":\"physical_ram\",\"alternatives\":[{\"op\":\"gte\",\"value\":16,\"unit\":\"GB\"}]}]";
        var q=new HashMap<String,List<String>>();q.put("categoryId",List.of(laptop.toString()));q.put("specFilters",List.of(filters));q.put("maxPrice",List.of("150"));
        assertThat(search(q).getTotalElements()).isZero();q.put("maxPrice",List.of("200"));
        var result=search(q);assertThat(result.getItems()).hasSize(1);assertThat(result.getItems().getFirst().getRepresentativeVariantId()).isEqualTo(expensive);
        q.put("q",List.of("cheap"));assertThat(search(q).getTotalElements()).isZero();
    }
    @Test void technicalKeywords_shouldUseApprovedFacts_andTypedEquality(){
        var p=seed("Device",null,"device",10,16,laptop);
        assertThat(ids(search(Map.of("q",List.of("16.0 GB"))))).containsExactly(p.product());
        assertThat(search(Map.of("q",List.of("16GB"))).getTotalElements()).isZero();
        assertThat(search(Map.of("q",List.of("physical_ram"))).getTotalElements()).isZero();
    }
    @Test void invalidFilters_shouldDifferFromValidNoMatches_andUnknownCategory(){
        seed("Device",null,"a",10,8,laptop);
        String valid="[{\"key\":\"physical_ram\",\"alternatives\":[{\"op\":\"eq\",\"value\":999,\"unit\":\"GB\"}]}]";
        assertThat(search(Map.of("categoryId",List.of(laptop.toString()),"specFilters",List.of(valid))).getTotalElements()).isZero();
        assertThatThrownBy(()->search(Map.of("categoryId",List.of(tablet.toString()),"specFilters",List.of(valid)))).isInstanceOf(InvalidCatalogRequestException.class);
        assertThat(search(Map.of("categoryId",List.of(UUID.randomUUID().toString()),"specFilters",List.of(valid))).getTotalElements()).isZero();
    }
    @Test void pagination_shouldKeepTotalsAndMatchingPriceOrder(){
        seed("Điện B",null,"b",20,8,laptop);seed("Dien A",null,"a",10,8,laptop);seed("Other",null,"c",30,8,laptop);
        var first=search(Map.of("sort",List.of("price-desc"),"size",List.of("1")));assertThat(first.getItems().getFirst().getDisplayAmount()).isEqualTo(BigInteger.valueOf(30));assertThat(first.getTotalElements()).isEqualTo(3);
        var last=search(Map.of("sort",List.of("name-asc"),"page",List.of("99")));assertThat(last.getItems()).isEmpty();assertThat(last.getTotalElements()).isEqualTo(3);
    }
    @Test void ranking_shouldUseOnlyEligibleSkuTuple_andKeepRepresentativeIndependent(){
        var p=seed("Alpha quality Sony",null,"sony alpha",10,8,laptop);
        UUID eligible=tx.execute(status->{var v=CatalogFixtures.variant(em.find(Product.class,p.product()),Map.of("choice","other"),BigDecimal.valueOf(30));v.setSku("other-sku");v.setTechnicalSpecifications(Map.of("schema_version",1,"values",Map.of("physical_ram",Map.of("value",16,"unit","GB"))));em.persist(v);em.flush();return v.getId();});
        var competitor=seed("The Sony Alpha device",null,"competitor",25,16,laptop);
        assertThat(ids(search(Map.of("q",List.of("sony alpha"))))).containsExactly(p.product(),competitor.product());
        String filters="[{\"key\":\"physical_ram\",\"alternatives\":[{\"op\":\"eq\",\"value\":16,\"unit\":\"GB\"}]}]";
        for(var extra:List.of(Map.of("minPrice",List.of("20")),Map.of("categoryId",List.of(laptop.toString()),"specFilters",List.of(filters)),Map.of("categoryId",List.of(laptop.toString()),"specFilters",List.of(filters),"minPrice",List.of("20")))){
            var q=new HashMap<String,List<String>>(extra);q.put("q",List.of("sony alpha"));var page=search(q);
            assertThat(ids(page)).containsExactly(competitor.product(),p.product());
            assertThat(page.getItems().get(1).getRepresentativeVariantId()).isEqualTo(eligible);
            assertThat(page.getItems().get(1).getDisplayAmount()).isEqualTo(BigInteger.valueOf(30));
        }
        // RK10:q-only,both variants eligible;highest rank may come from costlier variant.
        tx.executeWithoutResult(status->em.find(ProductVariant.class,p.variant()).setSellingPrice(BigDecimal.valueOf(200)));
        var qOnly=search(Map.of("q",List.of("sony alpha")));
        assertThat(ids(qOnly)).containsExactly(p.product(),competitor.product());
        assertThat(qOnly.getItems().getFirst().getRepresentativeVariantId()).isEqualTo(eligible);
    }
    @Test void specifications_shouldGuardMalformedValuesAndKnownQualityContradictions(){
        var a=seed("Laptop 64GB Storage",null,"a",10,8,laptop);
        tx.executeWithoutResult(status->em.find(ProductVariant.class,a.variant()).setTechnicalSpecifications(Map.of("schema_version",1,"values",Map.of("installed_storage_gb",Map.of("value",65,"unit","GB"),"physical_ram","16","display_resolution","1600x600","cpu_model","secret-exynos"))));
        assertThat(search(Map.of("q",List.of("65 gb"))).getTotalElements()).isZero();
        assertThat(search(Map.of("q",List.of("secret-exynos"))).getTotalElements()).isZero();
        String filters="[{\"key\":\"physical_ram\",\"alternatives\":[{\"op\":\"eq\",\"value\":16,\"unit\":\"GB\"}]}]";
        assertThat(search(Map.of("categoryId",List.of(laptop.toString()),"specFilters",List.of(filters))).getTotalElements()).isZero();
        tx.executeWithoutResult(status->{em.find(Product.class,a.product()).setName("Laptop 16:10");em.find(ProductVariant.class,a.variant()).setTechnicalSpecifications(Map.of("schema_version",1,"values",Map.of("display_resolution","1600x600","display_diagonal",Map.of("value",6088,"unit","inch"))));});
        assertThat(search(Map.of("q",List.of("1600x600"))).getTotalElements()).isZero();
        assertThat(search(Map.of("q",List.of("6088 inch"))).getTotalElements()).isZero();
    }
    @Test void quantityOperators_shouldBeInclusiveAndExact_withoutUnitConversion(){
        seed("Laptop",null,"a",10,16,laptop);
        for(var entry:Map.of("eq",16,"gt",15,"gte",16,"lt",17,"lte",16).entrySet()){
            String json="[{\"key\":\"physical_ram\",\"alternatives\":[{\"op\":\""+entry.getKey()+"\",\"value\":"+entry.getValue()+",\"unit\":\"GB\"}]}]";
            assertThat(search(Map.of("categoryId",List.of(laptop.toString()),"specFilters",List.of(json))).getTotalElements()).isOne();
        }
        String range="[{\"key\":\"physical_ram\",\"alternatives\":[{\"op\":\"between\",\"min\":16,\"max\":16,\"unit\":\"GB\"}]}]";
        assertThat(search(Map.of("categoryId",List.of(laptop.toString()),"specFilters",List.of(range))).getTotalElements()).isOne();
        assertThatThrownBy(()->search(Map.of("categoryId",List.of(laptop.toString()),"specFilters",List.of(range.replace("GB","MB"))))).isInstanceOf(InvalidCatalogRequestException.class);
    }
    @Test void textOperatorsAndAlternatives_shouldUseLiteralSemanticsAndOrWithinKey(){
        seed("Laptop",null,"a",10,16,laptop);
        for(var pair:List.of(List.of("eq","1920x1080"),List.of("prefix","1920"),List.of("contains","x108"))){
            String json="[{\"key\":\"display_resolution\",\"alternatives\":[{\"op\":\""+pair.get(0)+"\",\"value\":\""+pair.get(1)+"\"}]}]";
            assertThat(search(Map.of("categoryId",List.of(laptop.toString()),"specFilters",List.of(json))).getTotalElements()).isOne();
        }
        String json="[{\"key\":\"physical_ram\",\"alternatives\":[{\"op\":\"eq\",\"value\":8,\"unit\":\"GB\"},{\"op\":\"eq\",\"value\":16,\"unit\":\"GB\"}]}]";
        assertThat(search(Map.of("categoryId",List.of(laptop.toString()),"specFilters",List.of(json))).getTotalElements()).isOne();
    }
    @Test void repeatedQuery_shouldDedupMembership_butNotInventRepeatedPhrase(){
        var exact=seed("Sony Sony",null,"a",10,8,laptop);var one=seed("Sony",null,"b",10,8,laptop);
        assertThat(ids(search(Map.of("q",List.of("sony sony"))))).containsExactly(exact.product(),one.product());
    }
    @Test void ambiguousTaxonomy_shouldDisableOnlyThatBinding_notPlainSearch(){
        seed("Laptop Device",null,"a",10,16,laptop);
        tx.executeWithoutResult(status->{var cat=CatalogFixtures.category();cat.setName("Laptop");em.persist(cat);});
        assertThat(search(Map.of("q",List.of("device"))).getTotalElements()).isOne();
        String json="[{\"key\":\"physical_ram\",\"alternatives\":[{\"op\":\"eq\",\"value\":16,\"unit\":\"GB\"}]}]";
        assertThatThrownBy(()->search(Map.of("categoryId",List.of(laptop.toString()),"specFilters",List.of(json)))).isInstanceOf(InvalidCatalogRequestException.class);
        assertThat(search(Map.of("q",List.of("16 gb"))).getTotalElements()).isZero();
    }

    @Test void ranking_shouldPreferAllExactOverSkuPlusFuzzy_andBreakFullTiesByUuid(){
        var fuzzy=seed("Camera Samsnug",null,"ID-X",10,8,laptop);
        var exact=seed("Samsung Camera",null,"other",20,8,laptop);
        tx.executeWithoutResult(status->em.find(Product.class,exact.product()).setDescription("ID-X"));
        assertThat(ids(search(Map.of("q",List.of("ID-X samsung"))))).containsExactly(exact.product(),fuzzy.product());
        var tie1=seed("tie phone",null,"t1",30,8,laptop);var tie2=seed("tie phone",null,"t2",40,8,laptop);
        var expected=List.of(tie1.product(),tie2.product()).stream().sorted(Comparator.comparing(UUID::toString)).toList();
        assertThat(ids(search(Map.of("q",List.of("tie phone"))))).containsExactlyElementsOf(expected);
    }

    @Test void multipleSpecificationGroups_shouldNotCombineDifferentVariants_orKeywordSources(){
        var p=seed("Laptop",null,"cheap",10,8,laptop);
        tx.executeWithoutResult(st->{
            em.find(ProductVariant.class,p.variant()).setTechnicalSpecifications(Map.of("schema_version",1,"values",Map.of("physical_ram",Map.of("value",8,"unit","GB"),"installed_storage_gb",Map.of("value",256,"unit","GB"))));
            var other=CatalogFixtures.variant(em.find(Product.class,p.product()),Map.of("choice","large"),BigDecimal.valueOf(30));other.setSku("expensive");other.setTechnicalSpecifications(Map.of("schema_version",1,"values",Map.of("physical_ram",Map.of("value",16,"unit","GB"),"installed_storage_gb",Map.of("value",128,"unit","GB"))));em.persist(other);
        });
        String groups="[{\"key\":\"physical_ram\",\"alternatives\":[{\"op\":\"eq\",\"value\":16,\"unit\":\"GB\"}]},{\"key\":\"installed_storage_gb\",\"alternatives\":[{\"op\":\"eq\",\"value\":256,\"unit\":\"GB\"}]}]";
        assertThat(search(Map.of("categoryId",List.of(laptop.toString()),"specFilters",List.of(groups))).getTotalElements()).isZero();
        assertThat(search(Map.of("q",List.of("cheap 16 gb"))).getTotalElements()).isZero();
        assertThat(search(Map.of("q",List.of("16 256"))).getTotalElements()).isZero();
    }
    @Test void categoryCycles_shouldTerminateAndDedupeProducts(){
        var p=seed("Laptop Device",null,"one",10,8,child);
        jdbc.update("UPDATE categories SET parent_category_id=? WHERE id=?",child,laptop);
        assertThat(ids(search(Map.of("categoryId",List.of(laptop.toString()))))).containsExactly(p.product());
    }

    @Test void fuzzy_shouldRejectMultiEditNaturalNameCollisions_butKeepApprovedTypos(){
        seed("Samson device",null,"a",10,8,laptop);seed("Sonic device",null,"b",10,8,laptop);seed("Lenox device",null,"c",10,8,laptop);
        for(String query:List.of("samsung","sony","lenovo"))assertThat(search(Map.of("q",List.of(query))).getTotalElements()).isZero();
        var samsung=seed("Samsung device",null,"s",10,8,laptop);
        assertThat(ids(search(Map.of("q",List.of("samsnug"))))).containsExactly(samsung.product());
    }
    @Test void malformedStoredText_shouldNotBreakBrowseOrOtherSearchFields(){
        var p=seed("Valid Device",null,"owned-sku",10,8,laptop);
        tx.executeWithoutResult(st->em.find(Product.class,p.product()).setDescription("invalid"+(char)151+"source description"));
        assertThat(ids(search(Map.of()))).containsExactly(p.product());
        assertThat(ids(search(Map.of("q",List.of("valid"))))).containsExactly(p.product());
        assertThat(search(Map.of("q",List.of("missing"))).getTotalElements()).isZero();
        assertThat(jdbc.queryForObject("SELECT description FROM products WHERE id=?",String.class,p.product())).contains(String.valueOf((char)151));
    }
    @Test void approvedBindings_shouldSupportTabletDiagonalAndLaptopStorage_withoutEnablingTabletRam(){
        var tab=seed("Tablet Device",null,"tablet-good",10,8,tablet);
        var laptopProduct=seed("Laptop Device",null,"laptop-good",20,16,laptop);
        tx.executeWithoutResult(st->em.find(ProductVariant.class,laptopProduct.variant()).setTechnicalSpecifications(Map.of("schema_version",1,"values",Map.of("installed_storage_gb",Map.of("value",256,"unit","GB")))));
        String diagonal="[{\"key\":\"display_diagonal\",\"alternatives\":[{\"op\":\"eq\",\"value\":15.6,\"unit\":\"inch\"}]}]";
        assertThat(ids(search(Map.of("categoryId",List.of(tablet.toString()),"specFilters",List.of(diagonal))))).containsExactly(tab.product());
        String storage="[{\"key\":\"installed_storage_gb\",\"alternatives\":[{\"op\":\"gte\",\"value\":256,\"unit\":\"GB\"}]}]";
        assertThat(ids(search(Map.of("categoryId",List.of(laptop.toString()),"specFilters",List.of(storage))))).containsExactly(laptopProduct.product());
        assertThat(ids(search(Map.of("q",List.of("256 GB"))))).containsExactly(laptopProduct.product());
    }
    @Test void zeroMissingAndBooleanFacts_shouldRemainDistinct_withoutImplicitCoercion(){
        var zero=seed("Zero Device",null,"zero",10,0,laptop);
        var malformed=seed("Wrong Device",null,"wrong",20,8,laptop);
        tx.executeWithoutResult(st->em.find(ProductVariant.class,malformed.variant()).setTechnicalSpecifications(Map.of("schema_version",1,"values",Map.of("physical_ram",false))));
        String filter="[{\"key\":\"physical_ram\",\"alternatives\":[{\"op\":\"eq\",\"value\":0,\"unit\":\"GB\"}]}]";
        assertThat(ids(search(Map.of("categoryId",List.of(laptop.toString()),"specFilters",List.of(filter))))).containsExactly(zero.product());
        assertThat(ids(search(Map.of("q",List.of("0 GB"))))).containsExactly(zero.product());
        assertThat(search(Map.of("q",List.of("false"))).getTotalElements()).isZero();
    }
    @Test void invalidLegacyDocuments_shouldNotCastFail_withoutWeakeningPersistedConstraints(){
        seed("Data Device",null,"safe",10,16,laptop);
        String filter="[{\"key\":\"physical_ram\",\"alternatives\":[{\"op\":\"eq\",\"value\":16,\"unit\":\"GB\"}]}]";
        for(String document:List.of("{\"schema_version\":2,\"values\":{}}","{\"schema_version\":1,\"values\":[]}","{\"schema_version\":1,\"values\":{\"physical_ram\":{\"value\":\"16\",\"unit\":\"GB\"}}}","{\"schema_version\":1,\"values\":{\"physical_ram\":{\"value\":16,\"unit\":\"MB\"}}}","{\"schema_version\":1,\"values\":{\"physical_ram\":{\"value\":16,\"unit\":\"GB\",\"extra\":true}}}")){
            tx.executeWithoutResult(st->{
                // Connection-local read view supplies legacy shapes;public rows/constraints are untouched.
                // Documents above are fixed test literals,never request-derived SQL.
                em.createNativeQuery("CREATE TEMP VIEW product_variants AS SELECT id,product_id,sku,selling_price,'"+document+"'::jsonb AS technical_specifications FROM public.product_variants").executeUpdate();
                try{
                    assertThat(search(Map.of("categoryId",List.of(laptop.toString()),"specFilters",List.of(filter))).getTotalElements()).isZero();
                    assertThat(search(Map.of("q",List.of("16 GB"))).getTotalElements()).isZero();
                }finally{em.createNativeQuery("DROP VIEW pg_temp.product_variants").executeUpdate();}
            });
        }
        assertThat(search(Map.of("categoryId",List.of(laptop.toString()),"specFilters",List.of(filter))).getTotalElements()).isOne();
    }
    @Test void registryInheritance_shouldStopAtAnotherApprovedFamilyRoot(){
        seed("Tablet Device",null,"tablet-owned",10,16,tablet);
        jdbc.update("UPDATE categories SET parent_category_id=? WHERE id=?",laptop,tablet);
        String ram="[{\"key\":\"physical_ram\",\"alternatives\":[{\"op\":\"eq\",\"value\":16,\"unit\":\"GB\"}]}]";
        assertThatThrownBy(()->search(Map.of("categoryId",List.of(tablet.toString()),"specFilters",List.of(ram)))).isInstanceOf(InvalidCatalogRequestException.class);
        assertThat(search(Map.of("categoryId",List.of(laptop.toString()),"specFilters",List.of(ram))).getTotalElements()).isZero();
        assertThat(search(Map.of("q",List.of("16 GB"))).getTotalElements()).isZero();
        String diagonal="[{\"key\":\"display_diagonal\",\"alternatives\":[{\"op\":\"eq\",\"value\":15.6,\"unit\":\"inch\"}]}]";
        assertThat(search(Map.of("categoryId",List.of(tablet.toString()),"specFilters",List.of(diagonal))).getTotalElements()).isOne();
        UUID phone=tx.execute(st->{var c=CatalogFixtures.category(em.find(Category.class,laptop));c.setName("Smartphone");em.persist(c);return c.getId();});
        seed("Smartphone Device",null,"phone-owned",10,16,phone);
        assertThatThrownBy(()->search(Map.of("categoryId",List.of(phone.toString()),"specFilters",List.of(diagonal)))).isInstanceOf(InvalidCatalogRequestException.class);
        assertThat(search(Map.of("q",List.of("16 GB"))).getTotalElements()).isZero();
        assertThat(search(Map.of("categoryId",List.of(laptop.toString()),"specFilters",List.of(diagonal))).getTotalElements()).isOne();
        assertThat(search(Map.of("categoryId",List.of(laptop.toString()))).getTotalElements()).isEqualTo(2);
    }
}
