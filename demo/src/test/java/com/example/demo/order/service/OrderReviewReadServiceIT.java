package com.example.demo.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.demo.config.ReviewIntegrationSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class OrderReviewReadServiceIT extends ReviewIntegrationSupport {
    @Autowired OrderReviewReadService orders;
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
