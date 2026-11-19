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
        if (status == OrderStatus.SHIPPING || status == OrderStatus.COMPLETED) {
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
}
