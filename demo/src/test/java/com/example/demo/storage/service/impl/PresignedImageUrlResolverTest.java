package com.example.demo.storage.service.impl;

import com.example.demo.storage.config.S3StorageProperties;
import com.example.demo.storage.exception.StorageUnavailableException;
import com.example.demo.storage.service.ObjectStorageService;
import com.example.demo.storage.service.PreparedDownloadSigner;
import com.example.demo.storage.service.PreparedImageUrls;
import java.time.Duration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class PresignedImageUrlResolverTest {
    private final ObjectStorageService storage = mock(ObjectStorageService.class);
    private final PresignedImageUrlResolver resolver = new PresignedImageUrlResolver(storage, new S3StorageProperties());

    @Test void resolve_shouldUseConfiguredLifetimeWithoutObjectInspection() {
        when(storage.prepareDownload("images/avatar", Duration.ofSeconds(300))).thenReturn("url");
        assertThat(resolver.resolve("images/avatar")).isEqualTo("url");
        verify(storage).prepareDownload("images/avatar", Duration.ofSeconds(300));
        verifyNoMoreInteractions(storage);
    }

    @Test void prepare_shouldResolveMultipleImageTypesThroughOneResourceFreeCapability() {
        PreparedDownloadSigner signer = mock(PreparedDownloadSigner.class);
        when(storage.prepareDownloadSigner(Duration.ofSeconds(300))).thenReturn(signer);
        when(signer.sign("images/product")).thenReturn("product-url");
        when(signer.sign("images/avatar")).thenReturn("avatar-url");
        PreparedImageUrls urls = resolver.prepare();
        assertThat(urls.resolve("images/product")).isEqualTo("product-url");
        assertThat(urls.resolve("images/avatar")).isEqualTo("avatar-url");
        verify(storage).prepareDownloadSigner(Duration.ofSeconds(300));
        verifyNoMoreInteractions(storage);
    }

    @Test void resolve_shouldRejectMissingKeyWithoutCallingStorage() {
        assertThatThrownBy(() -> resolver.resolve(null)).isInstanceOf(StorageUnavailableException.class);
        assertThatThrownBy(() -> resolver.resolve(" ")).isInstanceOf(StorageUnavailableException.class);
        verifyNoMoreInteractions(storage);
    }
}
