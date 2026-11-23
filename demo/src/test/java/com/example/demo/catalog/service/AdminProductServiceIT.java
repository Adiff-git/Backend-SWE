package com.example.demo.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.demo.catalog.dto.request.AdminProductRequests;
import com.example.demo.catalog.dto.request.AdminProductRequests.Action;
import com.example.demo.catalog.dto.request.CatalogField;
import com.example.demo.catalog.exception.AdminCatalogException;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

class AdminProductServiceIT extends AdminCatalogIntegrationSupport {
    @Test void minimalDraft_shouldCommitWithoutVariantOrInventedOptionalData(){
        var d=draft();assertThat(d.getStatus()).isEqualTo("DRAFT");assertThat(d.getVersion()).isEqualTo("0");
        assertThat(d.getVariants()).isEmpty();assertThat(d.getImages()).isEmpty();assertThat(d.getBrand()).isNull();assertThat(d.getDescription()).isNull();
        assertThat(d.getPublicationReadiness().isEligible()).isFalse();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inventory_balances",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM products WHERE id=?",Integer.class,d.getId())).isOne();
    }
    @Test void publication_shouldRejectIncompleteThenAllowReadyZeroStockAndExplicitLifecycle(){
        var d=draft();
        assertThatThrownBy(()->transition(d.getId(),Action.PUBLISH)).isInstanceOf(AdminCatalogException.class);
        assertThat(products.get(d.getId()).getVersion()).isEqualTo("0");
        variant(d.getId(),"READY","black",java.math.BigDecimal.ONE);image(d.getId(),0,true);
        var published=transition(d.getId(),Action.PUBLISH);assertThat(published.getStatus()).isEqualTo("PUBLISHED");
        assertThat(jdbc.queryForObject("SELECT stock FROM inventory_balances",Long.class)).isZero();
        assertThat(transition(d.getId(),Action.UNPUBLISH).getStatus()).isEqualTo("UNPUBLISHED");
        assertThat(transition(d.getId(),Action.PUBLISH).getStatus()).isEqualTo("PUBLISHED");
        assertThat(transition(d.getId(),Action.ARCHIVE).getStatus()).isEqualTo("ARCHIVED");
        var restored=transition(d.getId(),Action.RESTORE);assertThat(restored.getStatus()).isEqualTo("DRAFT");assertThat(restored.getLastRestoredAt()).isNotNull();
        assertThat(transition(d.getId(),Action.RESTORE).getVersion()).isEqualTo(restored.getVersion());
    }
    @Test void currentNoOp_shouldNotConsumeRevision_butStaleNoOpMustConflict(){
        UUID id=ready();var before=products.get(id);
        assertThat(products.transition(id,version(before),taxonomy(before),Action.PUBLISH).getVersion()).isEqualTo(before.getVersion());
        transition(id,Action.UNPUBLISH);
        assertThatThrownBy(()->products.transition(id,version(before),taxonomy(before),Action.UNPUBLISH)).isInstanceOf(AdminCatalogException.class);
    }
    @Test void optionalFields_shouldDistinguishOmittedEmptyAndExplicitNull(){
        UUID brand=UUID.randomUUID();jdbc.update("INSERT INTO brands(id,code,name) VALUES (?,'brand','Brand')",brand);
        var d=products.create(new AdminProductRequests.Create("Optional",category,CatalogField.supplied(brand),0L,CatalogField.supplied("Original")));
        var changed=products.update(d.getId(),new AdminProductRequests.Patch(version(d),taxonomy(d),CatalogField.supplied("Renamed"),CatalogField.absent(),CatalogField.absent(),null,CatalogField.absent()));
        assertThat(changed.getDescription()).isEqualTo("Original");assertThat(changed.getBrand().getId()).isEqualTo(brand);
        changed=products.update(d.getId(),new AdminProductRequests.Patch(version(changed),taxonomy(changed),CatalogField.absent(),CatalogField.absent(),CatalogField.supplied(null),null,CatalogField.supplied("")));
        assertThat(changed.getDescription()).isEmpty();assertThat(changed.getBrand()).isNull();
        changed=products.update(d.getId(),new AdminProductRequests.Patch(version(changed),taxonomy(changed),CatalogField.absent(),CatalogField.absent(),CatalogField.absent(),null,CatalogField.supplied(null)));
        assertThat(changed.getDescription()).isNull();
    }
    @Test void archive_shouldRetainChildrenAndRejectPreparationUntilRestored(){
        UUID id=ready();int variantsBefore=jdbc.queryForObject("SELECT COUNT(*) FROM product_variants",Integer.class);
        var archived=transition(id,Action.ARCHIVE);
        assertThatThrownBy(()->products.update(id,new AdminProductRequests.Patch(version(archived),taxonomy(archived),CatalogField.supplied("Forbidden"),CatalogField.absent(),CatalogField.absent(),null,CatalogField.absent())))
                .isInstanceOf(AdminCatalogException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product_variants",Integer.class)).isEqualTo(variantsBefore);
        assertThat(products.get(id).getVersion()).isEqualTo(archived.getVersion());
    }
    @Test void adminListing_shouldIncludeAllStatesAndUseLiteralNameFilterWithAccurateTotals(){
        UUID a=draft().getId(),b=ready();transition(b,Action.ARCHIVE);
        var all=products.list(new AdminProductRequests.Query(PageRequest.of(0,20),Set.of(),null,false,null,null,AdminProductRequests.Ordering.ID_ASC));
        assertThat(all.getTotalItems()).isEqualTo(2);assertThat(all.getItems()).extracting(row->row.getId()).containsExactlyInAnyOrder(a,b);
        var literal=products.list(new AdminProductRequests.Query(PageRequest.of(0,20),Set.of(),null,false,null,"%_",AdminProductRequests.Ordering.ID_ASC));
        assertThat(literal.getItems()).isEmpty();assertThat(literal.getTotalItems()).isZero();
    }
}
