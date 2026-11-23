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
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ProductCatalogServiceImplTest {
    private final ProductRepository products = mock(ProductRepository.class);
    private final ProductImageRepository images = mock(ProductImageRepository.class);
    private final ProductVariantRepository variants = mock(ProductVariantRepository.class);
    private final ProductImageStorage storage = mock(ProductImageStorage.class);
    private final com.example.demo.inventory.service.InventoryService inventory = mock(com.example.demo.inventory.service.InventoryService.class);
    private final ProductCatalogServiceImpl service = new ProductCatalogServiceImpl(
            products, images, variants, storage, new ProductCatalogConverter(),
            jakarta.validation.Validation.buildDefaultValidatorFactory().getValidator(), inventory);

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
        when(products.lockStatusesForShare(anyCollection())).thenReturn(List.<Object[]>of(new Object[]{product.getId(),"PUBLISHED"}));
        when(products.findPublicSummaries(anyCollection())).thenReturn(views(List.of(product)));
        assertThat(service.getProductSummaries(List.of(product.getId())).get(product.getId()).getPrimaryImageUrl())
                .isNull();
        verifyNoInteractions(variants, storage);
    }

    @Test
    void getProductSummaries_shouldExcludeLateHiddenProduct_whenFinalRevalidationFails() {
        Product product = product();
        when(products.lockStatusesForShare(anyCollection())).thenReturn(List.<Object[]>of(new Object[]{product.getId(),"PUBLISHED"}));
        when(products.findPublicSummaries(anyCollection())).thenReturn(views(List.of()));
        assertThat(service.getProductSummaries(List.of(product.getId()))).isEmpty();
        verifyNoInteractions(variants, storage);
    }

    @Test
    void getProductSummaries_shouldNotSignUnpublishedImage_whenLockedStatusChanged() {
        Product product = product();
        ProductImage image = CatalogFixtures.image(product, 0, true);
        stubImages(List.of(image));
        when(products.findPublicSummaries(anyCollection())).thenReturn(views(List.of(product)));
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
        stubImages(List.of(CatalogFixtures.image(product,0,true)));
        when(products.findPublicSummaries(anyCollection())).thenReturn(views(List.of(product)));
        when(storage.prepareReadSigner()).thenThrow(new CatalogStorageUnavailableException());
        assertThatThrownBy(() -> service.getProductSummaries(List.of(product.getId())))
                .isInstanceOf(CatalogStorageUnavailableException.class);
        verify(products, never()).lockStatusesForShare(anyCollection());
    }

    @Test
    void getProductSummaries_shouldReturnSignedSummary_whenLockedProductIsPublished() {
        Product product = product();
        ProductImage image = CatalogFixtures.image(product, 0, true);
        stubImages(List.of(image));
        when(products.findPublicSummaries(anyCollection())).thenReturn(views(List.of(product)));
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
        when(products.findPublicSummaries(anyCollection())).thenReturn(views(List.of(second, first)));
        when(products.lockStatusesForShare(anyCollection())).thenReturn(List.of(new Object[]{first.getId(),"PUBLISHED"},new Object[]{second.getId(),"PUBLISHED"}));
        when(products.recheckPublishedCandidates(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any())).thenReturn(List.of(current(first,variant,new java.math.BigInteger("123456789012345678901234567890"))));
        when(inventory.getAvailability(List.of(variant))).thenReturn(Map.of(variant,
                new com.example.demo.inventory.dto.response.InventoryAvailability(0, "OUT_OF_STOCK")));
        var page = service.listProducts(0, 2);
        assertThat(page.getItems()).extracting(com.example.demo.catalog.dto.response.ProductSummary::getId)
                .containsExactly(first.getId());
        var item = (com.example.demo.catalog.dto.response.ProductListingItem) page.getItems().getFirst();
        assertThat(item.getRepresentativeVariantId()).isEqualTo(variant);
        assertThat(item.getAvailability().getStatus()).isEqualTo("OUT_OF_STOCK");
        assertThat(item.getDisplayPrice().getAmount()).isEqualTo(new java.math.BigInteger("123456789012345678901234567890"));
        // Corrupt/orphan candidate is suppressed, never substituted or refilled.
        assertThat(page.getTotalItems()).isEqualTo(3);
        assertThat(page.getTotalPages()).isEqualTo(2);
        verify(images).findCartPrimaryImages(List.of(first.getId(), second.getId()));
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
                        new com.example.demo.catalog.search.ProductCandidatePage.Candidate(visible.getId(), visible.getId(), java.math.BigInteger.TEN),
                        new com.example.demo.catalog.search.ProductCandidatePage.Candidate(hidden.getId(), hidden.getId(), java.math.BigInteger.TEN)), 2));
        stubImages(List.of(image,hiddenImage));
        when(products.findPublicSummaries(anyCollection())).thenReturn(views(List.of(hidden, visible)));
        when(products.lockStatusesForShare(anyCollection())).thenReturn(List.of(
                new Object[]{visible.getId(), "PUBLISHED"}, new Object[]{hidden.getId(), "ARCHIVED"}));
        when(products.recheckPublishedCandidates(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any())).thenReturn(List.of(current(visible,visible.getId(),java.math.BigInteger.TEN)));
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
        stubImages(List.of(CatalogFixtures.image(product,0,true)));
        when(products.findPublicSummaries(anyCollection())).thenReturn(views(List.of(product)));
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
        when(products.findPublicSummaries(anyCollection())).thenReturn(views(List.of()));
        var page = service.listProducts(0, 20);
        assertThat(page.getItems()).isEmpty();
        assertThat(page.getTotalItems()).isEqualTo(1);
        verifyNoInteractions(storage, variants);
    }

    @Test void getProduct_shouldReturnPublicNotFoundWithoutResolvingPrivateGalleryCredentials(){
        UUID id=UUID.randomUUID();
        // The scalar peek queries Published active associations only; private gallery is not a signing candidate.
        when(images.findCartPrimaryImages(List.of(id))).thenReturn(List.of());
        when(products.findPublishedForShare(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getProduct(id)).isInstanceOf(ProductNotFoundException.class);
        verifyNoInteractions(storage,variants);
    }

    @Test void getProduct_shouldExcludeDisabledVariantsAndLoadActiveGalleryAfterParentProtection(){
        var fixture=CatalogFixtures.publishedProduct();Product product=fixture.product();UUID id=UUID.randomUUID();
        ReflectionTestUtils.setField(product,"id",id);ReflectionTestUtils.setField(fixture.variant(),"id",UUID.randomUUID());
        var disabled=CatalogFixtures.variant(product,Map.of("choice","disabled"),java.math.BigDecimal.ZERO);disabled.setEnabled(false);
        when(products.findPublishedForShare(id)).thenReturn(Optional.of(view(product)));
        when(variants.findPublicVariants(id)).thenReturn(List.of(variantView(fixture.variant()),variantView(disabled)));
        var image=CatalogFixtures.image(product,0,true);ReflectionTestUtils.setField(image,"id",UUID.randomUUID());
        stubImages(List.of(image));var signer=mock(PreparedReadSigner.class);when(storage.prepareReadSigner()).thenReturn(signer);
        when(signer.signReadUrl(image.getObjectKey())).thenReturn("signed");
        when(images.findPublicGallery(id)).thenReturn(List.of(imageView(image)));
        assertThat(service.getProduct(id).getVariants()).hasSize(1);
        var order=org.mockito.Mockito.inOrder(products,variants,images);
        order.verify(images).findCartPrimaryImages(List.of(id));order.verify(products).findPublishedForShare(id);
        order.verify(variants).findPublicVariants(id);order.verify(images).findPublicGallery(id);
    }

    @Test void getProduct_shouldRejectCorruptPublishedEnabledZeroPrice(){
        var fixture=CatalogFixtures.publishedProduct();UUID id=UUID.randomUUID();ReflectionTestUtils.setField(fixture.product(),"id",id);
        fixture.variant().setSellingPrice(java.math.BigDecimal.ZERO);
        when(products.findPublishedForShare(id)).thenReturn(Optional.of(view(fixture.product())));
        when(variants.findPublicVariants(id)).thenReturn(List.of(variantView(fixture.variant())));
        assertThatThrownBy(() -> service.getProduct(id)).isInstanceOf(com.example.demo.catalog.exception.CatalogConflictException.class);
    }

    private java.util.List<com.example.demo.catalog.repository.CatalogPublicViews.Product> views(List<Product> rows){return rows.stream().map(this::view).toList();}
    private com.example.demo.catalog.repository.CatalogPublicViews.Product view(Product p){
        return new org.springframework.data.projection.SpelAwareProxyProjectionFactory().createProjection(
                com.example.demo.catalog.repository.CatalogPublicViews.Product.class,
                Map.of("id",p.getId(),"name",p.getName(),"categoryId",UUID.randomUUID(),"categoryName",p.getCategory().getName()));
    }
    private com.example.demo.catalog.repository.CatalogPublicViews.Variant variantView(com.example.demo.catalog.entity.ProductVariant v){
        return new org.springframework.data.projection.SpelAwareProxyProjectionFactory().createProjection(
                com.example.demo.catalog.repository.CatalogPublicViews.Variant.class,
                Map.of("id",v.getId()==null?UUID.randomUUID():v.getId(),"sku",v.getSku(),"enabled",v.isEnabled(),"price",v.getSellingPrice(),"options",v.getOptionCombination(),"specifications",v.getTechnicalSpecifications()));
    }
    private com.example.demo.catalog.repository.CatalogPublicViews.Image imageView(ProductImage v){
        return new org.springframework.data.projection.SpelAwareProxyProjectionFactory().createProjection(
                com.example.demo.catalog.repository.CatalogPublicViews.Image.class,
                Map.of("id",v.getId(),"objectKey",v.getObjectKey(),"altText",v.getAltText(),"width",v.getWidth(),"height",v.getHeight(),"displayOrder",v.getDisplayOrder(),"primaryImage",v.isPrimary()));
    }

    private final Map<UUID,String> currentKeys=new java.util.HashMap<>();
    private void stubImages(List<ProductImage> rows){
        rows.forEach(i -> currentKeys.put(i.getProduct().getId(),i.getObjectKey()));
        when(images.findCartPrimaryImages(anyCollection())).thenReturn(rows.stream().map(i -> new Object[]{i.getProduct().getId(),i.getObjectKey()}).toList());
    }
    private com.example.demo.catalog.repository.ProductSearchRepository.CurrentCandidate current(Product p,UUID variant,java.math.BigInteger price){
        return new com.example.demo.catalog.repository.ProductSearchRepository.CurrentCandidate(p.getId(),variant,price,p.getName(),p.getCategory().getId(),p.getCategory().getName(),currentKeys.get(p.getId()));
    }

    private Product product() {
        Product product = CatalogFixtures.publishedProduct().product();
        ReflectionTestUtils.setField(product, "id", UUID.randomUUID());
        return product;
    }
}
