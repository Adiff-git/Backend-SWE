package com.example.demo.cart.dto.response;

import com.example.demo.catalog.dto.response.SellingPrice;
import java.util.List;
import lombok.Value;

@Value
public class CartResponse {
    List<CartItemResponse> items;
    long selectedItemCount;
    long selectedTotalQuantity;
    SellingPrice selectedSubtotal;
    SellingPrice finalTotal;

    public CartResponse(List<CartItemResponse> items, long selectedItemCount, long selectedTotalQuantity,
            SellingPrice selectedSubtotal, SellingPrice finalTotal) {
        this.items = List.copyOf(items);
        this.selectedItemCount = selectedItemCount;
        this.selectedTotalQuantity = selectedTotalQuantity;
        this.selectedSubtotal = selectedSubtotal;
        this.finalTotal = finalTotal;
    }
}
