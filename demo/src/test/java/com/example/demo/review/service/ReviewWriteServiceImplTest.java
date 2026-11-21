package com.example.demo.review.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.demo.media.dto.response.PreparedReviewImages;
import com.example.demo.media.service.MediaService;
import com.example.demo.order.service.OrderReviewReadService;
import com.example.demo.review.dto.request.PatchReviewRequest;
import com.example.demo.review.repository.ProductReviewImageRepository;
import com.example.demo.review.repository.ProductReviewRepository;
import com.example.demo.review.service.impl.ReviewWriteServiceImpl;
import com.example.demo.user.entity.User;
import com.example.demo.user.enums.AccountStatus;
import com.example.demo.user.service.UserService;
import java.time.Clock;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReviewWriteServiceImplTest {
    @Test void update_shouldRejectInactiveActorBeforePurchaseOrReviewAccess() {
        UserService users = mock(UserService.class); User inactive = new User(); inactive.setAccountStatus(AccountStatus.INACTIVE);
        when(users.getUserEntityForUpdate(1L)).thenReturn(inactive);
        ProductReviewRepository reviews = mock(ProductReviewRepository.class); ProductReviewImageRepository images = mock(ProductReviewImageRepository.class);
        OrderReviewReadService orders = mock(OrderReviewReadService.class); MediaService media = mock(MediaService.class);
        ReviewWriteServiceImpl writer = new ReviewWriteServiceImpl(reviews, images, users, orders, media, Clock.systemUTC());
        assertThatThrownBy(() -> writer.update(1L, 2L, new PatchReviewRequest(), new PreparedReviewImages(Map.of())))
                .isInstanceOf(com.example.demo.common.identity.IdentityUnavailableException.class);
        verifyNoInteractions(reviews, images, orders, media);
    }
}
