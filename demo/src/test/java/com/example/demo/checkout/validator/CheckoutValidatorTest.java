package com.example.demo.checkout.validator;

import com.example.demo.checkout.dto.response.SkippedCheckoutItem;
import com.example.demo.cart.dto.response.CheckoutCartSeed;
import com.example.demo.catalog.dto.response.CheckoutVariantState;
import com.example.demo.catalog.entity.ProductStatus;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CheckoutValidatorTest {
    private final CheckoutValidator validator = new CheckoutValidator();

    @Test void stockReason_shouldRequireFullQuantityAndDistinguishMissingWithoutCapping() {
        CheckoutCartSeed.Row row = new CheckoutCartSeed.Row(1L, UUID.randomUUID(), UUID.randomUUID(), 3, true);
        assertThat(validator.stockReason(row, new com.example.demo.inventory.dto.response.InventoryAvailability(3, "IN_STOCK"))).isNull();
        assertThat(validator.stockReason(row, new com.example.demo.inventory.dto.response.InventoryAvailability(2, "IN_STOCK")))
                .isEqualTo(SkippedCheckoutItem.Reason.INSUFFICIENT_STOCK);
        assertThat(validator.stockReason(row, new com.example.demo.inventory.dto.response.InventoryAvailability(0, "OUT_OF_STOCK")))
                .isEqualTo(SkippedCheckoutItem.Reason.INSUFFICIENT_STOCK);
        assertThat(validator.stockReason(row, new com.example.demo.inventory.dto.response.InventoryAvailability(0, "INVENTORY_UNAVAILABLE")))
                .isEqualTo(SkippedCheckoutItem.Reason.INVENTORY_UNAVAILABLE);
    }

    @Test void fingerprint_shouldRepresentCanonicalClientCommandOnly() {
        assertThat(validator.fingerprint(1L)).isEqualTo(com.example.demo.testdata.checkout.CheckoutTestData.ADDRESS_ONE_FINGERPRINT);
        assertThat(validator.fenceKey(1L,"Case_sensitive_123")).isNotEqualTo(validator.fenceKey(1L,"case_sensitive_123"));
        assertThat(validator.fenceKey(1L,"Case_sensitive_123")).isNotEqualTo(validator.fenceKey(2L,"Case_sensitive_123"));
    }

    @Test void reason_shouldAcceptZeroPriceAndQuantityBoundaries() {
        UUID product = UUID.randomUUID();
        UUID variant = UUID.randomUUID();
        CheckoutVariantState state = new CheckoutVariantState(variant,product,ProductStatus.PUBLISHED,BigDecimal.ZERO,"Product","SKU",Map.of());
        assertThat(validator.reason(new CheckoutCartSeed.Row(1L,product,variant,1,true),state)).isNull();
        assertThat(validator.reason(new CheckoutCartSeed.Row(2L,product,variant,999,true),state)).isNull();
    }

    @Test void reason_shouldPreferMissingVariantBeforeInvalidQuantity() {
        assertThat(validator.reason(new CheckoutCartSeed.Row(1L,UUID.randomUUID(),UUID.randomUUID(),0,true),null))
                .isEqualTo(SkippedCheckoutItem.Reason.VARIANT_UNAVAILABLE);
    }

    @Test void reason_shouldUseOneDeterministicBusinessReasonWithoutExceptionBasedSkipping() {
        UUID product=UUID.randomUUID();
        UUID variant=UUID.randomUUID();
        CheckoutCartSeed.Row invalidQuantity=new CheckoutCartSeed.Row(1L,product,variant,0,true);
        assertThat(validator.reason(invalidQuantity,new CheckoutVariantState(variant,product,null,null,null,null,Map.of())))
                .isEqualTo(SkippedCheckoutItem.Reason.PRODUCT_UNAVAILABLE);
        assertThat(validator.reason(invalidQuantity,new CheckoutVariantState(variant,product,ProductStatus.ARCHIVED,null,"Hidden","Hidden",Map.of())))
                .isEqualTo(SkippedCheckoutItem.Reason.PRODUCT_NOT_FOR_SALE);
        assertThat(validator.reason(invalidQuantity,new CheckoutVariantState(variant,product,ProductStatus.PUBLISHED,null,"Product","SKU",Map.of())))
                .isEqualTo(SkippedCheckoutItem.Reason.INVALID_QUANTITY);
        CheckoutCartSeed.Row valid=new CheckoutCartSeed.Row(1L,product,variant,1,true);
        for (BigDecimal price : java.util.Arrays.asList(null,BigDecimal.valueOf(-1),BigDecimal.valueOf(0.5))) {
            assertThat(validator.reason(valid,new CheckoutVariantState(variant,product,ProductStatus.PUBLISHED,price,"Product","SKU",Map.of())))
                    .isEqualTo(SkippedCheckoutItem.Reason.INVALID_PRICE);
        }
        assertThat(validator.reason(new CheckoutCartSeed.Row(1L,product,variant,1000,true),
                new CheckoutVariantState(variant,product,ProductStatus.PUBLISHED,BigDecimal.ZERO,"Product","SKU",Map.of())))
                .isEqualTo(SkippedCheckoutItem.Reason.INVALID_QUANTITY);
    }
}
