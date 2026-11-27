package com.example.demo.cart.service.impl;
import com.example.demo.cart.service.CartWalletReadService;
import com.example.demo.cart.service.CartPreviewService;
import com.example.demo.cart.repository.CartRepository;
import com.example.demo.cart.repository.CartItemRepository;
import com.example.demo.catalog.service.CatalogCartReadService;
import java.math.BigInteger;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service @RequiredArgsConstructor
public class CartWalletReadServiceImpl implements CartWalletReadService {
    private final CartRepository carts;private final CartItemRepository items;private final CatalogCartReadService catalog;private final CartPreviewService previews; private final java.time.Clock clock;
    @Override @Transactional(readOnly=true)
    public BigInteger getEligibleSubtotal(Long owner) { return getPricingContext(owner).getSubtotal(); }
    @Override @Transactional(readOnly=true)
    public com.example.demo.cart.dto.response.CartWalletContext getPricingContext(Long owner) {
        var cart=carts.findByUserId(owner).orElse(null);
        var rows=cart==null ? List.<com.example.demo.cart.entity.CartItem>of() : items.findByCartIdOrderByAddedAtDescIdAsc(cart.getId());
        var prepared=catalog.preparePricing(rows.stream().map(com.example.demo.cart.entity.CartItem::getProductVariantId).toList(),clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS));
        var states=catalog.getVariantStates(prepared);
        var at=prepared.getPricingInstant();
        return new com.example.demo.cart.dto.response.CartWalletContext(previews.getCartResponse(null,rows,states,false,at).getSelectedSubtotal().getAmount(),at);
    }
}
