package com.example.demo.inventory.converter;

import com.example.demo.inventory.dto.response.AdminInventoryVariant.StockStatus;

public final class AdminInventoryStockStatusConverter {
    private AdminInventoryStockStatusConverter() {}

    public static StockStatus fromAvailable(Long available) {
        if (available == null) return StockStatus.UNAVAILABLE;
        return available == 0 ? StockStatus.OUT_OF_STOCK : StockStatus.IN_STOCK;
    }
}
