package com.example.demo.wishlist.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.example.demo.catalog.CatalogFixtures;
import com.example.demo.catalog.entity.Product;
import com.example.demo.catalog.entity.ProductStatus;
import com.example.demo.catalog.exception.CatalogStorageUnavailableException;
import com.example.demo.catalog.exception.ProductNotFoundException;
import com.example.demo.common.exception.ApiErrorFactory;
import com.example.demo.wishlist.exception.WishlistConflictException;
import com.example.demo.catalog.storage.ProductImageStorage;
import com.example.demo.fixture.wishlist.WishlistFixtures;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import tools.jackson.databind.json.JsonMapper;
import com.example.demo.wishlist.dto.response.WishlistPage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class WishlistServiceIT {
    @Autowired private WishlistService service;
    @Autowired private EntityManager entityManager;
    @Autowired private TransactionTemplate transactions;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private JsonMapper json;
    @MockitoBean private ProductImageStorage storage;
    @MockitoBean private Clock clock;
    private Long owner;
    private UUID productId;
    private final Instant now = Instant.parse("2026-10-31T02:00:00Z");

    @BeforeEach
    void prepare() {
        cleanup();
        when(clock.instant()).thenReturn(now);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        transactions.executeWithoutResult(status -> {
            owner = WishlistFixtures.persistUser(entityManager).getId();
            productId = WishlistFixtures.persistProduct(entityManager, ProductStatus.PUBLISHED).getId();
        });
    }

    @AfterEach
    void cleanup() {
        jdbc.execute("TRUNCATE TABLE users, categories CASCADE");
    }

    @Test
    void addProduct_shouldCommitOnceAndPreserveTime_whenDuplicate() {
        AddWishlistResult first = service.addProduct(owner, productId);
        when(clock.instant()).thenReturn(now.plusSeconds(60));
        AddWishlistResult second = service.addProduct(owner, productId);
        assertThat(first.isCreated()).isTrue();
        assertThat(second.isCreated()).isFalse();
        assertThat(second.getItem().getAddedAt()).isEqualTo(first.getItem().getAddedAt());
        assertThat(count()).isEqualTo(1);
    }

    @Test
    void addProduct_shouldRollbackNewMembership_whenSigningFails() {
        addImage();
        when(storage.prepareReadSigner()).thenThrow(new CatalogStorageUnavailableException());
        assertThatThrownBy(() -> service.addProduct(owner, productId))
                .isInstanceOf(CatalogStorageUnavailableException.class);
        assertThat(count()).isZero();
    }

    @Test
    void addProduct_shouldRetainExistingMembership_whenProductBecomesHidden() {
        Instant addedAt = service.addProduct(owner, productId).getItem().getAddedAt();
        jdbc.update("UPDATE products SET status = 'DRAFT' WHERE id = ?", productId);
        assertThatThrownBy(() -> service.addProduct(owner, productId)).isInstanceOf(ProductNotFoundException.class);
        assertThat(count()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT added_at FROM wishlist_items", java.sql.Timestamp.class).toInstant())
                .isEqualTo(addedAt);
    }

    @Test
    void addProduct_shouldRollbackAndHideMetadata_whenFinalPublicationCheckFails() {
        addImage();
        when(storage.prepareReadSigner()).thenAnswer(invocation -> {
            // Controlled external-boundary callback changes eligibility before the final catalog check.
            jdbc.update("UPDATE products SET status = 'ARCHIVED' WHERE id = ?", productId);
            return new ProductImageStorage.PreparedReadSigner() {
                @Override public String signReadUrl(String key) {
                    throw new AssertionError("Hidden image must not be signed");
                }
                @Override public void close() {
                }
            };
        });
        assertThatThrownBy(() -> service.addProduct(owner, productId)).isInstanceOf(ProductNotFoundException.class);
        assertThat(count()).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM products WHERE id = ?", String.class, productId))
                .isEqualTo("PUBLISHED");
    }

    @Test
    void addProduct_shouldTranslateRealForeignKeyFailureAfterRollback_whenParentDisappears() {
        // A disposable-database trigger creates the precise between-check-and-insert failure.
        jdbc.execute("CREATE FUNCTION wishlist_test_remove_parent() RETURNS TRIGGER AS $$ BEGIN "
                + "DELETE FROM product_variants WHERE product_id = NEW.product_id; "
                + "DELETE FROM products WHERE id = NEW.product_id; RETURN NEW; END; $$ LANGUAGE plpgsql");
        jdbc.execute("CREATE TRIGGER wishlist_test_remove_parent BEFORE INSERT ON wishlist_items "
                + "FOR EACH ROW EXECUTE FUNCTION wishlist_test_remove_parent()");
        try {
            Throwable failure = org.assertj.core.api.Assertions.catchThrowable(() -> service.addProduct(owner, productId));
            assertThat(failure).isNotNull();
            assertThat(new ApiErrorFactory().from(failure, "/api/me/wishlist").getStatus()).isEqualTo(404);
            assertThat(count()).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM products WHERE id = ?", Long.class, productId))
                    .isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE id = ?", Long.class, owner))
                    .isEqualTo(1);
        } finally {
            jdbc.execute("DROP TRIGGER wishlist_test_remove_parent ON wishlist_items");
            jdbc.execute("DROP FUNCTION wishlist_test_remove_parent()");
        }
    }

    @Test
    void addProduct_shouldReportSafeConflict_whenMembershipIsRemovedBeforeLookup() {
        jdbc.execute("CREATE FUNCTION wishlist_test_remove_membership() RETURNS TRIGGER AS $$ BEGIN "
                + "DELETE FROM wishlist_items WHERE id = NEW.id; RETURN NULL; END; $$ LANGUAGE plpgsql");
        jdbc.execute("CREATE TRIGGER wishlist_test_remove_membership AFTER INSERT ON wishlist_items "
                + "FOR EACH ROW EXECUTE FUNCTION wishlist_test_remove_membership()");
        try {
            assertThatThrownBy(() -> service.addProduct(owner, productId)).isInstanceOf(WishlistConflictException.class);
            assertThat(count()).isZero();
        } finally {
            jdbc.execute("DROP TRIGGER wishlist_test_remove_membership ON wishlist_items");
            jdbc.execute("DROP FUNCTION wishlist_test_remove_membership()");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"PUBLISHED", "DRAFT", "ARCHIVED"})
    void removeProduct_shouldPreserveParentsAndOtherMembership_whenProductHasAnyVisibility(String state) {
        service.addProduct(owner, productId);
        Long other = transactions.execute(status -> WishlistFixtures.persistUser(entityManager).getId());
        service.addProduct(other, productId);
        jdbc.update("UPDATE products SET status = ? WHERE id = ?", state, productId);
        service.removeProduct(owner, productId);
        service.removeProduct(owner, productId);
        assertThat(count()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT user_id FROM wishlist_items", Long.class)).isEqualTo(other);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users", Long.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM products", Long.class)).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, 60})
    void addProduct_shouldSampleFreshTimeWithoutStrictIncrease_whenReAddedAfterRemoval(long elapsed) {
        Instant first = service.addProduct(owner, productId).getItem().getAddedAt();
        Long oldId = jdbc.queryForObject("SELECT id FROM wishlist_items", Long.class);
        service.removeProduct(owner, productId);
        when(clock.instant()).thenReturn(now.plusSeconds(elapsed));
        AddWishlistResult added = service.addProduct(owner, productId);
        assertThat(added.isCreated()).isTrue();
        assertThat(added.getItem().getAddedAt()).isEqualTo(first.plusSeconds(elapsed));
        assertThat(jdbc.queryForObject("SELECT id FROM wishlist_items", Long.class)).isNotEqualTo(oldId);
    }

    @Test
    void removeProduct_shouldRollbackMembershipDeletion_whenDatabaseRejectsOperation() {
        service.addProduct(owner, productId);
        jdbc.execute("CREATE FUNCTION wishlist_test_reject_removal() RETURNS TRIGGER AS $$ BEGIN "
                + "RAISE EXCEPTION 'Controlled test rejection' USING ERRCODE = '23514'; END; $$ LANGUAGE plpgsql");
        jdbc.execute("CREATE TRIGGER wishlist_test_reject_removal AFTER DELETE ON wishlist_items "
                + "FOR EACH ROW EXECUTE FUNCTION wishlist_test_reject_removal()");
        try {
            assertThatThrownBy(() -> service.removeProduct(owner, productId)).isInstanceOf(RuntimeException.class);
            assertThat(count()).isEqualTo(1);
        } finally {
            jdbc.execute("DROP TRIGGER wishlist_test_reject_removal ON wishlist_items");
            jdbc.execute("DROP FUNCTION wishlist_test_reject_removal()");
        }
    }

    private void addImage() {
        transactions.executeWithoutResult(status -> {
            Product product = entityManager.find(Product.class, productId);
            entityManager.persist(CatalogFixtures.image(product, 0, true));
        });
    }

    private long count() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM wishlist_items", Long.class);
    }

    @Test
    void listWishlist_shouldRetainAndRestoreMembership_whenProductVisibilityChanges() {
        Instant addedAt = service.addProduct(owner, productId).getItem().getAddedAt();
        for (String hidden : java.util.List.of("DRAFT", "ARCHIVED")) {
            jdbc.update("UPDATE products SET status = ? WHERE id = ?", hidden, productId);
            assertThat(service.listWishlist(owner, 0, 20).getItems()).isEmpty();
            assertThat(service.listWishlist(owner, 0, 20).getTotalItems()).isZero();
            assertThat(count()).isEqualTo(1);
        }
        jdbc.update("UPDATE products SET status = 'PUBLISHED' WHERE id = ?", productId);
        WishlistPage restored = service.listWishlist(owner, 0, 20);
        assertThat(restored.getItems()).hasSize(1);
        assertThat(restored.getItems().getFirst().getAddedAt()).isEqualTo(addedAt);
        assertThat(restored.getItems().getFirst().getProduct().getPrimaryImageUrl()).isNull();
    }

    @Test
    void listWishlist_shouldReturnConflictWithoutPartialPage_whenSelectedProductBecomesHidden() {
        service.addProduct(owner, productId);
        addImage();
        when(storage.prepareReadSigner()).thenAnswer(invocation -> {
            jdbc.update("UPDATE products SET status = 'DRAFT' WHERE id = ?", productId);
            return new ProductImageStorage.PreparedReadSigner() {
                @Override public String signReadUrl(String key) {
                    throw new AssertionError("Hidden metadata must not be signed");
                }
                @Override public void close() {
                }
            };
        });
        assertThatThrownBy(() -> service.listWishlist(owner, 0, 20)).isInstanceOf(WishlistConflictException.class);
        assertThat(count()).isEqualTo(1);
    }

    @Test
    void listWishlist_shouldUseBoundedQueriesAndDetachedResponses_whenPageContainsOneHundredImages() {
        transactions.executeWithoutResult(status -> {
            for (int index = 0; index < 101; index++) {
                Product product = WishlistFixtures.persistProduct(entityManager, ProductStatus.PUBLISHED);
                entityManager.persist(CatalogFixtures.image(product, 0, true));
                entityManager.flush();
                jdbc.update("INSERT INTO wishlist_items(user_id, product_id, added_at) VALUES (?, ?, CURRENT_TIMESTAMP)",
                        owner, product.getId());
            }
        });
        when(storage.prepareReadSigner()).thenAnswer(invocation -> new ProductImageStorage.PreparedReadSigner() {
            @Override public String signReadUrl(String key) { return "https://images.test/temporary"; }
            @Override public void close() { }
        });
        SessionFactory factory = entityManagerFactory.unwrap(SessionFactory.class);
        for (int size : java.util.List.of(1, 100)) {
            factory.getStatistics().clear();
            WishlistPage page = service.listWishlist(owner, 0, size);
            assertThat(page.getItems()).hasSize(size);
            assertThat(page.getTotalItems()).isEqualTo(101);
            long queries = factory.getStatistics().getPrepareStatementCount();
            assertThat(queries).isLessThanOrEqualTo(6);
            String response = json.writeValueAsString(page);
            assertThat(response).doesNotContain("rating", "review", "variants", "objectKey", "userId");
            assertThat(factory.getStatistics().getPrepareStatementCount()).isEqualTo(queries);
        }
    }
}
