package com.example.demo.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.demo.catalog.dto.request.AdminProductRequests.Action;
import com.example.demo.catalog.dto.request.AdminVariantRequests.Patch;
import com.example.demo.catalog.dto.request.CatalogField;
import com.example.demo.catalog.dto.response.AdminCatalogResponses.ProductDetail;
import com.example.demo.catalog.exception.AdminCatalogException;
import com.example.demo.catalog.service.AdminProductService;
import com.example.demo.catalog.service.AdminVariantService;
import com.example.demo.catalog.service.ProductCatalogService;
import com.example.demo.catalog.storage.ProductImageStorage;
import com.example.demo.checkout.dto.request.CheckoutRequest;
import com.example.demo.checkout.exception.NoEligibleCheckoutItemsException;
import com.example.demo.checkout.service.CheckoutService;
import com.example.demo.config.CheckoutIntegrationSupport;
import com.example.demo.order.dto.request.CancelOrderRequest;
import com.example.demo.order.service.OrderService;
import com.example.demo.recommendation.RecommendationStore;
import com.example.demo.wishlist.service.WishlistService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

/** Authored under T084; real committed consumer/Admin flow, executed only after runtime prerequisites. */
@Import(AdminCatalogConsumerCompatibilityIT.LocalSigning.class)
class AdminCatalogConsumerCompatibilityIT extends CheckoutIntegrationSupport {
    @Autowired AdminProductService admin;
    @Autowired AdminVariantService variants;
    @Autowired com.example.demo.cart.service.CartService carts;
    @Autowired CheckoutService checkout;
    @Autowired OrderService orders;
    @Autowired ProductCatalogService publicCatalog;
    @Autowired RecommendationStore recommendations;
    @Autowired WishlistService wishlist;

    @Test void withdrawal_shouldKeepCartAndWishlistThenRevalidateDisabledPriceAndInventoryOnRepublish(){
        wishlist.addProduct(fixture.owner(),fixture.product());
        var cartBefore=jdbc.queryForMap("SELECT * FROM cart_items WHERE id=?",fixture.item());
        var stockBefore=jdbc.queryForMap("SELECT stock,reserved FROM inventory_balances WHERE original_variant_id=?",fixture.variant());
        transition(Action.UNPUBLISH);
        var detail=admin.get(fixture.product());
        variants.setEnabled(fixture.product(),fixture.variant(),version(detail),taxonomy(detail),false);
        assertThat(carts.getCart(fixture.owner()).getItems()).anySatisfy(row -> {
            assertThat(row.getId()).isEqualTo(fixture.item());assertThat(row.isPurchasable()).isFalse();
            assertThat(row.getQuantity()).isEqualTo(2);assertThat(row.isSelected()).isTrue();
        });
        assertThatThrownBy(() -> checkout.checkout(fixture.owner(),"Admin_withdrawal_key_123",new CheckoutRequest(fixture.address())))
                .isInstanceOf(NoEligibleCheckoutItemsException.class);
        assertThat(jdbc.queryForMap("SELECT * FROM cart_items WHERE id=?",fixture.item())).isEqualTo(cartBefore);
        assertThat(wishlist.listWishlist(fixture.owner(),0,20).getItems()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wishlist_items WHERE user_id=? AND product_id=?",Long.class,fixture.owner(),fixture.product())).isOne();
        assertThat(recommendations.catalog()).noneMatch(row -> row.candidate().productId().equals(fixture.product()));
        assertThat(publicCatalog.listProducts(0,100).getItems()).noneMatch(row -> row.getId().equals(fixture.product()));
        detail=admin.get(fixture.product());
        variants.setEnabled(fixture.product(),fixture.variant(),version(detail),taxonomy(detail),true);
        detail=admin.get(fixture.product());
        variants.update(fixture.product(),fixture.variant(),price(detail,BigDecimal.valueOf(1250)));
        transition(Action.PUBLISH);
        var recovered=carts.getCart(fixture.owner()).getItems().stream().filter(row -> row.getId().equals(fixture.item())).findFirst().orElseThrow();
        assertThat(recovered.isPurchasable()).isTrue();assertThat(recovered.getUnitPrice().getAmount()).isEqualTo(1250);
        assertThat(jdbc.queryForMap("SELECT stock,reserved FROM inventory_balances WHERE original_variant_id=?",fixture.variant())).isEqualTo(stockBefore);
        assertThat(wishlist.listWishlist(fixture.owner(),0,20).getItems()).hasSize(1);
    }

    @Test void disabledVariant_shouldStayUnavailableWhileParentAndSiblingRemainPublished(){
        var before=admin.get(fixture.product());
        var sibling=variants.create(fixture.product(),new com.example.demo.catalog.dto.request.AdminVariantRequests.Create(
                version(before),taxonomy(before),"CONSUMER-SIBLING-"+java.util.UUID.randomUUID(),BigDecimal.valueOf(2000),
                java.util.Map.of("choice","enabled-sibling"),java.util.Map.of("schema_version",1,"values",java.util.Map.of()),true));
        var cartBefore=jdbc.queryForMap("SELECT * FROM cart_items WHERE id=?",fixture.item());
        var balanceBefore=jdbc.queryForMap("SELECT stock,reserved FROM inventory_balances WHERE original_variant_id=?",fixture.variant());
        var current=admin.get(fixture.product());
        variants.setEnabled(fixture.product(),fixture.variant(),version(current),taxonomy(current),false);
        assertThat(admin.get(fixture.product()).getStatus()).isEqualTo("PUBLISHED");
        assertThat(carts.getCart(fixture.owner()).getItems()).anySatisfy(row -> {
            assertThat(row.getId()).isEqualTo(fixture.item());assertThat(row.isPurchasable()).isFalse();
            assertThat(row.isSelected()).isTrue();assertThat(row.getQuantity()).isEqualTo(2);
        });
        assertThatThrownBy(() -> checkout.checkout(fixture.owner(),"Admin_disable_key_123",new CheckoutRequest(fixture.address())))
                .isInstanceOf(NoEligibleCheckoutItemsException.class);
        assertThat(jdbc.queryForMap("SELECT * FROM cart_items WHERE id=?",fixture.item())).isEqualTo(cartBefore);
        assertThat(publicCatalog.getProduct(fixture.product()).getVariants()).extracting(v -> v.getId())
                .containsExactly(sibling.getVariant().getId());
        assertThat(recommendations.refresh(List.of(fixture.product()))).singleElement().satisfies(row ->
                assertThat(row.variantId()).isEqualTo(sibling.getVariant().getId()));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM orders",Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_reservations",Long.class)).isZero();
        current=admin.get(fixture.product());
        variants.setEnabled(fixture.product(),fixture.variant(),version(current),taxonomy(current),true);
        assertThat(carts.getCart(fixture.owner()).getItems()).anySatisfy(row -> {
            assertThat(row.getId()).isEqualTo(fixture.item());assertThat(row.isPurchasable()).isTrue();
        });
        assertThat(jdbc.queryForMap("SELECT stock,reserved FROM inventory_balances WHERE original_variant_id=?",fixture.variant())).isEqualTo(balanceBefore);
    }

    @Test void archiveRestore_shouldPreserveReceiptSnapshotsAndAllocationsAndNotRepublish(){
        var receipt=checkout.checkout(fixture.owner(),"Admin_history_key_123",new CheckoutRequest(fixture.address()));
        Long order=receipt.getOrder().getId();
        var items=jdbc.queryForList("SELECT * FROM order_items WHERE order_id=? ORDER BY id",order);
        var allocations=jdbc.queryForList("SELECT * FROM inventory_reservations WHERE order_id=? ORDER BY id",order);
        var balances=jdbc.queryForList("SELECT * FROM inventory_balances ORDER BY id");
        var storedReceipt=jdbc.queryForMap("SELECT * FROM checkout_receipts WHERE order_id=?",order);
        transition(Action.ARCHIVE);transition(Action.RESTORE);
        assertThat(admin.get(fixture.product()).getStatus()).isEqualTo("DRAFT");
        assertThat(jdbc.queryForList("SELECT * FROM order_items WHERE order_id=? ORDER BY id",order)).isEqualTo(items);
        assertThat(jdbc.queryForList("SELECT * FROM inventory_reservations WHERE order_id=? ORDER BY id",order)).isEqualTo(allocations);
        assertThat(jdbc.queryForList("SELECT * FROM inventory_balances ORDER BY id")).isEqualTo(balances);
        assertThat(jdbc.queryForMap("SELECT * FROM checkout_receipts WHERE order_id=?",order)).isEqualTo(storedReceipt);
        assertThat(checkout.checkout(fixture.owner(),"Admin_history_key_123",new CheckoutRequest(fixture.address())))
                .usingRecursiveComparison().isEqualTo(receipt);
        orders.cancelOrder(fixture.owner(),order,new CancelOrderRequest("customer cancellation after archive"));
        assertThat(jdbc.queryForList("SELECT state FROM inventory_reservations WHERE order_id=?",String.class,order)).containsOnly("RELEASED");
        assertThat(jdbc.queryForObject("SELECT reserved FROM inventory_balances WHERE original_variant_id=?",Long.class,fixture.variant())).isZero();
        assertThat(jdbc.queryForList("SELECT * FROM order_items WHERE order_id=? ORDER BY id",order)).isEqualTo(items);
    }

    @Test void zeroPrice_shouldBeRejectedByPublishedGuardWithoutConsumingRevisionAndRemainPreparationOnly(){
        var before=admin.get(fixture.product());
        assertThatThrownBy(() -> variants.update(fixture.product(),fixture.variant(),price(before,BigDecimal.ZERO)))
                .isInstanceOf(AdminCatalogException.class);
        assertThat(admin.get(fixture.product()).getVersion()).isEqualTo(before.getVersion());
        assertThat(jdbc.queryForObject("SELECT base_price FROM product_variants WHERE id=?",BigDecimal.class,fixture.variant())).isEqualByComparingTo("1000");
        assertThatThrownBy(() -> jdbc.update("UPDATE product_variants SET base_price=0 WHERE id=?",fixture.variant()))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        transition(Action.UNPUBLISH);
        var unpublished=admin.get(fixture.product());
        variants.update(fixture.product(),fixture.variant(),price(unpublished,BigDecimal.ZERO));
        assertThatThrownBy(() -> checkout.checkout(fixture.owner(),"Admin_zero_price_key_123",new CheckoutRequest(fixture.address())))
                .isInstanceOf(NoEligibleCheckoutItemsException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM orders",Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM inventory_reservations",Long.class)).isZero();
    }

    private void transition(Action action){var d=admin.get(fixture.product());admin.transition(fixture.product(),version(d),taxonomy(d),action);}
    private long version(ProductDetail d){return Long.parseLong(d.getVersion());}
    private long taxonomy(ProductDetail d){return Long.parseLong(d.getTaxonomyVersion());}
    private Patch price(ProductDetail d,BigDecimal price){return new Patch(version(d),taxonomy(d),CatalogField.absent(),CatalogField.supplied(price),CatalogField.absent(),CatalogField.absent());}
    @TestConfiguration(proxyBeanMethods=false)
    static class LocalSigning {
        @Bean @Primary ProductImageStorage consumerImageSigner(){return () -> new ProductImageStorage.PreparedReadSigner(){
            public String signReadUrl(String key){return "https://images.test/consumer-read";}
            public void close(){ }
        };}
    }
}
