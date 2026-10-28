package com.example.demo.catalog.storage;

import com.example.demo.catalog.exception.CatalogStorageUnavailableException;
import com.example.demo.storage.config.S3StorageProperties;
import com.example.demo.storage.exception.StorageUnavailableException;
import com.example.demo.storage.service.ObjectStorageService;
import com.example.demo.storage.service.PreparedDownloadSigner;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class S3ProductImageStorageTest {
    @Test void delegatesToSharedStorageWithFiveMinuteLifetimeAndClosesPreparedSigner() {
        ObjectStorageService shared = mock(ObjectStorageService.class);
        PreparedDownloadSigner prepared = mock(PreparedDownloadSigner.class);
        when(shared.prepareDownloadSigner(Duration.ofSeconds(300))).thenReturn(prepared);
        when(prepared.sign("images/final")).thenReturn("signed-url");
        try (var signer = new S3ProductImageStorage(shared, new S3StorageProperties()).prepareReadSigner()) {
            assertThat(signer.signReadUrl("images/final")).isEqualTo("signed-url");
        }
        verify(prepared).close();
    }
    @Test void translatesSharedStorageFailureWithoutLeakingDetails() {
        ObjectStorageService shared = mock(ObjectStorageService.class);
        when(shared.prepareDownloadSigner(any())).thenThrow(new StorageUnavailableException());
        assertThatThrownBy(() -> new S3ProductImageStorage(shared, new S3StorageProperties()).prepareReadSigner())
                .isInstanceOf(CatalogStorageUnavailableException.class);
    }
}
