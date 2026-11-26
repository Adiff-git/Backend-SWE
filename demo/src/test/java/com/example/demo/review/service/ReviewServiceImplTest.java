package com.example.demo.review.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.demo.media.service.MediaService;
import com.example.demo.review.converter.ReviewConverter;
import com.example.demo.review.dto.request.CreateReviewRequest;
import com.example.demo.review.exception.InvalidReviewRequestException;
import com.example.demo.review.service.impl.ReviewServiceImpl;
import com.example.demo.review.validator.PatchReviewValidator;
import com.example.demo.review.validator.ReviewValidator;
import com.example.demo.user.service.UserReviewReadService;
import org.junit.jupiter.api.Test;

class ReviewServiceImplTest {
    @Test void create_shouldRejectAmbientTransactionBeforeAnyStorageOrWriterWork() {
        ReviewWriteService writer = mock(ReviewWriteService.class); MediaService media = mock(MediaService.class);
        ReviewServiceImpl service = new ReviewServiceImpl(writer, mock(ReviewValidator.class), mock(PatchReviewValidator.class), media, mock(UserReviewReadService.class), new ReviewConverter(),mock(com.example.demo.order.service.OrderReviewReadService.class));
        org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThatThrownBy(() -> service.create(1L, 2L, 3L, new CreateReviewRequest())).isInstanceOf(IllegalStateException.class);
            verifyNoInteractions(writer, media);
        } finally { org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(false); }
    }
    @Test void create_shouldNotPrepareStorageOrWrite_whenContentIsInvalid() {
        ReviewWriteService writer = mock(ReviewWriteService.class); MediaService media = mock(MediaService.class);
        ReviewValidator validator = mock(ReviewValidator.class); CreateReviewRequest request = new CreateReviewRequest();
        doThrow(new InvalidReviewRequestException()).when(validator).validate(request);
        ReviewServiceImpl service = new ReviewServiceImpl(writer, validator, mock(PatchReviewValidator.class), media, mock(UserReviewReadService.class), new ReviewConverter(),mock(com.example.demo.order.service.OrderReviewReadService.class));
        assertThatThrownBy(() -> service.create(1L, 2L, 3L, request)).isInstanceOf(InvalidReviewRequestException.class);
        verifyNoInteractions(writer, media);
    }
}
