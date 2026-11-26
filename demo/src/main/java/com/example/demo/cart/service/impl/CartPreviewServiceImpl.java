package com.example.demo.cart.service.impl;
import com.example.demo.cart.service.CartPreviewService;
import com.example.demo.cart.entity.Cart;
import com.example.demo.cart.entity.CartItem;
import com.example.demo.cart.dto.response.CartResponse;
import com.example.demo.cart.dto.response.CartReadSeed;
import com.example.demo.cart.calculator.CartCalculator;
import com.example.demo.cart.converter.CartConverter;
import com.example.demo.cart.validator.CartValidator;
import com.example.demo.catalog.dto.response.CartVariantState;
import com.example.demo.catalog.dto.response.SellingPrice;
import com.example.demo.voucher.service.VoucherService;
import com.example.demo.voucher.exception.VoucherNotFoundException;
import com.example.demo.voucher.exception.VoucherNotApplicableException;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
@Service @RequiredArgsConstructor
public class CartPreviewServiceImpl implements CartPreviewService {
    private final CartCalculator calculator; private final CartConverter converter;
    private final CartValidator validator; private final VoucherService vouchers; private final Clock clock;
    @Override public CartResponse getCartResponse(Cart cart, List<CartItem> rows, Map<UUID,CartVariantState> states, boolean rejectInvalid) {
        var at=states.values().stream().map(CartVariantState::getPricing).filter(java.util.Objects::nonNull)
                .map(com.example.demo.catalog.dto.response.VariantPricing::getPricedAt).findFirst()
                .orElseGet(() -> clock.instant().truncatedTo(ChronoUnit.MICROS));
        return getCartResponse(cart,rows,states,rejectInvalid,at);
    }
    @Override public CartResponse getCartResponse(Cart cart,List<CartItem> rows,Map<UUID,CartVariantState> states,boolean rejectInvalid,java.time.Instant at) {
        java.util.Objects.requireNonNull(at,"Prepared pricing instant is required");
        List<CartReadSeed> seeds = rows.stream().map(converter::toSeed).toList();
        Map<Long,String> reasons = new HashMap<>();
        for (CartReadSeed seed : seeds) {
            String reason = validator.ineligibleReason(states.get(seed.getVariantId()), seed.getQuantity());
            if (reason != null) reasons.put(seed.getId(), reason);
        }
        var response = converter.toResponse(calculator.calculate(seeds, states, reasons));
        if (cart == null || cart.getVoucherEntitlementId() == null) return response;
        var preview = vouchers.getPreview(cart.getUserId(), cart.getVoucherEntitlementId(), response.getSelectedSubtotal().getAmount(), at);
        if (preview.getReason() != null) {
            if (rejectInvalid) {
                if ("VOUCHER_UNAVAILABLE".equals(preview.getReason())) throw new VoucherNotFoundException();
                throw new VoucherNotApplicableException(preview.getReason());
            }
            cart.selectVoucher(null);
        }
        return new CartResponse(response.getItems(), response.getSelectedItemCount(), response.getSelectedTotalQuantity(), response.getSelectedSubtotal(),
            new SellingPrice(response.getSelectedSubtotal().getAmount().subtract(preview.getDiscount()), "VND"),
            new SellingPrice(preview.getDiscount(), "VND"), preview.getVoucher(), preview.getReason());
    }
}
