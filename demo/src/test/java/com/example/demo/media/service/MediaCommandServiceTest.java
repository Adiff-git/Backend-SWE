package com.example.demo.media.service;

import com.example.demo.media.converter.MediaConverter;
import com.example.demo.media.dto.request.CreateImageUploadRequest;
import com.example.demo.media.entity.ImageUpload;
import com.example.demo.media.enums.ImageUploadStatus;
import com.example.demo.media.repository.ImageUploadRepository;
import com.example.demo.media.service.impl.MediaCommandServiceImpl;
import com.example.demo.media.validator.ImageContentValidator;
import com.example.demo.media.validator.ImageUploadValidator;
import com.example.demo.storage.config.S3StorageProperties;
import com.example.demo.storage.service.ObjectStorageService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MediaCommandServiceTest {
    private final Instant now = Instant.parse("2026-10-27T00:00:00Z");
    private final ImageUploadRepository repository = mock(ImageUploadRepository.class);
    private final ObjectStorageService storage = mock(ObjectStorageService.class);
    private final MediaCommandServiceImpl commands = new MediaCommandServiceImpl(repository, storage,
            new ImageContentValidator(), new ImageUploadValidator(), new MediaConverter(),
            new S3StorageProperties(), Clock.fixed(now, ZoneOffset.UTC));

    @Test void create_shouldDeriveOwnerAndAlignDeadline() {
        CreateImageUploadRequest request = new CreateImageUploadRequest();
        request.setContentType("image/png");
        when(storage.prepareUpload(any(), eq("image/png"), eq(now.plusSeconds(900))))
                .thenReturn(new com.example.demo.storage.dto.response.PreparedUpload("signed",now.plusSeconds(900)));
        commands.create(8L, request);
        ArgumentCaptor<ImageUpload> record = ArgumentCaptor.forClass(ImageUpload.class);
        verify(repository).saveAndFlush(record.capture());
        assertThat(record.getValue().getOwnerUserId()).isEqualTo(8L);
        assertThat(record.getValue().getStagingKey()).startsWith("staging/users/8/");
        assertThat(record.getValue().getExpiresAt()).isEqualTo(now.plusSeconds(900));
    }

    @Test void complete_shouldReturnExpiredOutcomeWithoutThrowingInsideTransaction_whenDeadlineReached() {
        ImageUpload image = ImageUpload.pending(8L, "staging/key", "image/png", now.minusSeconds(900), now);
        when(repository.findByIdAndOwnerUserId(1L, 8L)).thenReturn(Optional.of(image));
        assertThat(commands.complete(8L, 1L).isExpired()).isTrue();
        assertThat(image.getStatus()).isEqualTo(ImageUploadStatus.EXPIRED);
        verify(repository).flush();
        verify(storage, never()).inspect(any());
    }

    @Test void complete_shouldSkipStaging_whenAlreadyCompleted() {
        ImageUpload image = ImageUpload.pending(8L, "staging/key", "image/png", now.minusSeconds(900), now);
        image.complete("images/final");
        when(repository.findByIdAndOwnerUserId(1L, 8L)).thenReturn(Optional.of(image));
        assertThat(commands.complete(8L, 1L).getFinalKey()).isEqualTo("images/final");
        verify(storage, never()).inspect(any());
    }
}
