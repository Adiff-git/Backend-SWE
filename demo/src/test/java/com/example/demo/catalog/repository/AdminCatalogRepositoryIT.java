package com.example.demo.catalog.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.demo.catalog.entity.Product;
import com.example.demo.catalog.exception.AdminCatalogException;
import com.example.demo.catalog.repository.CatalogMutationRepository.Resource;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** Real commit boundaries: no test transaction can conceal deferred failures. */
@DataJpaTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(CatalogMutationRepository.class)
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class AdminCatalogRepositoryIT {
    @Autowired EntityManager entities;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate transactions;
    @Autowired CatalogMutationRepository mutations;
    private UUID category;
    private UUID product;

    @BeforeEach void seedCommittedDraft(){
        jdbc.execute("TRUNCATE users,categories,brands RESTART IDENTITY CASCADE");
        category=UUID.randomUUID();product=UUID.randomUUID();
        transactions.executeWithoutResult(status->{
            jdbc.update("INSERT INTO categories(id,name) VALUES (?,'Foundation category')",category);
            jdbc.update("INSERT INTO products(id,category_id,name,status) VALUES (?,?,'Foundation Draft','DRAFT')",product,category);
        });
    }
    @Test void nativeClaim_shouldRefreshManagedRevisionAndCommitExactlyOnce(){
        long taxonomy=jdbc.queryForObject("SELECT version FROM catalog_taxonomy_state WHERE id=1",Long.class);
        transactions.executeWithoutResult(status->{
            Product existing=entities.find(Product.class,product);assertThat(existing.getVersion()).isZero();
            assertThat(mutations.lockTaxonomy(false)).isEqualTo(taxonomy);
            assertThat(mutations.lockVersions(Resource.PRODUCT,List.of(product),true).get(product)).isZero();
            mutations.claim(Resource.PRODUCT,product,0);
            Product fresh=mutations.freshProduct(product);assertThat(fresh.getVersion()).isOne();
            fresh.setName("Committed change");mutations.flush();
        });
        assertThat(version()).isOne();assertThat(jdbc.queryForObject("SELECT name FROM products WHERE id=?",String.class,product)).isEqualTo("Committed change");
        assertThatThrownBy(()->transactions.executeWithoutResult(status->mutations.claim(Resource.PRODUCT,product,0)))
                .isInstanceOf(AdminCatalogException.class);
        assertThat(version()).isOne();
    }
    @Test void failureAfterClaim_shouldRollBackRevisionAndManagedChanges(){
        assertThatThrownBy(()->transactions.executeWithoutResult(status->{
            mutations.lockTaxonomy(false);mutations.lockVersions(Resource.PRODUCT,List.of(product),true);
            mutations.claim(Resource.PRODUCT,product,0);mutations.freshProduct(product).setName("Must roll back");
            mutations.flush();throw new IllegalStateException("Synthetic late failure");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(version()).isZero();assertThat(jdbc.queryForObject("SELECT name FROM products WHERE id=?",String.class,product)).isEqualTo("Foundation Draft");
    }
    @Test void deferredPublicationFailure_shouldRejectActualCommitAndRestoreRevision(){
        assertThatThrownBy(()->transactions.executeWithoutResult(status->{
            mutations.lockTaxonomy(false);mutations.lockVersions(Resource.PRODUCT,List.of(product),true);
            mutations.claim(Resource.PRODUCT,product,0);
            jdbc.update("UPDATE products SET status='PUBLISHED' WHERE id=?",product);
            assertThat(jdbc.queryForObject("SELECT status FROM products WHERE id=?",String.class,product)).isEqualTo("PUBLISHED");
        })).isInstanceOf(RuntimeException.class);
        assertThat(version()).isZero();assertThat(jdbc.queryForObject("SELECT status FROM products WHERE id=?",String.class,product)).isEqualTo("DRAFT");
    }
    @Test void missingSingleton_shouldFailClosedWithoutResettingOrManufacturingRevision(){
        assertThatThrownBy(()->transactions.executeWithoutResult(status->{
            jdbc.update("DELETE FROM catalog_taxonomy_state WHERE id=1");mutations.lockTaxonomy(false);
        })).isInstanceOf(AdminCatalogException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM catalog_taxonomy_state WHERE id=1",Integer.class)).isOne();
    }
    @Test void taxonomyClaimAndSqlNormalization_shouldCommitAndEnforceSiblingUniqueness(){
        long old=jdbc.queryForObject("SELECT version FROM catalog_taxonomy_state WHERE id=1",Long.class);
        transactions.executeWithoutResult(status->{
            assertThat(mutations.lockTaxonomy(true)).isEqualTo(old);mutations.claimTaxonomy(old);
            assertThat(jdbc.queryForObject("SELECT catalog_name_key(?)",String.class,"\u2000 MiXeD\u00a0")).isEqualTo("mixed");
        });
        assertThat(jdbc.queryForObject("SELECT version FROM catalog_taxonomy_state WHERE id=1",Long.class)).isEqualTo(old+1);
        assertThatThrownBy(()->transactions.executeWithoutResult(status->jdbc.update("INSERT INTO categories(id,name) VALUES (?,'  FOUNDATION CATEGORY  ')",UUID.randomUUID())))
                .isInstanceOf(RuntimeException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM categories",Integer.class)).isOne();
    }
    @Test void childFailureAfterClaim_shouldRollBackChildInventoryAndParentRevision(){
        UUID variant=UUID.randomUUID();
        assertThatThrownBy(()->transactions.executeWithoutResult(status->{
            mutations.lockTaxonomy(false);mutations.lockVersions(Resource.PRODUCT,List.of(product),true);
            mutations.claim(Resource.PRODUCT,product,0);
            jdbc.update("INSERT INTO product_variants(id,product_id,sku,option_combination,selling_price) VALUES (?,?,'ROLLBACK-CHILD','{}',1000)",variant,product);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_balances WHERE original_variant_id=?",Integer.class,variant)).isOne();
            throw new IllegalStateException("Synthetic child commit failure");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(version()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product_variants WHERE id=?",Integer.class,variant)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_balances WHERE original_variant_id=?",Integer.class,variant)).isZero();
    }
    @Test void competingConditionalClaims_shouldHaveOneCommittedWinner() throws Exception {
        var claimed=new java.util.concurrent.CountDownLatch(1);
        var attempted=new java.util.concurrent.CountDownLatch(1);
        try(var executor=java.util.concurrent.Executors.newFixedThreadPool(2)){
            var first=executor.submit(()->transactions.execute(status->{
                mutations.lockTaxonomy(false);mutations.lockVersions(Resource.PRODUCT,List.of(product),true);
                mutations.claim(Resource.PRODUCT,product,0);claimed.countDown();
                try{assertThat(attempted.await(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();}
                catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IllegalStateException(interrupted);}
                return "committed";
            }));
            assertThat(claimed.await(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            var second=executor.submit(()->{
                attempted.countDown();
                try{
                    transactions.executeWithoutResult(status->{
                        mutations.lockTaxonomy(false);mutations.lockVersions(Resource.PRODUCT,List.of(product),true);
                        mutations.claim(Resource.PRODUCT,product,0);
                    });return "unexpected commit";
                }catch(AdminCatalogException stale){return stale.getCode();}
            });
            assertThat(first.get(15,java.util.concurrent.TimeUnit.SECONDS)).isEqualTo("committed");
            assertThat(second.get(15,java.util.concurrent.TimeUnit.SECONDS)).isEqualTo("CATALOG_STALE_VERSION");
        }
        assertThat(version()).isOne();
    }
    private long version(){return jdbc.queryForObject("SELECT version FROM products WHERE id=?",Long.class,product);}
}
