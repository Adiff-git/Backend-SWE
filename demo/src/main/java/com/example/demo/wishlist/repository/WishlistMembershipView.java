package com.example.demo.wishlist.repository;

import java.time.Instant;
import java.util.UUID;

/** Scalar membership data; no relationship traversal is needed for mapping. */
public interface WishlistMembershipView {
    UUID getProductId();
    Instant getAddedAt();
}
