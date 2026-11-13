package com.example.demo.behavior;

import java.time.*;
import java.util.*;
import com.example.demo.catalog.search.ProductSearchNormalizer;
import com.example.demo.catalog.service.ProductCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BehaviorService {
    private final BehaviorStore store;
    private final ProductCatalogService catalog;
    private final Clock clock;
    public static final Duration RETENTION = Duration.ofDays(90);

    @Transactional
    public boolean ingest(Long owner, BehaviorRequest event) {
        if (owner == null) throw new BehaviorException(401, "Authentication required");
        String normalized = validate(event);
        // Check retry before current publication status: a previously accepted event remains retryable.
        var previous = store.retry(owner, event.clientEventId());
        if (previous.isPresent()) { identical(previous.get(), event); return false; }
        if (event.productId() != null) catalog.getPublishedProductEntity(event.productId());
        boolean created = store.insert(owner, event, normalized, clock.instant());
        if (!created) identical(store.retry(owner, event.clientEventId()).orElseThrow(
            () -> new BehaviorException(409, "Retry event submission")), event);
        return created;
    }
    String validate(BehaviorRequest event) {
        if (event == null || event.eventType() == null || event.clientEventId() == null)
            throw new BehaviorException(400, "eventType and clientEventId are required");
        switch (event.eventType()) {
            case SEARCH -> {
                if (event.productId() != null || event.sourceContext() != null || event.searchQuery() == null)
                    throw new BehaviorException(400, "Invalid search event fields");
                String query = new ProductSearchNormalizer().normalizeQuery(event.searchQuery()).getOriginalNormalized();
                if (query.isBlank()) throw new BehaviorException(400, "Search query must not be blank");
                return query;
            }
            case PRODUCT_VIEW, PRODUCT_CLICK -> {
                if (event.productId() == null || event.searchQuery() != null
                    || event.eventType() == BehaviorType.PRODUCT_VIEW && event.sourceContext() != null)
                    throw new BehaviorException(400, "Invalid product event fields");
            }
            default -> throw new BehaviorException(400, "Event type is not accepted from clients");
        }
        return null;
    }
    private void identical(BehaviorEvent previous, BehaviorRequest event) {
        if (previous.eventType() != event.eventType() || !Objects.equals(previous.productId(), event.productId())
            || !Objects.equals(previous.searchQuery(), event.searchQuery()) || previous.sourceContext() != event.sourceContext())
            throw new BehaviorException(409, "clientEventId was already used for a different event");
    }
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void wishlist(Long owner, UUID product, BehaviorType type) {
        if (type != BehaviorType.WISHLIST_ADD && type != BehaviorType.WISHLIST_REMOVE) throw new IllegalArgumentException("Invalid wishlist type");
        store.insert(owner, new BehaviorRequest(type, product, null, null, null), null, clock.instant());
    }
    @Transactional(readOnly = true)
    public BehaviorPage history(Long owner, BehaviorType type, Instant from, Instant to, int page, int size) {
        if (owner == null) throw new BehaviorException(401, "Authentication required");
        if (page < 0 || page > 100000 || size < 1 || size > 100) throw new BehaviorException(400, "Invalid pagination");
        Instant now = clock.instant(), cutoff = now.minus(RETENTION);
        if (from == null) from = cutoff;
        if (to == null) to = now;
        if (from.isAfter(to) || Duration.between(from, to).compareTo(RETENTION) > 0 || to.isAfter(now))
            throw new BehaviorException(400, "Date range must be ordered, not future, and at most 90 days");
        return store.history(owner, type, from.isBefore(cutoff) ? cutoff : from, to, page, size);
    }
    @Transactional(readOnly = true)
    public BehaviorOverview overview(int days) {
        if (days != 7 && days != 30 && days != 90) throw new BehaviorException(400, "days must be 7, 30 or 90");
        Instant now = clock.instant();
        return store.overview(now.minus(Duration.ofDays(days)), now, days);
    }
}
