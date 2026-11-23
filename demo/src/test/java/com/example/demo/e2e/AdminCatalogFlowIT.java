package com.example.demo.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.demo.catalog.dto.request.AdminProductRequests;
import com.example.demo.catalog.dto.request.AdminVariantRequests;
import com.example.demo.catalog.dto.request.CatalogField;
import com.example.demo.catalog.dto.response.AdminCatalogResponses.ProductDetail;
import com.example.demo.catalog.service.AdminCatalogIntegrationSupport;
import com.example.demo.catalog.service.AdminProductService;
import com.example.demo.catalog.service.AdminVariantService;
import com.example.demo.catalog.service.ProductCatalogService;
import com.example.demo.cart.dto.request.AddCartItemRequest;
import com.example.demo.cart.service.CartService;
import com.example.demo.checkout.dto.request.CheckoutRequest;
import com.example.demo.checkout.exception.NoEligibleCheckoutItemsException;
import com.example.demo.checkout.service.CheckoutService;
import com.example.demo.config.CheckoutIntegrationSupport;
import com.example.demo.fixture.inventory.InventoryFixtures;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/** Complete committed service workflow; HTTP/security and real object bytes have separate owning ITs. */
@Import(AdminCatalogIntegrationSupport.LocalSigning.class)
class AdminCatalogFlowIT extends CheckoutIntegrationSupport {
    @Autowired AdminProductService products;
    @Autowired AdminVariantService variants;
    @Autowired ProductCatalogService publicCatalog;
    @Autowired CartService carts;
    @Autowired CheckoutService checkout;

    @Test void draftThroughPurchaseWithdrawalRestoreAndRepublishMustPreserveHistoricalCommerce(){
        UUID category=jdbc.queryForObject("SELECT category_id FROM products WHERE id=?",UUID.class,fixture.product());
        ProductDetail draft=products.create(new AdminProductRequests.Create("Lifecycle flow",category,CatalogField.absent(),null,CatalogField.absent()));
        UUID product=draft.getId();
        assertThat(draft.getStatus()).isEqualTo("DRAFT");assertThat(draft.getVariants()).isEmpty();
        var variant=variants.create(product,new AdminVariantRequests.Create(version(draft),taxonomy(draft),"FLOW-"+product,BigDecimal.valueOf(1000),Map.of(),Map.of("schema_version",1,"values",Map.of()),true)).getVariant().getId();
        // Authorized isolated publication fixture; storage ownership/content is verified by Gallery ITs.
        jdbc.update("INSERT INTO product_images(id,product_id,object_key,alt_text,content_type,byte_size,width,height,display_order,is_primary,created_at) VALUES (?,?,?,'Flow image','image/png',100,10,10,0,true,CURRENT_TIMESTAMP)",UUID.randomUUID(),product,"final/flow/"+product);
        assertThat(jdbc.queryForObject("SELECT stock FROM inventory_balances WHERE original_variant_id=?",Long.class,variant)).isZero();
        transition(product,AdminProductRequests.Action.PUBLISH);
        assertThat(publicCatalog.getProduct(product).getVariants()).hasSize(1);
        InventoryFixtures.supplyStock(jdbc,variant,8);
        carts.removeItem(fixture.owner(),fixture.item());add(variant,2);
        var receipt=checkout.checkout(fixture.owner(),"Flow_purchase_key_123",new CheckoutRequest(fixture.address()));
        var oldItems=jdbc.queryForList("SELECT * FROM order_items WHERE order_id=?",receipt.getOrder().getId());
        var oldOrder=jdbc.queryForMap("SELECT * FROM orders WHERE id=?",receipt.getOrder().getId());
        var oldReceipt=jdbc.queryForMap("SELECT * FROM checkout_receipts WHERE order_id=?",receipt.getOrder().getId());
        var oldAllocations=jdbc.queryForList("SELECT * FROM inventory_reservations ORDER BY id");
        var oldBalances=jdbc.queryForList("SELECT * FROM inventory_balances ORDER BY id");
        assertThat(receipt.getOrder().getPayableTotal().getAmount()).isEqualTo(2000);
        var current=products.get(product);
        variants.update(product,variant,new AdminVariantRequests.Patch(version(current),taxonomy(current),CatalogField.absent(),CatalogField.supplied(BigDecimal.valueOf(2500)),CatalogField.absent(),CatalogField.absent()));
        add(variant,1);
        transition(product,AdminProductRequests.Action.UNPUBLISH);
        var retained=jdbc.queryForMap("SELECT * FROM cart_items WHERE product_variant_id=?",variant);
        assertThat(carts.getCart(fixture.owner()).getItems()).anySatisfy(i->{assertThat(i.getVariantId()).isEqualTo(variant);assertThat(i.isPurchasable()).isFalse();});
        assertThatThrownBy(()->checkout.checkout(fixture.owner(),"Flow_unavailable_key_123",new CheckoutRequest(fixture.address()))).isInstanceOf(NoEligibleCheckoutItemsException.class);
        transition(product,AdminProductRequests.Action.ARCHIVE);transition(product,AdminProductRequests.Action.RESTORE);
        assertThat(products.get(product).getStatus()).isEqualTo("DRAFT");
        assertThat(publicCatalog.getProductSummaries(List.of(product))).isEmpty();
        current=products.get(product);variants.setEnabled(product,variant,version(current),taxonomy(current),false);
        current=products.get(product);variants.setEnabled(product,variant,version(current),taxonomy(current),true);
        assertThat(products.get(product).getStatus()).isEqualTo("DRAFT");
        transition(product,AdminProductRequests.Action.PUBLISH);
        assertThat(carts.getCart(fixture.owner()).getItems()).anySatisfy(i->{assertThat(i.getVariantId()).isEqualTo(variant);assertThat(i.isPurchasable()).isTrue();assertThat(i.getUnitPrice().getAmount()).isEqualTo(2500);});
        assertThat(jdbc.queryForMap("SELECT * FROM cart_items WHERE product_variant_id=?",variant)).isEqualTo(retained);
        assertThat(jdbc.queryForList("SELECT * FROM order_items WHERE order_id=?",receipt.getOrder().getId())).isEqualTo(oldItems);
        assertThat(jdbc.queryForMap("SELECT * FROM orders WHERE id=?",receipt.getOrder().getId())).isEqualTo(oldOrder);
        assertThat(jdbc.queryForMap("SELECT * FROM checkout_receipts WHERE order_id=?",receipt.getOrder().getId())).isEqualTo(oldReceipt);
        assertThat(jdbc.queryForList("SELECT * FROM inventory_reservations ORDER BY id")).isEqualTo(oldAllocations);
        assertThat(jdbc.queryForList("SELECT * FROM inventory_balances ORDER BY id")).isEqualTo(oldBalances);
        assertThat(checkout.checkout(fixture.owner(),"Flow_purchase_key_123",new CheckoutRequest(fixture.address()))).usingRecursiveComparison().isEqualTo(receipt);
        var fresh=checkout.checkout(fixture.owner(),"Flow_current_key_123",new CheckoutRequest(fixture.address()));
        assertThat(fresh.getOrder().getPayableTotal().getAmount()).isEqualTo(2500);
        assertThat(jdbc.queryForObject("SELECT reserved FROM inventory_balances WHERE original_variant_id=?",Long.class,variant)).isEqualTo(3);
        assertThat(jdbc.queryForList("SELECT quantity FROM inventory_reservations WHERE state='ACTIVE' ORDER BY quantity",Integer.class)).containsExactly(1,2);
    }
    private void add(UUID variant,int quantity){AddCartItemRequest request=new AddCartItemRequest();request.setProductVariantId(variant);request.setQuantity(quantity);carts.addItem(fixture.owner(),request);}
    private void transition(UUID product,AdminProductRequests.Action action){var p=products.get(product);products.transition(product,version(p),taxonomy(p),action);}
    private long version(ProductDetail p){return Long.parseLong(p.getVersion());}
    private long taxonomy(ProductDetail p){return Long.parseLong(p.getTaxonomyVersion());}
}
