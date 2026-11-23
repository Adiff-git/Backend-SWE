package com.example.demo.catalog.repository;

import com.example.demo.catalog.entity.Product;
import com.example.demo.catalog.exception.AdminCatalogException;
import jakarta.persistence.EntityManager;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Fixed catalog SQL, called only inside an owning service's ordered write transaction. */
@Repository
@RequiredArgsConstructor
public class CatalogMutationRepository {
    public enum Resource {
        PRODUCT("products"), CATEGORY("categories"), BRAND("brands");
        private final String table;
        Resource(String table) { this.table = table; }
    }
    private final EntityManager entities;
    public static final Comparator<UUID> UUID_ORDER = Comparator.comparing(UUID::toString);

    public long lockTaxonomy(boolean exclusive) {
        requireWriteTransaction();
        List<?> rows = entities.createNativeQuery("SELECT version FROM catalog_taxonomy_state WHERE id = 1 FOR "
                + (exclusive ? "UPDATE" : "SHARE")).getResultList();
        if (rows.size() != 1) throw maintenance();
        return revision(rows.getFirst());
    }

    public Map<UUID, Long> lockVersions(Resource resource, Collection<UUID> ids, boolean exclusive) {
        requireWriteTransaction();
        if (ids.isEmpty()) return Map.of();
        List<UUID> ordered = ids.stream().distinct().sorted(UUID_ORDER).toList();
        List<?> rows = entities.createNativeQuery("SELECT id, version FROM " + resource.table
                + " WHERE id IN (:ids) ORDER BY id FOR " + (exclusive ? "UPDATE" : "SHARE"))
                .setParameter("ids", ordered).getResultList();
        Map<UUID, Long> result = new LinkedHashMap<>();
        for (Object row : rows) {
            Object[] pair = (Object[]) row;
            result.put((UUID) pair[0], revision(pair[1]));
        }
        if (result.size() != ordered.size()) throw new AdminCatalogException(404, "CATALOG_RESOURCE_NOT_FOUND", "Catalog resource not found");
        return result;
    }

    public void claim(Resource resource, UUID id, long expected) {
        requireWriteTransaction();
        requireIncrementable(expected);
        entities.flush();
        int updated = entities.createNativeQuery("UPDATE " + resource.table
                + " SET version = version + 1 WHERE id = :id AND version = :expected")
                .setParameter("id", id).setParameter("expected", expected).executeUpdate();
        if (updated != 1) throw new AdminCatalogException(409, "CATALOG_STALE_VERSION", "Catalog changed; reload before retrying");
    }

    public void claimTaxonomy(long expected) {
        requireWriteTransaction();
        requireIncrementable(expected);
        int updated = entities.createNativeQuery("UPDATE catalog_taxonomy_state SET version = version + 1 WHERE id = 1 AND version = :expected")
                .setParameter("expected", expected).executeUpdate();
        if (updated != 1) throw new AdminCatalogException(409, "CATALOG_STALE_VERSION", "Taxonomy changed; reload before retrying");
    }

    public Product freshProduct(UUID id) {
        Product product = entities.find(Product.class, id);
        if (product == null) throw new AdminCatalogException(404, "CATALOG_RESOURCE_NOT_FOUND", "Product not found");
        entities.refresh(product);
        return product;
    }

    /** The caller has already claimed Product before invoking either owned child lock. */
    public void lockVariants(UUID productId, Collection<UUID> ids) { lockChildren("product_variants", productId, ids); }
    public void lockImages(UUID productId, Collection<UUID> ids) { lockChildren("product_images", productId, ids); }

    private void lockChildren(String table, UUID productId, Collection<UUID> ids) {
        requireWriteTransaction();
        if (ids.isEmpty()) return;
        List<UUID> ordered = ids.stream().distinct().sorted(UUID_ORDER).toList();
        List<?> rows = entities.createNativeQuery("SELECT id FROM " + table
                + " WHERE product_id = :product AND id IN (:ids) ORDER BY id FOR UPDATE")
                .setParameter("product", productId).setParameter("ids", ordered).getResultList();
        if (rows.size() != ordered.size()) throw new AdminCatalogException(404, "CATALOG_RESOURCE_NOT_FOUND", "Owned catalog resource not found");
    }

    public void flush() { entities.flush(); }

    /** Re-read managed siblings after the owning aggregate lock, never trust an old context snapshot. */
    public void refresh(Object entity) { entities.refresh(entity); }

    private static long revision(Object value) {
        if (!(value instanceof Number number) || number.longValue() < 0) throw maintenance();
        return number.longValue();
    }

    private static void requireIncrementable(long expected) {
        if (expected < 0) throw new AdminCatalogException(400, "CATALOG_INVALID_REQUEST", "Invalid revision");
        if (expected == Long.MAX_VALUE) throw maintenance();
    }

    private static void requireWriteTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()) throw maintenance();
    }

    private static AdminCatalogException maintenance() {
        return new AdminCatalogException(503, "CATALOG_MAINTENANCE_REQUIRED", "Catalog revision service is unavailable");
    }
}
