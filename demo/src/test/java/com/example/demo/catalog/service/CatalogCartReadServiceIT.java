package com.example.demo.catalog.service;

import com.example.demo.catalog.CatalogFixtures;
import com.example.demo.catalog.dto.response.CartVariantState;
import com.example.demo.catalog.entity.ProductStatus;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@org.springframework.context.annotation.Import(com.example.demo.config.SqlCaptureConfiguration.class)
class CatalogCartReadServiceIT {
    @Autowired CatalogCartReadService service;
    @Autowired EntityManager entities;
    @Autowired TransactionTemplate transactions;
    @Autowired JdbcTemplate jdbc;

    @AfterEach void cleanup() { jdbc.execute("TRUNCATE TABLE categories CASCADE"); }

    @org.junit.jupiter.params.ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(ints = {100,101,205})
    void getVariantStates_shouldReadCompleteBatchesAndRetainHiddenRows(int publishedCount) {
        List<UUID> ids = transactions.execute(status -> {
            List<UUID> saved = new ArrayList<>();
            for (int index = 0; index <= publishedCount; index++) {
                CatalogFixtures.ProductFixture fixture = CatalogFixtures.persistProduct(entities,
                        index == publishedCount ? ProductStatus.ARCHIVED : ProductStatus.PUBLISHED);
                saved.add(fixture.variant().getId());
            }
            return saved;
        });
        com.example.demo.config.SqlCapture.begin();
        Map<UUID, CartVariantState> states;
        java.util.List<String> sql;
        try { states = service.getVariantStates(ids); } finally { sql = com.example.demo.config.SqlCapture.end(); }
        assertThat(sql.stream().filter(statement -> statement.toLowerCase(java.util.Locale.ROOT).contains("from inventory_balances"))).hasSize((publishedCount+99)/100);
        assertThat(states).hasSize(publishedCount+1);
        assertThat(states.get(ids.get(publishedCount)).getProductName()).isNull();
        assertThat(states.get(ids.get(publishedCount)).getAvailability()).isNull();
        assertThat(states.get(ids.get(0)).getAvailability().getStatus()).isEqualTo("OUT_OF_STOCK");
        assertThat(states.get(ids.get(0)).getOptions()).isNotEmpty();
        assertThat(service.hasImageCandidates(ids)).isFalse();
        assertThat(service.getCartPresentation(ids, null)).hasSize(publishedCount+1);
    }

    @Test void getVariantStates_shouldTreatNegativePriceAsUnavailableWithoutChangingCatalog() {
        UUID variant = transactions.execute(status -> CatalogFixtures.persistProduct(entities, ProductStatus.PUBLISHED).variant().getId());
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update("UPDATE product_variants SET base_price=-1 WHERE id=?", variant))
                .hasStackTraceContaining("ck_product_variants_base_price");
        transactions.executeWithoutResult(tx -> {
            jdbc.execute("CREATE TEMP VIEW product_variants AS SELECT id,product_id,sku,option_combination,-1::NUMERIC AS base_price FROM public.product_variants");
            assertThat(service.getVariantStates(List.of(variant)).get(variant).getAmount()).isNull();
            tx.setRollbackOnly();
        });
        jdbc.update("UPDATE product_variants SET base_price=0 WHERE id=?", variant);
        assertThat(service.getVariantStates(List.of(variant)).get(variant).isPurchasable()).isTrue();
    }

    @Test void getVariantStates_shouldRejectFractionalAndNonfiniteAmounts() {
        UUID variant = transactions.execute(status -> CatalogFixtures.persistProduct(entities, ProductStatus.PUBLISHED).variant().getId());
        for (String price : List.of("0.5", "NaN", "Infinity", "-Infinity")) {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update(
                    "UPDATE product_variants SET base_price=CAST(? AS numeric) WHERE id=?", price, variant))
                    .as(price).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        }
    }
}
