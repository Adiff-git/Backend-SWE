package com.example.demo.order.service;

import com.example.demo.order.dto.response.ReviewPurchaseSeed;

public interface OrderReviewReadService {
    ReviewPurchaseSeed getReviewPurchase(Long actor, Long order, Long item, boolean creating);
    Long getReviewOrderId(Long actor, Long item);
}
