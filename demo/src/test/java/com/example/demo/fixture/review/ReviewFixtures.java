package com.example.demo.fixture.review;

import com.example.demo.catalog.CatalogFixtures;
import com.example.demo.catalog.entity.ProductStatus;
import com.example.demo.fixture.order.OrderFixtures;
import com.example.demo.order.entity.Order;
import com.example.demo.order.entity.OrderItem;
import com.example.demo.order.entity.OrderStatus;
import com.example.demo.user.entity.User;
import jakarta.persistence.EntityManager;
import java.util.UUID;

/** Caller owns commits and cleanup; no service under test is used to arrange data. */
public final class ReviewFixtures {
    private ReviewFixtures() { }

    public static Purchase purchase(EntityManager entities, OrderStatus status) {
        User owner = OrderFixtures.customer(entities);
        User foreign = OrderFixtures.customer(entities);
        CatalogFixtures.ProductFixture product = CatalogFixtures.persistProduct(entities, ProductStatus.PUBLISHED);
        Order order = com.example.demo.testdata.order.OrderTestData.order(owner.getId(), status).build();
        entities.persist(order);
        OrderItem item = OrderFixtures.referencedItem(entities, order, product.product().getId(), product.variant().getId());
        entities.flush();
        return new Purchase(owner.getId(), foreign.getId(), order.getId(), item.getId(), product.product().getId());
    }

    public record Purchase(Long owner, Long foreign, Long order, Long item, UUID product) { }

    public static Long additionalItem(EntityManager entities, Long owner, UUID product) {
        Order order = com.example.demo.testdata.order.OrderTestData.order(owner, OrderStatus.COMPLETED).build();
        entities.persist(order);
        OrderItem item = com.example.demo.testdata.order.OrderTestData.item(order).productId(product).build();
        entities.persist(item); entities.flush(); return item.getId();
    }
}
