package com.example.demo.review.dto.response;

import java.util.UUID;
import lombok.Value;

@Value
public class ReviewImageResponse {
    UUID id;
    String finalUrl;
}
