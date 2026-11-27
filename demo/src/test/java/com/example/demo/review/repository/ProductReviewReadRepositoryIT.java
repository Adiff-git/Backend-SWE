package com.example.demo.review.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.demo.fixture.review.ReviewFixtures;
import com.example.demo.order.entity.OrderStatus;
import com.example.demo.review.entity.ProductReview;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest @ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE) @ActiveProfiles("test")
class ProductReviewReadRepositoryIT {
    @Autowired EntityManager entities;
    @Autowired ProductReviewRepository reviews;
    @Test void filters_shouldShareCountAndPagePredicatesWithoutImageMultiplicityForEverySort() {
        var purchase=ReviewFixtures.purchase(entities,OrderStatus.COMPLETED);
        record Expected(Long id, short rating, Instant at, boolean comment, boolean images) {}
        java.util.List<Expected> fixture=new java.util.ArrayList<>();
        for (int i=0;i<10;i++) {
            long item=i==0 ? purchase.item() : ReviewFixtures.additionalItem(entities,purchase.owner(),purchase.product());
            String comment=new String[]{null,"", "\u00a0\u2003\u3000", "text", "\u200b"}[i%5];
            var review=new ProductReview(item,purchase.owner(),purchase.product(),i%5+1,comment,Instant.EPOCH.plusSeconds(i/3));
            entities.persist(review); entities.flush();
            boolean hasImages=i%2==0;
            if(hasImages) for(int image=0;image<2;image++) {
                var upload=com.example.demo.media.entity.ImageUpload.pending(purchase.owner(),"staging/"+java.util.UUID.randomUUID(),"image/png",Instant.EPOCH,Instant.EPOCH.plusSeconds(1));
                upload.complete("final/"+java.util.UUID.randomUUID()); entities.persist(upload); entities.flush();
                entities.persist(new com.example.demo.review.entity.ProductReviewImage(review.getId(),upload.getId(),image));
            }
            fixture.add(new Expected(review.getId(),review.getRating(),review.getCreatedAt(),i%5>=3,hasImages));
        }
        entities.flush(); entities.clear();
        for (Integer rating : java.util.Arrays.asList(null,1,2,3,4,5))
        for (Boolean images : java.util.Arrays.asList(null,true,false))
        for (Boolean comment : java.util.Arrays.asList(null,true,false))
        for (String sort : java.util.List.of("createdAt,asc","createdAt,desc","rating,asc","rating,desc")) {
            var params=new org.springframework.util.LinkedMultiValueMap<String,String>();
            params.add("sort",sort); params.add("size","3");
            if(rating!=null) params.add("rating",rating.toString());
            if(images!=null) params.add("hasImages",images.toString());
            if(comment!=null) params.add("hasComment",comment.toString());
            var request=com.example.demo.review.dto.request.ReviewListRequest.parse(params);
            java.util.Comparator<Expected> primary=sort.startsWith("rating") ? java.util.Comparator.comparingInt(Expected::rating) : java.util.Comparator.comparing(Expected::at);
            if(sort.endsWith("desc")) primary=primary.reversed();
            var expected=fixture.stream().filter(x -> rating==null || x.rating()==rating)
                .filter(x -> images==null || x.images()==images).filter(x -> comment==null || x.comment()==comment)
                .sorted(primary.thenComparing(Expected::id,java.util.Comparator.reverseOrder())).toList();
            assertThat(reviews.countMatching(purchase.product(),request)).as(params.toString()).isEqualTo(expected.size());
            assertThat(reviews.findMatching(purchase.product(),request)).as(params.toString()).extracting(ProductReview::getId)
                .containsExactlyElementsOf(expected.stream().limit(3).map(Expected::id).toList());
        }
        var empty=new org.springframework.util.LinkedMultiValueMap<String,String>(); empty.add("page","99");
        assertThat(reviews.findMatching(purchase.product(),com.example.demo.review.dto.request.ReviewListRequest.parse(empty))).isEmpty();
    }
    @Test void aggregate_shouldCountAllPurchasesIndependentlyOfPage() {
        ReviewFixtures.Purchase purchase = ReviewFixtures.purchase(entities, OrderStatus.COMPLETED);
        for (int rating : new int[]{1, 4, 5}) entities.persist(new ProductReview(ReviewFixtures.additionalItem(entities, purchase.owner(), purchase.product()), purchase.owner(), purchase.product(), rating, null, Instant.EPOCH));
        entities.flush(); entities.clear();
        assertThat(reviews.aggregate(purchase.product()).getReviewCount()).isEqualTo(3);
        assertThat(reviews.aggregate(purchase.product()).getAverageRating()).isBetween(new java.math.BigDecimal("3.33"), new java.math.BigDecimal("3.34"));
        assertThat(reviews.findByProductId(purchase.product(), PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "rating", "id"))).getContent()).extracting(ProductReview::getRating).containsExactly((short) 5);
    }
}
