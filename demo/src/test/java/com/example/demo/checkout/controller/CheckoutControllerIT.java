package com.example.demo.checkout.controller;

import com.example.demo.config.CheckoutIntegrationSupport;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

@AutoConfigureMockMvc
class CheckoutControllerIT extends CheckoutIntegrationSupport {
    @Autowired MockMvc mvc;
    @Autowired PasswordEncoder passwords;
    @Autowired JsonMapper json;
    String token;
    String identifier;
    String refreshToken;
    final java.util.List<Long> customRoles = new java.util.ArrayList<>();

    @AfterEach void removeCustomRoles() {
        for (Long role : customRoles) {
            jdbc.update("DELETE FROM user_roles WHERE role_id=?",role);
            jdbc.update("DELETE FROM role_permissions WHERE role_id=?",role);
            jdbc.update("DELETE FROM roles WHERE id=?",role);
        }
    }

    @BeforeEach void loginFixtureCustomer() throws Exception {
        identifier = jdbc.queryForObject("SELECT username FROM users WHERE id=?",String.class,fixture.owner());
        jdbc.update("UPDATE users SET password=?,email=?,email_verified_at=CURRENT_TIMESTAMP WHERE id=?",
                passwords.encode("Password 123"),"checkout-"+UUID.randomUUID()+"@example.com",fixture.owner());
        jdbc.update("INSERT INTO user_roles(user_id,role_id) SELECT ?,id FROM roles WHERE name='USER'",fixture.owner());
        token = login();
    }

    String login() throws Exception {
        org.springframework.mock.web.MockHttpServletResponse response=mvc.perform(post("/api/auth/login").contentType("application/json")
                .content(json.writeValueAsString(Map.of("identifier",identifier,"password","Password 123"))))
                .andExpect(status().isOk()).andReturn().getResponse();
        refreshToken=json.readTree(response.getContentAsString()).get("refreshToken").asString();
        return response.getHeader("Authorization");
    }

    MockHttpServletRequestBuilder checkout(String key, Long address) {
        return post("/api/me/cart/checkout").header("Authorization",token).header("Idempotency-Key",key)
                .contentType("application/json").content(json.writeValueAsString(Map.of("addressId",address)));
    }

    @Test void checkout_shouldReturnPrivateCreatedReceiptAndExistingHistory() throws Exception {
        org.springframework.mock.web.MockHttpServletResponse response = mvc.perform(checkout("Api_checkout_key_1",fixture.address()))
                .andExpect(status().isCreated()).andExpect(header().string("Cache-Control","private, no-store"))
                .andReturn().getResponse();
        JsonNode receipt = json.readTree(response.getContentAsString());
        Long id = receipt.get("order").get("id").longValue();
        assertThat(response.getHeader("Location")).isEqualTo("/api/me/orders/"+id);
        assertThat(receipt.get("order").get("items").size()).isEqualTo(1);
        assertThat(receipt.get("order").get("recipientNameSnapshot").asText()).isEqualTo("Recipient");
        JsonNode page = json.readTree(mvc.perform(get("/api/me/orders?status=PENDING").header("Authorization",token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(page.get("items").get(0).get("id").longValue()).isEqualTo(id);
        JsonNode detail = json.readTree(mvc.perform(get("/api/me/orders/"+id).header("Authorization",token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(detail.get("id").longValue()).isEqualTo(id);
        assertThat(jdbc.queryForList("SELECT id FROM cart_items",Long.class)).containsExactly(fixture.unselected());
    }

    @Test void checkout_shouldHideForeignAndUnknownAddressesAndKeepKeyReusable() throws Exception {
        Long foreignAddress = jdbc.queryForObject("INSERT INTO addresses(user_id,recipient_name,recipient_phone,address_text,is_default) VALUES (?,'Foreign','0123456789','Hidden',TRUE) RETURNING id",Long.class,fixture.foreign());
        String detail = null;
        for (Long address : List.of(foreignAddress,Long.MAX_VALUE)) {
            JsonNode error = json.readTree(mvc.perform(checkout("Api_reusable_key_1",address))
                    .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString());
            if (detail == null) detail = error.get("detail").asText();
            else assertThat(error.get("detail").asText()).isEqualTo(detail);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM checkout_receipts",Integer.class)).isZero();
        mvc.perform(checkout("Api_reusable_key_1",fixture.address())).andExpect(status().isCreated());
    }

    @Test void checkout_shouldRequireCurrentCheckoutGrantWithoutAdditionalDomainGrants() throws Exception {
        jdbc.update("DELETE FROM user_roles WHERE user_id=?",fixture.owner());
        mvc.perform(checkout("Api_grant_key_123",fixture.address())).andExpect(status().isForbidden());
        Long role = jdbc.queryForObject("INSERT INTO roles(name) VALUES (?) RETURNING id",Long.class,"CHECKOUT_ONLY_"+UUID.randomUUID());
        customRoles.add(role);
        jdbc.update("INSERT INTO user_roles(user_id,role_id) VALUES (?,?)",fixture.owner(),role);
        jdbc.update("INSERT INTO role_permissions(role_id,permission_id) SELECT ?,id FROM permissions WHERE name='MANAGE_CHECKOUT'",role);
        JsonNode body = json.readTree(mvc.perform(checkout("Api_grant_key_123",fixture.address()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        mvc.perform(get("/api/me/orders/"+body.get("order").get("id").longValue()).header("Authorization",token))
                .andExpect(status().isForbidden());
        jdbc.update("DELETE FROM role_permissions WHERE role_id=?",role);
        mvc.perform(checkout("Api_grant_key_123",fixture.address())).andExpect(status().isForbidden());
        jdbc.update("INSERT INTO role_permissions(role_id,permission_id) SELECT ?,id FROM permissions WHERE name='MANAGE_CHECKOUT'",role);
        mvc.perform(checkout("Api_grant_key_123",fixture.address())).andExpect(status().isCreated());
    }

    @Test void checkout_shouldExposeLocationForBrowserAndRejectInactiveIdentity() throws Exception {
        mvc.perform(checkout("Api_browser_key_1",fixture.address()).header("Origin","http://localhost:3000"))
                .andExpect(status().isCreated()).andExpect(header().string("Access-Control-Expose-Headers","Authorization, Location"));
        jdbc.update("UPDATE users SET account_status='INACTIVE' WHERE id=?",fixture.owner());
        mvc.perform(checkout("Api_browser_key_1",fixture.address())).andExpect(status().isUnauthorized());
    }

    @Test void checkout_shouldRecoverOriginalReceiptAfterLostResponseAddressDeletionCartChangeAndCancellation() throws Exception {
        JsonNode original=json.readTree(mvc.perform(checkout("Lost_response_12345",fixture.address()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        Long order=original.get("order").get("id").longValue();
        // Discarded HTTP result models a committed response the caller never received.
        jdbc.update("UPDATE cart_items SET selected=TRUE,quantity=3 WHERE id=?",fixture.unselected());
        jdbc.update("DELETE FROM addresses WHERE id=?",fixture.address());
        mvc.perform(post("/api/me/orders/"+order+"/cancel").header("Authorization",token)).andExpect(status().isNoContent());
        JsonNode replay=json.readTree(mvc.perform(checkout("Lost_response_12345",fixture.address()))
                .andExpect(status().isCreated()).andExpect(header().string("Location","/api/me/orders/"+order))
                .andReturn().getResponse().getContentAsString());
        assertThat(replay).isEqualTo(original);
        assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE id=?",String.class,order)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT quantity FROM cart_items WHERE id=?",Integer.class,fixture.unselected())).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders",Integer.class)).isEqualTo(1);
        mvc.perform(checkout("Lost_response_12345",Long.MAX_VALUE)).andExpect(status().isConflict());
        mvc.perform(checkout("Lost_response_12345",fixture.address())).andExpect(status().isCreated());
    }

    @Test void checkout_shouldRetainReceiptAcrossLogoutAndNewSessionWithoutAllowingRevokedToken() throws Exception {
        JsonNode original=json.readTree(mvc.perform(checkout("Session_replay_1234",fixture.address()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        mvc.perform(post("/api/auth/logout").contentType("application/json")
                .content(json.writeValueAsString(Map.of("refreshToken",refreshToken)))).andExpect(status().isNoContent());
        mvc.perform(checkout("Session_replay_1234",fixture.address())).andExpect(status().isUnauthorized());
        token=login();
        JsonNode replay=json.readTree(mvc.perform(checkout("Session_replay_1234",fixture.address()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        assertThat(replay).isEqualTo(original);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM orders",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM checkout_receipts",Integer.class)).isEqualTo(1);
    }
}
