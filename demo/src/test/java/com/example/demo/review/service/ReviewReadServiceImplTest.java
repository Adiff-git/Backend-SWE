package com.example.demo.review.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.demo.catalog.exception.ProductNotFoundException;
import com.example.demo.catalog.service.ProductCatalogService;
import com.example.demo.media.service.MediaService;
import com.example.demo.review.converter.ReviewConverter;
import com.example.demo.review.dto.request.ReviewListRequest;
import com.example.demo.review.repository.ProductReviewImageRepository;
import com.example.demo.review.repository.ProductReviewRepository;
import com.example.demo.review.service.impl.ReviewReadServiceImpl;
import com.example.demo.user.service.UserReviewReadService;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReviewReadServiceImplTest {
    @Test void getReviews_shouldCheckPublicationBeforeRetrievingReviewOrAuthorData() {
        UUID product = UUID.randomUUID(); ProductCatalogService catalog = mock(ProductCatalogService.class);
        ProductReviewRepository reviews = mock(ProductReviewRepository.class); ProductReviewImageRepository images = mock(ProductReviewImageRepository.class);
        UserReviewReadService users = mock(UserReviewReadService.class); MediaService media = mock(MediaService.class);
        doThrow(new ProductNotFoundException()).when(catalog).getPublishedProductEntity(product);
        assertThatThrownBy(() -> new ReviewReadServiceImpl(reviews, images, catalog, users, media, new ReviewConverter(),mock(com.example.demo.order.service.OrderReviewReadService.class)).getReviews(product, new ReviewListRequest(0, 20, "createdAt,desc")))
                .isInstanceOf(ProductNotFoundException.class);
        verifyNoInteractions(reviews, images, users, media);
    }
}
