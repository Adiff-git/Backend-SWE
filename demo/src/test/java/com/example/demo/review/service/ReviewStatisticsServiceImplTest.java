package com.example.demo.review.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.demo.catalog.service.ProductCatalogService;
import com.example.demo.review.repository.ProductReviewRepository;
import com.example.demo.review.service.impl.ReviewStatisticsServiceImpl;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReviewStatisticsServiceImplTest {
    @Test void getSummary_shouldRoundHalfUpAndRepresentEmptyAverageAsNull() {
        ProductReviewRepository repository = mock(ProductReviewRepository.class);
        UUID id = UUID.randomUUID(); ProductReviewRepository.RatingAggregate aggregate = mock(ProductReviewRepository.RatingAggregate.class);
        when(repository.aggregate(id)).thenReturn(aggregate); when(aggregate.getReviewCount()).thenReturn(8L);
        when(aggregate.getAverageRating()).thenReturn(new BigDecimal("3.125"));
        ReviewStatisticsServiceImpl service = new ReviewStatisticsServiceImpl(repository, mock(ProductCatalogService.class));
        assertThat(service.getSummary(id).getAverageRating()).isEqualByComparingTo("3.13");
        when(aggregate.getReviewCount()).thenReturn(0L); when(aggregate.getAverageRating()).thenReturn(null);
        assertThat(service.getSummary(id).getAverageRating()).isNull();
    }
}
