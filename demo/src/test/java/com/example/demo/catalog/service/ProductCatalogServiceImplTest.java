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
            products, images, variants, storage, new ProductCatalogConverter(),
            jakarta.validation.Validation.buildDefaultValidatorFactory().getValidator());

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

    @Test
    void listingUsesSelectedMetadataAndQueryOrderWithBatchedLoading() {
        Product first = product();
        Product second = product();
        UUID variant = UUID.randomUUID();
        when(products.findPublishedCandidates(org.mockito.ArgumentMatchers.any())).thenReturn(
                new com.example.demo.catalog.search.ProductCandidatePage(List.of(
                        new com.example.demo.catalog.search.ProductCandidatePage.Candidate(first.getId(), variant,
                                new java.math.BigInteger("123456789012345678901234567890")),
                        new com.example.demo.catalog.search.ProductCandidatePage.Candidate(second.getId(), null, null)), 3));
        when(products.findAllByIdsAndStatusWithCategory(List.of(first.getId(), second.getId()), ProductStatus.PUBLISHED))
                .thenReturn(List.of(second, first));
        var page = service.listProducts(0, 2);
        assertThat(page.getItems()).extracting(com.example.demo.catalog.dto.response.ProductSummary::getId)
                .containsExactly(first.getId(), second.getId());
        var item = (com.example.demo.catalog.dto.response.ProductListingItem) page.getItems().getFirst();
        assertThat(item.getRepresentativeVariantId()).isEqualTo(variant);
        assertThat(item.getDisplayPrice().getAmount()).isEqualTo(new java.math.BigInteger("123456789012345678901234567890"));
        var orphan = (com.example.demo.catalog.dto.response.ProductListingItem) page.getItems().get(1);
        assertThat(orphan.getRepresentativeVariantId()).isNull();
        assertThat(orphan.getDisplayPrice()).isNull();
        assertThat(page.getTotalItems()).isEqualTo(3);
        assertThat(page.getTotalPages()).isEqualTo(2);
        verify(images).findPrimaryImagesForProducts(List.of(first.getId(), second.getId()));
        verify(products, never()).findPublishedIdPage(org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(variants, storage);
    }

    @Test
    void listingEmptyLaterPageRetainsSnapshotCountWithoutBatchLoads() {
        when(products.findPublishedCandidates(org.mockito.ArgumentMatchers.any())).thenReturn(
                new com.example.demo.catalog.search.ProductCandidatePage(List.of(), 3));
        var page = service.listProducts(9, 2);
        assertThat(page.getItems()).isEmpty();
        assertThat(page.getTotalItems()).isEqualTo(3);
        assertThat(page.getTotalPages()).isEqualTo(2);
        verifyNoInteractions(images, variants, storage);
    }

    @Test
    void listingPreparesSignerBeforeLocksAndSuppressesLateHiddenProduct() {
        Product visible = product();
        Product hidden = product();
        ProductImage image = CatalogFixtures.image(visible, 0, true);
        ProductImage hiddenImage = CatalogFixtures.image(hidden, 0, true);
        when(products.findPublishedCandidates(org.mockito.ArgumentMatchers.any())).thenReturn(
                new com.example.demo.catalog.search.ProductCandidatePage(List.of(
                        new com.example.demo.catalog.search.ProductCandidatePage.Candidate(visible.getId(), null, null),
                        new com.example.demo.catalog.search.ProductCandidatePage.Candidate(hidden.getId(), null, null)), 2));
        when(images.findPrimaryImagesForProducts(anyCollection())).thenReturn(List.of(image, hiddenImage));
        when(products.findAllByIdsAndStatusWithCategory(anyCollection(), eq(ProductStatus.PUBLISHED)))
                .thenReturn(List.of(hidden, visible));
        when(products.lockStatusesForShare(anyCollection())).thenReturn(List.of(
                new Object[]{visible.getId(), "PUBLISHED"}, new Object[]{hidden.getId(), "ARCHIVED"}));
        PreparedReadSigner signer = mock(PreparedReadSigner.class);
        when(storage.prepareReadSigner()).thenReturn(signer);
        when(signer.signReadUrl(image.getObjectKey())).thenReturn("https://images.test/signed");
        var page = service.listProducts(0, 20);
        assertThat(page.getItems()).extracting(com.example.demo.catalog.dto.response.ProductSummary::getId)
                .containsExactly(visible.getId());
        assertThat(page.getItems().getFirst().getPrimaryImageUrl()).isEqualTo("https://images.test/signed");
        assertThat(page.getTotalItems()).isEqualTo(2);
        var order = org.mockito.Mockito.inOrder(storage, products, signer);
        order.verify(storage).prepareReadSigner();
        order.verify(products).lockStatusesForShare(anyCollection());
        order.verify(signer).signReadUrl(image.getObjectKey());
        order.verify(signer).close();
        verify(signer, never()).signReadUrl(hiddenImage.getObjectKey());
        verifyNoInteractions(variants);
    }

    @Test
    void listingSignerPreparationFailureDoesNotTakeLocks() {
        Product product = product();
        when(products.findPublishedCandidates(org.mockito.ArgumentMatchers.any())).thenReturn(
                new com.example.demo.catalog.search.ProductCandidatePage(List.of(
                        new com.example.demo.catalog.search.ProductCandidatePage.Candidate(product.getId(), null, null)), 1));
        when(images.findPrimaryImagesForProducts(anyCollection())).thenReturn(List.of(CatalogFixtures.image(product, 0, true)));
        when(products.findAllByIdsAndStatusWithCategory(anyCollection(), eq(ProductStatus.PUBLISHED))).thenReturn(List.of(product));
        when(storage.prepareReadSigner()).thenThrow(new CatalogStorageUnavailableException());
        assertThatThrownBy(() -> service.listProducts(0, 20)).isInstanceOf(CatalogStorageUnavailableException.class);
        verify(products, never()).lockStatusesForShare(anyCollection());
    }

    @Test
    void listingValidatesDirectRequestsAndForwardsCriteriaWithoutActivatingFilters() {
        assertThatThrownBy(() -> service.listProducts(-1, 20)).isInstanceOf(InvalidCatalogRequestException.class);
        assertThatThrownBy(() -> service.listProducts((com.example.demo.catalog.dto.request.ProductSearchRequest)null))
                .isInstanceOf(InvalidCatalogRequestException.class);
        verifyNoInteractions(products);
        var request = new com.example.demo.catalog.dto.request.ProductSearchRequest();
        request.setQ("Điện thoại");
        request.setCategoryId("12345678-1234-1234-1234-123456789abc");
        when(products.findPublishedCandidates(org.mockito.ArgumentMatchers.any())).thenThrow(
                new InvalidCatalogRequestException("Unsupported current increment"));
        assertThatThrownBy(() -> service.listProducts(request)).isInstanceOf(InvalidCatalogRequestException.class);
        var criteria = org.mockito.ArgumentCaptor.forClass(com.example.demo.catalog.search.ProductSearchCriteria.class);
        verify(products).findPublishedCandidates(criteria.capture());
        assertThat(criteria.getValue().getKeyword().getOriginalNormalized()).isEqualTo("dien thoai");
        assertThat(criteria.getValue().getCategoryId()).isEqualTo(UUID.fromString(request.getCategoryId()));
        verifyNoInteractions(images, variants, storage);
    }

    @Test
    void listingSuppressesProductHiddenDuringBatchLoading() {
        Product product = product();
        when(products.findPublishedCandidates(org.mockito.ArgumentMatchers.any())).thenReturn(
                new com.example.demo.catalog.search.ProductCandidatePage(List.of(
                        new com.example.demo.catalog.search.ProductCandidatePage.Candidate(product.getId(), null, null)), 1));
        when(products.findAllByIdsAndStatusWithCategory(anyCollection(), eq(ProductStatus.PUBLISHED))).thenReturn(List.of());
        var page = service.listProducts(0, 20);
        assertThat(page.getItems()).isEmpty();
        assertThat(page.getTotalItems()).isEqualTo(1);
        verifyNoInteractions(storage, variants);
    }

    private Product product() {
        Product product = CatalogFixtures.publishedProduct().product();
        ReflectionTestUtils.setField(product, "id", UUID.randomUUID());
        return product;
    }
}
