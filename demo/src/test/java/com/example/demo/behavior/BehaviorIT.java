package com.example.demo.behavior;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.when;

import com.example.demo.fixture.wishlist.WishlistFixtures;
import com.example.demo.catalog.entity.ProductStatus;
import jakarta.persistence.EntityManager;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(properties="spring.datasource.hikari.maximum-pool-size=4")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BehaviorIT {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @Autowired TransactionTemplate tx;
    @Autowired BehaviorService service;
    @Autowired BehaviorStore store;
    @Autowired com.example.demo.wishlist.service.WishlistService wishlist;
    @Autowired com.example.demo.user.service.UserService users;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder passwords;
    @Autowired JsonMapper json;
    @MockitoBean Clock clock;
    Instant now;
    UUID product;
    Actor customer;
    record Actor(Long id, String bearer) { }
    @BeforeEach void setup() throws Exception {
        cleanup();
        now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        when(clock.instant()).thenReturn(now);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        product = tx.execute(s -> WishlistFixtures.persistProduct(em, ProductStatus.PUBLISHED).getId());
        customer = actor();
    }
    @AfterEach void cleanup() { jdbc.execute("TRUNCATE users,categories CASCADE"); }
    Actor actor() throws Exception {
        String name = "behavior-" + UUID.randomUUID();
        var user = users.createVerifiedAccount(name+"@example.com", passwords.encode("Password 123"), now);
        var result = mvc.perform(post("/api/auth/login").contentType("application/json")
            .content(json.writeValueAsString(Map.of("identifier",name,"password","Password 123")))).andExpect(status().isOk()).andReturn();
        Long id = jdbc.queryForObject("SELECT id FROM users WHERE username=?", Long.class, name);
        return new Actor(id, result.getResponse().getHeader("Authorization"));
    }
    void admin(Actor actor) {
        jdbc.update("INSERT INTO roles(name) VALUES ('ADMIN') ON CONFLICT DO NOTHING");
        jdbc.update("INSERT INTO user_roles(user_id,role_id) SELECT ?,id FROM roles WHERE name='ADMIN'", actor.id());
    }
    Map<String,Object> event(String type) {
        var result = new LinkedHashMap<String,Object>();
        result.put("eventType", type); result.put("clientEventId", UUID.randomUUID().toString());
        if (type.equals("SEARCH")) result.put("searchQuery", "Điện thoại Samsung");
        else result.put("productId", product.toString());
        return result;
    }
    int submit(Actor actor, Object payload) throws Exception {
        return mvc.perform(post("/api/me/behavior/events").header("Authorization", actor.bearer())
            .contentType("application/json").content(json.writeValueAsString(payload))).andReturn().getResponse().getStatus();
    }
    long count() { return jdbc.queryForObject("SELECT count(*) FROM user_behavior_events", Long.class); }

    @Test void explicitEventsOnlyAndRetryConflict() throws Exception {
        mvc.perform(get("/api/products")).andExpect(status().isOk());
        mvc.perform(get("/api/products/"+product)).andExpect(status().isOk());
        assertThat(count()).isZero();
        for (String type : List.of("PRODUCT_VIEW","SEARCH","PRODUCT_CLICK")) {
            var payload = event(type);
            assertThat(submit(customer,payload)).isEqualTo(201);
            assertThat(submit(customer,payload)).isEqualTo(200);
            payload.put(type.equals("SEARCH") ? "searchQuery" : "productId", type.equals("SEARCH") ? "laptop" : UUID.randomUUID().toString());
            assertThat(submit(customer,payload)).isEqualTo(409);
        }
        assertThat(count()).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT normalized_query FROM user_behavior_events WHERE event_type='SEARCH'",String.class)).isEqualTo("dien thoai samsung");
    }
    @Test void authenticationOwnershipAndAdmin() throws Exception {
        mvc.perform(post("/api/me/behavior/events").contentType("application/json").content(json.writeValueAsString(event("SEARCH")))).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me/behavior")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/behavior/overview")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/behavior/overview").header("Authorization",customer.bearer())).andExpect(status().isForbidden());
        Actor other = actor(); Actor admin = actor(); admin(admin);
        assertThat(submit(admin,event("SEARCH"))).isEqualTo(403);
        assertThat(submit(customer,event("SEARCH"))).isEqualTo(201);
        mvc.perform(get("/api/me/behavior").header("Authorization",other.bearer())).andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(0));
        mvc.perform(get("/api/me/behavior").param("customerId",customer.id().toString()).header("Authorization",other.bearer())).andExpect(status().isBadRequest());
        mvc.perform(get("/api/internal/behavior/users/"+customer.id()).header("Authorization",other.bearer())).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/behavior/overview").header("Authorization",admin.bearer())).andExpect(status().isOk()).andExpect(jsonPath("$.counts.SEARCH").value(1));
    }
    @ParameterizedTest @ValueSource(strings={"WISHLIST_ADD","WISHLIST_REMOVE","PURCHASE","ADD_TO_CART","BOGUS"})
    void disallowClientBusinessEvents(String type) throws Exception { assertThat(submit(customer,event(type))).isEqualTo(400); }
    @Test void strictFieldsAndMalformedJson() throws Exception {
        for (String field : List.of("customerId","userId","occurredAt","metadata")) {
            var payload=event("SEARCH"); payload.put(field,"spoof"); assertThat(submit(customer,payload)).isEqualTo(400);
        }
        for (String body : List.of("{", "null", "[]", "{}", "{\"eventType\":\"SEARCH\",\"clientEventId\":\"bad\"}"))
            mvc.perform(post("/api/me/behavior/events").header("Authorization",customer.bearer()).contentType("application/json").content(body)).andExpect(status().isBadRequest());
        for (String query : List.of("", "   ", "x".repeat(257), "\u0000", "!!!", "x ".repeat(17))) {
            var payload=event("SEARCH"); payload.put("searchQuery",query); assertThat(submit(customer,payload)).isEqualTo(400);
        }
        var search=event("SEARCH"); search.put("productId",product); assertThat(submit(customer,search)).isEqualTo(400);
        var view=event("PRODUCT_VIEW"); view.put("searchQuery","abc"); assertThat(submit(customer,view)).isEqualTo(400);
        view=event("PRODUCT_VIEW"); view.remove("productId"); assertThat(submit(customer,view)).isEqualTo(400);
        view=event("PRODUCT_VIEW"); view.put("productId","bad"); assertThat(submit(customer,view)).isEqualTo(400);
        view=event("PRODUCT_VIEW"); view.remove("clientEventId"); assertThat(submit(customer,view)).isEqualTo(400);
        var click=event("PRODUCT_CLICK"); click.put("sourceContext","ARBITRARY"); assertThat(submit(customer,click)).isEqualTo(400);
        click.put("sourceContext","SEARCH_RESULTS"); assertThat(submit(customer,click)).isEqualTo(201);
        assertThat(count()).isEqualTo(1);
    }
    @Test void missingAndHiddenProducts() throws Exception {
        var payload=event("PRODUCT_VIEW"); payload.put("productId",UUID.randomUUID()); assertThat(submit(customer,payload)).isEqualTo(404);
        jdbc.update("UPDATE products SET status='DRAFT' WHERE id=?", product);
        assertThat(submit(customer,event("PRODUCT_VIEW"))).isEqualTo(404);
        assertThat(count()).isZero();
    }
    @Test void concurrentRetriesAndCustomerScopedKeys() throws Exception {
        Actor other=actor(); var payload=event("SEARCH");
        var pool=Executors.newFixedThreadPool(4); var start=new CountDownLatch(1);
        try {
            var tasks=new ArrayList<Future<Integer>>();
            for (int i=0;i<4;i++) tasks.add(pool.submit(()-> { start.await(); return submit(customer,payload); }));
            start.countDown(); var statuses=new ArrayList<Integer>();
            for(var task:tasks) statuses.add(task.get(20,TimeUnit.SECONDS));
            assertThat(statuses).containsExactlyInAnyOrder(201,200,200,200);
        } finally { pool.shutdownNow(); pool.awaitTermination(10,TimeUnit.SECONDS); }
        assertThat(submit(other,payload)).isEqualTo(201);
        assertThat(count()).isEqualTo(2);
    }
    @Test void wishlistStateTransitionsRollbackAndTrackingFailure() {
        assertThat(wishlist.addProduct(customer.id(),product).isCreated()).isTrue();
        wishlist.addProduct(customer.id(),product);
        assertThat(count()).isEqualTo(1);
        tx.executeWithoutResult(s -> { wishlist.removeProduct(customer.id(),product); s.setRollbackOnly(); });
        assertThat(count()).isEqualTo(1);
        wishlist.removeProduct(customer.id(),product); wishlist.removeProduct(customer.id(),product);
        assertThat(count()).isEqualTo(2);
        tx.executeWithoutResult(s -> { wishlist.addProduct(customer.id(),product); s.setRollbackOnly(); });
        assertThat(count()).isEqualTo(2);
        jdbc.execute("CREATE FUNCTION behavior_reject() RETURNS trigger AS $$ BEGIN RAISE EXCEPTION 'test failure'; END; $$ LANGUAGE plpgsql");
        jdbc.execute("CREATE TRIGGER behavior_reject BEFORE INSERT ON user_behavior_events FOR EACH ROW EXECUTE FUNCTION behavior_reject()");
        try {
            assertThatThrownBy(()->wishlist.addProduct(customer.id(),product)).isInstanceOf(RuntimeException.class);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM wishlist_items",Long.class)).isZero();
            assertThat(count()).isEqualTo(2);
        } finally { jdbc.execute("DROP TRIGGER behavior_reject ON user_behavior_events"); jdbc.execute("DROP FUNCTION behavior_reject()"); }
        assertThatThrownBy(()->wishlist.addProduct(customer.id(),UUID.randomUUID())).isInstanceOf(RuntimeException.class);
        assertThat(count()).isEqualTo(2);
    }
    @Test void nonCustomerWishlistDoesNotTrack() {
        admin(customer);
        wishlist.addProduct(customer.id(),product); wishlist.removeProduct(customer.id(),product);
        assertThat(count()).isZero();
    }
    void searchAt(Long owner, String query, Instant when) {
        store.insert(owner,new BehaviorRequest(BehaviorType.SEARCH,null,query,UUID.randomUUID(),null),query,when);
    }
    @Test void historyPaginationFiltersTiesAndExpiry() throws Exception {
        searchAt(customer.id(),"a",now); searchAt(customer.id(),"b",now); searchAt(customer.id(),"c",now.minusSeconds(1));
        searchAt(customer.id(),"expired",now.minus(BehaviorService.RETENTION).minusSeconds(1));
        assertThat(submit(customer,event("PRODUCT_VIEW"))).isEqualTo(201);
        var first=service.history(customer.id(),BehaviorType.SEARCH,null,null,0,1);
        var second=service.history(customer.id(),BehaviorType.SEARCH,null,null,1,1);
        assertThat(first.totalItems()).isEqualTo(3); assertThat(first.totalPages()).isEqualTo(3);
        assertThat(first.items().getFirst().id().toString()).isGreaterThan(second.items().getFirst().id().toString());
        assertThat(service.history(customer.id(),BehaviorType.SEARCH,null,null,0,1)).isEqualTo(first);
        assertThat(service.history(customer.id(),null,now.minusMillis(500),now,0,100).totalItems()).isEqualTo(3);
        for (String param:List.of("page=-1","size=0","size=101","page=100001","eventType=INVALID","from=bad","from="+now.plusSeconds(1))) {
            String[] parts=param.split("=",2);
            mvc.perform(get("/api/me/behavior").param(parts[0],parts[1]).header("Authorization",customer.bearer())).andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/me/behavior").param("page","0","1").header("Authorization",customer.bearer())).andExpect(status().isBadRequest());
        assertThatThrownBy(()->service.history(customer.id(),null,now.minusSeconds(100*86400),now,0,20)).isInstanceOf(BehaviorException.class);
    }
    @Test void retentionBoundaryRepeatAndConcurrentInsert() throws Exception {
        var cutoff=now.minus(BehaviorService.RETENTION);
        searchAt(customer.id(),"expired",cutoff.minusNanos(1000)); searchAt(customer.id(),"boundary",cutoff); searchAt(customer.id(),"inside",cutoff.plusNanos(1000));
        assertThat(service.history(customer.id(),null,null,null,0,20).totalItems()).isEqualTo(2);
        assertThat(store.cleanup(cutoff,1)).isEqualTo(1);
        assertThat(store.cleanup(cutoff,1)).isZero(); assertThat(count()).isEqualTo(2);
        var pool=Executors.newFixedThreadPool(2);
        try {
            var deletion=pool.submit(()->store.cleanup(cutoff,1000));
            var insertion=pool.submit(()-> { searchAt(customer.id(),"fresh",now); return true; });
            deletion.get(10,TimeUnit.SECONDS); insertion.get(10,TimeUnit.SECONDS);
        } finally { pool.shutdownNow(); }
        assertThat(count()).isEqualTo(3);
        new BehaviorRetention(store,clock).cleanup(); assertThat(count()).isEqualTo(3);
    }
    @Test void analyticsWindowsSuppressionAndBounds() throws Exception {
        Actor other=actor(), third=actor(), admin=actor(); admin(admin);
        for(Actor actor:List.of(customer,other,third)) searchAt(actor.id(),"laptop",now);
        searchAt(customer.id(),"private@example.com",now);
        searchAt(customer.id(),"old",now.minus(Duration.ofDays(8)));
        searchAt(customer.id(),"expired",now.minus(Duration.ofDays(91)));
        assertThat(submit(customer,event("PRODUCT_VIEW"))).isEqualTo(201);
        assertThat(submit(customer,event("PRODUCT_CLICK"))).isEqualTo(201);
        wishlist.addProduct(customer.id(),product); wishlist.removeProduct(customer.id(),product);
        mvc.perform(get("/api/admin/behavior/overview").header("Authorization",admin.bearer())).andExpect(status().isOk())
            .andExpect(jsonPath("$.counts.SEARCH").value(4)).andExpect(jsonPath("$.counts.PRODUCT_VIEW").value(1))
            .andExpect(jsonPath("$.counts.PRODUCT_CLICK").value(1)).andExpect(jsonPath("$.counts.WISHLIST_ADD").value(1))
            .andExpect(jsonPath("$.counts.WISHLIST_REMOVE").value(1)).andExpect(jsonPath("$.popularSearchTerms.length()").value(1))
            .andExpect(jsonPath("$.popularSearchTerms[0].query").value("laptop")).andExpect(jsonPath("$.mostViewedProducts[0].count").value(1));
        for(int days:List.of(30,90)) mvc.perform(get("/api/admin/behavior/overview").param("days",""+days).header("Authorization",admin.bearer()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.counts.SEARCH").value(5));
        mvc.perform(get("/api/admin/behavior/overview").param("days","365").header("Authorization",admin.bearer())).andExpect(status().isBadRequest());
    }
    @Test void databaseConstraintsAndDeletionCascade() {
        assertThatThrownBy(()->jdbc.update("INSERT INTO user_behavior_events(id,customer_id,event_type,occurred_at) VALUES (?,?,?,?)",
            UUID.randomUUID(),customer.id(),"PRODUCT_VIEW",java.sql.Timestamp.from(now))).isInstanceOf(org.springframework.dao.DataAccessException.class);
        searchAt(customer.id(),"test",now);
        jdbc.update("DELETE FROM refresh_credentials WHERE session_id IN (SELECT id FROM authentication_sessions WHERE user_id=?)",customer.id());
        jdbc.update("DELETE FROM authentication_sessions WHERE user_id=?",customer.id());
        // Other account relationships have independent deletion policies; removing them is an account-deletion responsibility.
        jdbc.update("DELETE FROM user_roles WHERE user_id=?",customer.id());
        jdbc.update("DELETE FROM users WHERE id=?",customer.id());
        assertThat(count()).isZero();
    }
    @Test void representativeWorkloadRemainsBounded() {
        jdbc.update("INSERT INTO user_behavior_events(id,customer_id,event_type,product_id,client_event_id,occurred_at) "
            + "SELECT gen_random_uuid(),?,'PRODUCT_VIEW',?,gen_random_uuid(),? FROM generate_series(1,1500)",
            customer.id(),product,java.sql.Timestamp.from(now));
        var page=service.history(customer.id(),null,null,null,0,100);
        assertThat(page.items()).hasSize(100); assertThat(page.totalItems()).isEqualTo(1500);
        var overview=service.overview(90);
        assertThat(overview.counts().get("PRODUCT_VIEW")).isEqualTo(1500);
        assertThat(overview.mostViewedProducts()).containsExactly(new BehaviorOverview.ProductCount(product,1500));
        assertThat(overview.popularSearchTerms()).isEmpty();
    }
    @Autowired org.springframework.web.context.WebApplicationContext context;
    @Test void generatedOpenApiIncludesStableBehaviorContract() throws Exception {
        // Inspect runtime documentation in-process; do not relax deployed security for documentation.
        var documentation=org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context).build();
        var result=documentation.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();
        var root=json.readTree(result.getResponse().getContentAsString());
        var post=root.path("paths").path("/api/me/behavior/events").path("post");
        assertThat(post.path("responses").has("201")).isTrue();
        assertThat(post.path("responses").has("409")).isTrue();
        assertThat(root.path("components").path("schemas").has("BehaviorPage")).isTrue();
        assertThat(root.path("components").path("schemas").has("BehaviorOverview")).isTrue();
        assertThat(root.path("components").path("securitySchemes").path("bearerAuth").path("scheme").asString()).isEqualTo("bearer");
    }

    @Test void productDeletionCascadesOnlyItsEvents() throws Exception {
        assertThat(submit(customer,event("PRODUCT_VIEW"))).isEqualTo(201);
        assertThat(submit(customer,event("SEARCH"))).isEqualTo(201);
        tx.executeWithoutResult(s -> {
            jdbc.update("DELETE FROM product_variants WHERE product_id=?",product);
            jdbc.update("DELETE FROM products WHERE id=?",product);
        });
        assertThat(count()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT event_type FROM user_behavior_events",String.class)).isEqualTo("SEARCH");
    }
    @Test void concurrentConflictingRetriesRejectLoser() throws Exception {
        var first=event("PRODUCT_VIEW"); var second=event("PRODUCT_CLICK");
        second.put("clientEventId",first.get("clientEventId"));
        var pool=Executors.newFixedThreadPool(2); var start=new CountDownLatch(1);
        try {
            var a=pool.submit(()->{start.await(); return submit(customer,first);});
            var b=pool.submit(()->{start.await(); return submit(customer,second);});
            start.countDown();
            assertThat(List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(201,409);
        } finally { pool.shutdownNow(); pool.awaitTermination(10,TimeUnit.SECONDS); }
        assertThat(count()).isEqualTo(1);
    }

}
