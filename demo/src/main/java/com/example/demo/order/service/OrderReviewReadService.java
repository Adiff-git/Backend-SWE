package com.example.demo.order.service;

import com.example.demo.order.dto.response.ReviewPurchaseSeed;

public interface OrderReviewReadService {
    java.util.Map<Long,java.util.Map<String,String>> getReviewOptionSnapshots(java.util.Collection<Long> itemIds);
    ReviewPurchaseSeed getReviewPurchase(Long actor, Long order, Long item, boolean creating);
    Long getReviewOrderId(Long actor, Long item);
}
