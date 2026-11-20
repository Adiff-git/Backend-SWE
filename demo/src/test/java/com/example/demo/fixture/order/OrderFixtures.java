package com.example.demo.fixture.order;

import com.example.demo.fixture.wishlist.WishlistFixtures;
import com.example.demo.order.entity.Order;
import com.example.demo.order.entity.OrderItem;
import com.example.demo.order.entity.OrderStatus;
import com.example.demo.testdata.order.OrderTestData;
import com.example.demo.user.entity.User;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Caller owns fixture transactions, commit and cleanup. Never uses the repository under test. */
public final class OrderFixtures {
    private OrderFixtures() { }

    public static User customer(EntityManager entities) {
        return WishlistFixtures.persistUser(entities);
    }

    public static Order persist(EntityManager entities, Long owner, OrderStatus status, int rows) {
        Order order = OrderTestData.order(owner, status).merchandiseTotal(BigDecimal.valueOf(rows * 100L)).build();
        entities.persist(order);
        for (int i = 0; i < rows; i++) entities.persist(OrderTestData.item(order).build());
        entities.flush();
        return order;
    }

    public static List<Order> corpus(EntityManager entities, Long owner, int count) {
        List<Order> orders = new ArrayList<>();
        OrderStatus[] statuses = OrderStatus.values();
        for (int i = 0; i < count; i++) orders.add(persist(entities, owner, statuses[i % statuses.length], 5));
        return orders;
    }

    public static OrderItem referencedItem(EntityManager entities, Order order, UUID product, UUID variant) {
        OrderItem item = OrderTestData.item(order).productId(product).productVariantId(variant).build();
        entities.persist(item);
        return item;
    }
}
