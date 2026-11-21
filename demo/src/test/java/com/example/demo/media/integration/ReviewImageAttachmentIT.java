package com.example.demo.media.integration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

import com.example.demo.config.ReviewIntegrationSupport;
import com.example.demo.media.dto.response.PreparedReviewImages;
import com.example.demo.media.entity.ImageUpload;
import com.example.demo.media.service.MediaService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ReviewImageAttachmentIT extends ReviewIntegrationSupport {
    @Autowired MediaService media;
    @Test void prepareReviewImages_shouldVerifyOwnedObjectAndRejectForeignOwner() {
        UUID image = transactions.execute(status -> {
            ImageUpload upload = ImageUpload.pending(purchase.owner(), "review/" + UUID.randomUUID(), "image/png", Instant.EPOCH, Instant.EPOCH.plusSeconds(1));
            upload.complete("review/final/" + UUID.randomUUID()); entities.persist(upload); entities.flush(); return upload.getId();
        });
        try {
            PreparedReviewImages prepared = media.prepareReviewImages(purchase.owner(), List.of(image));
            verify(storage).verifyFinalExists(prepared.getFinalKeys().get(image));
            transactions.executeWithoutResult(status -> media.validatePreparedReviewImages(purchase.owner(), prepared));
            assertThatThrownBy(() -> media.prepareReviewImages(purchase.foreign(), List.of(image)))
                    .isInstanceOf(com.example.demo.media.exception.ImageUploadNotFoundException.class);
        } finally { jdbc.update("DELETE FROM image_uploads WHERE id=?", image); }
    }
}
