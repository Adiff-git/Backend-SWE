package com.example.demo.cart.repository;

import com.example.demo.catalog.CatalogFixtures;
import com.example.demo.catalog.entity.ProductStatus;
import com.example.demo.fixture.wishlist.WishlistFixtures;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class CartItemRepositoryIT {
    @Autowired EntityManager entities;
    @Autowired JdbcTemplate jdbc;
    @Autowired CartItemRepository repository;
    Long cart;
    UUID product;
    UUID variant;

    @BeforeEach void prepare() {
        Long user = WishlistFixtures.persistUser(entities).getId();
        CatalogFixtures.ProductFixture fixture = CatalogFixtures.persistProduct(entities, ProductStatus.PUBLISHED);
        entities.flush();
        product = fixture.product().getId();
        variant = fixture.variant().getId();
        cart = jdbc.queryForObject("INSERT INTO carts(user_id) VALUES (?) RETURNING id", Long.class, user);
    }

    private void insert(int quantity) {
        jdbc.update("INSERT INTO cart_items(cart_id,product_id,product_variant_id,quantity,selected,added_at,public_product_name,public_sku,public_options) VALUES (?,?,?,?,TRUE,CURRENT_TIMESTAMP,'Product','SKU','{}'::JSONB)",
                cart, product, variant, quantity);
    }

    @ParameterizedTest @ValueSource(ints = {1, 1000})
    void schema_shouldAcceptBoundaryQuantity(int quantity) {
        insert(quantity);
        assertThat(jdbc.queryForObject("SELECT quantity FROM cart_items WHERE cart_id=?", Integer.class, cart)).isEqualTo(quantity);
    }

    @ParameterizedTest @ValueSource(ints = {0, 1001})
    void schema_shouldRejectOutsideQuantity(int quantity) {
        assertThatThrownBy(() -> insert(quantity)).hasMessageContaining("ck_cart_items_quantity");
    }

    @Test void schema_shouldRejectDuplicateVariant() {
        insert(1);
        assertThatThrownBy(() -> insert(2)).hasMessageContaining("uk_cart_items_cart_id_product_variant_id");
    }

    @Test void schema_shouldRestrictCatalogDeletion() {
        insert(1);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM product_variants WHERE id=?", variant))
                .hasMessageContaining("fk_cart_items_product_variant_id");
    }

    @Test void schema_shouldCascadeOnlyCartOwnedItems() {
        insert(1);
        jdbc.update("DELETE FROM carts WHERE id=?", cart);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cart_items WHERE cart_id=?", Long.class, cart)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product_variants WHERE id=?", Long.class, variant)).isEqualTo(1);
    }

    @Test void reads_shouldScopeOwnerAndOrderTimestampTiesByItemId() {
        insert(1);
        var second = CatalogFixtures.persistProduct(entities, ProductStatus.PUBLISHED);
        entities.flush();
        jdbc.update("INSERT INTO cart_items(cart_id,product_id,product_variant_id,quantity,selected,added_at,public_product_name,public_sku,public_options) "
                + "SELECT cart_id,?,?,2,TRUE,added_at,public_product_name,public_sku,public_options FROM cart_items WHERE cart_id=?",
                second.product().getId(), second.variant().getId(), cart);
        Long owner = jdbc.queryForObject("SELECT user_id FROM carts WHERE id=?", Long.class, cart);
        var expected = jdbc.queryForList("SELECT id FROM cart_items WHERE cart_id=? ORDER BY added_at DESC,id ASC", Long.class, cart);
        assertThat(repository.findByCartIdOrderByAddedAtDescIdAsc(cart).stream().map(row -> row.getId()).toList()).containsExactlyElementsOf(expected);
        assertThat(repository.findVariantIdsForOwner(owner)).containsExactlyInAnyOrder(variant, second.variant().getId());
        assertThat(repository.findOwnedVariantId(-1L, expected.getFirst())).isEmpty();
        assertThat(repository.findByIdAndCartId(expected.getFirst(), -1L)).isEmpty();
    }
}
