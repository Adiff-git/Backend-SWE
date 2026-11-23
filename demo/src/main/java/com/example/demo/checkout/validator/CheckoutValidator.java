package com.example.demo.checkout.validator;

import com.example.demo.cart.dto.response.CheckoutCartSeed;
import com.example.demo.catalog.dto.response.CheckoutVariantState;
import com.example.demo.catalog.entity.ProductStatus;
import com.example.demo.checkout.dto.request.CheckoutRequest;
import com.example.demo.checkout.dto.response.SkippedCheckoutItem;
import com.example.demo.checkout.entity.CheckoutReceipt;
import com.example.demo.checkout.exception.CheckoutKeyConflictException;
import com.example.demo.checkout.exception.InvalidCheckoutRequestException;
import com.example.demo.common.identity.IdentityUnavailableException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

@Component
public class CheckoutValidator {
    public SkippedCheckoutItem.Reason stockReason(CheckoutCartSeed.Row row,
            com.example.demo.inventory.dto.response.InventoryAvailability availability) {
        if (availability == null || "INVENTORY_UNAVAILABLE".equals(availability.getStatus())) return SkippedCheckoutItem.Reason.INVENTORY_UNAVAILABLE;
        return availability.getAvailableQuantity() < row.getQuantity() ? SkippedCheckoutItem.Reason.INSUFFICIENT_STOCK : null;
    }
    public void request(Long owner, String key, CheckoutRequest request) {
        if (owner == null || owner <= 0) throw new IdentityUnavailableException();
        if (key == null || !key.matches("[A-Za-z0-9_-]{16,128}") || request == null
                || request.getAddressId() == null || request.getAddressId() <= 0) throw new InvalidCheckoutRequestException();
    }

    public void matching(CheckoutReceipt receipt, Long address) {
        if (!address.equals(receipt.getRequestAddressId()) || !fingerprint(address).equals(receipt.getRequestFingerprint()))
            throw new CheckoutKeyConflictException();
        if ((receipt.getReceiptVersion() != 1 && receipt.getReceiptVersion() != 2) || receipt.getResponseStatus() != 201) throw new IllegalStateException("Unsupported stored checkout receipt");
    }

    public long fenceKey(Long owner, String key) { return ByteBuffer.wrap(digest("checkout:v1:"+owner+":"+key)).getLong(); }
    public String fingerprint(Long address) { return HexFormat.of().formatHex(digest("checkout:v1:addressId="+address)); }

    private byte[] digest(String value) {
        try { return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    public SkippedCheckoutItem.Reason reason(CheckoutCartSeed.Row row, CheckoutVariantState state) {
        if (state == null || !state.isEnabled()) return SkippedCheckoutItem.Reason.VARIANT_UNAVAILABLE;
        if (state.getProductId() == null || state.getStatus() == null) return SkippedCheckoutItem.Reason.PRODUCT_UNAVAILABLE;
        if (state.getStatus() != ProductStatus.PUBLISHED) return SkippedCheckoutItem.Reason.PRODUCT_NOT_FOR_SALE;
        if (row.getQuantity() < 1 || row.getQuantity() > 999) return SkippedCheckoutItem.Reason.INVALID_QUANTITY;
        if (state.getPrice() == null || state.getPrice().signum() <= 0) return SkippedCheckoutItem.Reason.INVALID_PRICE;
        try { state.getPrice().toBigIntegerExact(); }
        catch (ArithmeticException invalid) { return SkippedCheckoutItem.Reason.INVALID_PRICE; }
        return null;
    }
}
