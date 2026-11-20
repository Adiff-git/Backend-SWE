package com.example.demo.checkout.dto.response;
import com.example.demo.catalog.dto.response.SellingPrice;
import com.example.demo.voucher.dto.response.VoucherSnapshot;
import lombok.Getter;
import lombok.EqualsAndHashCode;
@Getter @EqualsAndHashCode(callSuper=true)
public final class CheckoutOrderReceiptV2 extends CheckoutOrderReceipt {
    private final SellingPrice voucherDiscount;
    private final VoucherSnapshot voucher;
    public CheckoutOrderReceiptV2(CheckoutOrderReceipt original, SellingPrice discount, VoucherSnapshot voucher) {
        super(original.getId(),original.getOrderNumber(),original.getPlacedAt(),original.getStatus(),original.getRecipientNameSnapshot(),
            original.getRecipientPhoneSnapshot(),original.getDeliveryAddressSnapshot(),original.getItems(),original.getMerchandiseTotal(),original.getPayableTotal());
        this.voucherDiscount=discount; this.voucher=voucher;
    }
}
