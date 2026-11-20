package com.example.demo.order.validator;

import com.example.demo.order.entity.Order;
import com.example.demo.order.entity.OrderStatus;
import com.example.demo.order.exception.OrderConflictException;
import com.example.demo.order.exception.OrderNotFoundException;
import com.example.demo.testdata.order.OrderTestData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderValidatorTest {
    private final OrderValidator validator = new OrderValidator();

    @ParameterizedTest @EnumSource(OrderStatus.class)
    void canCancel_shouldEnforceApprovedStateMatrix(OrderStatus status) {
        Order order = OrderTestData.order(1L,status).build();
        if (status == OrderStatus.SHIPPING || status == OrderStatus.COMPLETED || status == OrderStatus.FAILED) {
            assertThatThrownBy(() -> validator.canCancel(order,1L)).isInstanceOf(OrderConflictException.class);
        } else assertThat(validator.canCancel(order,1L)).isEqualTo(status != OrderStatus.CANCELLED);
    }

    @Test void canCancel_shouldHideMissingAndForeignState() {
        assertThatThrownBy(() -> validator.canCancel(null,1L)).isInstanceOf(OrderNotFoundException.class);
        assertThatThrownBy(() -> validator.canCancel(OrderTestData.order(2L,OrderStatus.PENDING).build(),1L))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test void validateTime_shouldRejectBeforePlacementAndPermitEquality() {
        Order order = OrderTestData.order(1L,OrderStatus.PENDING).build();
        assertThatThrownBy(() -> validator.validateTime(order,OrderTestData.PLACED.minusSeconds(1))).isInstanceOf(OrderConflictException.class);
        validator.validateTime(order,OrderTestData.PLACED);
    }
    @Test void validateCreation_shouldKeepItemSumsAndRejectIncoherentDiscountOrSnapshot() {
        var amount = java.math.BigInteger.valueOf(100);
        var discount = java.math.BigInteger.TEN;
        var item = new com.example.demo.order.dto.request.CreateOrderCommand.Item(1L,java.util.UUID.randomUUID(),java.util.UUID.randomUUID(),"Product","SKU",java.util.Map.of(),1,amount,amount);
        var money = new com.example.demo.catalog.dto.response.SellingPrice(amount,"VND");
        var snapshot = new com.example.demo.voucher.dto.response.VoucherSnapshot(1L,1L,"TEN","Ten","FIXED_AMOUNT",java.math.BigDecimal.TEN,
            new com.example.demo.catalog.dto.response.SellingPrice(java.math.BigInteger.ZERO,"VND"),null,money,
            new com.example.demo.catalog.dto.response.SellingPrice(discount,"VND"),OrderTestData.PLACED);
        var valid = new com.example.demo.order.dto.request.CreateOrderCommand(1L,OrderTestData.PLACED,"Name","Phone","Address",java.util.List.of(item),amount,discount,amount.subtract(discount),snapshot);
        validator.validateCreation(valid);
        assertThatThrownBy(() -> validator.validateCreation(new com.example.demo.order.dto.request.CreateOrderCommand(1L,OrderTestData.PLACED.plusSeconds(1),"Name","Phone","Address",java.util.List.of(item),amount,discount,amount.subtract(discount),snapshot))).isInstanceOf(com.example.demo.order.exception.InvalidOrderRequestException.class);
        assertThat(item.getLineTotal()).isEqualTo(amount);
        assertThatThrownBy(() -> validator.validateCreation(new com.example.demo.order.dto.request.CreateOrderCommand(1L,OrderTestData.PLACED,"Name","Phone","Address",java.util.List.of(item),amount,discount,amount,snapshot))).isInstanceOf(com.example.demo.order.exception.InvalidOrderRequestException.class);
        assertThatThrownBy(() -> validator.validateCreation(new com.example.demo.order.dto.request.CreateOrderCommand(1L,OrderTestData.PLACED,"Name","Phone","Address",java.util.List.of(item),amount,discount,amount.subtract(discount),null))).isInstanceOf(com.example.demo.order.exception.InvalidOrderRequestException.class);
    }
}
