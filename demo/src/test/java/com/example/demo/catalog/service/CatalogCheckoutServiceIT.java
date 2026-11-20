package com.example.demo.catalog.service;

import com.example.demo.config.CheckoutIntegrationSupport;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogCheckoutServiceIT extends CheckoutIntegrationSupport {
    @Autowired CatalogCheckoutService service;
    @Autowired javax.sql.DataSource dataSource;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    com.example.demo.catalog.repository.ProductVariantRepository variants;

    @Test void getCheckoutVariantStates_shouldCaptureCurrentProtectedPriceAndOptions() {
        jdbc.update("UPDATE product_variants SET selling_price=1234 WHERE id=?",fixture.variant());
        transactions.executeWithoutResult(s -> {
            com.example.demo.catalog.dto.response.CheckoutVariantState state = service.getCheckoutVariantStates(List.of(fixture.variant())).get(fixture.variant());
            assertThat(state.getPrice()).isEqualByComparingTo(BigDecimal.valueOf(1234));
            assertThat(state.getProductId()).isEqualTo(fixture.product());
            assertThat(state.getOptions()).isNotEmpty();
        });
    }

    @Test void getCheckoutVariantStates_shouldBatchDistinctSetsAcrossHundredRowBoundary() {
        java.util.List<java.util.UUID> ids = transactions.execute(s -> {
            java.util.List<java.util.UUID> result = new java.util.ArrayList<>();
            result.add(fixture.variant());
            com.example.demo.catalog.entity.Product product = entities.getReference(com.example.demo.catalog.entity.Product.class,fixture.product());
            for (int i=0;i<204;i++) {
                com.example.demo.catalog.entity.ProductVariant variant = com.example.demo.catalog.CatalogFixtures.variant(product,
                        java.util.Map.of("choice","batch-"+i),BigDecimal.ONE);
                entities.persist(variant);
                result.add(variant.getId());
            }
            return result;
        });
        for (int count : new int[]{100,101,205}) {
            transactions.executeWithoutResult(s -> {
                java.util.List<java.util.UUID> requested = new java.util.ArrayList<>(ids.subList(0,count));
                requested.addAll(ids.subList(0,count));
                assertThat(service.getCheckoutVariantStates(requested)).hasSize(count);
            });
        }
    }

    @Test void getCheckoutVariantStates_shouldHoldBothParentAndVariantAgainstCompetingWrites() {
        transactions.executeWithoutResult(s -> {
            service.getCheckoutVariantStates(List.of(fixture.variant()));
            try (java.sql.Connection competitor = dataSource.getConnection()) {
                competitor.setAutoCommit(false);
                for (String table : List.of("products","product_variants")) {
                    java.util.UUID id = table.equals("products") ? fixture.product() : fixture.variant();
                    org.assertj.core.api.Assertions.assertThatThrownBy(() -> {
                        try (java.sql.PreparedStatement statement = competitor.prepareStatement("SELECT id FROM "+table+" WHERE id=? FOR UPDATE NOWAIT")) {
                            statement.setObject(1,id);
                            statement.executeQuery();
                        }
                    }).isInstanceOfSatisfying(java.sql.SQLException.class, failure -> assertThat(failure.getSQLState()).isEqualTo("55P03"));
                    competitor.rollback();
                }
            } catch (java.sql.SQLException failure) { throw new IllegalStateException(failure); }
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    void getCheckoutVariantStates_shouldConflictWhenParentChangesAfterCandidateDiscovery() throws Exception {
        transactions.executeWithoutResult(s -> entities.persist(com.example.demo.catalog.CatalogFixtures.variant(
                entities.getReference(com.example.demo.catalog.entity.Product.class,fixture.product()),
                java.util.Map.of("choice","replacement"),BigDecimal.ONE)));
        java.util.UUID target=jdbc.queryForObject("SELECT product_id FROM cart_items WHERE id=?",java.util.UUID.class,fixture.unselected());
        java.util.concurrent.atomic.AtomicBoolean moved=new java.util.concurrent.atomic.AtomicBoolean();
        org.mockito.stubbing.Answer<?> delegate=org.mockito.Mockito.mockingDetails(variants).getMockCreationSettings().getDefaultAnswer();
        try (java.util.concurrent.ExecutorService executor=java.util.concurrent.Executors.newSingleThreadExecutor()) {
            org.mockito.Mockito.doAnswer(invocation -> {
                java.util.List<Object[]> result=(java.util.List<Object[]>)delegate.answer(invocation);
                if(moved.compareAndSet(false,true)) executor.submit(() ->
                        jdbc.update("UPDATE product_variants SET product_id=? WHERE id=?",target,fixture.variant()))
                        .get(10,java.util.concurrent.TimeUnit.SECONDS);
                return result;
            }).when(variants).findCheckoutParents(org.mockito.ArgumentMatchers.anyCollection());
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.getCheckoutVariantStates(List.of(fixture.variant())))
                    .isInstanceOf(com.example.demo.catalog.exception.CatalogConflictException.class);
        }
        assertThat(jdbc.queryForObject("SELECT product_id FROM product_variants WHERE id=?",java.util.UUID.class,fixture.variant())).isEqualTo(target);
    }

    @Test void getCheckoutVariantStates_shouldRepresentNonfinitePricesWithoutHidingDatabaseFailures() {
        for(String value : List.of("NaN","Infinity","-Infinity","-1","0.5")) {
            transactions.executeWithoutResult(s -> {
                // Transactional corruption fixture; rollback restores both constraint and original value.
                jdbc.execute("ALTER TABLE product_variants DROP CONSTRAINT ck_product_variants_price_whole_finite");
                jdbc.update("UPDATE product_variants SET selling_price=CAST(? AS NUMERIC) WHERE id=?",value,fixture.variant());
                com.example.demo.catalog.dto.response.CheckoutVariantState state=service.getCheckoutVariantStates(List.of(fixture.variant())).get(fixture.variant());
                if(value.equals("-1") || value.equals("0.5")) assertThat(state.getPrice()).isEqualByComparingTo(new BigDecimal(value));
                else assertThat(state.getPrice()).isNull();
                s.setRollbackOnly();
            });
        }
        assertThat(jdbc.queryForObject("SELECT selling_price FROM product_variants WHERE id=?",BigDecimal.class,fixture.variant()))
                .isEqualByComparingTo(BigDecimal.valueOf(1000));
        assertThat(service.getCheckoutVariantStates(List.of(java.util.UUID.randomUUID()))).isEmpty();
    }
}
