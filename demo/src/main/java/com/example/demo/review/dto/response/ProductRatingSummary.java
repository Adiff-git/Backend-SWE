package com.example.demo.review.dto.response;

import java.math.BigDecimal;
import lombok.Value;
@Value
public class ProductRatingSummary {
    long reviewCount;
    BigDecimal averageRating;
}
