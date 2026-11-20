package com.example.demo.checkout.exception;

import com.example.demo.checkout.dto.response.SkippedCheckoutItem;
import com.example.demo.common.exception.ApplicationException;
import java.util.List;
import lombok.Getter;

@Getter
public class NoEligibleCheckoutItemsException extends ApplicationException {
    private final List<SkippedCheckoutItem> skippedItems;

    public NoEligibleCheckoutItemsException(List<SkippedCheckoutItem> skippedItems) {
        super(422, "No eligible selected cart items");
        this.skippedItems = List.copyOf(skippedItems);
    }

    @Override public String getCode() { return "NO_ELIGIBLE_CHECKOUT_ITEMS"; }
    @Override public java.util.Map<String,Object> getProperties() { return java.util.Map.of("skippedItems",skippedItems); }
}
