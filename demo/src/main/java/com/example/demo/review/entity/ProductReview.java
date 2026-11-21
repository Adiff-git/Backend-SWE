package com.example.demo.review.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity @Table(name = "product_reviews") @Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductReview {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, updatable = false) private Long orderItemId;
    @Column(nullable = false, updatable = false) private Long authorId;
    @Column(nullable = false, updatable = false) private UUID productId;
    @Column(nullable = false) private Short rating;
    @Column(columnDefinition = "TEXT") private String comment;
    @Column(nullable = false, updatable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;

    public ProductReview(Long item, Long author, UUID product, int rating, String comment, Instant at) {
        this.orderItemId = item; this.authorId = author; this.productId = product;
        this.rating = (short) rating; this.comment = comment; this.createdAt = at; this.updatedAt = at;
    }
    public void edit(int rating, String comment, Instant at) {
        this.rating = (short) rating; this.comment = comment; this.updatedAt = at;
    }
}
