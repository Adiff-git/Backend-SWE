package com.example.demo.cart.service.impl;

import com.example.demo.cart.dto.response.CheckoutCartSeed;
import com.example.demo.cart.entity.Cart;
import com.example.demo.cart.exception.CartConflictException;
import com.example.demo.cart.repository.CartItemRepository;
import com.example.demo.cart.repository.CartRepository;
import com.example.demo.cart.service.CartCheckoutService;
import com.example.demo.cart.validator.CartValidator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class CartCheckoutServiceImpl implements CartCheckoutService {
    private static final int BATCH_SIZE = 100;
    private final CartRepository carts;
    private final CartItemRepository items;
    private final CartValidator validator;

    @Override
    public CheckoutCartSeed getCheckoutCartSeed(Long owner) {
        Cart cart = carts.findOwnedForUpdate(owner).orElse(null);
        if (cart == null) return new CheckoutCartSeed(owner,null,List.of());
        return new CheckoutCartSeed(owner,cart.getId(),items.findByCartIdAndSelectedTrueOrderByIdAsc(cart.getId()).stream()
                .map(item -> new CheckoutCartSeed.Row(item.getId(),item.getProductId(),item.getProductVariantId(),item.getQuantity(),item.isSelected())).toList(),cart.getVoucherEntitlementId(),cart.getVoucherSelectionRevision());
    }

    @Override
    public void consumeCheckoutItems(Long owner, CheckoutCartSeed seed, List<CheckoutCartSeed.Row> included) {
        validator.checkoutConsumption(owner,seed,included);
        for (int start = 0; start < included.size(); start += BATCH_SIZE) {
            List<CheckoutCartSeed.Row> batch = included.subList(start,Math.min(start+BATCH_SIZE,included.size()));
            if (items.deleteCheckoutRows(owner,seed.getCartId(),batch) != batch.size()) throw new CartConflictException();
        }
    }
    @Override public void clearCheckoutVoucher(Long owner, CheckoutCartSeed seed) {
        validator.owner(owner);
        if (!owner.equals(seed.getOwnerId()) || seed.getSelectedEntitlementId()==null
                || carts.clearCheckoutVoucher(owner,seed.getCartId(),seed.getSelectedEntitlementId(),seed.getSelectionRevision())!=1) throw new CartConflictException();
    }
}
