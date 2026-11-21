package com.example.demo.review.dto.response;

import java.util.List;
import lombok.Value;
@Value
public class ReviewPage {
    List<ReviewResponse> items;
    int page;
    int size;
    long totalElements;
    long totalPages;
}
