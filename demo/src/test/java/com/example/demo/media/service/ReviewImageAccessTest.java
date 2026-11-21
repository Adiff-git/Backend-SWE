package com.example.demo.media.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.demo.media.converter.MediaConverter;
import com.example.demo.media.entity.ImageUpload;
import com.example.demo.media.repository.ImageUploadRepository;
import com.example.demo.media.service.impl.MediaServiceImpl;
import com.example.demo.media.validator.ImageUploadValidator;
import com.example.demo.storage.service.ImageUrlResolver;
import com.example.demo.storage.service.ObjectStorageService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReviewImageAccessTest {
    private final ImageUploadRepository repository = mock(ImageUploadRepository.class);
    private final ObjectStorageService storage = mock(ObjectStorageService.class);
    private final ImageUrlResolver urls = mock(ImageUrlResolver.class);
    private final MediaServiceImpl service = new MediaServiceImpl(mock(MediaCommandService.class), repository, storage,
            mock(MediaConverter.class), mock(ImageUploadValidator.class), urls);

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = com.example.demo.media.enums.ImageUploadStatus.class, names = {"PENDING", "EXPIRED"})
    void prepareReviewImages_shouldRejectIncompleteImageBeforeStorageCalls(com.example.demo.media.enums.ImageUploadStatus state) {
        UUID id = UUID.randomUUID();
        ImageUpload image = ImageUpload.pending(1L, "staging", "image/png", Instant.EPOCH, Instant.EPOCH.plusSeconds(60));
        if (state == com.example.demo.media.enums.ImageUploadStatus.EXPIRED) image.expire();
        when(repository.findAllById(List.of(id))).thenReturn(List.of(image));
        assertThatThrownBy(() -> service.prepareReviewImages(1L, List.of(id)))
                .isInstanceOf(com.example.demo.media.exception.UploadStateConflictException.class);
        verify(storage, never()).verifyFinalExists(org.mockito.ArgumentMatchers.anyString());
    }
    @Test void getSafeAttachedImages_shouldRetainUnknownIdsWithoutStorageLookup() {
        UUID id = UUID.randomUUID(); when(repository.findAllById(List.of(id))).thenReturn(List.of());
        when(urls.prepare()).thenThrow(new com.example.demo.storage.exception.StorageUnavailableException());
        assertThat(service.getSafeAttachedImages(List.of(id)).get(id).getFinalUrl()).isNull();
        verify(storage, never()).verifyFinalExists(org.mockito.ArgumentMatchers.anyString());
    }
}
