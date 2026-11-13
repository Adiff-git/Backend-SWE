package com.example.demo.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.demo.catalog.CatalogFixtures;
import com.example.demo.catalog.converter.ProductCatalogConverter;
import com.example.demo.catalog.dto.request.ProductSearchRequest;
import com.example.demo.catalog.dto.response.ProductListingItem;
import com.example.demo.catalog.dto.response.ProductPage;
import com.example.demo.catalog.entity.Product;
import com.example.demo.catalog.entity.ProductImage;
import com.example.demo.catalog.entity.ProductStatus;
import com.example.demo.catalog.exception.CatalogStorageUnavailableException;
import com.example.demo.catalog.repository.ProductImageRepository;
import com.example.demo.catalog.repository.ProductRepository;
import com.example.demo.catalog.repository.ProductVariantRepository;
import com.example.demo.catalog.search.ProductCandidatePage;
import com.example.demo.catalog.search.ProductCandidatePage.Candidate;
import com.example.demo.catalog.search.ProductSearchCriteria;
import com.example.demo.catalog.search.ProductSearchNormalizer;
import com.example.demo.catalog.storage.ProductImageStorage;
import com.example.demo.catalog.storage.ProductImageStorage.PreparedReadSigner;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

/** Service-only orchestration;mocked candidate eligibility is NOT repository/search acceptance. */
class ProductKeywordSearchServiceImplTest {
    private static final ValidatorFactory VALIDATION = Validation.buildDefaultValidatorFactory();
    private final ProductRepository products = mock(ProductRepository.class);
    private final ProductImageRepository images = mock(ProductImageRepository.class);
    private final ProductVariantRepository variants = mock(ProductVariantRepository.class);
    private final ProductImageStorage storage = mock(ProductImageStorage.class);
    private final ProductCatalogServiceImpl service = new ProductCatalogServiceImpl(
            products, images, variants, storage, new ProductCatalogConverter(), VALIDATION.getValidator());

    @AfterAll
    static void closeValidation() { VALIDATION.close(); }

    @AfterEach
    void verifyNoVariantReselection() {
        verifyNoInteractions(variants);
        verify(products, never()).findByIdAndStatusWithCategory(any(), any());
    }

    @Test
    void listProducts_shouldMapCheapestEligibleProjection_whenKeywordIsProductOnly() {
        var fixture = fixture("Điện thoại");
        ReflectionTestUtils.setField(fixture.variant(), "id", UUID.randomUUID());
        fixture.variant().setSellingPrice(BigDecimal.valueOf(1000));
        var expensive = CatalogFixtures.variant(fixture.product(), Map.of("choice", "large"), BigDecimal.valueOf(9000));
        ReflectionTestUtils.setField(expensive, "id", UUID.randomUUID());
        selected(List.of(new Candidate(fixture.product().getId(), fixture.variant().getId(), BigInteger.valueOf(1000))),
                List.of(fixture.product()));

        ProductPage page = service.listProducts(request("Điện thoại"));

        ProductListingItem item = item(page, 0);
        assertThat(item.getRepresentativeVariantId()).isEqualTo(fixture.variant().getId());
        assertThat(item.getDisplayPrice().getAmount()).isEqualTo(BigInteger.valueOf(1000));
        assertThat(item.getDisplayPrice().getCurrency()).isEqualTo("VND");
        assertThat(item.getRepresentativeVariantId()).isNotEqualTo(expensive.getId());
        verifyNoInteractions(storage);
    }

    @Test
    void listProducts_shouldKeepCostlierSkuEligibleVariant_insteadOfReloadingCheapestOverall() {
        var fixture = fixture("Phone");
        ReflectionTestUtils.setField(fixture.variant(), "id", UUID.randomUUID());
        fixture.variant().setSellingPrice(BigDecimal.ONE); // Cheaper overall,but not the repository-selected SKU.
        var matching = CatalogFixtures.variant(fixture.product(), Map.of("choice", "sku-match"),
                new BigDecimal("123456789012345678901234567890"));
        ReflectionTestUtils.setField(matching, "id", UUID.randomUUID());
        matching.setSku("SKU-MODEL-256"); // Synthetic structural SKU,not a vocabulary approval.
        selected(List.of(new Candidate(fixture.product().getId(), matching.getId(), matching.getSellingPrice().toBigIntegerExact())),
                List.of(fixture.product()));

        ProductPage page = service.listProducts(request(matching.getSku()));

        assertThat(item(page, 0).getRepresentativeVariantId()).isEqualTo(matching.getId());
        assertThat(item(page, 0).getDisplayPrice().getAmount()).isEqualTo(matching.getSellingPrice().toBigIntegerExact());
        assertThat(item(page, 0).getRepresentativeVariantId()).isNotEqualTo(fixture.variant().getId());
        var criteria = criteria();
        assertThat(criteria.getKeyword().getTokens()).extracting(ProductSearchNormalizer.Token::getText)
                .containsExactly("sku-model-256");
        assertThat(criteria.getKeyword().getTokens()).allMatch(ProductSearchNormalizer.Token::isStructurallyProtected);
    }

    @Test
    void listProducts_shouldFollowCandidateOrder_whenBatchRowsArriveInDifferentOrder() {
        var first = fixture("First");
        var second = fixture("Second");
        UUID firstVariant = UUID.randomUUID();
        UUID secondVariant = UUID.randomUUID();
        selected(List.of(new Candidate(second.product().getId(), secondVariant, BigInteger.TEN),
                new Candidate(first.product().getId(), firstVariant, BigInteger.ONE)),
                List.of(first.product(), second.product()));

        ProductPage page = service.listProducts(request("phone"));

        assertThat(page.getItems()).extracting(summary -> summary.getId())
                .containsExactly(second.product().getId(), first.product().getId());
        assertThat(item(page, 0).getRepresentativeVariantId()).isEqualTo(secondVariant);
        assertThat(item(page, 1).getRepresentativeVariantId()).isEqualTo(firstVariant);
        assertThat(item(page, 0).getDisplayPrice().getAmount()).isEqualTo(BigInteger.TEN);
        verify(images).findPrimaryImagesForProducts(List.of(second.product().getId(), first.product().getId()));
        // Initial batch plus mandatory final imageless publication recheck;both stay batched.
        verify(products, org.mockito.Mockito.times(2)).findAllByIdsAndStatusWithCategory(
                List.of(second.product().getId(), first.product().getId()), ProductStatus.PUBLISHED);
    }

    @Test
    void listProducts_shouldNormalizeAndDeduplicateMembership_withoutLosingPhraseSequence() {
        when(products.findPublishedCandidates(any())).thenReturn(new ProductCandidatePage(List.of(), 7));
        var request = request("  ĐIỆN điện thoại Điện  ");
        request.setPage(5);
        request.setSize(2);

        ProductPage page = service.listProducts(request);

        var criteria = criteria();
        assertThat(criteria.getKeyword().getOriginalNormalized()).isEqualTo("dien dien thoai dien");
        assertThat(criteria.getKeyword().getSequence()).extracting(ProductSearchNormalizer.Token::getText)
                .containsExactly("dien", "dien", "thoai", "dien");
        assertThat(criteria.getKeyword().getTokens()).extracting(ProductSearchNormalizer.Token::getText)
                .containsExactly("dien", "thoai");
        assertThat(criteria.getPage()).isEqualTo(5);
        assertThat(criteria.getSize()).isEqualTo(2);
        assertThat(criteria.getSpecificationGroups()).isNull();
        assertThat(page.getItems()).isEmpty();
        assertThat(page.getTotalItems()).isEqualTo(7);
        assertThat(page.getTotalPages()).isEqualTo(4);
        verifyNoInteractions(images, storage);
    }

    @Test
    void listProducts_shouldKeepPairedNullMetadata_whenRepositoryReturnsSharedOnlyOrphan() {
        var fixture = fixture("Máy tính");
        // This in-memory Product has no persisted relationships;eligibility is the repository's responsibility.
        selected(List.of(new Candidate(fixture.product().getId(), null, null)), List.of(fixture.product()));

        ProductPage page = service.listProducts(request("máy tính"));

        assertThat(item(page, 0).getId()).isEqualTo(fixture.product().getId());
        assertThat(item(page, 0).getRepresentativeVariantId()).isNull();
        assertThat(item(page, 0).getDisplayPrice()).isNull();
        assertThat(page.getTotalItems()).isOne();
        verifyNoInteractions(storage);
    }

    @Test
    void listProducts_shouldPrepareThenLockAndSignOnlyPublishedCandidate_whenStatusChanges() {
        var visible = fixture("Visible");
        var hidden = fixture("Hidden");
        ProductImage visibleImage = CatalogFixtures.image(visible.product(), 0, true);
        ProductImage hiddenImage = CatalogFixtures.image(hidden.product(), 0, true);
        UUID selectedVariant = UUID.randomUUID();
        selected(List.of(new Candidate(visible.product().getId(), selectedVariant, BigInteger.TEN),
                new Candidate(hidden.product().getId(), UUID.randomUUID(), BigInteger.ONE)),
                List.of(hidden.product(), visible.product()));
        when(images.findPrimaryImagesForProducts(anyCollection())).thenReturn(List.of(hiddenImage, visibleImage));
        when(products.lockStatusesForShare(anyCollection())).thenReturn(List.of(
                new Object[]{visible.product().getId(), "PUBLISHED"}, new Object[]{hidden.product().getId(), "ARCHIVED"}));
        PreparedReadSigner signer = mock(PreparedReadSigner.class);
        when(storage.prepareReadSigner()).thenReturn(signer);
        when(signer.signReadUrl(visibleImage.getObjectKey())).thenReturn("https://images.test/selected");

        ProductPage page = service.listProducts(request("phone"));

        assertThat(page.getItems()).hasSize(1);
        assertThat(item(page, 0).getId()).isEqualTo(visible.product().getId());
        assertThat(item(page, 0).getRepresentativeVariantId()).isEqualTo(selectedVariant);
        assertThat(item(page, 0).getPrimaryImageUrl()).isEqualTo("https://images.test/selected");
        assertThat(page.getTotalItems()).isEqualTo(2); // Eligibility snapshot;publication suppression is fail-closed.
        var order = inOrder(storage, products, signer);
        order.verify(storage).prepareReadSigner();
        order.verify(products).lockStatusesForShare(anyCollection());
        order.verify(signer).signReadUrl(visibleImage.getObjectKey());
        order.verify(signer).close();
        verify(signer, never()).signReadUrl(hiddenImage.getObjectKey());
    }

    @Test
    void listProducts_shouldAvoidLocksAndRetry_whenSignerPreparationFails() {
        var fixture = fixture("Phone");
        selected(List.of(new Candidate(fixture.product().getId(), UUID.randomUUID(), BigInteger.TEN)), List.of(fixture.product()));
        when(images.findPrimaryImagesForProducts(anyCollection()))
                .thenReturn(List.of(CatalogFixtures.image(fixture.product(), 0, true)));
        when(storage.prepareReadSigner()).thenThrow(new CatalogStorageUnavailableException());

        assertThatThrownBy(() -> service.listProducts(request("phone"))).isInstanceOf(CatalogStorageUnavailableException.class);

        verify(products, never()).lockStatusesForShare(anyCollection());
        verify(storage).prepareReadSigner();
        verify(products).findPublishedCandidates(any());
    }

    @Test
    void listProducts_shouldCloseSignerAndPropagateFailure_whenLocalSigningFails() {
        var fixture = fixture("Phone");
        ProductImage image = CatalogFixtures.image(fixture.product(), 0, true);
        selected(List.of(new Candidate(fixture.product().getId(), UUID.randomUUID(), BigInteger.TEN)), List.of(fixture.product()));
        when(images.findPrimaryImagesForProducts(anyCollection())).thenReturn(List.of(image));
        when(products.lockStatusesForShare(anyCollection())).thenReturn(
                List.<Object[]>of(new Object[]{fixture.product().getId(), "PUBLISHED"}));
        PreparedReadSigner signer = mock(PreparedReadSigner.class);
        when(storage.prepareReadSigner()).thenReturn(signer);
        when(signer.signReadUrl(image.getObjectKey())).thenThrow(new CatalogStorageUnavailableException());

        assertThatThrownBy(() -> service.listProducts(request("phone"))).isInstanceOf(CatalogStorageUnavailableException.class);

        verify(signer).close();
        verify(signer).signReadUrl(image.getObjectKey());
        verify(storage).prepareReadSigner();
    }

    private CatalogFixtures.ProductFixture fixture(String name) {
        var fixture = CatalogFixtures.publishedProduct();
        ReflectionTestUtils.setField(fixture.product(), "id", UUID.randomUUID());
        ReflectionTestUtils.setField(fixture.category(), "id", UUID.randomUUID());
        fixture.product().setName(name);
        return fixture;
    }

    private ProductSearchRequest request(String keyword) {
        var request = new ProductSearchRequest();
        request.setQ(keyword);
        return request;
    }

    private void selected(List<Candidate> candidates, List<Product> batchRows) {
        when(products.findPublishedCandidates(any())).thenReturn(new ProductCandidatePage(candidates, candidates.size()));
        when(products.findAllByIdsAndStatusWithCategory(anyCollection(), eq(ProductStatus.PUBLISHED))).thenReturn(batchRows);
    }

    private ProductSearchCriteria criteria() {
        var captor = ArgumentCaptor.forClass(ProductSearchCriteria.class);
        verify(products).findPublishedCandidates(captor.capture());
        return captor.getValue();
    }

    private ProductListingItem item(ProductPage page, int index) {
        return (ProductListingItem) page.getItems().get(index);
    }
}
