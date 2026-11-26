package com.example.demo.review.dto.response;

import com.example.demo.user.dto.response.ReviewAuthorSummary;
import java.time.Instant;
import java.util.List;
import lombok.Value;

@Value
@lombok.AllArgsConstructor
public class ReviewResponse {
    Long id;
    int rating;
    String comment;
    List<ReviewImageResponse> images;
    ReviewAuthorSummary author;
    Instant createdAt;
    Instant updatedAt;
    boolean verifiedPurchase;
    java.util.Map<String,String> purchasedOptions;
    public ReviewResponse(Long id,int rating,String comment,List<ReviewImageResponse> images,ReviewAuthorSummary author,
            Instant createdAt,Instant updatedAt,boolean verifiedPurchase) {
        this(id,rating,comment,images,author,createdAt,updatedAt,verifiedPurchase,null);
    }
}
