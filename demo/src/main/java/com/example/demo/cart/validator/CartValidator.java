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
    public static final int MAX_QUANTITY = 1000;

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
