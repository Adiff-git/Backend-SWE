package com.example.demo.catalog.repository;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.demo.catalog.exception.AdminCatalogException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class CatalogMutationRepositoryTest {
    private final EntityManager entities = mock(EntityManager.class);
    private final CatalogMutationRepository repository = new CatalogMutationRepository(entities);

    @BeforeEach void writeTransactionMetadata() { TransactionSynchronizationManager.setActualTransactionActive(true); }
    @AfterEach void clearTransactionMetadata() { TransactionSynchronizationManager.clear(); }

    @Test
    void gate_shouldFailIfMissing_withoutCreatingOrResettingIt() {
        Query query = mock(Query.class);
        when(entities.createNativeQuery("SELECT version FROM catalog_taxonomy_state WHERE id = 1 FOR SHARE")).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());
        assertThatThrownBy(() -> repository.lockTaxonomy(false)).isInstanceOfSatisfying(AdminCatalogException.class,
                failure -> org.assertj.core.api.Assertions.assertThat(failure.getStatus()).isEqualTo(503));
        verify(query, never()).executeUpdate();
    }

    @Test
    void claim_shouldRejectOverflowOrReadOnlyBeforeAnySql() {
        assertThatThrownBy(() -> repository.claim(CatalogMutationRepository.Resource.PRODUCT, UUID.randomUUID(), Long.MAX_VALUE))
                .isInstanceOfSatisfying(AdminCatalogException.class,
                        failure -> org.assertj.core.api.Assertions.assertThat(failure.getStatus()).isEqualTo(503));
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);
        assertThatThrownBy(() -> repository.lockTaxonomy(false)).isInstanceOf(AdminCatalogException.class);
        verify(entities, never()).createNativeQuery(anyString());
    }

    @Test
    void claim_shouldBindObservedRevision_andRejectLostConditionalClaimWithoutRetry() {
        UUID id = UUID.randomUUID();
        Query query = mock(Query.class);
        when(entities.createNativeQuery("UPDATE products SET version = version + 1 WHERE id = :id AND version = :expected")).thenReturn(query);
        when(query.setParameter("id", id)).thenReturn(query);
        when(query.setParameter("expected", 7L)).thenReturn(query);
        when(query.executeUpdate()).thenReturn(0);
        assertThatThrownBy(() -> repository.claim(CatalogMutationRepository.Resource.PRODUCT, id, 7))
                .isInstanceOfSatisfying(AdminCatalogException.class,
                        failure -> org.assertj.core.api.Assertions.assertThat(failure.getCode()).isEqualTo("CATALOG_STALE_VERSION"));
        verify(query).executeUpdate();
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(entities, query);
        order.verify(entities).flush();
        order.verify(entities).createNativeQuery(anyString());
        order.verify(query).setParameter("id", id);
        order.verify(query).setParameter("expected", 7L);
        order.verify(query).executeUpdate();
    }
}
