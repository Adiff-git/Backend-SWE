package com.example.demo.cart.dto.response;

import com.example.demo.catalog.dto.response.SellingPrice;
import java.util.List;
import lombok.Value;

@Value
public class CartResponse {
    List<CartItemResponse> items;
    long totalItemCount;
    long selectedItemCount;
    long selectedTotalQuantity;
    SellingPrice selectedSubtotal;
    SellingPrice finalTotal;
    SellingPrice voucherDiscount;
    com.example.demo.voucher.dto.response.VoucherSummary voucher;
    String voucherSelectionClearedReason;

    public CartResponse(List<CartItemResponse> items, long selectedItemCount, long selectedTotalQuantity,
            SellingPrice selectedSubtotal, SellingPrice finalTotal) {
        this(items, selectedItemCount, selectedTotalQuantity, selectedSubtotal, finalTotal,
                new SellingPrice(java.math.BigInteger.ZERO, "VND"), null, null);
    }
    public CartResponse(List<CartItemResponse> items, long selectedItemCount, long selectedTotalQuantity,
            SellingPrice selectedSubtotal, SellingPrice finalTotal, SellingPrice voucherDiscount,
            com.example.demo.voucher.dto.response.VoucherSummary voucher, String clearedReason) {
        this.voucherDiscount = voucherDiscount;
        this.voucher = voucher;
        this.voucherSelectionClearedReason = clearedReason;
        this.items = List.copyOf(items);
        this.totalItemCount = this.items.size();
        this.selectedItemCount = selectedItemCount;
        this.selectedTotalQuantity = selectedTotalQuantity;
        this.selectedSubtotal = selectedSubtotal;
        this.finalTotal = finalTotal;
    }
}
