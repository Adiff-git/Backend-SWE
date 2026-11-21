package com.example.demo.review.dto.request;

import com.example.demo.review.exception.InvalidReviewRequestException;
import java.util.Set;
import lombok.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.util.MultiValueMap;

@Value
public class ReviewListRequest {
    int page;
    int size;
    String sort;
    public static ReviewListRequest parse(MultiValueMap<String, String> parameters) {
        if (parameters.entrySet().stream().anyMatch(e -> !Set.of("page", "size", "sort").contains(e.getKey()) || e.getValue().size() != 1))
            throw new InvalidReviewRequestException();
        int page = number(parameters.getFirst("page"), 0);
        int size = number(parameters.getFirst("size"), 20);
        String sort = parameters.getFirst("sort") == null ? "createdAt,desc" : parameters.getFirst("sort");
        if (page < 0 || size < 1 || size > 100 || !Set.of("createdAt,asc", "createdAt,desc", "rating,asc", "rating,desc").contains(sort))
            throw new InvalidReviewRequestException();
        return new ReviewListRequest(page, size, sort);
    }
    private static int number(String value, int fallback) {
        if (value == null) return fallback;
        if (!value.matches("0|[1-9][0-9]*")) throw new InvalidReviewRequestException();
        try { return Integer.parseInt(value); } catch (NumberFormatException invalid) { throw new InvalidReviewRequestException(); }
    }
    public Pageable pageable() {
        String[] parts = sort.split(",");
        return PageRequest.of(page, size, Sort.by(Sort.Direction.fromString(parts[1]), parts[0]).and(Sort.by(Sort.Direction.DESC, "id")));
    }
}
