package com.example.demo.wishlist.converter;

import com.example.demo.catalog.dto.response.ProductSummary;
import com.example.demo.wishlist.dto.response.WishlistItemResponse;
import com.example.demo.wishlist.dto.response.WishlistPage;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

/** Wraps the owning catalog domain's already prepared summary without fetching. */
@Component
public class WishlistConverter {
    public WishlistItemResponse toItem(Instant addedAt, ProductSummary product) {
        return new WishlistItemResponse(addedAt, product);
    }

    public WishlistPage toPage(List<WishlistItemResponse> items, int page, int size, long totalItems, int totalPages) {
        return new WishlistPage(List.copyOf(items), page, size, totalItems, totalPages);
    }
}
