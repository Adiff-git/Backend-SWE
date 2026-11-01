package com.example.demo.wishlist.dto.response;

import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class WishlistPage {
    private final List<WishlistItemResponse> items;
    private final int page;
    private final int size;
    private final long totalItems;
    private final int totalPages;
}
