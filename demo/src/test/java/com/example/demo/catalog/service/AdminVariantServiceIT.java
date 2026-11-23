package com.example.demo.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.demo.catalog.dto.request.AdminProductRequests.Action;
import com.example.demo.catalog.dto.request.AdminVariantRequests;
import com.example.demo.catalog.dto.request.CatalogField;
import com.example.demo.catalog.exception.AdminCatalogException;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AdminVariantServiceIT extends AdminCatalogIntegrationSupport {
    private AdminVariantRequests.Patch price(UUID product,BigDecimal amount){var d=products.get(product);return new AdminVariantRequests.Patch(version(d),taxonomy(d),CatalogField.absent(),CatalogField.supplied(amount),CatalogField.absent(),CatalogField.absent());}
    @Test void creation_shouldRetainMandatoryIdentityAndInitializeZeroInventory(){
        var d=draft();UUID variant=variant(d.getId(),"CASE-SKU","black",BigDecimal.ZERO);
        assertThat(variants.list(d.getId()).getItems()).singleElement().satisfies(v->{assertThat(v.getSku()).isEqualTo("CASE-SKU");assertThat(v.isEnabled()).isTrue();});
        assertThat(jdbc.queryForMap("SELECT stock,reserved FROM inventory_balances WHERE original_variant_id=?",variant)).containsEntry("stock",0L).containsEntry("reserved",0L);
        assertThat(products.get(d.getId()).getVersion()).isEqualTo("1");
    }
    @Test void publishedPriceAndLastEnabledChanges_shouldRollBackParentAndChild(){
        UUID product=ready();var before=products.get(product);UUID variant=before.getVariants().getFirst().getId();
        assertThatThrownBy(()->variants.update(product,variant,price(product,BigDecimal.ZERO))).isInstanceOf(AdminCatalogException.class);
        assertThatThrownBy(()->variants.setEnabled(product,variant,version(before),taxonomy(before),false)).isInstanceOf(AdminCatalogException.class);
        assertThat(products.get(product).getVersion()).isEqualTo(before.getVersion());
        assertThat(jdbc.queryForObject("SELECT selling_price FROM product_variants WHERE id=?",BigDecimal.class,variant)).isEqualByComparingTo("1000");
        assertThat(jdbc.queryForObject("SELECT enabled FROM product_variants WHERE id=?",Boolean.class,variant)).isTrue();
    }
    @Test void disabledPreparation_shouldRetainSkuAndPermitArchivedEnableWithoutRestoration(){
        var d=draft();UUID variant=variant(d.getId(),"RETAINED-SKU","black",BigDecimal.ZERO);var current=products.get(d.getId());
        variants.setEnabled(d.getId(),variant,version(current),taxonomy(current),false);transition(d.getId(),Action.ARCHIVE);
        var archived=products.get(d.getId());variants.setEnabled(d.getId(),variant,version(archived),taxonomy(archived),true);
        assertThat(products.get(d.getId()).getStatus()).isEqualTo("ARCHIVED");
        var another=draft();
        assertThatThrownBy(()->variant(another.getId(),"retained-sku","white",BigDecimal.ONE)).isInstanceOf(AdminCatalogException.class);
        assertThat(products.get(another.getId()).getVariants()).isEmpty();assertThat(products.get(another.getId()).getVersion()).isEqualTo("0");
    }
    @Test void specificationReplacement_shouldAffectOnlyAddressedVariantAndRejectWrongApplicableUnit(){
        UUID product=draft().getId();UUID first=variant(product,"FIRST","black",BigDecimal.ONE),second=variant(product,"SECOND","white",BigDecimal.TEN);
        Map<String,Object> facts=Map.of("schema_version",1,"values",Map.of("physical_ram",Map.of("value",16,"unit","GB"),"custom_boolean",false));
        var current=products.get(product);
        variants.replaceSpecifications(product,first,new AdminVariantRequests.Patch(version(current),taxonomy(current),CatalogField.absent(),CatalogField.absent(),CatalogField.absent(),CatalogField.supplied(facts)));
        assertThat(variants.specifications(product,first).getTechnicalSpecifications()).containsKey("values");
        assertThat(variants.specifications(product,second).getTechnicalSpecifications()).isEqualTo(Map.of("schema_version",1,"values",Map.of()));
        image(product,0,true);transition(product,Action.PUBLISH);var before=products.get(product);
        Map<String,Object> invalid=Map.of("schema_version",1,"values",Map.of("physical_ram",Map.of("value",16,"unit","inch")));
        assertThatThrownBy(()->variants.replaceSpecifications(product,first,new AdminVariantRequests.Patch(version(before),taxonomy(before),CatalogField.absent(),CatalogField.absent(),CatalogField.absent(),CatalogField.supplied(invalid))))
                .isInstanceOf(AdminCatalogException.class);
        assertThat(products.get(product).getVersion()).isEqualTo(before.getVersion());
        assertThat(jdbc.queryForObject("SELECT selling_price FROM product_variants WHERE id=?",BigDecimal.class,second)).isEqualByComparingTo("10");
    }
    @Test void childWrite_shouldRejectStaleAggregateRevisionAndForeignOwnership(){
        UUID product=ready();var before=products.get(product);UUID variant=before.getVariants().getFirst().getId();
        variants.update(product,variant,price(product,BigDecimal.valueOf(2000)));
        var stale=new AdminVariantRequests.Patch(version(before),taxonomy(before),CatalogField.supplied("STALE"),CatalogField.absent(),CatalogField.absent(),CatalogField.absent());
        assertThatThrownBy(()->variants.update(product,variant,stale)).isInstanceOf(AdminCatalogException.class);
        UUID other=draft().getId();assertThatThrownBy(()->variants.update(other,variant,price(other,BigDecimal.ONE))).isInstanceOf(AdminCatalogException.class);
        assertThat(jdbc.queryForObject("SELECT sku FROM product_variants WHERE id=?",String.class,variant)).isNotEqualTo("STALE");
    }
}
