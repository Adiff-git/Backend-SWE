package com.example.demo.wishlist.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.demo.catalog.entity.Product;
import com.example.demo.catalog.entity.ProductStatus;
import com.example.demo.fixture.wishlist.WishlistFixtures;
import com.example.demo.user.entity.User;
import com.example.demo.wishlist.entity.WishlistItem;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import java.util.List;

@DataJpaTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class WishlistRepositoryIT {
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private DataSource dataSource;
    @Autowired private WishlistRepository repository;

    @Test
    void insertIfAbsent_shouldKeepTransactionUsableAndOriginalTime_whenDuplicate() {
        User user = WishlistFixtures.persistUser(entityManager);
        Product product = WishlistFixtures.persistProduct(entityManager, ProductStatus.PUBLISHED);
        entityManager.flush();
        Instant original = Instant.parse("2026-10-31T00:00:00Z");
        assertThat(repository.insertIfAbsent(user.getId(), product.getId(), original)).isEqualTo(1);
        assertThat(repository.insertIfAbsent(user.getId(), product.getId(), original.plusSeconds(60))).isZero();
        assertThat(repository.findAddedAt(user.getId(), product.getId())).contains(original);
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void mapping_shouldPreserveReferencesAndTimestamp_whenPersisted() {
        User user = WishlistFixtures.persistUser(entityManager);
        Product product = WishlistFixtures.persistProduct(entityManager, ProductStatus.PUBLISHED);
        Instant savedAt = Instant.parse("2026-10-31T01:02:03.123456Z");
        WishlistItem item = BeanUtils.instantiateClass(WishlistItem.class);
        ReflectionTestUtils.setField(item, "user", user);
        ReflectionTestUtils.setField(item, "product", product);
        ReflectionTestUtils.setField(item, "addedAt", savedAt);
        entityManager.persist(item);
        entityManager.flush();
        Long id = item.getId();
        entityManager.clear();
        WishlistItem stored = entityManager.find(WishlistItem.class, id);
        assertThat(stored.getAddedAt()).isEqualTo(savedAt);
        assertThat(entityManager.getEntityManagerFactory().getPersistenceUnitUtil()
                .isLoaded(stored, "product")).isFalse();
        assertThat(entityManager.getEntityManagerFactory().getPersistenceUnitUtil()
                .isLoaded(stored, "user")).isFalse();
        assertThat(stored.getProduct().getId()).isEqualTo(product.getId());
        assertThat(stored.getUser().getId()).isEqualTo(user.getId());
    }

    @Test
    void schema_shouldRejectDuplicatePair_whenInsertedTwice() {
        User user = WishlistFixtures.persistUser(entityManager);
        Product product = WishlistFixtures.persistProduct(entityManager, ProductStatus.PUBLISHED);
        entityManager.flush();
        insert(user.getId(), product.getId());
        assertThatThrownBy(() -> insert(user.getId(), product.getId()))
                .hasMessageContaining("uk_wishlist_items_user_id_product_id");
    }

    @Test
    void schema_shouldRejectMissingParent_whenForeignKeyIsInvalid() {
        User user = WishlistFixtures.persistUser(entityManager);
        entityManager.flush();
        assertThatThrownBy(() -> insert(user.getId(), UUID.randomUUID()))
                .hasMessageContaining("fk_wishlist_items_product_id");
    }

    @Test
    void schema_shouldRejectMissingUser_whenForeignKeyIsInvalid() {
        Product product = WishlistFixtures.persistProduct(entityManager, ProductStatus.PUBLISHED);
        entityManager.flush();
        assertThatThrownBy(() -> insert(Long.MIN_VALUE, product.getId()))
                .hasMessageContaining("fk_wishlist_items_user_id");
    }

    @Test
    void schema_shouldRejectNullTimestamp_whenMembershipIsInserted() {
        User user = WishlistFixtures.persistUser(entityManager);
        Product product = WishlistFixtures.persistProduct(entityManager, ProductStatus.PUBLISHED);
        entityManager.flush();
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO wishlist_items(user_id, product_id, added_at) VALUES (?, ?, NULL)",
                user.getId(), product.getId())).hasMessageContaining("added_at");
    }

    @Test
    void cleanup_shouldDeleteOnlyDependents_whenUserIsPhysicallyDeleted() {
        User user = WishlistFixtures.persistUser(entityManager);
        Product product = WishlistFixtures.persistProduct(entityManager, ProductStatus.PUBLISHED);
        entityManager.flush();
        insert(user.getId(), product.getId());
        jdbc.update("DELETE FROM users WHERE id = ?", user.getId());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wishlist_items WHERE user_id = ?",
                Long.class, user.getId())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM products WHERE id = ?",
                Long.class, product.getId())).isEqualTo(1);
    }

    @Test
    void cleanup_shouldDeleteOnlyDependents_whenProductIsPhysicallyDeleted() {
        User user = WishlistFixtures.persistUser(entityManager);
        Product product = WishlistFixtures.persistProduct(entityManager, ProductStatus.PUBLISHED);
        entityManager.flush();
        insert(user.getId(), product.getId());
        jdbc.update("DELETE FROM product_variants WHERE product_id = ?", product.getId());
        jdbc.update("DELETE FROM products WHERE id = ?", product.getId());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wishlist_items WHERE product_id = ?",
                Long.class, product.getId())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE id = ?",
                Long.class, user.getId())).isEqualTo(1);
    }

    @Test
    void schema_shouldDefineRequiredColumnsAndIndexes_whenMigrated() {
        assertThat(jdbc.queryForList("SELECT column_name FROM information_schema.columns "
                + "WHERE table_schema = current_schema() AND table_name = 'wishlist_items' "
                + "AND is_nullable = 'NO'", String.class))
                .containsExactlyInAnyOrder("id", "user_id", "product_id", "added_at");
        assertThat(jdbc.queryForList("SELECT indexname FROM pg_indexes "
                + "WHERE schemaname = current_schema() AND tablename = 'wishlist_items'", String.class))
                .contains("idx_wishlist_items_product_id", "idx_wishlist_items_user_id_added_at_product_id");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void migration_shouldGrantOnlyUserRole_whenUpgradingExistingAssignments() {
        String schema = "wishlist_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        try {
            Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
                    .target("9").load().migrate();
            jdbc.update("INSERT INTO " + schema + ".users(username, password, account_status, joined_at) "
                    + "VALUES ('existing-user', 'test', 'ACTIVE', CURRENT_TIMESTAMP)");
            jdbc.update("INSERT INTO " + schema + ".roles(name) VALUES ('CUSTOM')");
            jdbc.update("INSERT INTO " + schema + ".user_roles(user_id, role_id) "
                    + "SELECT u.id, r.id FROM " + schema + ".users u CROSS JOIN " + schema
                    + ".roles r WHERE r.name = 'USER'");
            Long assignments = jdbc.queryForObject("SELECT COUNT(*) FROM " + schema + ".user_roles", Long.class);
            Long grants = jdbc.queryForObject("SELECT COUNT(*) FROM " + schema + ".role_permissions", Long.class);
            Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).load().migrate();
            assertThat(jdbc.queryForList("SELECT r.name FROM " + schema + ".roles r JOIN " + schema
                    + ".role_permissions rp ON r.id = rp.role_id JOIN " + schema
                    + ".permissions p ON p.id = rp.permission_id WHERE p.name = 'MANAGE_WISHLIST'",
                    String.class)).containsExactly("USER");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + schema + ".user_roles", Long.class))
                    .isEqualTo(assignments);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + schema + ".role_permissions", Long.class))
                    .isEqualTo(grants + 1);
        } finally {
            // The identifier is a fixed prefix plus generated hex, never external input.
            jdbc.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    private void insert(Long userId, UUID productId) {
        jdbc.update("INSERT INTO wishlist_items(user_id, product_id, added_at) "
                + "VALUES (?, ?, CURRENT_TIMESTAMP)", userId, productId);
    }

    @Test
    void findVisibleMemberships_shouldFilterBeforePagingAndCount_whenStatusesAndOwnersAreMixed() {
        User owner = WishlistFixtures.persistUser(entityManager);
        User other = WishlistFixtures.persistUser(entityManager);
        Product visibleA = WishlistFixtures.persistProduct(entityManager, ProductStatus.PUBLISHED);
        Product visibleB = WishlistFixtures.persistProduct(entityManager, ProductStatus.PUBLISHED);
        Product hidden = WishlistFixtures.persistProduct(entityManager, ProductStatus.ARCHIVED);
        Product foreign = WishlistFixtures.persistProduct(entityManager, ProductStatus.PUBLISHED);
        entityManager.flush();
        Instant tie = Instant.parse("2026-10-31T00:00:00Z");
        repository.insertIfAbsent(owner.getId(), visibleA.getId(), tie);
        repository.insertIfAbsent(owner.getId(), visibleB.getId(), tie);
        repository.insertIfAbsent(owner.getId(), hidden.getId(), tie.plusSeconds(10));
        repository.insertIfAbsent(other.getId(), foreign.getId(), tie.plusSeconds(20));
        List<String> expected = List.of(visibleA.getId().toString(), visibleB.getId().toString())
                .stream().sorted().toList();
        for (int page = 0; page < 2; page++) {
            org.springframework.data.domain.Page<WishlistMembershipView> result = repository.findVisibleMemberships(
                    owner.getId(), PageRequest.of(page, 1));
            assertThat(result.getTotalElements()).isEqualTo(2);
            assertThat(result.getTotalPages()).isEqualTo(2);
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().getFirst().getProductId().toString()).isEqualTo(expected.get(page));
        }
        assertThat(repository.findVisibleMemberships(owner.getId(), PageRequest.of(99, 1)).getContent()).isEmpty();
        assertThat(repository.findVisibleMemberships(Long.MIN_VALUE, PageRequest.of(0, 20)).getTotalElements()).isZero();
    }
}
