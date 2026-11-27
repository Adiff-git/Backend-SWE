package com.example.demo.review.dto.response;

import java.math.BigDecimal;
import lombok.Value;
@Value
@lombok.AllArgsConstructor
public class ProductRatingSummary {
    long reviewCount;
    BigDecimal averageRating;
    java.util.Map<String,Long> ratingDistribution;
    public ProductRatingSummary(long count,BigDecimal average) {
        this(count,average,java.util.Map.of("1",0L,"2",0L,"3",0L,"4",0L,"5",0L));
    }
}
