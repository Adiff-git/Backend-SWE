package com.example.demo.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.demo.catalog.CatalogFixtures;
import com.example.demo.catalog.converter.ProductCatalogConverter;
import com.example.demo.catalog.entity.ProductStatus;
import com.example.demo.catalog.exception.CatalogStorageUnavailableException;
import com.example.demo.catalog.exception.ProductNotFoundException;
import com.example.demo.catalog.repository.ProductImageRepository;
import com.example.demo.catalog.repository.ProductRepository;
import com.example.demo.catalog.storage.ProductImageStorage;
import com.example.demo.inventory.service.InventoryService;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Map;
import com.example.demo.catalog.dto.response.ProductPriceRange;
import com.example.demo.catalog.dto.response.SellingPrice;
import com.example.demo.catalog.dto.response.VariantPricing;
import com.example.demo.catalog.service.ProductCatalogService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class RecommendationHydratorTest {
    private final ProductRepository products=mock(ProductRepository.class);
    private final ProductImageRepository images=mock(ProductImageRepository.class);
    private final ProductImageStorage storage=mock(ProductImageStorage.class);
    private final ProductCatalogService catalog=mock(ProductCatalogService.class);
    private final InventoryService inventory=mock(InventoryService.class);
    private final RecommendationHydrator hydrator=new RecommendationHydrator(products,images,storage,new ProductCatalogConverter(),catalog,inventory);

    @Test void hydrate_shouldSignOnlyCurrentImageAndUseLockedRefreshedVariantPrice(){
        var product=CatalogFixtures.publishedProduct().product();UUID id=UUID.randomUUID(),variant=UUID.randomUUID();
        ReflectionTestUtils.setField(product,"id",id);
        when(images.findCartPrimaryImages(List.of(id))).thenReturn(List.<Object[]>of(new Object[]{id,"detached"}),List.<Object[]>of(new Object[]{id,"current"}));
        var signer=mock(ProductImageStorage.PreparedReadSigner.class);when(storage.prepareReadSigner()).thenReturn(signer);
        when(signer.signReadUrl("current")).thenReturn("signed-current");
        when(products.lockStatusesForShare(anyCollection())).thenReturn(List.<Object[]>of(new Object[]{id,"PUBLISHED"}));
        var current=new org.springframework.data.projection.SpelAwareProxyProjectionFactory().createProjection(
                com.example.demo.catalog.repository.CatalogPublicViews.Product.class,java.util.Map.of("id",id,"name",product.getName(),"categoryId",UUID.randomUUID(),"categoryName",product.getCategory().getName()));
        when(products.findPublicSummaries(List.of(id))).thenReturn(List.of(current));
        when(catalog.getPriceRangesByIds(List.of(id))).thenReturn(Map.of(id,range(variant,99)));
        var result=hydrator.hydrate(List.of(id),1);
        assertThat(result).hasSize(1);assertThat(result.getFirst().getPrimaryImageUrl()).isEqualTo("signed-current");
        assertThat(result.getFirst().getRepresentativeVariantId()).isEqualTo(variant);
        assertThat(result.getFirst().getDisplayPrice().getAmount()).isEqualTo(99);
        verify(signer,never()).signReadUrl("detached");
        var order=inOrder(storage,products,catalog,signer);order.verify(storage).prepareReadSigner();
        order.verify(products).lockStatusesForShare(anyCollection());order.verify(products).findPublicSummaries(List.of(id));
        order.verify(catalog).getPriceRangesByIds(List.of(id));order.verify(signer).signReadUrl("current");
    }

    @Test void hydrate_shouldSuppressProductWhenLastEnabledCandidateDisappears(){
        UUID id=UUID.randomUUID();
        when(products.lockStatusesForShare(anyCollection())).thenReturn(List.<Object[]>of(new Object[]{id,"PUBLISHED"}));
        when(catalog.getPriceRangesByIds(List.of(id))).thenReturn(Map.of());
        assertThat(hydrator.hydrate(List.of(id),1)).isEmpty();verify(inventory,never()).getAvailability(anyCollection());
    }

    @Test void hydrate_shouldRejectWithdrawnSourceEvenWhenAlternativesRemain(){
        UUID source=UUID.randomUUID();
        assertThatThrownBy(() -> hydrator.hydrate(List.of(),1,source)).isInstanceOf(ProductNotFoundException.class);
    }

    @Test void hydrate_shouldFailClosedIfGalleryAppearsAfterCredentialPeek(){
        UUID id=UUID.randomUUID();
        when(images.findCartPrimaryImages(List.of(id))).thenReturn(List.of(),List.<Object[]>of(new Object[]{id,"new"}));
        when(products.lockStatusesForShare(anyCollection())).thenReturn(List.<Object[]>of(new Object[]{id,"PUBLISHED"}));
        when(catalog.getPriceRangesByIds(List.of(id))).thenReturn(Map.of(id,range(UUID.randomUUID(),10)));
        assertThatThrownBy(() -> hydrator.hydrate(List.of(id),1)).isInstanceOf(CatalogStorageUnavailableException.class);
    }
    private ProductPriceRange range(UUID variant, long amount) {
        var price = new SellingPrice(BigInteger.valueOf(amount), "VND");
        var pricing = new VariantPricing(price, price, new SellingPrice(BigInteger.ZERO,"VND"),
                null, Instant.now(), null, null);
        var endpoint = new ProductPriceRange.Endpoint(variant, pricing);
        return new ProductPriceRange(endpoint, endpoint);
    }
}
