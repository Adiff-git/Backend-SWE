package com.example.demo.wishlist.validator;

import com.example.demo.common.identity.IdentityUnavailableException;
import com.example.demo.wishlist.exception.InvalidWishlistRequestException;
import org.springframework.stereotype.Component;

@Component
public class WishlistValidator {
    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    public void validateOwner(Long ownerId) {
        if (ownerId == null) {
            throw new IdentityUnavailableException();
        }
    }

    public void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_SIZE) {
            throw new InvalidWishlistRequestException("Invalid wishlist pagination");
        }
    }
}
