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
class CatalogCartReadServiceIT {
    @Autowired CatalogCartReadService service;
    @Autowired EntityManager entities;
    @Autowired TransactionTemplate transactions;
    @Autowired JdbcTemplate jdbc;

    @AfterEach void cleanup() { jdbc.execute("TRUNCATE TABLE categories CASCADE"); }

    @Test void getVariantStates_shouldReadCompleteBatchesAndRetainHiddenRows() {
        List<UUID> ids = transactions.execute(status -> {
            List<UUID> saved = new ArrayList<>();
            for (int index = 0; index < 105; index++) {
                CatalogFixtures.ProductFixture fixture = CatalogFixtures.persistProduct(entities,
                        index == 104 ? ProductStatus.ARCHIVED : ProductStatus.PUBLISHED);
                saved.add(fixture.variant().getId());
            }
            return saved;
        });
        Map<UUID, CartVariantState> states = service.getVariantStates(ids);
        assertThat(states).hasSize(105);
        assertThat(states.get(ids.get(104)).getProductName()).isNull();
        assertThat(states.get(ids.get(0)).getOptions()).isNotEmpty();
        assertThat(service.hasImageCandidates(ids)).isFalse();
        assertThat(service.getCartPresentation(ids, null)).hasSize(105);
    }

    @Test void getVariantStates_shouldTreatNegativePriceAsUnavailableWithoutChangingCatalog() {
        UUID variant = transactions.execute(status -> CatalogFixtures.persistProduct(entities, ProductStatus.PUBLISHED).variant().getId());
        jdbc.update("UPDATE product_variants SET selling_price=-1 WHERE id=?", variant);
        assertThat(service.getVariantStates(List.of(variant)).get(variant).getAmount()).isNull();
        jdbc.update("UPDATE product_variants SET selling_price=0 WHERE id=?", variant);
        assertThat(service.getVariantStates(List.of(variant)).get(variant).isPurchasable()).isTrue();
    }

    @Test void getVariantStates_shouldRejectFractionalAndNonfiniteAmounts() {
        UUID variant = transactions.execute(status -> CatalogFixtures.persistProduct(entities, ProductStatus.PUBLISHED).variant().getId());
        for (String price : List.of("0.5", "NaN", "Infinity", "-Infinity")) {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update(
                    "UPDATE product_variants SET selling_price=CAST(? AS numeric) WHERE id=?", price, variant))
                    .as(price).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        }
    }
}
