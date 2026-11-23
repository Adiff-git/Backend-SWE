package com.example.demo.fixture.checkout;

import com.example.demo.testdata.checkout.CheckoutTestData;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;
import com.example.demo.catalog.CatalogFixtures;
import com.example.demo.catalog.entity.ProductStatus;
import com.example.demo.cart.entity.Cart;
import com.example.demo.cart.entity.CartItem;
import com.example.demo.fixture.wishlist.WishlistFixtures;
import com.example.demo.user.entity.Address;
import com.example.demo.user.entity.User;
import jakarta.persistence.EntityManager;

/** Caller owns transaction/commit/cleanup. Direct SQL is used for schema integrity fixtures. */
public final class CheckoutFixtures {
    public static final Instant PLACED_AT = Instant.parse("2026-11-18T01:00:00Z");
    private CheckoutFixtures() { }

    public static Long customer(JdbcTemplate jdbc) {
        return jdbc.queryForObject("INSERT INTO users(username,password,account_status,joined_at) VALUES (?, 'fixture-password','ACTIVE',CURRENT_TIMESTAMP) RETURNING id",
                Long.class, "checkout-" + UUID.randomUUID());
    }

    public static HistoricalOrder order(JdbcTemplate jdbc, Long owner) {
        return order(jdbc, owner, true);
    }

    /** Explicit historical migration fixture before the inventory column exists. */
    public static HistoricalOrder preInventoryOrder(JdbcTemplate jdbc, Long owner) {
        return order(jdbc, owner, false);
    }

    public static HistoricalOrder historicalZeroOrder(JdbcTemplate jdbc,Long owner){
        return order(jdbc,owner,true,0);
    }

    private static HistoricalOrder order(JdbcTemplate jdbc, Long owner, boolean inventorySchema) {
        return order(jdbc,owner,inventorySchema,100);
    }

    private static HistoricalOrder order(JdbcTemplate jdbc, Long owner, boolean inventorySchema,int historicalAmount) {
        Long id = jdbc.queryForObject("SELECT nextval(pg_get_serial_sequence('orders','id'))", Long.class);
        String participationColumn = inventorySchema ? ",inventory_managed" : "";
        String participationValue = inventorySchema ? ",false" : "";
        jdbc.update("INSERT INTO orders(id,customer_id,order_number,placed_at,status,recipient_name_snapshot,recipient_phone_snapshot,delivery_address_snapshot,merchandise_total,payable_total" + participationColumn + ") VALUES (?, ?, ?, ?, 'PENDING','Recipient','0123456789','Address',?,?" + participationValue + ")",
                id, owner, "TEST-" + id, java.sql.Timestamp.from(PLACED_AT),historicalAmount,historicalAmount);
        Long item = jdbc.queryForObject("INSERT INTO order_items(order_id,product_name_snapshot,variant_sku_snapshot,variant_options_snapshot,quantity,unit_price_snapshot,line_total) VALUES (?,'Product','SKU','{}',1,?,?) RETURNING id", Long.class, id,historicalAmount,historicalAmount);
        return new HistoricalOrder(id, item);
    }

    public static String body(HistoricalOrder order) {
        return JsonMapper.builder().build().writeValueAsString(CheckoutTestData.receipt(order.id(), order.itemId(), PLACED_AT));
    }

    public static void receipt(JdbcTemplate jdbc, Long owner, HistoricalOrder order, String key) {
        jdbc.update("INSERT INTO checkout_receipts(customer_id,operation,idempotency_key,request_address_id,request_fingerprint,order_id,receipt_version,response_status,location,receipt_body,created_at) VALUES (?,'CHECKOUT',?,1,?,?,1,201,?,CAST(? AS JSONB),?)",
                owner, key, CheckoutTestData.ADDRESS_ONE_FINGERPRINT, order.id(), "/api/me/orders/" + order.id(), body(order), java.sql.Timestamp.from(PLACED_AT));
    }

    public record HistoricalOrder(Long id, Long itemId) { }

    public static Scenario scenario(EntityManager entities) {
        User owner = WishlistFixtures.persistUser(entities);
        User foreign = WishlistFixtures.persistUser(entities);
        Address address = new Address();
        address.setUser(owner);
        address.setRecipientName("Recipient");
        address.setRecipientPhone("0123456789");
        address.setAddressText("Address");
        address.setIsDefault(true);
        entities.persist(address);
        CatalogFixtures.ProductFixture product = CatalogFixtures.persistReadyProduct(entities);
        CatalogFixtures.ProductFixture other = CatalogFixtures.persistReadyProduct(entities);
        Cart cart = new Cart(owner.getId());
        entities.persist(cart);
        CartItem item = cartItem(entities,cart,product,true);
        CartItem unselected = cartItem(entities,cart,other,false);
        entities.flush();
        com.example.demo.fixture.inventory.InventoryFixtures.supplyStock(entities, product.variant().getId(), 1000);
        com.example.demo.fixture.inventory.InventoryFixtures.supplyStock(entities, other.variant().getId(), 1000);
        return new Scenario(owner.getId(),foreign.getId(),address.getId(),cart.getId(),item.getId(),unselected.getId(),
                product.product().getId(),product.variant().getId());
    }

    private static CartItem cartItem(EntityManager entities, Cart cart, CatalogFixtures.ProductFixture product, boolean selected) {
        CartItem item = new CartItem(cart.getId(),PLACED_AT);
        item.setProductId(product.product().getId());
        item.setProductVariantId(product.variant().getId());
        item.setQuantity(2);
        item.setSelected(selected);
        item.setPublicProductName("Earlier display name");
        item.setPublicSku("Earlier display SKU");
        item.setPublicOptions(java.util.Map.of());
        entities.persist(item);
        return item;
    }

    public record Scenario(Long owner, Long foreign, Long address, Long cart, Long item, Long unselected,
            UUID product, UUID variant) { }

    public static CartItem additionalItem(EntityManager entities, Long cartId, boolean selected) {
        CatalogFixtures.ProductFixture product = CatalogFixtures.persistReadyProduct(entities);
        CartItem item = cartItem(entities,entities.getReference(Cart.class,cartId),product,selected);
        entities.flush();
        com.example.demo.fixture.inventory.InventoryFixtures.supplyStock(entities, product.variant().getId(), 1000);
        return item;
    }
}
