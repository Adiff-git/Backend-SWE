package com.example.demo.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.demo.catalog.dto.request.AdminProductRequests;
import com.example.demo.catalog.dto.request.AdminProductRequests.Action;
import com.example.demo.catalog.dto.request.AdminTaxonomyRequests;
import com.example.demo.catalog.dto.request.AdminVariantRequests;
import com.example.demo.catalog.dto.request.CatalogField;
import com.example.demo.catalog.exception.AdminCatalogException;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class AdminTaxonomyServiceIT extends AdminCatalogIntegrationSupport {
    @Autowired AdminCategoryService categories;
    @Autowired AdminBrandService brands;
    private long topology(){return jdbc.queryForObject("SELECT version FROM catalog_taxonomy_state WHERE id=1",Long.class);}
    private UUID category(String name,UUID parent){return categories.create(new AdminTaxonomyRequests.CategoryCreate(topology(),name,parent)).getCategory().getId();}
    private void move(UUID id,UUID parent){var current=categories.get(id);categories.update(id,new AdminTaxonomyRequests.CategoryPatch(Long.parseLong(current.getCategory().getVersion()),Long.parseLong(current.getTaxonomyVersion()),CatalogField.absent(),CatalogField.supplied(parent)));}
    @Test void names_shouldUseSqlWhitespaceEqualityWithinSiblingsButAllowDifferentParents(){
        UUID parent=category("Parent",null),other=category("Other",null);
        UUID child=category("\u2000 Devices\u00a0",parent);
        assertThat(categories.get(child).getCategory().getName()).isEqualTo("Devices");
        assertThatThrownBy(()->category("  DEVICES\t",parent)).isInstanceOf(AdminCatalogException.class);
        assertThat(category("devices",other)).isNotEqualTo(child);
        assertThatThrownBy(()->category(" laptop ",null)).isInstanceOf(AdminCatalogException.class);
        assertThatThrownBy(()->move(child,child)).isInstanceOf(AdminCatalogException.class);
        UUID descendant=category("Descendant",child);
        assertThatThrownBy(()->move(parent,descendant)).isInstanceOf(AdminCatalogException.class);
        assertThat(categories.get(parent).getCategory().getParentCategoryId()).isNull();
    }
    @Test void reparent_shouldRetainDirectProductAssignmentAndRejectDestinationCollision(){
        UUID left=category("Left",null),right=category("Right",null),child=category("Child",left);
        var d=products.create(new AdminProductRequests.Create("Assigned",child,CatalogField.absent(),null,CatalogField.absent()));
        move(child,right);
        assertThat(jdbc.queryForObject("SELECT category_id FROM products WHERE id=?",UUID.class,d.getId())).isEqualTo(child);
        assertThat(categories.get(child).getCategory().getParentCategoryId()).isEqualTo(right);
        UUID duplicate=category("child",left);
        assertThatThrownBy(()->move(duplicate,right)).isInstanceOf(AdminCatalogException.class);
        assertThat(categories.get(duplicate).getCategory().getParentCategoryId()).isEqualTo(left);
    }
    @Test void deletion_shouldBlockChildrenAndArchivedReferences_butDeleteUnreferencedLeaf(){
        UUID parent=category("Parent",null),leaf=category("Leaf",parent);
        assertThatThrownBy(()->categories.delete(parent,0,topology())).isInstanceOf(AdminCatalogException.class);
        categories.delete(leaf,0,topology());
        assertThatThrownBy(()->categories.get(leaf)).isInstanceOf(AdminCatalogException.class);
        UUID archived=draft().getId();transition(archived,Action.ARCHIVE);
        assertThatThrownBy(()->categories.delete(category,0,topology())).isInstanceOf(AdminCatalogException.class);
        assertThat(jdbc.queryForObject("SELECT category_id FROM products WHERE id=?",UUID.class,archived)).isEqualTo(category);
    }
    @Test void moveMakingInvalidFactsApplicable_shouldRollBackTreeAndAllRevisions(){
        UUID generic=category("Other",null);UUID product=publishedGenericInvalidFact(generic);
        long before=topology();String productVersion=products.get(product).getVersion();
        assertThatThrownBy(()->move(generic,category)).isInstanceOf(AdminCatalogException.class);
        assertThat(topology()).isEqualTo(before);assertThat(categories.get(generic).getCategory().getParentCategoryId()).isNull();
        assertThat(products.get(product).getVersion()).isEqualTo(productVersion);
    }
    @Test void deletingAmbiguousBinding_shouldValidatePublishedProductsOutsideDeletedSubtree(){
        UUID parent=category("Other",null),ambiguous=category("Laptop",parent);
        UUID product=publishedGenericInvalidFact(category);long before=topology();
        assertThatThrownBy(()->categories.delete(ambiguous,0,topology())).isInstanceOf(AdminCatalogException.class);
        assertThat(topology()).isEqualTo(before);assertThat(categories.get(ambiguous).getCategory().getId()).isEqualTo(ambiguous);
        assertThat(products.get(product).getStatus()).isEqualTo("PUBLISHED");
    }
    @Test void brands_shouldKeepStableCodeNormalizedIdentityAndAllStateReferences(){
        var brand=brands.create(new AdminTaxonomyRequests.BrandCreate("stable-code","\u00a0 Brand\t"));
        assertThat(brand.getName()).isEqualTo("Brand");
        assertThatThrownBy(()->brands.create(new AdminTaxonomyRequests.BrandCreate("other-code"," brand "))).isInstanceOf(AdminCatalogException.class);
        var product=products.create(new AdminProductRequests.Create("Branded",category,CatalogField.supplied(brand.getId()),0L,CatalogField.absent()));
        transition(product.getId(),Action.ARCHIVE);
        assertThatThrownBy(()->brands.delete(brand.getId(),0)).isInstanceOf(AdminCatalogException.class);
        var renamed=brands.update(brand.getId(),new AdminTaxonomyRequests.BrandRename(0,"Renamed"));
        assertThat(renamed.getCode()).isEqualTo("stable-code");assertThat(renamed.getVersion()).isEqualTo("1");
        assertThatThrownBy(()->brands.update(brand.getId(),new AdminTaxonomyRequests.BrandRename(0,"Stale"))).isInstanceOf(AdminCatalogException.class);
        assertThat(products.get(product.getId()).getBrand().getId()).isEqualTo(brand.getId());
        var unused=brands.create(new AdminTaxonomyRequests.BrandCreate("unused","Unused"));brands.delete(unused.getId(),0);
        assertThatThrownBy(()->brands.get(unused.getId())).isInstanceOf(AdminCatalogException.class);
    }
    @Test void boundedCategoryPages_shouldExposeParentsAndAccurateRootTotals(){
        UUID parent=category("Parent",null);
        for(int n=0;n<4;n++)category("Child "+n,parent);
        var query=new AdminTaxonomyRequests.Query(org.springframework.data.domain.PageRequest.of(0,2),null,AdminTaxonomyRequests.Ordering.NAME_ASC,false,null);
        var page=categories.list(query);assertThat(page.getItems()).hasSize(2);assertThat(page.getTotalItems()).isEqualTo(6);assertThat(page.getTotalPages()).isEqualTo(3);
        var roots=categories.list(new AdminTaxonomyRequests.Query(org.springframework.data.domain.PageRequest.of(0,20),null,AdminTaxonomyRequests.Ordering.ID_ASC,true,null));
        assertThat(roots.getTotalItems()).isEqualTo(2);assertThat(roots.getItems()).allSatisfy(row->assertThat(row.getParentCategoryId()).isNull());
        var children=categories.list(new AdminTaxonomyRequests.Query(org.springframework.data.domain.PageRequest.of(0,20),null,AdminTaxonomyRequests.Ordering.ID_ASC,true,parent));
        assertThat(children.getItems()).hasSize(4).allSatisfy(row->assertThat(row.getParentCategoryId()).isEqualTo(parent));
    }
    private UUID publishedGenericInvalidFact(UUID assigned){
        var d=products.create(new AdminProductRequests.Create("Generic facts",assigned,CatalogField.absent(),null,CatalogField.absent()));
        Map<String,Object> facts=Map.of("schema_version",1,"values",Map.of("physical_ram",Map.of("value",8,"unit","inch")));
        variants.create(d.getId(),new AdminVariantRequests.Create(version(d),taxonomy(d),"GENERIC-"+d.getId(),BigDecimal.TEN,Map.of(),facts,true));
        image(d.getId(),0,true);transition(d.getId(),Action.PUBLISH);return d.getId();
    }
}
