package com.example.demo.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.demo.config.ReviewIntegrationSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class OrderReviewReadServiceIT extends ReviewIntegrationSupport {
    @Autowired OrderReviewReadService orders;
    @Test void getReviewOptionSnapshots_shouldBoundAndDefensivelyCopyOnlyHistoricalLabels() {
        var result=orders.getReviewOptionSnapshots(java.util.List.of(purchase.item()));
        var expected=tools.jackson.databind.json.JsonMapper.builder().build().readTree(jdbc.queryForObject("SELECT variant_options_snapshot::TEXT FROM order_items WHERE id=?",String.class,purchase.item()));
        tools.jackson.databind.JsonNode actual=tools.jackson.databind.json.JsonMapper.builder().build().valueToTree(result.get(purchase.item()));
        assertThat(actual).isEqualTo(expected);
        assertThatThrownBy(() -> result.put(999L,java.util.Map.of())).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.get(purchase.item()).put("changed","value")).isInstanceOf(UnsupportedOperationException.class);
        assertThat(orders.getReviewOptionSnapshots(java.util.List.of(Long.MAX_VALUE))).isEmpty();
        assertThatThrownBy(() -> orders.getReviewOptionSnapshots(java.util.stream.LongStream.rangeClosed(1,101).boxed().toList())).isInstanceOf(IllegalArgumentException.class);
        assertThat(orders.getReviewOptionSnapshots(java.util.List.of())).isEmpty();
    }
    @Test void getReviewPurchase_shouldReturnOnlyOwnedCompletedProof() {
        assertThat(orders.getReviewPurchase(purchase.owner(), purchase.order(), purchase.item(), true).getProductId()).isEqualTo(purchase.product());
        assertThatThrownBy(() -> orders.getReviewPurchase(purchase.foreign(), purchase.order(), purchase.item(), true))
                .isInstanceOf(com.example.demo.order.exception.OrderNotFoundException.class);
    }
    @Test void getReviewPurchase_shouldAllowHistoricalEditButNotCreation_whenProductLinkIsNull() {
        jdbc.update("UPDATE order_items SET product_id=NULL WHERE id=?", purchase.item());
        assertThat(orders.getReviewPurchase(purchase.owner(), purchase.order(), purchase.item(), false).getOrderItemId()).isEqualTo(purchase.item());
        assertThatThrownBy(() -> orders.getReviewPurchase(purchase.owner(), purchase.order(), purchase.item(), true))
                .isInstanceOf(com.example.demo.order.exception.OrderConflictException.class);
    }
}
