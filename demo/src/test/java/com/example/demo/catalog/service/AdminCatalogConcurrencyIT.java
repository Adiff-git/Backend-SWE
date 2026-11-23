package com.example.demo.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

import com.example.demo.catalog.dto.request.AdminProductRequests;
import com.example.demo.catalog.dto.request.AdminVariantRequests;
import com.example.demo.catalog.dto.request.CatalogField;
import com.example.demo.catalog.exception.AdminCatalogException;
import com.example.demo.catalog.repository.CatalogMutationRepository;
import com.example.demo.catalog.repository.CatalogMutationRepository.Resource;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/** Spy supplies barriers AFTER the real SQL claim; all locks/transactions/writes are PostgreSQL. */
@TestPropertySource(properties="spring.datasource.hikari.maximum-pool-size=6")
class AdminCatalogConcurrencyIT extends AdminCatalogIntegrationSupport {
    @MockitoSpyBean CatalogMutationRepository revisions;
    @Autowired AdminGalleryService gallery;
    enum Kind { SCALAR_PRICE, FACT_DISABLE, DISABLE_DISABLE, ORDER_DETACH, PRIMARY_ORDER }
    static Stream<Arguments> schedules(){return Stream.of(Kind.values()).flatMap(k->Stream.of(Arguments.of(k,false),Arguments.of(k,true)));}
    @ParameterizedTest @MethodSource("schedules")
    void aggregateRaces_shouldRejectStaleSecondWriterInBothLockDirections(Kind kind,boolean reverse) throws Exception {
        UUID product=ready();UUID first=products.get(product).getVariants().getFirst().getId();
        UUID secondVariant=variant(product,"SECOND-"+product,"white",BigDecimal.TEN);
        UUID a=products.get(product).getImages().getFirst().getId(),b=image(product,1,false);
        var current=products.get(product);long v=version(current),t=taxonomy(current);
        Supplier<?> left,right;
        switch(kind){
            case SCALAR_PRICE->{
                left=()->products.update(product,new AdminProductRequests.Patch(v,t,CatalogField.supplied("Won"),CatalogField.absent(),CatalogField.absent(),null,CatalogField.absent()));
                right=()->variants.update(product,first,new AdminVariantRequests.Patch(v,t,CatalogField.absent(),CatalogField.supplied(BigDecimal.valueOf(2000)),CatalogField.absent(),CatalogField.absent()));
            }
            case FACT_DISABLE->{
                Map<String,Object> facts=Map.of("schema_version",1,"values",Map.of("physical_ram",Map.of("value",16,"unit","GB")));
                left=()->variants.update(product,first,new AdminVariantRequests.Patch(v,t,CatalogField.absent(),CatalogField.absent(),CatalogField.absent(),CatalogField.supplied(facts)));
                right=()->variants.setEnabled(product,first,v,t,false);
            }
            case DISABLE_DISABLE->{left=()->variants.setEnabled(product,first,v,t,false);right=()->variants.setEnabled(product,secondVariant,v,t,false);}
            case ORDER_DETACH->{
                left=()->gallery.reorder(product,v,t,List.of(b,a));
                right=()->{gallery.remove(product,a,v,t,b);return "detached";};
            }
            case PRIMARY_ORDER->{left=()->gallery.primary(product,b,v,t);right=()->gallery.reorder(product,v,t,List.of(b,a));}
            default->throw new IllegalStateException();
        }
        race(product,reverse?right:left,reverse?left:right);
        assertThat(Long.parseLong(products.get(product).getVersion())).isEqualTo(v+1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product_images WHERE product_id=? AND active AND is_primary",Integer.class,product)).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product_variants WHERE product_id=? AND enabled",Integer.class,product)).isPositive();
        assertThat(jdbc.queryForObject("SELECT SUM(stock+reserved) FROM inventory_balances",Long.class)).isZero();
        switch(kind){
            case SCALAR_PRICE->{assertThat(products.get(product).getName()).isEqualTo(reverse?"Prepared product":"Won");assertThat(jdbc.queryForObject("SELECT selling_price FROM product_variants WHERE id=?",BigDecimal.class,first)).isEqualByComparingTo(reverse?"2000":"1000");}
            case FACT_DISABLE->{assertThat(jdbc.queryForObject("SELECT enabled FROM product_variants WHERE id=?",Boolean.class,first)).isEqualTo(!reverse);assertThat(variants.specifications(product,first).getTechnicalSpecifications().get("values").toString().contains("physical_ram")).isEqualTo(!reverse);}
            case DISABLE_DISABLE->assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product_variants WHERE product_id=? AND enabled",Integer.class,product)).isOne();
            case ORDER_DETACH->{assertThat(gallery.get(product).getItems()).hasSize(reverse?1:2);assertThat(jdbc.queryForObject("SELECT active FROM product_images WHERE id=?",Boolean.class,a)).isEqualTo(!reverse);}
            case PRIMARY_ORDER->assertThat(jdbc.queryForObject("SELECT id FROM product_images WHERE product_id=? AND active AND is_primary",UUID.class,product)).isEqualTo(reverse?a:b);
        }
    }
    @Test void unrelatedProduct_shouldCommitWhileAnotherProductClaimIsHeld() throws Exception {
        UUID blocked=ready(),other=ready();var a=products.get(blocked);var b=products.get(other);
        CountDownLatch claimed=new CountDownLatch(1),release=new CountDownLatch(1);instrument(blocked,claimed,release);
        try(var executor=java.util.concurrent.Executors.newFixedThreadPool(2)){
            var first=executor.submit(()->products.update(blocked,new AdminProductRequests.Patch(version(a),taxonomy(a),CatalogField.supplied("Held"),CatalogField.absent(),CatalogField.absent(),null,CatalogField.absent())));
            try{
                assertThat(claimed.await(10,TimeUnit.SECONDS)).isTrue();
                var independent=executor.submit(()->products.update(other,new AdminProductRequests.Patch(version(b),taxonomy(b),CatalogField.supplied("Independent"),CatalogField.absent(),CatalogField.absent(),null,CatalogField.absent())));
                assertThat(independent.get(10,TimeUnit.SECONDS).getName()).isEqualTo("Independent");
            }finally{release.countDown();}
            first.get(15,TimeUnit.SECONDS);
        }finally{reset(revisions);}
    }
    @Autowired AdminCategoryService categories;
    @Autowired AdminBrandService brands;
    enum TreeRace { CYCLE, ROOT_CREATE, ROOT_RENAME, SIBLING_RENAME, MOVE_COLLISION, ROOT_MOVE, DELETE_CHILD }
    static Stream<Arguments> treeSchedules(){return Stream.of(TreeRace.values()).flatMap(k->Stream.of(Arguments.of(k,false),Arguments.of(k,true)));}
    private long topology(){return jdbc.queryForObject("SELECT version FROM catalog_taxonomy_state WHERE id=1",Long.class);}
    private UUID addCategory(String name,UUID parent){return categories.create(new com.example.demo.catalog.dto.request.AdminTaxonomyRequests.CategoryCreate(topology(),name,parent)).getCategory().getId();}
    private Object moveCategory(UUID id,UUID parent,long t){return categories.update(id,new com.example.demo.catalog.dto.request.AdminTaxonomyRequests.CategoryPatch(0,t,CatalogField.absent(),CatalogField.supplied(parent)));}
    private Object renameCategory(UUID id,String name,long t){return categories.update(id,new com.example.demo.catalog.dto.request.AdminTaxonomyRequests.CategoryPatch(0,t,CatalogField.supplied(name),CatalogField.absent()));}
    @ParameterizedTest @MethodSource("treeSchedules")
    void taxonomyGateShouldSerializeCyclesAndNormalizedCollisions(TreeRace kind,boolean reverse) throws Exception {
        UUID left=addCategory("Left",null);UUID right=addCategory("Right",null);
        UUID a=addCategory(kind==TreeRace.ROOT_MOVE?"Equal":"Alpha",left);
        UUID b=addCategory(kind==TreeRace.ROOT_MOVE?" EQUAL ":"Beta",right);
        UUID sibling=addCategory("Sibling",left);
        long t=topology();Supplier<?> first,second;
        switch(kind){
            case CYCLE->{first=()->moveCategory(left,right,t);second=()->moveCategory(right,left,t);}
            case ROOT_CREATE->{first=()->categories.create(new com.example.demo.catalog.dto.request.AdminTaxonomyRequests.CategoryCreate(t,"\u2000 Collision ",null));second=()->categories.create(new com.example.demo.catalog.dto.request.AdminTaxonomyRequests.CategoryCreate(t,"COLLISION\t",null));}
            case ROOT_RENAME->{first=()->renameCategory(left,"Collision",t);second=()->renameCategory(right," collision ",t);}
            case SIBLING_RENAME->{first=()->renameCategory(a,"Collision",t);second=()->renameCategory(sibling,"COLLISION",t);}
            case MOVE_COLLISION->{first=()->renameCategory(a,"Beta",t);second=()->moveCategory(b,left,t);}
            case ROOT_MOVE->{first=()->moveCategory(a,null,t);second=()->moveCategory(b,null,t);}
            case DELETE_CHILD->{first=()->{categories.delete(a,0,t);return true;};second=()->categories.create(new com.example.demo.catalog.dto.request.AdminTaxonomyRequests.CategoryCreate(t,"New child",a));}
            default->throw new IllegalStateException();
        }
        gateRace(reverse?second:first,reverse?first:second,true,"CATALOG_STALE_VERSION");
        assertThat(topology()).isEqualTo(t+1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM categories c LEFT JOIN categories p ON p.id=c.parent_category_id WHERE c.parent_category_id IS NOT NULL AND p.id IS NULL",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM (SELECT parent_category_id,public.catalog_name_key(name) FROM categories GROUP BY parent_category_id,public.catalog_name_key(name) HAVING COUNT(*)>1) duplicates",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("WITH RECURSIVE walk AS (SELECT id,parent_category_id,ARRAY[id] path,false cycle FROM categories UNION ALL SELECT p.id,p.parent_category_id,w.path||p.id,p.id=ANY(w.path) FROM walk w JOIN categories p ON p.id=w.parent_category_id WHERE NOT w.cycle) SELECT COUNT(*) FROM walk WHERE cycle",Integer.class)).isZero();
        if(kind==TreeRace.ROOT_CREATE)org.assertj.core.api.Assertions.assertThatThrownBy(()->addCategory("collision",null)).isInstanceOf(AdminCatalogException.class);
    }
    static Stream<Arguments> referenceSchedules(){return Stream.of(false,true).flatMap(reverse->Stream.of(Arguments.of("CREATE",reverse),Arguments.of("ASSIGN",reverse),Arguments.of("PUBLISH",reverse)));}
    @ParameterizedTest @MethodSource("referenceSchedules")
    void taxonomyGateShouldSerializeCategoryChangesAndProductReferences(String kind,boolean reverse) throws Exception {
        UUID target=addCategory("Destination",null);UUID product=kind.equals("PUBLISH")?ready():draft().getId();
        if(kind.equals("PUBLISH"))transition(product,AdminProductRequests.Action.UNPUBLISH);
        var current=products.get(product);long t=topology();
        Supplier<?> categoryWrite=kind.equals("CREATE")?()->{categories.delete(target,0,t);return true;}:()->renameCategory(target,"Renamed",t);
        Supplier<?> productWrite=switch(kind){
            case "CREATE"->()->products.create(new AdminProductRequests.Create("Referenced",target,CatalogField.absent(),null,CatalogField.absent()));
            case "ASSIGN"->()->products.update(product,new AdminProductRequests.Patch(version(current),t,CatalogField.absent(),CatalogField.supplied(target),CatalogField.absent(),null,CatalogField.absent()));
            case "PUBLISH"->()->products.transition(product,version(current),t,AdminProductRequests.Action.PUBLISH);
            default->throw new IllegalStateException();
        };
        String error=kind.equals("CREATE")?(reverse?"CATALOG_DEPENDENCY_CONFLICT":"CATALOG_RESOURCE_NOT_FOUND"):(reverse?null:"CATALOG_STALE_VERSION");
        gateRace(reverse?productWrite:categoryWrite,reverse?categoryWrite:productWrite,!reverse,error);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM products p LEFT JOIN categories c ON c.id=p.category_id WHERE c.id IS NULL",Integer.class)).isZero();
        if(!kind.equals("CREATE")){
            assertThat(version(products.get(product))).isEqualTo(version(current)+(reverse?1:0));
            assertThat(topology()).isEqualTo(t+1);
        }
    }
    private void gateRace(Supplier<?> first,Supplier<?> second,boolean exclusive,String secondError) throws Exception {
        CountDownLatch claimed=new CountDownLatch(1),release=new CountDownLatch(1);AtomicBoolean once=new AtomicBoolean(true);
        doAnswer(call->{Object value=call.callRealMethod();if((boolean)call.getArgument(0)==exclusive && once.compareAndSet(true,false)){claimed.countDown();assertThat(release.await(15,TimeUnit.SECONDS)).isTrue();}return value;}).when(revisions).lockTaxonomy(org.mockito.ArgumentMatchers.anyBoolean());
        try(var executor=java.util.concurrent.Executors.newFixedThreadPool(2)){
            var winner=executor.submit(first::get);assertThat(claimed.await(10,TimeUnit.SECONDS)).isTrue();var follower=executor.submit(second::get);
            try{awaitDatabaseWaiter();}finally{release.countDown();}
            assertThat(winner.get(15,TimeUnit.SECONDS)).isNotNull();
            if(secondError==null)assertThat(follower.get(15,TimeUnit.SECONDS)).isNotNull();
            else try{follower.get(15,TimeUnit.SECONDS);throw new AssertionError("Expected conflict "+secondError);}catch(ExecutionException failure){assertThat(failure.getCause()).isInstanceOf(AdminCatalogException.class);assertThat(((AdminCatalogException)failure.getCause()).getCode()).isEqualTo(secondError);}
        }finally{release.countDown();reset(revisions);}
    }
    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans={false,true})
    void brandDeleteAndAssignmentShouldSerializeWithoutOrphans(boolean assignmentFirst) throws Exception {
        var brand=brands.create(new com.example.demo.catalog.dto.request.AdminTaxonomyRequests.BrandCreate("race-brand","Race brand"));
        var product=draft();long t=taxonomy(product);long v=version(product);
        Supplier<?> assign=()->products.update(product.getId(),new AdminProductRequests.Patch(v,t,CatalogField.absent(),CatalogField.absent(),CatalogField.supplied(brand.getId()),0L,CatalogField.absent()));
        Supplier<?> delete=()->{brands.delete(brand.getId(),0);return true;};
        CountDownLatch claimed=new CountDownLatch(1),release=new CountDownLatch(1);AtomicBoolean once=new AtomicBoolean(true);
        doAnswer(call->{Object result=call.callRealMethod();if(once.compareAndSet(true,false)){claimed.countDown();assertThat(release.await(15,TimeUnit.SECONDS)).isTrue();}return result;})
                .when(revisions).lockVersions(eq(Resource.BRAND),org.mockito.ArgumentMatchers.anyCollection(),org.mockito.ArgumentMatchers.anyBoolean());
        try(var executor=java.util.concurrent.Executors.newFixedThreadPool(2)){
            var first=executor.submit((assignmentFirst?assign:delete)::get);assertThat(claimed.await(10,TimeUnit.SECONDS)).isTrue();var second=executor.submit((assignmentFirst?delete:assign)::get);
            try{awaitDatabaseWaiter();}finally{release.countDown();}
            first.get(15,TimeUnit.SECONDS);
            try{second.get(15,TimeUnit.SECONDS);throw new AssertionError("Expected protected reference");}catch(ExecutionException failure){assertThat(failure.getCause()).isInstanceOf(AdminCatalogException.class);assertThat(((AdminCatalogException)failure.getCause()).getCode()).isEqualTo(assignmentFirst?"CATALOG_DEPENDENCY_CONFLICT":"CATALOG_RESOURCE_NOT_FOUND");}
        }finally{release.countDown();reset(revisions);}
        assertThat(version(products.get(product.getId()))).isEqualTo(v+(assignmentFirst?1:0));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM products p LEFT JOIN brands b ON b.id=p.brand_id WHERE p.brand_id IS NOT NULL AND b.id IS NULL",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM brands WHERE id=?",Integer.class,brand.getId())).isEqualTo(assignmentFirst?1:0);
    }
    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(booleans={false,true})
    void normalizedBrandUniqueIndexShouldProtectConcurrentCreates(boolean reverse) throws Exception {
        CountDownLatch inserted=new CountDownLatch(1),release=new CountDownLatch(1);
        org.springframework.transaction.support.TransactionTemplate tx=new org.springframework.transaction.support.TransactionTemplate(manager);
        String first=reverse?"\u2000 COLLISION\t":"Collision";String second=reverse?"Collision":"\u2000 COLLISION\t";
        try(var executor=java.util.concurrent.Executors.newFixedThreadPool(2)){
            var winner=executor.submit(()->tx.execute(status->{jdbc.update("INSERT INTO brands(id,code,name) VALUES (?, 'race-first',?)",UUID.randomUUID(),first);inserted.countDown();try{assertThat(release.await(15,TimeUnit.SECONDS)).isTrue();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}return true;}));
            assertThat(inserted.await(10,TimeUnit.SECONDS)).isTrue();
            var loser=executor.submit(()->brands.create(new com.example.demo.catalog.dto.request.AdminTaxonomyRequests.BrandCreate("race-second",second)));
            try{awaitDatabaseWaiter();}finally{release.countDown();}
            assertThat(winner.get(15,TimeUnit.SECONDS)).isTrue();
            org.assertj.core.api.Assertions.assertThatThrownBy(()->loser.get(15,TimeUnit.SECONDS)).hasCauseInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM brands WHERE public.catalog_name_key(name)='collision'",Integer.class)).isOne();
        }finally{release.countDown();}
    }
    @Autowired org.springframework.transaction.PlatformTransactionManager manager;
    @Test void skuShouldRemainReservedByDisabledAndArchivedVariantsAndStaleTokensMustNotWrite(){
        UUID product=ready();UUID retained=products.get(product).getVariants().getFirst().getId();
        String sku=jdbc.queryForObject("SELECT sku FROM product_variants WHERE id=?",String.class,retained);
        variant(product,"ALTERNATIVE","white",BigDecimal.TEN);var current=products.get(product);
        variants.setEnabled(product,retained,version(current),taxonomy(current),false);
        transition(product,AdminProductRequests.Action.ARCHIVE);
        var other=draft();
        org.assertj.core.api.Assertions.assertThatThrownBy(()->variant(other.getId(),sku,"black",BigDecimal.TEN)).isInstanceOf(AdminCatalogException.class);
        assertThat(version(products.get(other.getId()))).isEqualTo(version(other));
        UUID leaf=addCategory("Own revision",null);long t=topology();renameCategory(leaf,"Renamed",t);
        org.assertj.core.api.Assertions.assertThatThrownBy(()->renameCategory(leaf,"Stale",topology())).isInstanceOf(AdminCatalogException.class);
        var brand=brands.create(new com.example.demo.catalog.dto.request.AdminTaxonomyRequests.BrandCreate("stale-brand","Old"));
        brands.update(brand.getId(),new com.example.demo.catalog.dto.request.AdminTaxonomyRequests.BrandRename(0,"New"));
        org.assertj.core.api.Assertions.assertThatThrownBy(()->brands.update(brand.getId(),new com.example.demo.catalog.dto.request.AdminTaxonomyRequests.BrandRename(0,"Stale"))).isInstanceOf(AdminCatalogException.class);
        assertThat(brands.get(brand.getId()).getName()).isEqualTo("New");
    }
    private void race(UUID product,Supplier<?> first,Supplier<?> second) throws Exception {
        CountDownLatch claimed=new CountDownLatch(1),release=new CountDownLatch(1);instrument(product,claimed,release);
        try(var executor=java.util.concurrent.Executors.newFixedThreadPool(2)){
            var winner=executor.submit(first::get);
            assertThat(claimed.await(10,TimeUnit.SECONDS)).isTrue();
            var stale=executor.submit(second::get);
            try{awaitDatabaseWaiter();}finally{release.countDown();}
            assertThat(winner.get(15,TimeUnit.SECONDS)).isNotNull();
            try{stale.get(15,TimeUnit.SECONDS);throw new AssertionError("Stale writer committed");}
            catch(ExecutionException failure){assertThat(failure.getCause()).isInstanceOf(AdminCatalogException.class);assertThat(((AdminCatalogException)failure.getCause()).getCode()).isEqualTo("CATALOG_STALE_VERSION");}
        }finally{release.countDown();reset(revisions);}
    }
    private void instrument(UUID product,CountDownLatch claimed,CountDownLatch release){
        AtomicBoolean first=new AtomicBoolean(true);
        doAnswer(call->{call.callRealMethod();if(first.compareAndSet(true,false)){claimed.countDown();assertThat(release.await(15,TimeUnit.SECONDS)).isTrue();}return null;})
                .when(revisions).claim(eq(Resource.PRODUCT),eq(product),anyLong());
    }
    private void awaitDatabaseWaiter() throws Exception {
        long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);
        while(System.nanoTime()<until){
            Integer waiting=jdbc.queryForObject("SELECT COUNT(*) FROM pg_stat_activity WHERE datname=CURRENT_DATABASE() AND pid<>pg_backend_pid() AND wait_event_type='Lock'",Integer.class);
            if(waiting!=null && waiting>0)return;Thread.sleep(10);
        }
        throw new AssertionError("Second writer never reached a PostgreSQL lock wait");
    }
}
