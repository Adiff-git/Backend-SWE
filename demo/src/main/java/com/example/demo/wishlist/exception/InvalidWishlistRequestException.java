package com.example.demo.wishlist.exception;

import com.example.demo.common.exception.ApplicationException;

public final class InvalidWishlistRequestException extends ApplicationException {
    public InvalidWishlistRequestException(String message) {
        super(400, message);
    }
}
