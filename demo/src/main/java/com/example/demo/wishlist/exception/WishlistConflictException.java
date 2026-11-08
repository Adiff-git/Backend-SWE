package com.example.demo.wishlist.exception;

import com.example.demo.common.exception.ApplicationException;

public final class WishlistConflictException extends ApplicationException {
    public WishlistConflictException() {
        super(409, "Wishlist changed concurrently; you may retry");
    }
}
