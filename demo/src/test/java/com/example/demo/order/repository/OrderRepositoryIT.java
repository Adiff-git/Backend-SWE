package com.example.demo.order.repository;

import com.example.demo.fixture.order.OrderFixtures;
import com.example.demo.order.entity.OrderStatus;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest @ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE) @ActiveProfiles("test")
class OrderRepositoryIT {
    @Autowired EntityManager entities;
    @Autowired OrderRepository orders;

    @Test void findOwnedHeaders_shouldFilterBeforePagingAndKeepStableOrder() {
        Long owner = OrderFixtures.customer(entities).getId();
        Long foreign = OrderFixtures.customer(entities).getId();
        OrderFixtures.corpus(entities,owner,18);
        OrderFixtures.corpus(entities,foreign,6);
        entities.flush(); entities.clear();
        assertThat(orders.countOwnedOrders(owner,null)).isEqualTo(18);
        for (OrderStatus status : OrderStatus.values()) {
            assertThat(orders.countOwnedOrders(owner,status)).isEqualTo(3);
            assertThat(orders.findOwnedHeaders(owner,status,PageRequest.of(1,2,Sort.by("id")))).hasSize(1);
        }
        for (String sort : List.of("placedAt,asc","placedAt,desc","payableTotal,asc","payableTotal,desc")) {
            assertThat(orders.findOwnedHeadersLong(owner,null,0,2,sort)).extracting(h -> h.getId())
                    .containsExactlyElementsOf(orders.findOwnedHeaders(owner,null,PageRequest.of(0,2,Sort.by("id"))).stream().map(h -> h.getId()).toList());
            for (long offset : new long[]{2147483646L,2147483647L,2147483648L,214748364700L}) {
                assertThat(orders.findOwnedHeadersLong(owner,null,offset,2,sort)).isEmpty();
            }
        }
    }

    @Test void findOwnedHeader_shouldHideForeignAndUnknownOrders() {
        Long owner = OrderFixtures.customer(entities).getId();
        Long foreign = OrderFixtures.customer(entities).getId();
        Long id = OrderFixtures.persist(entities,owner,OrderStatus.COMPLETED,5).getId();
        assertThat(orders.findOwnedHeader(owner,id)).isPresent();
        assertThat(orders.findOwnedHeader(owner,id).orElseThrow().getVoucherDiscount()).isZero();
        assertThat(orders.findOwnedHeader(owner,id).orElseThrow().getVoucherSnapshot()).isNull();
        assertThat(orders.findOwnedHeader(foreign,id)).isEmpty();
        assertThat(orders.findOwnedHeader(owner,Long.MAX_VALUE)).isEmpty();
    }
}
