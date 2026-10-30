package com.example.demo.media.service;

import com.example.demo.media.converter.MediaConverter;
import com.example.demo.media.exception.UploadExpiredException;
import com.example.demo.media.repository.ImageUploadRepository;
import com.example.demo.media.service.impl.MediaServiceImpl;
import com.example.demo.media.validator.ImageUploadValidator;
import com.example.demo.storage.config.S3StorageProperties;
import com.example.demo.storage.exception.StorageUnavailableException;
import com.example.demo.storage.service.ObjectStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MediaServiceTest {
    private final MediaCommandService commands = mock(MediaCommandService.class);
    private final ObjectStorageService storage = mock(ObjectStorageService.class);
    private final MediaServiceImpl service = new MediaServiceImpl(commands, mock(ImageUploadRepository.class), storage,
            new MediaConverter(), new ImageUploadValidator(), new S3StorageProperties());

    @Test void complete_shouldRejectAmbientTransaction() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThatThrownBy(() -> service.completeImageUpload(1L, java.util.UUID.fromString("00000000-0000-4000-8000-000000000002"))).isInstanceOf(IllegalStateException.class);
        } finally { TransactionSynchronizationManager.setActualTransactionActive(false); }
    }

    @Test void complete_shouldTranslateExpiredOutcomeAfterCommandReturn() {
        when(commands.complete(1L, java.util.UUID.fromString("00000000-0000-4000-8000-000000000002"))).thenReturn(new ImageCompletionOutcome(true, java.util.UUID.fromString("00000000-0000-4000-8000-000000000002"), null));
        assertThatThrownBy(() -> service.completeImageUpload(1L, java.util.UUID.fromString("00000000-0000-4000-8000-000000000002"))).isInstanceOf(UploadExpiredException.class);
    }

    @Test void complete_shouldSurfaceSigningFailureAfterSuccessfulCommand() {
        when(commands.complete(1L, java.util.UUID.fromString("00000000-0000-4000-8000-000000000002"))).thenReturn(new ImageCompletionOutcome(false, java.util.UUID.fromString("00000000-0000-4000-8000-000000000002"), "images/final"));
        when(storage.prepareDownload(org.mockito.ArgumentMatchers.eq("images/final"), org.mockito.ArgumentMatchers.any()))
                .thenThrow(new StorageUnavailableException());
        assertThatThrownBy(() -> service.completeImageUpload(1L, java.util.UUID.fromString("00000000-0000-4000-8000-000000000002"))).isInstanceOf(StorageUnavailableException.class);
    }
}
