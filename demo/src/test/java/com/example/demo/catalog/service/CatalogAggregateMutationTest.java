package com.example.demo.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.demo.catalog.CatalogFixtures;
import com.example.demo.catalog.entity.Product;
import com.example.demo.catalog.exception.AdminCatalogException;
import com.example.demo.catalog.repository.CatalogMutationRepository;
import com.example.demo.catalog.repository.CatalogMutationRepository.Resource;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

/** Orchestration only; actual revisions/rollback/locks are proven later by T022. */
class CatalogAggregateMutationTest {
    @Test
    void currentNoOp_shouldNotConsumeARevision_evenAtMaximum() {
        CatalogMutationRepository repository = mock(CatalogMutationRepository.class);
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        UUID id = UUID.randomUUID();
        Product product = CatalogFixtures.draftProduct().product();
        when(repository.lockTaxonomy(false)).thenReturn(3L);
        when(repository.lockVersions(Resource.PRODUCT, List.of(id), true)).thenReturn(Map.of(id, Long.MAX_VALUE));
        when(repository.freshProduct(id)).thenReturn(product);
        CatalogAggregateMutation.ProductCommand<String> command = mock(CatalogAggregateMutation.ProductCommand.class);
        when(command.noOp(product)).thenReturn(true);
        when(command.unchanged(product)).thenReturn("unchanged");
        assertThat(new CatalogAggregateMutation(repository, manager).product(id, Long.MAX_VALUE, 3, command)).isEqualTo("unchanged");
        verify(repository, never()).claim(any(), any(), org.mockito.ArgumentMatchers.anyLong());
        verify(command, never()).apply(any());
    }

    @Test
    void lateFailure_shouldRequestRollback_andNeverReturnSuccessOrRetry() {
        CatalogMutationRepository repository = mock(CatalogMutationRepository.class);
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        SimpleTransactionStatus transaction = new SimpleTransactionStatus();
        when(manager.getTransaction(any())).thenReturn(transaction);
        UUID id = UUID.randomUUID();
        Product product = CatalogFixtures.draftProduct().product();
        when(repository.lockTaxonomy(false)).thenReturn(3L);
        when(repository.lockVersions(Resource.PRODUCT, List.of(id), true)).thenReturn(Map.of(id, 7L));
        when(repository.freshProduct(id)).thenReturn(product);
        CatalogAggregateMutation.ProductCommand<String> command = mock(CatalogAggregateMutation.ProductCommand.class);
        when(command.apply(product)).thenThrow(new IllegalStateException("late constraint failure"));
        assertThatThrownBy(() -> new CatalogAggregateMutation(repository, manager).product(id, 7, 3, command))
                .isInstanceOf(IllegalStateException.class);
        verify(repository).claim(Resource.PRODUCT, id, 7);
        verify(manager).rollback(transaction);
        verify(manager, never()).commit(any());
    }

    @Test
    void product_shouldClaimAndRefreshBeforeChildWork_andCommitBeforeReturning() {
        CatalogMutationRepository repository = mock(CatalogMutationRepository.class);
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        SimpleTransactionStatus transaction = new SimpleTransactionStatus();
        when(manager.getTransaction(any())).thenReturn(transaction);
        UUID id = UUID.randomUUID();
        Product product = CatalogFixtures.draftProduct().product();
        when(repository.lockTaxonomy(false)).thenReturn(3L);
        when(repository.lockVersions(Resource.PRODUCT, List.of(id), true)).thenReturn(Map.of(id, 7L));
        when(repository.freshProduct(id)).thenReturn(product);
        CatalogAggregateMutation.ProductCommand<String> command = mock(CatalogAggregateMutation.ProductCommand.class);
        when(command.apply(product)).thenReturn("accepted");
        assertThat(new CatalogAggregateMutation(repository, manager).product(id, 7, 3, command)).isEqualTo("accepted");
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(repository, command, manager);
        order.verify(repository).lockTaxonomy(false);
        order.verify(repository).lockVersions(Resource.PRODUCT, List.of(id), true);
        order.verify(repository).freshProduct(id);
        order.verify(command).noOp(product);
        order.verify(repository).claim(Resource.PRODUCT, id, 7);
        order.verify(repository).freshProduct(id);
        order.verify(command).apply(product);
        order.verify(repository).flush();
        order.verify(manager).commit(transaction);
        verify(manager).getTransaction(org.mockito.ArgumentMatchers.argThat(definition -> definition.getIsolationLevel() == TransactionDefinition.ISOLATION_READ_COMMITTED
                && definition.getPropagationBehavior() == TransactionDefinition.PROPAGATION_REQUIRED));
    }

    @Test
    void product_shouldRejectStaleTaxonomyBeforeAnyProductWrite_andRollback() {
        CatalogMutationRepository repository = mock(CatalogMutationRepository.class);
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        SimpleTransactionStatus transaction = new SimpleTransactionStatus();
        when(manager.getTransaction(any())).thenReturn(transaction);
        when(repository.lockTaxonomy(false)).thenReturn(4L);
        assertThatThrownBy(() -> new CatalogAggregateMutation(repository, manager)
                .product(UUID.randomUUID(), 7, 3, mock(CatalogAggregateMutation.ProductCommand.class)))
                .isInstanceOf(AdminCatalogException.class).hasMessageContaining("reload");
        verify(repository, never()).claim(any(), any(), org.mockito.ArgumentMatchers.anyLong());
        verify(manager).rollback(transaction);
    }

    @Test
    void uuidOrder_shouldMatchPostgresUnsignedCanonicalOrder_notJavaSignedUuidOrdering() {
        UUID low = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID high = UUID.fromString("80000000-0000-0000-0000-000000000000");
        assertThat(List.of(high, low).stream().sorted(CatalogMutationRepository.UUID_ORDER).toList()).containsExactly(low, high);
    }
    @Test void read_shouldLockTaxonomyBeforeProductShare_andCommitPreparedResult() {
        var repository=mock(CatalogMutationRepository.class);var manager=mock(PlatformTransactionManager.class);
        var transaction=new SimpleTransactionStatus();when(manager.getTransaction(any())).thenReturn(transaction);
        UUID id=UUID.randomUUID();Product product=CatalogFixtures.draftProduct().product();
        when(repository.lockTaxonomy(false)).thenReturn(8L);when(repository.freshProduct(id)).thenReturn(product);
        String result=new CatalogAggregateMutation(repository,manager).read(id,(taxonomy,row)->taxonomy+":"+row.getName());
        assertThat(result).startsWith("8:");
        var order=org.mockito.Mockito.inOrder(repository,manager);
        order.verify(repository).lockTaxonomy(false);order.verify(repository).lockVersions(Resource.PRODUCT,List.of(id),false);
        order.verify(repository).freshProduct(id);order.verify(manager).commit(transaction);
        verify(repository,never()).claim(any(),any(),org.mockito.ArgumentMatchers.anyLong());
    }

    @Test void product_shouldRejectStaleProductBeforeCallback_orClaim() {
        var repository=mock(CatalogMutationRepository.class);var manager=mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());UUID id=UUID.randomUUID();
        when(repository.lockTaxonomy(false)).thenReturn(3L);when(repository.lockVersions(Resource.PRODUCT,List.of(id),true)).thenReturn(Map.of(id,8L));
        var command=mock(CatalogAggregateMutation.ProductCommand.class);
        assertThatThrownBy(()->new CatalogAggregateMutation(repository,manager).product(id,7,3,command))
                .isInstanceOfSatisfying(AdminCatalogException.class,e->assertThat(e.getProperties()).containsEntry("currentVersion","8"));
        org.mockito.Mockito.verifyNoInteractions(command);verify(repository,never()).claim(any(),any(),org.mockito.ArgumentMatchers.anyLong());
    }
    @Test void commitFailure_shouldEscapeBeforeSuccess_withoutAutomaticRetry() {
        var repository=mock(CatalogMutationRepository.class);var manager=mock(PlatformTransactionManager.class);
        var transaction=new SimpleTransactionStatus();when(manager.getTransaction(any())).thenReturn(transaction);
        UUID id=UUID.randomUUID();Product product=CatalogFixtures.draftProduct().product();
        when(repository.lockTaxonomy(false)).thenReturn(3L);when(repository.lockVersions(Resource.PRODUCT,List.of(id),true)).thenReturn(Map.of(id,7L));
        when(repository.freshProduct(id)).thenReturn(product);
        var command=mock(CatalogAggregateMutation.ProductCommand.class);when(command.apply(product)).thenReturn("must not escape");
        org.mockito.Mockito.doThrow(new org.springframework.transaction.TransactionSystemException("late deferred failure")).when(manager).commit(transaction);
        assertThatThrownBy(()->new CatalogAggregateMutation(repository,manager).product(id,7,3,command)).isInstanceOf(org.springframework.transaction.TransactionSystemException.class);
        verify(command).apply(product);verify(manager).commit(transaction);
    }

}
