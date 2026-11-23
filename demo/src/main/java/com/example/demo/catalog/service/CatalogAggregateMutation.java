package com.example.demo.catalog.service;

import com.example.demo.catalog.entity.Product;
import com.example.demo.catalog.exception.AdminCatalogException;
import com.example.demo.catalog.repository.CatalogMutationRepository;
import com.example.demo.catalog.repository.CatalogMutationRepository.Resource;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Internal transaction boundary, not an HTTP mutation API or a second lifecycle implementation. */
@Component
public class CatalogAggregateMutation {
    private final CatalogMutationRepository repository;
    private final TransactionTemplate transactions;

    public CatalogAggregateMutation(CatalogMutationRepository repository, PlatformTransactionManager manager) {
        this.repository = repository;
        this.transactions = new TransactionTemplate(manager);
        transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        transactions.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    /** noOp/unchanged are pure reads; all business/child changes belong ONLY in apply. */
    public interface ProductCommand<T> {
        boolean noOp(Product current);
        T unchanged(Product current);
        T apply(Product refreshed);
    }

    public <T> T product(UUID productId, long expectedVersion, long expectedTaxonomyVersion, ProductCommand<T> command) {
        if (expectedVersion < 0 || expectedTaxonomyVersion < 0) {
            throw new AdminCatalogException(400, "CATALOG_INVALID_REQUEST", "Invalid revision");
        }
        requireCompatibleTransaction();
        // TransactionTemplate returns only after root commit. If joining REQUIRED, the owning
        // public service proxy must finish the outer transaction before HTTP exposes its DTO.
        return transactions.execute(status -> {
            compare(expectedTaxonomyVersion, repository.lockTaxonomy(false), true);
            compare(expectedVersion, repository.lockVersions(Resource.PRODUCT, List.of(productId), true).get(productId), false);
            Product current = repository.freshProduct(productId);
            if (command.noOp(current)) return command.unchanged(current);
            repository.claim(Resource.PRODUCT, productId, expectedVersion);
            T result = command.apply(repository.freshProduct(productId));
            repository.flush();
            return result;
        });
    }

    /** Read locks use a write-capable transaction because PostgreSQL FOR SHARE is required. */
    public <T> T read(UUID productId, java.util.function.BiFunction<Long, Product, T> reader) {
        requireCompatibleTransaction();
        return transactions.execute(status -> {
            long taxonomy = repository.lockTaxonomy(false);
            repository.lockVersions(Resource.PRODUCT, List.of(productId), false);
            return reader.apply(taxonomy, repository.freshProduct(productId));
        });
    }

    /** New Draft has no old Product revision; current references remain protected by the gate. */
    public <T> T create(java.util.function.LongFunction<T> command) {
        requireCompatibleTransaction();
        return transactions.execute(status -> command.apply(repository.lockTaxonomy(false)));
    }

    static void requireCompatibleTransaction() {
        if (TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                || TransactionSynchronizationManager.getCurrentTransactionIsolationLevel() != null
                && TransactionSynchronizationManager.getCurrentTransactionIsolationLevel() != TransactionDefinition.ISOLATION_READ_COMMITTED) {
            throw new AdminCatalogException(503, "CATALOG_MAINTENANCE_REQUIRED", "Incompatible catalog transaction");
        }
    }

    private static void compare(long expected, long actual, boolean taxonomy) {
        if (expected != actual) throw new AdminCatalogException(409, "CATALOG_STALE_VERSION", "Catalog changed; reload before retrying",
                Map.of(taxonomy ? "expectedTaxonomyVersion" : "expectedVersion", Long.toString(expected),
                        taxonomy ? "currentTaxonomyVersion" : "currentVersion", Long.toString(actual)));
    }
}
