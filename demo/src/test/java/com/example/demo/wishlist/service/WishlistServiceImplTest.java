package com.example.demo.wishlist.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.demo.catalog.dto.response.CategorySummary;
import com.example.demo.catalog.dto.response.ProductSummary;
import com.example.demo.catalog.entity.Product;
import com.example.demo.catalog.exception.CatalogStorageUnavailableException;
import com.example.demo.catalog.exception.ProductNotFoundException;
import com.example.demo.catalog.service.ProductCatalogService;
import com.example.demo.common.identity.IdentityUnavailableException;
import com.example.demo.user.entity.User;
import com.example.demo.user.exception.ResourceNotFoundException;
import com.example.demo.user.service.UserService;
import com.example.demo.wishlist.converter.WishlistConverter;
import com.example.demo.wishlist.exception.WishlistConflictException;
import com.example.demo.wishlist.repository.WishlistRepository;
import com.example.demo.wishlist.service.impl.WishlistServiceImpl;
import com.example.demo.wishlist.validator.WishlistValidator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WishlistServiceImplTest {
    private final WishlistRepository repository = mock(WishlistRepository.class);
    private final UserService users = mock(UserService.class);
    private final ProductCatalogService catalog = mock(ProductCatalogService.class);
    private final Instant now = Instant.parse("2026-10-31T01:00:00Z");
    private final UUID productId = UUID.randomUUID();
    private final WishlistServiceImpl service = new WishlistServiceImpl(repository, users, catalog,
            new WishlistValidator(), new WishlistConverter(), Clock.fixed(now, ZoneOffset.UTC), mock(com.example.demo.behavior.BehaviorService.class));

    @BeforeEach
    void prepare() {
        when(users.getUserEntity(1L)).thenReturn(new User());
        when(catalog.getPublishedProductEntity(productId)).thenReturn(mock(Product.class));
    }

    @Test
    void addProduct_shouldReturnCreatedItem_whenFirstInsertionSucceeds() {
        prepareSavedItem(1, now);
        AddWishlistResult result = service.addProduct(1L, productId);
        assertThat(result.isCreated()).isTrue();
        assertThat(result.getItem().getAddedAt()).isEqualTo(now);
        assertThat(result.getItem().getProduct().getId()).isEqualTo(productId);
    }

    @Test
    void addProduct_shouldPreserveOriginalTime_whenDuplicateIsIgnored() {
        Instant original = now.minusSeconds(60);
        prepareSavedItem(0, original);
        AddWishlistResult result = service.addProduct(1L, productId);
        assertThat(result.isCreated()).isFalse();
        assertThat(result.getItem().getAddedAt()).isEqualTo(original);
    }

    @Test
    void addProduct_shouldRejectUnavailableProductBeforeInsert_whenHiddenOrMissing() {
        when(catalog.getPublishedProductEntity(productId)).thenThrow(new ProductNotFoundException());
        assertThatThrownBy(() -> service.addProduct(1L, productId)).isInstanceOf(ProductNotFoundException.class);
        verify(repository, never()).insertIfAbsent(any(), any(), any());
    }

    @Test
    void addProduct_shouldRejectMissingCurrentAccount_whenUserDisappears() {
        when(users.getUserEntity(1L)).thenThrow(new ResourceNotFoundException());
        assertThatThrownBy(() -> service.addProduct(1L, productId)).isInstanceOf(IdentityUnavailableException.class);
        verify(repository, never()).insertIfAbsent(any(), any(), any());
    }

    @Test
    void addProduct_shouldReportConflict_whenWinningMembershipWasRemoved() {
        when(repository.findAddedAt(1L, productId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.addProduct(1L, productId)).isInstanceOf(WishlistConflictException.class);
    }

    @Test
    void addProduct_shouldRejectLateHiddenProduct_whenSummaryIsUnavailable() {
        when(repository.findAddedAt(1L, productId)).thenReturn(Optional.of(now));
        when(catalog.getProductSummaries(List.of(productId))).thenReturn(Map.of());
        assertThatThrownBy(() -> service.addProduct(1L, productId)).isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void addProduct_shouldSurfaceSafeSigningFailure_whenPreparationFails() {
        when(repository.findAddedAt(1L, productId)).thenReturn(Optional.of(now));
        when(catalog.getProductSummaries(List.of(productId))).thenThrow(new CatalogStorageUnavailableException());
        assertThatThrownBy(() -> service.addProduct(1L, productId))
                .isInstanceOf(CatalogStorageUnavailableException.class);
    }

    @Test
    void removeProduct_shouldDeleteOnlyOwnedReferenceWithoutCatalogLookup_whenEntryIsHiddenOrMissing() {
        service.removeProduct(1L, productId);
        verify(repository).deleteOwned(1L, productId);
        verifyNoInteractions(users, catalog);
    }

    @Test
    void removeProduct_shouldRejectUntrustedOwner_whenIdentityIsAbsent() {
        assertThatThrownBy(() -> service.removeProduct(null, productId)).isInstanceOf(IdentityUnavailableException.class);
        verifyNoInteractions(repository);
    }

    private void prepareSavedItem(int inserted, Instant addedAt) {
        when(repository.insertIfAbsent(1L, productId, now)).thenReturn(inserted);
        when(repository.findAddedAt(1L, productId)).thenReturn(Optional.of(addedAt));
        when(catalog.getProductSummaries(List.of(productId))).thenReturn(Map.of(productId,
                new ProductSummary(productId, "Product", new CategorySummary(UUID.randomUUID(), "Category"), null)));
    }
}
