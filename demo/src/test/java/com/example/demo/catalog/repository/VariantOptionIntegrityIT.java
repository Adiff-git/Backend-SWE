package com.example.demo.catalog.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.demo.catalog.CatalogFixtures;
import com.example.demo.catalog.entity.ProductStatus;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class VariantOptionIntegrityIT {
    @Autowired private EntityManager entities;
    @Autowired private TransactionTemplate transactions;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private DataSource source;

    @Test
    void commit_shouldRejectEquivalentCombinationsAndMismatchedDimensions() {
        UUID product = seed(Map.of("Color", "Red"));
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> insert(product, "{\" color \":\"RED\"}")))
                .hasStackTraceContaining("canonical");
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> insert(product, "{\"Size\":\"M\"}")))
                .hasStackTraceContaining("dimension");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product_variants WHERE product_id=?", Long.class, product))
                .isEqualTo(1);
    }

    @Test
    void commit_shouldRejectDuplicateCanonicalKeysBeforeMapConstruction() {
        UUID product = seed(Map.of("Color", "Red"));
        assertThatThrownBy(() -> transactions.executeWithoutResult(status ->
                insert(product, "{\"Color\":\"Blue\",\" color \":\"Green\"}")))
                .hasStackTraceContaining("canonical");
    }

    @Test
    void commit_shouldAllowSparseConcreteCombinationsWithSameDimensions() {
        UUID product = seed(Map.of("Color", "Red", "Size", "M"));
        transactions.executeWithoutResult(status -> insert(product, "{\"Color\":\"Blue\",\"Size\":\"L\"}"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product_variants WHERE product_id=?", Long.class, product))
                .isEqualTo(2);
    }

    @Test
    void parentMove_shouldValidateBothProductsAndRollbackMismatchedDimensionSets() {
        UUID sourceProduct = seed(Map.of("Color", "Red"));
        UUID targetProduct = seed(Map.of("Size", "M"));
        transactions.executeWithoutResult(status -> insert(sourceProduct, "{\"Color\":\"Blue\"}"));
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> jdbc.update(
                "UPDATE product_variants SET product_id=? WHERE product_id=? AND option_combination->>'Color'='Blue'",
                targetProduct, sourceProduct))).hasStackTraceContaining("dimension");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product_variants WHERE product_id=?", Long.class, sourceProduct))
                .isEqualTo(2);
    }

    @Test
    void deletion_shouldRejectLastVariantButAllowRemovingAnOptionedSibling() {
        UUID product = seed(Map.of("Color", "Red"));
        assertThatThrownBy(() -> transactions.executeWithoutResult(status ->
                jdbc.update("DELETE FROM product_variants WHERE product_id=?", product)))
                .hasStackTraceContaining("Invalid Published variants");
        transactions.executeWithoutResult(status -> insert(product, "{\"Color\":\"Blue\"}"));
        transactions.executeWithoutResult(status -> jdbc.update(
                "DELETE FROM product_variants WHERE product_id=? AND option_combination->>'Color'='Blue'", product));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product_variants WHERE product_id=?", Long.class, product))
                .isEqualTo(1);
    }

    @Test
    void concurrentCommits_shouldAllowOnlyOneCanonicalCombination() throws Exception {
        UUID product = seed(Map.of("Color", "Red"));
        CountDownLatch inserted = new CountDownLatch(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> competingInsert(product, "{\"Color\":\"Blue\"}", inserted));
            var second = executor.submit(() -> competingInsert(product, "{\" color \":\" BLUE \"}", inserted));
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product_variants WHERE product_id=?", Long.class, product))
                .isEqualTo(2);
    }

    private UUID seed(Map<String, String> options) {
        return transactions.execute(status -> {
            CatalogFixtures.ProductFixture fixture = CatalogFixtures.product(ProductStatus.PUBLISHED);
            fixture.variant().setOptionCombination(options);
            fixture.variant().setBasePrice(BigDecimal.valueOf(100));
            entities.persist(fixture.category());
            entities.persist(fixture.product());
            entities.persist(fixture.variant());
            entities.persist(CatalogFixtures.image(fixture.product(), 0, true));
            return fixture.product().getId();
        });
    }

    private void insert(UUID product, String options) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO product_variants(id,product_id,sku,option_combination,base_price) VALUES (?,?,?,CAST(? AS JSONB),100)",
                id, product, "OPTION-" + id, options);
    }

    private boolean competingInsert(UUID product, String options, CountDownLatch inserted) throws Exception {
        try (Connection connection = source.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO product_variants(id,product_id,sku,option_combination,base_price) VALUES (?,?,?,CAST(? AS JSONB),100)")) {
                UUID id = UUID.randomUUID();
                statement.setObject(1, id);
                statement.setObject(2, product);
                statement.setString(3, "OVERLAP-" + id);
                statement.setString(4, options);
                statement.executeUpdate();
                inserted.countDown();
                if (!inserted.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("No concurrent insertion");
                connection.commit();
                return true;
            } catch (SQLException rejected) {
                connection.rollback();
                if (!"23514".equals(rejected.getSQLState())) throw rejected;
                return false;
            }
        }
    }
}
