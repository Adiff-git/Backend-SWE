package com.example.demo.inventory.dto.response;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.Value;
import lombok.experimental.Accessors;

import java.util.List;

@Value
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class AdminInventoryPage<T> {
    List<T> items;
    int page;
    int size;
    long totalItems;
    long totalPages;

    public static <T> AdminInventoryPage<T> of(List<T> items, int page, int size, long total) {
        return new AdminInventoryPage<>(List.copyOf(items), page, size, total,
                total / size + (total % size == 0 ? 0 : 1));
    }
}
