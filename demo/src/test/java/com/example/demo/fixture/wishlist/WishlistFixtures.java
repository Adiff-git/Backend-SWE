package com.example.demo.fixture.wishlist;

import com.example.demo.catalog.CatalogFixtures;
import com.example.demo.catalog.entity.Product;
import com.example.demo.catalog.entity.ProductStatus;
import com.example.demo.user.entity.User;
import com.example.demo.user.enums.AccountStatus;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.UUID;

/** Persistent fixtures; the caller owns transaction and cleanup boundaries. */
public final class WishlistFixtures {
    private WishlistFixtures() {
    }

    public static User persistUser(EntityManager entityManager) {
        User user = new User();
        user.setUsername("wishlist-" + UUID.randomUUID());
        user.setPassword("isolated-test-fixture");
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setJoinedAt(Instant.parse("2026-10-31T00:00:00Z"));
        entityManager.persist(user);
        return user;
    }

    public static Product persistProduct(EntityManager entityManager, ProductStatus status) {
        return CatalogFixtures.persistProduct(entityManager, status).product();
    }
}
