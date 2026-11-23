package com.example.demo.e2e;

import com.example.demo.catalog.CatalogFixtures;
import com.example.demo.catalog.entity.ProductStatus;
import com.example.demo.user.service.UserService;
import jakarta.persistence.EntityManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/** Real local HTTP account/address/cart/checkout/history/cancellation/session workflow. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@org.springframework.context.annotation.Import(com.example.demo.catalog.service.AdminCatalogIntegrationSupport.LocalSigning.class)
class CheckoutFlowIT {
    @LocalServerPort int port;
    @Autowired UserService users;
    @Autowired PasswordEncoder passwords;
    @Autowired EntityManager entities;
    @Autowired TransactionTemplate transactions;
    @Autowired JdbcTemplate jdbc;
    @Autowired JsonMapper json;

    @AfterEach void cleanup() { com.example.demo.fixture.inventory.InventoryFixtures.cleanRetainedInventory(jdbc); }

    @Test void checkout_shouldCompleteSingleVendorPurchaseAndRecoverOriginalOutcomeAcrossSession() throws Exception {
        cleanup();
        UUID[] variants=transactions.execute(s -> new UUID[]{
                CatalogFixtures.persistReadyProduct(entities).variant().getId(),
                CatalogFixtures.persistReadyProduct(entities).variant().getId(),
                CatalogFixtures.persistReadyProduct(entities).variant().getId()});
        for (UUID variant : variants) com.example.demo.fixture.inventory.InventoryFixtures.supplyStock(jdbc, variant, 1000);
        String email="checkout-flow-"+UUID.randomUUID()+"@example.com";
        users.createVerifiedAccount(email,passwords.encode("Password 123"),Instant.now());
        try(HttpClient client=HttpClient.newHttpClient()) {
            HttpResponse<String> login=http(client,"POST","/api/auth/login",null,
                    json.writeValueAsString(Map.of("identifier",email,"password","Password 123")),null,200);
            String token=login.headers().firstValue("Authorization").orElseThrow();
            String refresh=json.readTree(login.body()).get("refreshToken").asString();
            JsonNode address=json.readTree(http(client,"POST","/api/me/addresses",token,json.writeValueAsString(Map.of(
                    "recipientName","Flow recipient","recipientPhone","0123456789","addressText","Original address","isDefault",true)),null,201).body());
            Long addressId=address.get("id").longValue();
            Long eligible=add(client,token,variants[0],2);
            Long skipped=add(client,token,variants[1],1);
            Long unselected=add(client,token,variants[2],3);
            http(client,"PUT","/api/me/cart/items/"+unselected+"/selection",token,"{\"selected\":false}",null,200);
            jdbc.update("UPDATE products SET status='ARCHIVED',name='Hidden changed name' WHERE id=(SELECT product_id FROM product_variants WHERE id=?)",variants[1]);
            jdbc.update("UPDATE product_variants SET selling_price=1250 WHERE id=?",variants[0]);
            String command=json.writeValueAsString(Map.of("addressId",addressId));
            String key="Full_flow_key_12345";
            HttpResponse<String> created=http(client,"POST","/api/me/cart/checkout",token,command,key,201);
            JsonNode receipt=json.readTree(created.body());
            Long order=receipt.get("order").get("id").longValue();
            assertThat(created.headers().firstValue("Location")).hasValue("/api/me/orders/"+order);
            assertThat(receipt.get("order").get("status").asString()).isEqualTo("PENDING");
            assertThat(receipt.get("order").get("payableTotal").get("amount").longValue()).isEqualTo(2500);
            assertThat(receipt.get("order").get("items").size()).isEqualTo(1);
            assertThat(receipt.get("skippedItems").get(0).get("cartItemId").longValue()).isEqualTo(skipped);
            assertThat(receipt.get("skippedItems").get(0).get("reason").asString()).isEqualTo("PRODUCT_NOT_FOR_SALE");
            assertThat(receipt.get("skippedItems").get(0).size()).isEqualTo(2);
            JsonNode cart=json.readTree(http(client,"GET","/api/me/cart",token,null,null,200).body());
            assertThat(cart.get("items").size()).isEqualTo(2);
            for(JsonNode item:cart.get("items")) assertThat(item.get("id").longValue()).isNotEqualTo(eligible);
            JsonNode page=json.readTree(http(client,"GET","/api/me/orders?status=PENDING",token,null,null,200).body());
            assertThat(page.get("items").get(0).get("id").longValue()).isEqualTo(order);
            http(client,"GET","/api/me/orders/"+order,token,null,null,200);
            http(client,"PUT","/api/me/addresses/"+addressId,token,json.writeValueAsString(Map.of(
                    "recipientName","New recipient","recipientPhone","0987654321","addressText","Updated address")),null,200);
            http(client,"PUT","/api/me/cart/items/"+unselected+"/quantity",token,"{\"quantity\":4}",null,200);
            assertThat(json.readTree(http(client,"POST","/api/me/cart/checkout",token,command,key,201).body())).isEqualTo(receipt);
            http(client,"POST","/api/me/orders/"+order+"/cancel",token,"{\"reason\":\"Changed mind\"}",null,204);
            JsonNode current=json.readTree(http(client,"GET","/api/me/orders/"+order,token,null,null,200).body());
            assertThat(current.get("status").asString()).isEqualTo("CANCELLED");
            assertThat(current.get("deliveryAddressSnapshot").asString()).isEqualTo("Original address");
            assertThat(json.readTree(http(client,"POST","/api/me/cart/checkout",token,command,key,201).body())).isEqualTo(receipt);
            http(client,"POST","/api/auth/logout",null,json.writeValueAsString(Map.of("refreshToken",refresh)),null,204);
            http(client,"POST","/api/me/cart/checkout",token,command,key,401);
            HttpResponse<String> laterLogin=http(client,"POST","/api/auth/login",null,
                    json.writeValueAsString(Map.of("identifier",email,"password","Password 123")),null,200);
            String later=laterLogin.headers().firstValue("Authorization").orElseThrow();
            assertThat(json.readTree(http(client,"POST","/api/me/cart/checkout",later,command,key,201).body())).isEqualTo(receipt);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders",Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM checkout_receipts",Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT quantity FROM cart_items WHERE id=?",Integer.class,unselected)).isEqualTo(4);
        }
    }

    private Long add(HttpClient client,String token,UUID variant,int quantity) throws Exception {
        JsonNode body=json.readTree(http(client,"POST","/api/me/cart/items",token,json.writeValueAsString(Map.of(
                "productVariantId",variant,"quantity",quantity)),null,200).body());
        for(JsonNode item:body.get("items")) if(item.get("variantId").asString().equals(variant.toString())) return item.get("id").longValue();
        throw new AssertionError("Added cart row not returned");
    }

    private HttpResponse<String> http(HttpClient client,String method,String path,String token,String body,String key,int expected) throws Exception {
        HttpRequest.Builder request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(15));
        if(token!=null) request.header("Authorization",token);
        if(key!=null) request.header("Idempotency-Key",key);
        if(body!=null) request.header("Content-Type","application/json");
        HttpResponse<String> response=client.send(request.method(method,body==null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(method+" "+path).isEqualTo(expected);
        if(path.startsWith("/api/me/cart")) assertThat(response.headers().firstValue("Cache-Control")).hasValue("private, no-store");
        return response;
    }
}
