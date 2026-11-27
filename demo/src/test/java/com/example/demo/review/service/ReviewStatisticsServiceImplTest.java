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
        var three=mock(ProductReviewRepository.RatingCount.class); when(three.getRating()).thenReturn(3); when(three.getRatingCount()).thenReturn(7L);
        var four=mock(ProductReviewRepository.RatingCount.class); when(four.getRating()).thenReturn(4); when(four.getRatingCount()).thenReturn(1L);
        when(repository.countRatings(id)).thenReturn(java.util.List.of(three,four));
        ReviewStatisticsServiceImpl service = new ReviewStatisticsServiceImpl(repository, mock(ProductCatalogService.class));
        assertThat(service.getSummary(id).getAverageRating()).isEqualByComparingTo("3.13");
        assertThat(service.getSummary(id).getRatingDistribution()).containsEntry("3",7L).containsEntry("4",1L).containsEntry("1",0L);
        assertThat(service.getSummary(id).getRatingDistribution().values().stream().mapToLong(Long::longValue).sum()).isEqualTo(8);
        when(repository.countRatings(id)).thenReturn(java.util.List.of());
        when(aggregate.getReviewCount()).thenReturn(0L); when(aggregate.getAverageRating()).thenReturn(null);
        assertThat(service.getSummary(id).getAverageRating()).isNull();
        assertThat(service.getSummary(id).getRatingDistribution()).hasSize(5).containsAllEntriesOf(java.util.Map.of("1",0L,"2",0L,"3",0L,"4",0L,"5",0L));
    }
}
