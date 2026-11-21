package com.example.demo.review.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity @Table(name = "product_review_images") @Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductReviewImage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, updatable = false) private Long reviewId;
    @Column(nullable = false, updatable = false) private UUID imageId;
    @Column(nullable = false) private Integer displayOrder;
    public ProductReviewImage(Long review, UUID image, int position) {
        this.reviewId = review; this.imageId = image; this.displayOrder = position;
    }
}
