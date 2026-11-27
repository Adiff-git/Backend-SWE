package com.example.demo.cart.converter;

import com.example.demo.cart.dto.response.CartItemResponse;
import com.example.demo.cart.dto.response.CartReadSeed;
import com.example.demo.cart.dto.response.CartResponse;
import com.example.demo.cart.dto.response.PreparedCart;
import com.example.demo.cart.entity.CartItem;
import com.example.demo.catalog.dto.response.CartVariantState;
import com.example.demo.catalog.dto.response.SellingPrice;
import java.math.BigInteger;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class CartConverter {
    public CartReadSeed toSeed(CartItem item) {
        return new CartReadSeed(item.getId(), item.getProductId(), item.getProductVariantId(), item.getQuantity(),
                item.isSelected(), item.getAddedAt(), item.getPublicProductName(), item.getPublicSku(), item.getPublicOptions());
    }

    public CartResponse toResponse(PreparedCart cart) {
        List<CartItemResponse> items = cart.getItems().stream().map(seed -> {
            CartVariantState state = cart.getStates().get(seed.getVariantId());
            boolean published = state != null && state.isPublished();
            boolean purchasable = !cart.getIneligibleReasons().containsKey(seed.getId());
            return new CartItemResponse(seed.getId(), seed.getProductId(), seed.getVariantId(),
                    published ? state.getProductName() : seed.getPublicProductName(),
                    published ? state.getSku() : seed.getPublicSku(),
                    published ? state.getOptions() : seed.getPublicOptions(),
                    published ? state.getPrimaryImageUrl() : null,
                    published ? money(state.getAmount()) : null, seed.getQuantity(), seed.isSelected(), purchasable,
                    cart.getIneligibleReasons().get(seed.getId()), money(cart.getSubtotals().get(seed.getId())),
                    published ? state.getAvailability() : null, purchasable ? state.getPricing() : null);
        }).toList();
        SellingPrice total = money(cart.getSelectedSubtotal());
        return new CartResponse(items, cart.getSelectedItemCount(), cart.getSelectedTotalQuantity(), total, total);
    }

    private SellingPrice money(BigInteger amount) { return amount == null ? null : new SellingPrice(amount, "VND"); }
}
