package com.example.demo.order.service;

import com.example.demo.config.CheckoutIntegrationSupport;
import com.example.demo.fixture.checkout.CheckoutFixtures;
import com.example.demo.order.dto.request.CreateOrderCommand;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderCreationServiceIT extends CheckoutIntegrationSupport {
    @Autowired OrderCreationService service;
    @Autowired com.example.demo.inventory.service.InventoryService inventory;

    @Test void createOrder_shouldCommitCompletePendingHistoricalAggregate() {
        CreateOrderCommand.Item item = new CreateOrderCommand.Item(fixture.item(),fixture.product(),fixture.variant(),"Product","SKU",Map.of(),2,BigInteger.valueOf(100),BigInteger.valueOf(200));
        CreateOrderCommand command = new CreateOrderCommand(fixture.owner(),CheckoutFixtures.PLACED_AT,"Recipient","0123456789","Address",List.of(item),BigInteger.valueOf(200));
        com.example.demo.order.dto.response.CreatedOrderSeed created = transactions.execute(s -> {
            var result = service.createOrder(command);
            inventory.reserveReservations(new com.example.demo.inventory.dto.request.ReservationCommand(result.getId(), command.getPlacedAt(),
                    result.getItems().stream().map(line -> new com.example.demo.inventory.dto.request.ReservationLine(line.getId(), line.getSnapshot().getVariantId(), line.getSnapshot().getQuantity())).toList()));
            return result;
        });
        assertThat(created.getOrderNumber()).isEqualTo("ORD-"+created.getId());
        assertThat(jdbc.queryForMap("SELECT status,merchandise_total,payable_total FROM orders WHERE id=?",created.getId()))
                .containsEntry("status","PENDING");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_items WHERE order_id=?",Integer.class,created.getId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cart_items",Integer.class)).isEqualTo(2);
    }

    @Test void createOrder_shouldRejectNumberCollisionWithoutPartialAggregateOrCartDeletion() {
        CheckoutFixtures.HistoricalOrder historical = transactions.execute(s -> CheckoutFixtures.order(jdbc,fixture.owner()));
        jdbc.update("UPDATE orders SET order_number=? WHERE id=?","ORD-"+(historical.id()+1),historical.id());
        CreateOrderCommand.Item item = new CreateOrderCommand.Item(fixture.item(),fixture.product(),fixture.variant(),"Product","SKU",Map.of(),1,BigInteger.valueOf(100),BigInteger.valueOf(100));
        CreateOrderCommand command = new CreateOrderCommand(fixture.owner(),CheckoutFixtures.PLACED_AT,"Recipient","0123456789","Address",List.of(item),BigInteger.valueOf(100));
        assertThatThrownBy(() -> service.createOrder(command)).hasStackTraceContaining("uk_orders_order_number");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM order_items",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cart_items",Integer.class)).isEqualTo(2);
    }
}
