package com.example.demo.order.repository;

import com.example.demo.fixture.order.OrderFixtures;
import com.example.demo.order.entity.Order;
import com.example.demo.order.entity.OrderStatus;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest @ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE) @ActiveProfiles("test")
class OrderItemRepositoryIT {
    @Autowired EntityManager entities;
    @Autowired OrderItemRepository items;

    @Test void findPreviews_shouldBoundEachOrderBeforeCombiningAndCountDistinctRows() {
        Long owner = OrderFixtures.customer(entities).getId();
        Order one = OrderFixtures.persist(entities,owner,OrderStatus.PENDING,5);
        Order two = OrderFixtures.persist(entities,owner,OrderStatus.SHIPPING,4);
        List<OrderItemRepository.ItemView> preview = items.findPreviews(List.of(one.getId(),two.getId()));
        assertThat(preview).hasSize(6);
        assertThat(preview.stream().filter(i -> i.getOrderId().equals(one.getId()))).allSatisfy(i -> assertThat(i.getRowCount()).isEqualTo(5));
        assertThat(preview).allSatisfy(i -> assertThat(i.getProductId()).isNull());
    }

    @Test void findDetails_shouldReturnAllHistoricalRowsInIdentifierOrder() {
        Long owner = OrderFixtures.customer(entities).getId();
        Order order = OrderFixtures.persist(entities,owner,OrderStatus.COMPLETED,7);
        assertThat(items.findDetails(order.getId())).hasSize(7)
                .isSortedAccordingTo(java.util.Comparator.comparing(OrderItemRepository.ItemView::getId));
    }
}
