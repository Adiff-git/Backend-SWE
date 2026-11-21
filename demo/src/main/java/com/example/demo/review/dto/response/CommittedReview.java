package com.example.demo.review.dto.response;

import com.example.demo.review.entity.ProductReview;
import java.util.List;
import java.util.UUID;
import lombok.Value;

/** Fully materialized mutation result; no ORM collections escape the writer transaction. */
@Value
public class CommittedReview {
    ProductReview review;
    List<UUID> imageIds;
}
