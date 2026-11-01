package com.example.demo.wishlist.service;

import java.util.UUID;
import com.example.demo.wishlist.dto.response.WishlistPage;

public interface WishlistService {
    AddWishlistResult addProduct(Long ownerId, UUID productId);

    WishlistPage listWishlist(Long ownerId, int page, int size);

    void removeProduct(Long ownerId, UUID productId);
}
