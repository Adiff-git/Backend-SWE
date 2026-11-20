package com.example.demo.catalog.service;

import com.example.demo.catalog.exception.CatalogStorageUnavailableException;
import com.example.demo.catalog.repository.ProductImageRepository;
import com.example.demo.catalog.repository.ProductRepository;
import com.example.demo.catalog.storage.ProductImageStorage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

class CatalogOrderReadServiceImplTest {
    private final ProductRepository products = mock(ProductRepository.class);
    private final ProductImageRepository images = mock(ProductImageRepository.class);
    private final ProductImageStorage storage = mock(ProductImageStorage.class);
    private final ProductImageStorage.PreparedReadSigner signer = mock(ProductImageStorage.PreparedReadSigner.class);
    private final CatalogOrderReadService service = new CatalogOrderReadServiceImpl(products, images, storage);
    private final UUID id = UUID.randomUUID();

    @Test void getPrimaryImageUrls_shouldSkipEmptyInput() {
        assertThat(service.getPrimaryImageUrls(Arrays.asList(null, null))).isEmpty();
        verifyNoInteractions(products, images, storage);
    }

    @Test void getPrimaryImageUrls_shouldDegradePrepareFailureWithoutLocks() {
        when(images.findOrderPrimaryImages(any())).thenReturn(List.<Object[]>of(new Object[]{id, "key"}));
        when(storage.prepareReadSigner()).thenThrow(new CatalogStorageUnavailableException());
        assertThat(service.getPrimaryImageUrls(List.of(id))).isEmpty();
        verifyNoInteractions(products);
    }

    @Test void getPrimaryImageUrls_shouldNeverSignHiddenImage() {
        when(images.findOrderPrimaryImages(any())).thenReturn(List.<Object[]>of(new Object[]{id, "key"}));
        when(storage.prepareReadSigner()).thenReturn(signer);
        when(products.lockStatusesForShare(any())).thenReturn(List.<Object[]>of(new Object[]{id, "ARCHIVED"}));
        assertThat(service.getPrimaryImageUrls(List.of(id, id))).isEmpty();
        verify(signer, never()).signReadUrl(any());
        verify(signer).close();
    }

    @Test void getPrimaryImageUrls_shouldDegradeSigningAndCloseFailure() {
        when(images.findOrderPrimaryImages(any())).thenReturn(List.<Object[]>of(new Object[]{id, "key"}));
        when(storage.prepareReadSigner()).thenReturn(signer);
        when(products.lockStatusesForShare(any())).thenReturn(List.<Object[]>of(new Object[]{id, "PUBLISHED"}));
        when(signer.signReadUrl("key")).thenThrow(new CatalogStorageUnavailableException());
        doThrow(new CatalogStorageUnavailableException()).when(signer).close();
        assertThat(service.getPrimaryImageUrls(List.of(id))).isEmpty();
    }

    @Test void getPrimaryImageUrls_shouldPreserveSuccessfulImagesWhenAnotherFails() {
        UUID other = UUID.randomUUID();
        when(images.findOrderPrimaryImages(any())).thenReturn(List.<Object[]>of(new Object[]{id, "one"}, new Object[]{other, "two"}));
        when(storage.prepareReadSigner()).thenReturn(signer);
        when(products.lockStatusesForShare(any())).thenReturn(List.<Object[]>of(new Object[]{id, "PUBLISHED"}, new Object[]{other, "PUBLISHED"}));
        when(signer.signReadUrl("one")).thenReturn("https://signed/one");
        when(signer.signReadUrl("two")).thenThrow(new CatalogStorageUnavailableException());
        assertThat(service.getPrimaryImageUrls(List.of(id, other))).containsOnlyKeys(id);
    }

    @Test void getPrimaryImageUrls_shouldChunkAndPropagateDatabaseFailure() {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 201; i++) ids.add(UUID.randomUUID());
        when(images.findOrderPrimaryImages(any())).thenAnswer(call -> {
            List<UUID> chunk = call.getArgument(0);
            assertThat(chunk).hasSizeLessThanOrEqualTo(100);
            return List.of();
        });
        assertThat(service.getPrimaryImageUrls(ids)).isEmpty();
        verify(images, org.mockito.Mockito.times(3)).findOrderPrimaryImages(any());
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("database")).when(images).findOrderPrimaryImages(any());
        assertThatThrownBy(() -> service.getPrimaryImageUrls(List.of(id)))
                .isInstanceOf(org.springframework.dao.DataAccessResourceFailureException.class);
    }
}
