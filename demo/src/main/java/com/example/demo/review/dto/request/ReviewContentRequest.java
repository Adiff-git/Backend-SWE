package com.example.demo.review.dto.request;

import com.example.demo.review.exception.InvalidReviewRequestException;
import com.example.demo.user.validator.CodePointLength;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import tools.jackson.databind.JsonNode;

@Getter
@com.fasterxml.jackson.annotation.JsonAutoDetect(fieldVisibility = com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.NONE,
        getterVisibility = com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.NONE)
public abstract class ReviewContentRequest {
    @Min(1) @Max(5) private Integer rating;
    @CodePointLength(max = 2000) private String comment;
    @Size(max = 10) private List<UUID> imageIds = List.of();
    private boolean ratingPresent;
    private boolean commentPresent;
    private boolean imageIdsPresent;

    public void setRating(JsonNode value) {
        ratingPresent = true;
        if (value == null || value.isNull()) throw new InvalidReviewRequestException();
        if (!value.isIntegralNumber() || value.bigIntegerValue().compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) > 0
                || value.bigIntegerValue().compareTo(BigInteger.valueOf(Integer.MIN_VALUE)) < 0) throw new InvalidReviewRequestException();
        rating = value.intValue();
    }
    public void setComment(JsonNode value) {
        commentPresent = true;
        if (value == null || value.isNull()) { comment = null; return; }
        if (!value.isString()) throw new InvalidReviewRequestException();
        String stripped = value.asString().strip();
        // PostgreSQL text cannot represent NUL; reject it as input, never as a persistence error.
        if (stripped.indexOf('\0') >= 0) throw new InvalidReviewRequestException();
        comment = stripped.isBlank() ? null : stripped;
    }
    public void setImageIds(JsonNode value) {
        imageIdsPresent = true;
        if (value == null || !value.isArray() || value.size() > 10) throw new InvalidReviewRequestException();
        List<UUID> ids = new ArrayList<>();
        for (JsonNode element : value) {
            if (!element.isString() || !element.asString().matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
                throw new InvalidReviewRequestException();
            ids.add(UUID.fromString(element.asString()));
        }
        if (new HashSet<>(ids).size() != ids.size()) throw new InvalidReviewRequestException();
        imageIds = List.copyOf(ids);
    }
    @JsonAnySetter public void rejectUnknown(String name, JsonNode value) { throw new InvalidReviewRequestException(); }
}
