package com.example.demo.review.repository;

import com.example.demo.review.dto.request.ReviewListRequest;
import com.example.demo.review.entity.ProductReview;
import com.example.demo.review.exception.InvalidReviewRequestException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

/** Identical scoped predicates for count and page; image presence never multiplies rows. */
@RequiredArgsConstructor
public class ProductReviewQueryRepositoryImpl implements ProductReviewQueryRepository {
    private final EntityManager entities;
    // Fixed Unicode White_Space set, matching the versioned catalog identity boundary.
    private static final String WHITE_SPACE = java.util.stream.IntStream.concat(
            java.util.stream.IntStream.of(9,10,11,12,13,32,133,160,5760),
            java.util.stream.IntStream.concat(java.util.stream.IntStream.rangeClosed(8192,8202),
                    java.util.stream.IntStream.of(8232,8233,8239,8287,12288)))
            .mapToObj(code -> "CHR("+code+")").collect(java.util.stream.Collectors.joining("||"));
    private static final String COMMENT_PRESENT = "BTRIM(COALESCE(r.comment,''),"+WHITE_SPACE+")<>''";
    private record Scope(String sql, Map<String,Object> parameters) {}

    private Scope scope(UUID product, ReviewListRequest request) {
        StringBuilder sql=new StringBuilder(" WHERE r.product_id=:product");
        Map<String,Object> parameters=new LinkedHashMap<>(); parameters.put("product",product);
        if(request.getRating()!=null) { sql.append(" AND r.rating=:rating"); parameters.put("rating",request.getRating()); }
        if(request.getHasImages()!=null) sql.append(request.getHasImages() ? " AND EXISTS " : " AND NOT EXISTS ")
                .append("(SELECT 1 FROM product_review_images image WHERE image.review_id=r.id)");
        if(request.getHasComment()!=null) sql.append(request.getHasComment() ? " AND (" : " AND NOT (").append(COMMENT_PRESENT).append(")");
        return new Scope(sql.toString(),parameters);
    }
    private Query bind(Query query, Scope scope) { scope.parameters().forEach(query::setParameter); return query; }
    @Override public long countMatching(UUID product, ReviewListRequest request) {
        var scope=scope(product,request);
        return ((Number)bind(entities.createNativeQuery("SELECT COUNT(*) FROM product_reviews r"+scope.sql()),scope).getSingleResult()).longValue();
    }
    @Override @SuppressWarnings("unchecked") public List<ProductReview> findMatching(UUID product, ReviewListRequest request) {
        String order=switch(request.getSort()) {
            case "createdAt,asc" -> "r.created_at ASC,r.id DESC";
            case "createdAt,desc" -> "r.created_at DESC,r.id DESC";
            case "rating,asc" -> "r.rating ASC,r.id DESC";
            case "rating,desc" -> "r.rating DESC,r.id DESC";
            default -> throw new InvalidReviewRequestException();
        };
        var scope=scope(product,request);
        return bind(entities.createNativeQuery("SELECT r.* FROM product_reviews r"+scope.sql()+" ORDER BY "+order+" LIMIT :limit OFFSET :offset",ProductReview.class),scope)
                .setParameter("limit",request.getSize()).setParameter("offset",(long)request.getPage()*request.getSize()).getResultList();
    }
}
