package com.example.demo.wishlist.service;

import com.example.demo.wishlist.dto.response.WishlistItemResponse;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Internal mutation outcome; the controller sends only item as the response body. */
@Getter
@RequiredArgsConstructor
public class AddWishlistResult {
    private final boolean created;
    private final WishlistItemResponse item;
}
