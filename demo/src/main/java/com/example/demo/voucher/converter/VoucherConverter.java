package com.example.demo.voucher.converter;
import com.example.demo.voucher.dto.response.VoucherPreview;
import com.example.demo.voucher.dto.response.VoucherSummary;
import com.example.demo.voucher.repository.VoucherRepository;
import com.example.demo.catalog.dto.response.SellingPrice;
import java.math.BigInteger;
import org.springframework.stereotype.Component;
@Component
public class VoucherConverter {
    public VoucherPreview toPreview(VoucherRepository.DefinitionRow definition, Long grant, BigInteger base, BigInteger discount, String reason) {
        if (definition == null || reason != null) return new VoucherPreview(null, BigInteger.ZERO, reason);
        return new VoucherPreview(new VoucherSummary(grant, definition.getId(), definition.getCode(), definition.getTitle(),
            definition.getDiscountType(), definition.getDiscountValue(), definition.getMaximumDiscount() == null ? null : money(definition.getMaximumDiscount().toBigIntegerExact()), money(base), money(discount)), discount, null);
    }
    public com.example.demo.voucher.dto.response.VoucherWalletEntry toWallet(VoucherRepository.WalletRow row,String reason) {
        return new com.example.demo.voucher.dto.response.VoucherWalletEntry(row.getWalletEntitlementId(),row.getId(),row.getCode(),row.getTitle(),row.getDescription(),row.getDiscountType(),row.getDiscountValue(),money(row.getMinimumSubtotal().toBigIntegerExact()),row.getMaximumDiscount()==null ? null : money(row.getMaximumDiscount().toBigIntegerExact()),row.getValidFrom(),row.getValidUntil(),row.getEnabled(),row.getGrantedAt(),reason==null,reason);
    }
    private SellingPrice money(BigInteger value) { return new SellingPrice(value, "VND"); }
}
