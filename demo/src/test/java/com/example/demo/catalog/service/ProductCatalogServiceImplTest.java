package com.example.demo.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.demo.catalog.CatalogFixtures;
import com.example.demo.catalog.converter.ProductCatalogConverter;
import com.example.demo.catalog.entity.Product;
import com.example.demo.catalog.entity.ProductImage;
import com.example.demo.catalog.entity.ProductStatus;
import com.example.demo.catalog.exception.CatalogStorageUnavailableException;
import com.example.demo.catalog.exception.InvalidCatalogRequestException;
import com.example.demo.catalog.exception.ProductNotFoundException;
import com.example.demo.catalog.repository.ProductImageRepository;
import com.example.demo.catalog.repository.ProductRepository;
import com.example.demo.catalog.repository.ProductVariantRepository;
import com.example.demo.catalog.storage.ProductImageStorage;
import com.example.demo.catalog.storage.ProductImageStorage.PreparedReadSigner;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ProductCatalogServiceImplTest {
    private final ProductRepository products = mock(ProductRepository.class);
    private final ProductImageRepository images = mock(ProductImageRepository.class);
    private final ProductVariantRepository variants = mock(ProductVariantRepository.class);
    private final ProductImageStorage storage = mock(ProductImageStorage.class);
    private final ProductCatalogServiceImpl service = new ProductCatalogServiceImpl(
            products, images, variants, storage, new ProductCatalogConverter());

    @Test
    void getPublishedProductEntity_shouldReturnReference_whenPublished() {
        Product product = product();
        when(products.findByIdAndStatus(product.getId(), ProductStatus.PUBLISHED)).thenReturn(Optional.of(product));
        assertThat(service.getPublishedProductEntity(product.getId())).isSameAs(product);
        verifyNoInteractions(images, variants, storage);
    }

    @Test
    void getPublishedProductEntity_shouldRejectUnavailableProduct_whenNotPublicOrMissing() {
        UUID id = UUID.randomUUID();
        when(products.findByIdAndStatus(id, ProductStatus.PUBLISHED)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getPublishedProductEntity(id)).isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void getProductSummaries_shouldAvoidFetching_whenInputIsEmpty() {
        assertThat(service.getProductSummaries(List.of())).isEmpty();
        verifyNoInteractions(products, images, variants, storage);
    }

    @Test
    void getProductSummaries_shouldRejectUnboundedInput_whenMoreThanOneHundredIds() {
        List<UUID> ids = IntStream.range(0, 101).mapToObj(index -> UUID.randomUUID()).toList();
        assertThatThrownBy(() -> service.getProductSummaries(ids)).isInstanceOf(InvalidCatalogRequestException.class);
        verifyNoInteractions(products, images, variants, storage);
    }

    @Test
    void getProductSummaries_shouldReturnNullImageWithoutDetailFetch_whenProductHasNoImage() {
        Product product = product();
        when(products.findAllByIdsAndStatusWithCategory(anyCollection(), eq(ProductStatus.PUBLISHED)))
                .thenReturn(List.of(product));
        assertThat(service.getProductSummaries(List.of(product.getId())).get(product.getId()).getPrimaryImageUrl())
                .isNull();
        verifyNoInteractions(variants, storage);
    }

    @Test
    void getProductSummaries_shouldExcludeLateHiddenProduct_whenFinalRevalidationFails() {
        Product product = product();
        when(products.findAllByIdsAndStatusWithCategory(anyCollection(), eq(ProductStatus.PUBLISHED)))
                .thenReturn(List.of(product), List.of());
        assertThat(service.getProductSummaries(List.of(product.getId()))).isEmpty();
        verifyNoInteractions(variants, storage);
    }

    @Test
    void getProductSummaries_shouldNotSignUnpublishedImage_whenLockedStatusChanged() {
        Product product = product();
        ProductImage image = CatalogFixtures.image(product, 0, true);
        when(images.findPrimaryImagesForProducts(anyCollection())).thenReturn(List.of(image));
        when(products.findAllByIdsAndStatusWithCategory(anyCollection(), eq(ProductStatus.PUBLISHED)))
                .thenReturn(List.of(product));
        when(products.lockStatusesForShare(anyCollection()))
                .thenReturn(List.<Object[]>of(new Object[] {product.getId(), "ARCHIVED"}));
        PreparedReadSigner signer = mock(PreparedReadSigner.class);
        when(storage.prepareReadSigner()).thenReturn(signer);
        assertThat(service.getProductSummaries(List.of(product.getId()))).isEmpty();
        verify(signer, never()).signReadUrl(image.getObjectKey());
        verify(signer).close();
        verifyNoInteractions(variants);
    }

    @Test
    void getProductSummaries_shouldPropagateSafeFailure_whenSignerPreparationFails() {
        Product product = product();
        when(images.findPrimaryImagesForProducts(anyCollection()))
                .thenReturn(List.of(CatalogFixtures.image(product, 0, true)));
        when(products.findAllByIdsAndStatusWithCategory(anyCollection(), eq(ProductStatus.PUBLISHED)))
                .thenReturn(List.of(product));
        when(storage.prepareReadSigner()).thenThrow(new CatalogStorageUnavailableException());
        assertThatThrownBy(() -> service.getProductSummaries(List.of(product.getId())))
                .isInstanceOf(CatalogStorageUnavailableException.class);
        verify(products, never()).lockStatusesForShare(anyCollection());
    }

    @Test
    void getProductSummaries_shouldReturnSignedSummary_whenLockedProductIsPublished() {
        Product product = product();
        ProductImage image = CatalogFixtures.image(product, 0, true);
        when(images.findPrimaryImagesForProducts(anyCollection())).thenReturn(List.of(image));
        when(products.findAllByIdsAndStatusWithCategory(anyCollection(), eq(ProductStatus.PUBLISHED)))
                .thenReturn(List.of(product));
        when(products.lockStatusesForShare(anyCollection()))
                .thenReturn(List.<Object[]>of(new Object[] {product.getId(), "PUBLISHED"}));
        PreparedReadSigner signer = mock(PreparedReadSigner.class);
        when(storage.prepareReadSigner()).thenReturn(signer);
        when(signer.signReadUrl(image.getObjectKey())).thenReturn("https://images.test/temporary");
        assertThat(service.getProductSummaries(List.of(product.getId())).get(product.getId()).getPrimaryImageUrl())
                .isEqualTo("https://images.test/temporary");
        verifyNoInteractions(variants);
    }

    private Product product() {
        Product product = CatalogFixtures.publishedProduct().product();
        ReflectionTestUtils.setField(product, "id", UUID.randomUUID());
        return product;
    }
}
