package com.example.demo.inventory.converter;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.demo.inventory.dto.response.AdminInventoryVariant.StockStatus;
import org.junit.jupiter.api.Test;

class AdminInventoryStockStatusConverterTest {
    @Test void distinguishesUnavailableFromKnownZeroAndPositiveStock() {
        assertThat(AdminInventoryStockStatusConverter.fromAvailable(null)).isEqualTo(StockStatus.UNAVAILABLE);
        assertThat(AdminInventoryStockStatusConverter.fromAvailable(0L)).isEqualTo(StockStatus.OUT_OF_STOCK);
        assertThat(AdminInventoryStockStatusConverter.fromAvailable(Long.MAX_VALUE)).isEqualTo(StockStatus.IN_STOCK);
    }
}
