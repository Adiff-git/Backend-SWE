package com.example.demo.order;

import com.example.demo.fixture.order.OrderFixtures;
import com.example.demo.order.controller.OrderController;
import com.example.demo.order.entity.OrderStatus;
import com.example.demo.user.service.UserService;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class OrderFlowIT {
    @Autowired MockMvc mvc;
    @Autowired UserService users;
    @Autowired PasswordEncoder passwords;
    @Autowired JsonMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entities;
    @Autowired TransactionTemplate transactions;
    @Autowired @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mappings;
    Long owner;
    Long order;
    String email;
    String token;
    String refresh;

    @BeforeEach void prepare() throws Exception {
        cleanup();
        email = "order-flow-"+UUID.randomUUID()+"@example.com";
        owner = users.createVerifiedAccount(email,passwords.encode("Password 123"),Instant.now());
        order = transactions.execute(s -> OrderFixtures.persist(entities,owner,OrderStatus.CONFIRMED,5).getId());
        MvcResult login = login();
        token = login.getResponse().getHeader("Authorization");
        refresh = json.readTree(login.getResponse().getContentAsString()).get("refreshToken").asString();
    }
    @AfterEach void cleanup() { jdbc.execute("TRUNCATE TABLE users,categories CASCADE"); }

    @Test void orderFlow_shouldRetainHistoryAcrossCancellationLogoutAndLaterSession() throws Exception {
        mvc.perform(get("/api/me/orders?status=CONFIRMED").header("Authorization",token)).andExpect(status().isOk());
        JsonNode before = detail(token);
        mvc.perform(post("/api/me/orders/"+order+"/cancel").header("Authorization",token).contentType("application/json")
                .content("{\"reason\":\" Changed plans \"}")).andExpect(status().isNoContent());
        mvc.perform(post("/api/me/orders/"+order+"/cancel").header("Authorization",token)).andExpect(status().isNoContent());
        JsonNode cancelled = detail(token);
        assertThat(cancelled.get("status").asString()).isEqualTo("CANCELLED");
        assertThat(cancelled.get("cancellationReason").asString()).isEqualTo("Changed plans");
        assertThat(cancelled.get("items")).isEqualTo(before.get("items"));
        assertThat(cancelled.get("deliveryAddressSnapshot")).isEqualTo(before.get("deliveryAddressSnapshot"));
        assertThat(cancelled.get("payableTotal")).isEqualTo(before.get("payableTotal"));
        mvc.perform(post("/api/auth/logout").contentType("application/json").content(json.writeValueAsString(Map.of("refreshToken",refresh))))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/me/orders").header("Authorization",token)).andExpect(status().isUnauthorized());
        String later = login().getResponse().getHeader("Authorization");
        assertThat(detail(later)).isEqualTo(cancelled);
    }

    @Test void orderFlow_shouldDenyCustomRoleWithoutGrantAndUseCurrentAccountState() throws Exception {
        jdbc.update("DELETE FROM user_roles WHERE user_id=?",owner);
        Long role = jdbc.queryForObject("INSERT INTO roles(name) VALUES (?) RETURNING id",Long.class,"ORDER_CUSTOM_"+UUID.randomUUID());
        jdbc.update("INSERT INTO user_roles(user_id,role_id) VALUES (?,?)",owner,role);
        mvc.perform(get("/api/me/orders").header("Authorization",token)).andExpect(status().isForbidden());
        mvc.perform(get("/api/me/orders/"+order).header("Authorization",token)).andExpect(status().isForbidden());
        mvc.perform(post("/api/me/orders/"+order+"/cancel").header("Authorization",token)).andExpect(status().isForbidden());
        jdbc.update("INSERT INTO role_permissions(role_id,permission_id) SELECT ?,id FROM permissions WHERE name='MANAGE_ORDER'",role);
        mvc.perform(get("/api/me/orders").header("Authorization",token)).andExpect(status().isOk());
        jdbc.update("UPDATE users SET account_status='INACTIVE' WHERE id=?",owner);
        mvc.perform(get("/api/me/orders").header("Authorization",token)).andExpect(status().isUnauthorized());
    }

    @Test void orderFlow_shouldExposeExactlyThreeRoutesAndNoCustomerCompletion() throws Exception {
        List<String> routes = mappings.getHandlerMethods().entrySet().stream()
                .filter(entry -> entry.getValue().getBeanType()==OrderController.class)
                .flatMap(entry -> entry.getKey().getPatternValues().stream()).toList();
        assertThat(routes).containsExactlyInAnyOrder("/api/me/orders","/api/me/orders/{orderId}","/api/me/orders/{orderId}/cancel");
        jdbc.update("UPDATE orders SET status='SHIPPING' WHERE id=?",order);
        Map<String,Object> before = jdbc.queryForMap("SELECT * FROM orders WHERE id=?",order);
        mvc.perform(post("/api/me/orders/"+order+"/confirm-receipt").header("Authorization",token)).andExpect(status().isForbidden());
        mvc.perform(put("/api/me/orders/"+order+"/status").header("Authorization",token).contentType("application/json").content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForMap("SELECT * FROM orders WHERE id=?",order)).isEqualTo(before);
    }

    private MvcResult login() throws Exception {
        return mvc.perform(post("/api/auth/login").contentType("application/json")
                .content(json.writeValueAsString(Map.of("identifier",email,"password","Password 123"))))
                .andExpect(status().isOk()).andReturn();
    }
    private JsonNode detail(String access) throws Exception {
        return json.readTree(mvc.perform(get("/api/me/orders/"+order).header("Authorization",access))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
}
