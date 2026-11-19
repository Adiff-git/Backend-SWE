package com.example.demo.order.controller;

import com.example.demo.fixture.order.OrderFixtures;
import com.example.demo.order.entity.OrderStatus;
import com.example.demo.user.service.UserService;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class OrderControllerIT {
    @Autowired MockMvc mvc;
    @Autowired UserService users;
    @Autowired PasswordEncoder passwords;
    @Autowired JsonMapper json;
    @Autowired EntityManager entities;
    @Autowired TransactionTemplate transactions;
    @Autowired JdbcTemplate jdbc;
    Long owner;
    String token;

    @BeforeEach void prepare() throws Exception {
        cleanup();
        String email = "order-" + UUID.randomUUID() + "@example.com";
        owner = users.createVerifiedAccount(email,passwords.encode("Password 123"),Instant.now());
        token = mvc.perform(post("/api/auth/login").contentType("application/json")
                .content(json.writeValueAsString(Map.of("identifier",email,"password","Password 123"))))
                .andExpect(status().isOk()).andReturn().getResponse().getHeader("Authorization");
    }
    @AfterEach void cleanup() { jdbc.execute("TRUNCATE TABLE users,categories CASCADE"); }

    @Test void listOrders_shouldTraverseEveryOwnedFilterAndSortWithExactMetadata() throws Exception {
        transactions.executeWithoutResult(s -> {
            OrderFixtures.corpus(entities,owner,125);
            OrderFixtures.corpus(entities,OrderFixtures.customer(entities).getId(),25);
        });
        jdbc.update("UPDATE orders SET payable_total=id%7,placed_at=placed_at-(id%3)*INTERVAL '1 second'");
        for (String filter : List.of("", "PENDING","CONFIRMED","SHIPPING","COMPLETED","CANCELLED")) {
            for (String sort : List.of("placedAt,asc","placedAt,desc","payableTotal,asc","payableTotal,desc")) {
                String column = sort.startsWith("placedAt") ? "placed_at" : "payable_total";
                String direction = sort.endsWith("asc") ? "ASC" : "DESC";
                List<Long> expected = jdbc.queryForList("SELECT id FROM orders WHERE customer_id=?" + (filter.isEmpty() ? "" : " AND status='"+filter+"'") + " ORDER BY " + column + " " + direction + ",id ASC",Long.class,owner);
                for (int size : new int[]{1,20,100}) {
                    List<Long> actual = new ArrayList<>();
                    int pages = (expected.size()+size-1)/size;
                    for (int page=0;page<=pages;page++) {
                        JsonNode body = json.readTree(mvc.perform(get("/api/me/orders" + (filter.isEmpty() ? "" : "?status=" + filter)).header("Authorization",token)
                                .param("page",Integer.toString(page)).param("size",Integer.toString(size))
                                .param("sort",sort))
                                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
                        assertThat(body.get("totalItems").asInt()).isEqualTo(expected.size());
                        assertThat(body.get("totalPages").asInt()).isEqualTo(pages);
                        for (JsonNode item : body.get("items")) {
                            actual.add(item.get("id").asLong());
                            assertThat(item.has("deliveryAddressSnapshot")).isFalse();
                            assertThat(item.get("itemCount").asInt()).isEqualTo(5);
                            assertThat(item.get("itemPreview").size()).isEqualTo(3);
                            assertThat(item.get("remainingItemCount").asInt()).isEqualTo(2);
                        }
                    }
                    assertThat(actual).containsExactlyElementsOf(expected);
                }
            }
        }
    }

    @Test void listOrders_shouldUseCurrentPermissionsAndKeepMaximumPageContract() throws Exception {
        mvc.perform(get("/api/me/orders?page=2147483647&size=100").header("Authorization",token))
                .andExpect(status().isOk());
        jdbc.update("DELETE FROM user_roles WHERE user_id=?",owner);
        mvc.perform(get("/api/me/orders").header("Authorization",token)).andExpect(status().isForbidden());
        jdbc.update("INSERT INTO user_roles(user_id,role_id) SELECT ?,id FROM roles WHERE name='USER'",owner);
        mvc.perform(get("/api/me/orders").header("Authorization",token)).andExpect(status().isOk());
    }

    @Test void getOrder_shouldReturnCompletePrivateHistoryAndSafeForeignNotFound() throws Exception {
        Long id = transactions.execute(s -> OrderFixtures.persist(entities,owner,OrderStatus.COMPLETED,5).getId());
        Long foreign = transactions.execute(s -> OrderFixtures.persist(entities,OrderFixtures.customer(entities).getId(),OrderStatus.PENDING,1).getId());
        JsonNode body = json.readTree(mvc.perform(get("/api/me/orders/"+id).header("Authorization",token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(body.get("items").size()).isEqualTo(5);
        assertThat(body.get("deliveryAddressSnapshot").asString()).isEqualTo("Historical address");
        assertThat(body.get("completedAt").isNull()).isFalse();
        assertThat(body.has("payment")).isFalse();
        assertThat(body.has("itemPreview")).isFalse();
        for (Long missing : List.of(foreign,Long.MAX_VALUE)) {
            JsonNode error = json.readTree(mvc.perform(get("/api/me/orders/"+missing).header("Authorization",token))
                    .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString());
            assertThat(error.get("code").asString()).isEqualTo("ORDER_NOT_FOUND");
            assertThat(error.get("detail").asString()).isEqualTo("Order not found");
        }
    }

    @Test void cancelOrder_shouldApplyAllStatusesAndValidateRepeatsThroughRealHttp() throws Exception {
        for (OrderStatus state : OrderStatus.values()) {
            Long id = transactions.execute(s -> OrderFixtures.persist(entities,owner,state,2).getId());
            Map<String,Object> before = jdbc.queryForMap("SELECT * FROM orders WHERE id=?",id);
            int expected = state == OrderStatus.SHIPPING || state == OrderStatus.COMPLETED ? 409 : 204;
            mvc.perform(post("/api/me/orders/"+id+"/cancel").header("Authorization",token).contentType("application/json")
                    .content("{\"reason\":\" first \"}")).andExpect(status().is(expected));
            Map<String,Object> after = jdbc.queryForMap("SELECT * FROM orders WHERE id=?",id);
            if (expected==409 || state==OrderStatus.CANCELLED) assertThat(after).isEqualTo(before);
            else {
                assertThat(after.get("cancellation_reason")).isEqualTo("first");
                mvc.perform(post("/api/me/orders/"+id+"/cancel").header("Authorization",token).contentType("application/problem+json")
                        .content("{\"reason\":\"second\"}")).andExpect(status().isNoContent());
                assertThat(jdbc.queryForMap("SELECT * FROM orders WHERE id=?",id)).isEqualTo(after);
            }
            mvc.perform(post("/api/me/orders/"+id+"/cancel").header("Authorization",token).contentType("application/json")
                    .content("{\"reason\":5}")).andExpect(status().isBadRequest());
            assertThat(jdbc.queryForMap("SELECT * FROM orders WHERE id=?",id)).isEqualTo(after);
        }
        Long foreign = transactions.execute(s -> OrderFixtures.persist(entities,OrderFixtures.customer(entities).getId(),OrderStatus.PENDING,1).getId());
        for (Long missing : List.of(foreign,Long.MAX_VALUE)) {
            mvc.perform(post("/api/me/orders/"+missing+"/cancel").header("Authorization",token)).andExpect(status().isNotFound());
        }
    }

    @Test void cancelOrder_shouldReturnClock409ForFirstActionBut204ForFutureDatedRepeat() throws Exception {
        Long id = transactions.execute(s -> OrderFixtures.persist(entities,owner,OrderStatus.PENDING,1).getId());
        Instant future = Instant.now().plusSeconds(86400);
        jdbc.update("UPDATE orders SET placed_at=? WHERE id=?",java.sql.Timestamp.from(future),id);
        Map<String,Object> before = jdbc.queryForMap("SELECT * FROM orders WHERE id=?",id);
        mvc.perform(post("/api/me/orders/"+id+"/cancel").header("Authorization",token)).andExpect(status().isConflict());
        assertThat(jdbc.queryForMap("SELECT * FROM orders WHERE id=?",id)).isEqualTo(before);
        jdbc.update("UPDATE orders SET status='CANCELLED',cancelled_at=?,cancellation_reason='winner' WHERE id=?",java.sql.Timestamp.from(future.plusSeconds(1)),id);
        Map<String,Object> winner = jdbc.queryForMap("SELECT * FROM orders WHERE id=?",id);
        mvc.perform(post("/api/me/orders/"+id+"/cancel").header("Authorization",token)).andExpect(status().isNoContent());
        assertThat(jdbc.queryForMap("SELECT * FROM orders WHERE id=?",id)).isEqualTo(winner);
    }
}
