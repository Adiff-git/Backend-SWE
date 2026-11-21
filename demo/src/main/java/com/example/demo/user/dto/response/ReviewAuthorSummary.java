package com.example.demo.user.dto.response;

import com.example.demo.media.dto.response.ImageAccessResponse;
import lombok.Value;

@Value
public class ReviewAuthorSummary {
    String displayName;
    ImageAccessResponse profileImage;
    public static ReviewAuthorSummary formerCustomer() { return new ReviewAuthorSummary("Former customer", null); }
}
