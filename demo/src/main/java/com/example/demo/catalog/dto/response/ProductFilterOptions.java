package com.example.demo.catalog.dto.response;

import java.util.List;
import java.math.BigDecimal;
import io.swagger.v3.oas.annotations.media.Schema;

public record ProductFilterOptions(String key, String type, String unit, List<Option> options) {
    public record Option(@Schema(oneOf = {BigDecimal.class, String.class}) Object value, long productCount) { }
}
