package com.example.demo.wishlist.service.impl;

import com.example.demo.catalog.dto.response.ProductSummary;
import com.example.demo.catalog.exception.ProductNotFoundException;
import com.example.demo.catalog.service.ProductCatalogService;
import com.example.demo.common.identity.IdentityUnavailableException;
import com.example.demo.user.exception.ResourceNotFoundException;
import com.example.demo.user.service.UserService;
import com.example.demo.wishlist.converter.WishlistConverter;
import com.example.demo.wishlist.exception.WishlistConflictException;
import com.example.demo.wishlist.repository.WishlistRepository;
import com.example.demo.wishlist.service.AddWishlistResult;
import com.example.demo.wishlist.service.WishlistService;
import com.example.demo.wishlist.validator.WishlistValidator;
import com.example.demo.wishlist.dto.response.WishlistItemResponse;
import com.example.demo.wishlist.dto.response.WishlistPage;
import com.example.demo.wishlist.repository.WishlistMembershipView;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.ArrayList;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

@Service
@RequiredArgsConstructor
public class WishlistServiceImpl implements WishlistService {
    private final WishlistRepository repository;
    private final UserService users;
    private final ProductCatalogService catalog;
    private final WishlistValidator validator;
    private final WishlistConverter converter;
    private final Clock clock;

    @Override
    @Transactional
    public void removeProduct(Long ownerId, UUID productId) {
        validator.validateOwner(ownerId);
        repository.deleteOwned(ownerId, productId);
    }

    @Override
    @Transactional
    public AddWishlistResult addProduct(Long ownerId, UUID productId) {
        validator.validateOwner(ownerId);
        try {
            users.getUserEntity(ownerId);
        } catch (ResourceNotFoundException failure) {
            throw new IdentityUnavailableException();
        }
        catalog.getPublishedProductEntity(productId);
        boolean created = repository.insertIfAbsent(ownerId, productId, clock.instant()) == 1;
        // A separate READ COMMITTED statement sees a committed concurrent winner.
        Instant addedAt = repository.findAddedAt(ownerId, productId).orElseThrow(WishlistConflictException::new);
        ProductSummary summary = catalog.getProductSummaries(List.of(productId)).get(productId);
        if (summary == null) {
            throw new ProductNotFoundException();
        }
        return new AddWishlistResult(created, converter.toItem(addedAt, summary));
    }

    @Override
    @Transactional // Non-mutating operation; catalog signing requires a write-capable transaction.
    public WishlistPage listWishlist(Long ownerId, int page, int size) {
        validator.validateOwner(ownerId);
        validator.validatePage(page, size);
        Page<WishlistMembershipView> memberships = repository.findVisibleMemberships(ownerId, PageRequest.of(page, size));
        List<UUID> ids = memberships.getContent().stream().map(WishlistMembershipView::getProductId).toList();
        Map<UUID, ProductSummary> summaries = ids.isEmpty() ? Map.of() : catalog.getProductSummaries(ids);
        List<WishlistItemResponse> items = new ArrayList<>();
        for (WishlistMembershipView membership : memberships.getContent()) {
            ProductSummary summary = summaries.get(membership.getProductId());
            if (summary == null) {
                throw new WishlistConflictException();
            }
            items.add(converter.toItem(membership.getAddedAt(), summary));
        }
        return converter.toPage(items, page, size, memberships.getTotalElements(), memberships.getTotalPages());
    }
}
