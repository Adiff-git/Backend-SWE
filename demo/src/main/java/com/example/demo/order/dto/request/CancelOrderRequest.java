package com.example.demo.order.dto.request;

import jakarta.validation.constraints.AssertTrue;
import lombok.Value;

@Value
public class CancelOrderRequest {
    private static final int MAX_REASON_CODE_POINTS = 500;
    String reason;

    public CancelOrderRequest(String reason) {
        String normalized = reason == null ? null : reason.strip();
        this.reason = normalized == null || normalized.isEmpty() ? null : normalized;
    }

    @AssertTrue(message = "Cancellation reason exceeds 500 Unicode code points")
    public boolean isReasonValid() {
        return reason == null || reason.codePointCount(0,reason.length()) <= MAX_REASON_CODE_POINTS;
    }
}
