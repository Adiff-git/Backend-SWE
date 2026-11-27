package com.example.demo.review.dto.request;

import com.example.demo.review.exception.InvalidReviewRequestException;
import java.util.Set;
import lombok.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.util.MultiValueMap;

@Value
@lombok.AllArgsConstructor
public class ReviewListRequest {
    int page;
    int size;
    String sort;
    Integer rating;
    Boolean hasImages;
    Boolean hasComment;
    public ReviewListRequest(int page,int size,String sort) { this(page,size,sort,null,null,null); }
    public static ReviewListRequest parse(MultiValueMap<String, String> parameters) {
        if (parameters.entrySet().stream().anyMatch(e -> !Set.of("page", "size", "sort", "rating", "hasImages", "hasComment").contains(e.getKey()) || e.getValue()==null || e.getValue().size() != 1 || e.getValue().getFirst()==null))
            throw new InvalidReviewRequestException();
        int page = number(parameters.getFirst("page"), 0);
        int size = number(parameters.getFirst("size"), 20);
        String sort = parameters.getFirst("sort") == null ? "createdAt,desc" : parameters.getFirst("sort");
        if (page < 0 || size < 1 || size > 100 || !Set.of("createdAt,asc", "createdAt,desc", "rating,asc", "rating,desc").contains(sort))
            throw new InvalidReviewRequestException();
        String rawRating=parameters.getFirst("rating");
        if(rawRating!=null && !rawRating.matches("[1-5]")) throw new InvalidReviewRequestException();
        return new ReviewListRequest(page,size,sort,rawRating==null ? null : Integer.valueOf(rawRating),
                flag(parameters.getFirst("hasImages")),flag(parameters.getFirst("hasComment")));
    }
    private static Boolean flag(String value) {
        if(value==null) return null;
        if(!value.equals("true") && !value.equals("false")) throw new InvalidReviewRequestException();
        return Boolean.valueOf(value);
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
