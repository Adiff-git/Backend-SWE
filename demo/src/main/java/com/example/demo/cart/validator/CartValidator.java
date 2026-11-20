package com.example.demo.cart.validator;

import com.example.demo.cart.exception.CartItemUnavailableException;
import com.example.demo.cart.exception.CartQuantityLimitException;
import com.example.demo.cart.exception.CartVariantSwitchException;
import com.example.demo.cart.exception.InvalidCartRequestException;
import com.example.demo.cart.exception.CartItemNotFoundException;
import com.example.demo.cart.entity.CartItem;
import com.example.demo.catalog.dto.response.CartVariantState;
import com.example.demo.catalog.exception.ProductNotFoundException;
import com.example.demo.common.identity.IdentityUnavailableException;
import java.util.UUID;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class CartValidator {
    public static final int MIN_QUANTITY = 1;
    public static final int MAX_QUANTITY = 999;

    public String ineligibleReason(CartVariantState state, int quantity) {
        if (state == null || !state.isPublished()) return "PRODUCT_UNAVAILABLE";
        if (state.getAmount() == null) return "PRICE_UNAVAILABLE";
        if (state.getAvailability() == null || "INVENTORY_UNAVAILABLE".equals(state.getAvailability().getStatus())) return "INVENTORY_UNAVAILABLE";
        if (state.getAvailability().getAvailableQuantity() < quantity) return "INSUFFICIENT_STOCK";
        return null;
    }

    public void stockEligible(CartVariantState state, int quantity) {
        if (state == null || state.getAvailability() == null
                || "INVENTORY_UNAVAILABLE".equals(state.getAvailability().getStatus())
                || state.getAvailability().getAvailableQuantity() < quantity) {
            throw new com.example.demo.cart.exception.CartStockConflictException();
        }
    }

    public void checkoutConsumption(Long owner, com.example.demo.cart.dto.response.CheckoutCartSeed seed,
            List<com.example.demo.cart.dto.response.CheckoutCartSeed.Row> included) {
        owner(owner);
        if (seed == null || !java.util.Objects.equals(owner,seed.getOwnerId()) || seed.getCartId() == null
                || included == null || included.stream().map(com.example.demo.cart.dto.response.CheckoutCartSeed.Row::getId).distinct().count() != included.size()
                || !seed.getRows().containsAll(included) || included.stream().anyMatch(row -> !row.isSelected()))
            throw new com.example.demo.cart.exception.CartConflictException();
    }

    public void owner(Long owner) {
        if (owner == null || owner <= 0) throw new IdentityUnavailableException();
    }

    public int quantity(Integer quantity) {
        if (quantity == null || quantity < MIN_QUANTITY || quantity > MAX_QUANTITY) throw new InvalidCartRequestException();
        return quantity;
    }

    public int addedQuantity(int existing, int requested) {
        quantity(requested);
        long result = (long) existing + requested;
        if (result < MIN_QUANTITY || result > MAX_QUANTITY) throw new CartQuantityLimitException();
        return (int) result;
    }

    public void eligible(CartVariantState target) {
        if (target == null || !target.isPublished()) throw new ProductNotFoundException();
        if (!target.isPurchasable()) throw new CartItemUnavailableException();
    }

    public void selectable(CartVariantState target) {
        if (target == null || !target.isPurchasable()) throw new CartItemUnavailableException();
    }

    public void sameProduct(UUID sourceProduct, UUID targetProduct) {
        if (!sourceProduct.equals(targetProduct)) throw new CartVariantSwitchException();
    }

    public boolean selection(Boolean selected) {
        if (selected == null) throw new InvalidCartRequestException();
        return selected;
    }

    public void ownedItemExists(boolean exists) {
        if (!exists) throw new CartItemNotFoundException();
    }

    public CartItem ownedItem(List<CartItem> ownedRows, Long itemId) {
        return ownedRows.stream().filter(row -> row.getId().equals(itemId)).findFirst().orElseThrow(CartItemNotFoundException::new);
    }
}
