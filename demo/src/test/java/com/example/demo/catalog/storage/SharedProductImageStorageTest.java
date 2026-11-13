package com.example.demo.catalog.storage;

import com.example.demo.catalog.exception.CatalogStorageUnavailableException;
import com.example.demo.storage.exception.StorageUnavailableException;
import com.example.demo.storage.service.ImageUrlResolver;
import com.example.demo.storage.service.PreparedImageUrls;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SharedProductImageStorageTest {
    @Test void prepareReadSigner_shouldUseSharedResolverWithoutOwningResources() {
        ImageUrlResolver resolver = mock(ImageUrlResolver.class);
        PreparedImageUrls urls = mock(PreparedImageUrls.class);
        when(resolver.prepare()).thenReturn(urls);
        when(urls.resolve("images/final")).thenReturn("signed-url");
        ProductImageStorage.PreparedReadSigner signer = new SharedProductImageStorage(resolver).prepareReadSigner();
        signer.close();
        assertThat(signer.signReadUrl("images/final")).isEqualTo("signed-url");
    }

    @Test void prepareReadSigner_shouldTranslatePreparationFailureWithoutLeakingDetails() {
        ImageUrlResolver resolver = mock(ImageUrlResolver.class);
        when(resolver.prepare()).thenThrow(new StorageUnavailableException());
        assertThatThrownBy(() -> new SharedProductImageStorage(resolver).prepareReadSigner())
                .isInstanceOf(CatalogStorageUnavailableException.class);
    }

    @Test void signReadUrl_shouldTranslateSigningFailureWithoutCleanupReplacingIt() {
        ImageUrlResolver resolver = mock(ImageUrlResolver.class);
        when(resolver.prepare()).thenReturn(key -> { throw new StorageUnavailableException(); });
        assertThatThrownBy(() -> {
            try (ProductImageStorage.PreparedReadSigner signer = new SharedProductImageStorage(resolver).prepareReadSigner()) {
                signer.signReadUrl("images/final");
            }
        }).isInstanceOf(CatalogStorageUnavailableException.class);
    }
}
