package com.example.demo.wishlist.dto.response;

import com.example.demo.catalog.dto.response.ProductSummary;
import java.time.Instant;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class WishlistItemResponse {
    private final Instant addedAt;
    private final ProductSummary product;
}
