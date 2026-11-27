package com.example.demo.checkout.dto.response;
import com.example.demo.catalog.dto.response.SellingPrice;
import java.time.Instant;
import com.example.demo.order.dto.response.DirectSaleSnapshot;
import lombok.Getter;
import lombok.EqualsAndHashCode;
@Getter @EqualsAndHashCode(callSuper=true)
public final class CheckoutItemReceiptV3 extends CheckoutItemReceipt {
    private final SellingPrice originalUnitPriceSnapshot;
    private final SellingPrice directUnitDiscountSnapshot;
    private final DirectSaleSnapshot directSaleSnapshot;
    private final Instant pricingInstantSnapshot;
    public CheckoutItemReceiptV3(CheckoutItemReceipt item, SellingPrice original, SellingPrice discount,
            DirectSaleSnapshot sale, Instant at) {
        super(item.getId(), item.getProductNameSnapshot(), item.getVariantSkuSnapshot(), item.getVariantOptionsSnapshot(),
                item.getQuantity(), item.getUnitPriceSnapshot(), item.getLineTotal());
        this.originalUnitPriceSnapshot=original; this.directUnitDiscountSnapshot=discount;
        this.directSaleSnapshot=sale; this.pricingInstantSnapshot=at;
    }
}
