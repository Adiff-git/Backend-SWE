package com.example.demo.cart.service.impl;
import com.example.demo.cart.service.CartVoucherTransactionService;
import com.example.demo.cart.service.CartPreviewService;
import com.example.demo.cart.repository.CartRepository;
import com.example.demo.cart.repository.CartItemRepository;
import com.example.demo.cart.validator.CartValidator;
import com.example.demo.cart.dto.response.CartResponse;
import com.example.demo.cart.entity.Cart;
import com.example.demo.cart.entity.CartItem;
import com.example.demo.catalog.service.CatalogCartReadService;
import com.example.demo.voucher.service.VoucherService;
import com.example.demo.voucher.exception.VoucherNotFoundException;
import com.example.demo.voucher.exception.VoucherNotApplicableException;
import com.example.demo.storage.service.PreparedImageUrls;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
@Service @RequiredArgsConstructor @Transactional(isolation=Isolation.READ_COMMITTED)
public class CartVoucherTransactionServiceImpl implements CartVoucherTransactionService {
    private final CartRepository carts; private final CartItemRepository items;
    private final CatalogCartReadService catalog; private final CartPreviewService previews;
    private final CartValidator validator; private final VoucherService vouchers; private final Clock clock;
    @Value("${cart.lock-timeout-ms:5000}") private long lockTimeoutMs;
    @PostConstruct void validateConfiguration() { if (lockTimeoutMs<=0) throw new IllegalArgumentException("Cart lock timeout must be positive"); }
    @Override public CartResponse applyVoucher(Long owner, Long grant, PreparedImageUrls urls) {
        validator.owner(owner);
        if (grant == null || grant<=0) throw new com.example.demo.cart.exception.InvalidCartRequestException();
        carts.setLockTimeout(lockTimeoutMs + "ms");
        Cart cart = carts.findOwnedForUpdate(owner).orElse(null);
        List<CartItem> rows = cart == null ? List.of() : items.findByCartIdOrderByAddedAtDescIdAsc(cart.getId());
        var prepared = catalog.preparePricing(rows.stream().map(CartItem::getProductVariantId).toList(), clock.instant().truncatedTo(ChronoUnit.MICROS));
        var states = catalog.getCartPresentation(prepared, urls);
        // Calculate the merchandise base without evaluating or clearing an old selection.
        CartResponse base = previews.getCartResponse(null, rows, states, false,prepared.getPricingInstant());
        var quote = vouchers.getPreview(owner, grant, base.getSelectedSubtotal().getAmount(), prepared.getPricingInstant());
        if ("VOUCHER_UNAVAILABLE".equals(quote.getReason())) throw new VoucherNotFoundException();
        if (quote.getReason()!=null) throw new VoucherNotApplicableException(quote.getReason());
        if (cart == null) throw new VoucherNotApplicableException("NO_ELIGIBLE_ITEMS");
        cart.selectVoucher(grant);
        var finalStates = catalog.getCartPresentation(prepared, urls);
        return previews.getCartResponse(cart, rows, finalStates, true,prepared.getPricingInstant());
    }
    @Override public void removeVoucher(Long owner) {
        validator.owner(owner); carts.setLockTimeout(lockTimeoutMs + "ms");
        carts.findOwnedForUpdate(owner).ifPresent(cart -> { if (cart.getVoucherEntitlementId()!=null) cart.selectVoucher(null); });
    }
}
