package com.example.demo.order.controller;

import com.example.demo.auth.config.AuthSecurityConfiguration;
import com.example.demo.auth.config.JwtProperties;
import com.example.demo.auth.security.ApiAccessDeniedHandler;
import com.example.demo.auth.security.ApiAuthenticationEntryPoint;
import com.example.demo.auth.security.DomainUserDetailsService;
import com.example.demo.auth.service.AuthSessionService;
import com.example.demo.common.exception.ApiErrorFactory;
import com.example.demo.common.identity.CurrentUserIdProvider;
import com.example.demo.order.dto.response.OrderPage;
import com.example.demo.order.exception.OrderNotFoundException;
import com.example.demo.order.service.OrderService;
import com.example.demo.user.service.UserService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

@WebMvcTest(OrderController.class)
@Import({AuthSecurityConfiguration.class,JwtProperties.class,DomainUserDetailsService.class,
        ApiAuthenticationEntryPoint.class,ApiAccessDeniedHandler.class,ApiErrorFactory.class,OrderRequestReader.class})
@TestPropertySource(properties={"security.jwt.secret=controlled-test-key-with-at-least-32-bytes","security.jwt.issuer=urn:test"})
class OrderControllerTest {
    @Test void cancelOrder_shouldReturnSafeInventoryConflictWithoutStockOrOwnershipDetails() throws Exception {
        org.mockito.Mockito.doThrow(new com.example.demo.inventory.exception.InventoryConflictException()).when(service)
                .cancelOrder(eq(1L),eq(7L),any());
        mvc.perform(post("/api/me/orders/7/cancel").with(jwt().authorities(new SimpleGrantedAuthority("MANAGE_ORDER"))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVENTORY_CONFLICT"))
                .andExpect(jsonPath("$.inventoryBalanceId").doesNotExist()).andExpect(jsonPath("$.ownerId").doesNotExist())
                .andExpect(header().string("Cache-Control", "private, no-store"));
    }

    @Test void orderErrors_shouldKeepPrivateNoStoreBeforeAuthenticationAndMediaParsing() throws Exception {
        mvc.perform(post("/api/me/orders/7/cancel")).andExpect(status().isUnauthorized())
                .andExpect(header().string("Cache-Control", "private, no-store"));
        mvc.perform(post("/api/me/orders/7/cancel").with(jwt())).andExpect(status().isForbidden())
                .andExpect(header().string("Cache-Control", "private, no-store"));
        mvc.perform(post("/api/me/orders/7/cancel").with(jwt().authorities(new SimpleGrantedAuthority("MANAGE_ORDER")))
                .contentType("text/plain").content("{}"))
                .andExpect(status().isUnsupportedMediaType()).andExpect(header().string("Cache-Control", "private, no-store"));
    }
    @Autowired MockMvc mvc;
    @MockitoBean OrderService service;
    @MockitoBean UserService users;
    @MockitoBean AuthSessionService sessions;
    @MockitoBean CurrentUserIdProvider identity;
    @BeforeEach void prepare() { when(identity.getCurrentUserId()).thenReturn(1L); }

    @Test void listOrders_shouldReturnPrivatePageAndAcceptMaximumPage() throws Exception {
        when(service.listOrders(eq(1L),any())).thenReturn(new OrderPage(List.of(),Integer.MAX_VALUE,100,125,2));
        mvc.perform(get("/api/me/orders?page=2147483647&size=100").with(jwt().authorities(new SimpleGrantedAuthority("MANAGE_ORDER"))))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","private, no-store"))
                .andExpect(jsonPath("$.totalItems").value(125)).andExpect(jsonPath("$.totalPages").value(2));
    }

    @ParameterizedTest @ValueSource(strings={"page=", "size=0", "size=101", "page=-1", "page=01", "page=2147483648", "page=1&page=1", "status=", "status=shipping", "status=DELIVERED", "status=PENDING&status=SHIPPING", "sort=status,asc", "sort=placedAt,ASC", "ownerId=2"})
    void listOrders_shouldRejectInvalidParameters(String query) throws Exception {
        mvc.perform(get("/api/me/orders?"+query).with(jwt().authorities(new SimpleGrantedAuthority("MANAGE_ORDER"))))
                .andExpect(status().isBadRequest());
    }

    @Test void listOrders_shouldEnforceSecurityBeforeBinding() throws Exception {
        mvc.perform(get("/api/me/orders?page=bad")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me/orders?page=bad").with(jwt())).andExpect(status().isForbidden());
        mvc.perform(get("/api/me/orders").with(jwt().authorities(new SimpleGrantedAuthority("MANAGE_ORDER"))).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest @ValueSource(strings={"0","01","-1","abc","9223372036854775808"})
    void getOrder_shouldRejectInvalidIdentifier(String id) throws Exception {
        mvc.perform(get("/api/me/orders/"+id).with(jwt().authorities(new SimpleGrantedAuthority("MANAGE_ORDER"))))
                .andExpect(status().isBadRequest());
    }

    @Test void getOrder_shouldUseSafeNotFoundAndRejectExtraInput() throws Exception {
        when(service.getOrder(1L,7L)).thenThrow(new OrderNotFoundException());
        mvc.perform(get("/api/me/orders/7").with(jwt().authorities(new SimpleGrantedAuthority("MANAGE_ORDER"))))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
        mvc.perform(get("/api/me/orders/7?ownerId=2").with(jwt().authorities(new SimpleGrantedAuthority("MANAGE_ORDER"))))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/me/orders/7").with(jwt().authorities(new SimpleGrantedAuthority("MANAGE_ORDER"))).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest @ValueSource(strings={"","{}","{\"reason\":null}","{\"reason\":\"  \"}","{\"reason\":\" Changed mind \"}"})
    void cancelOrder_shouldAcceptOptionalReasonAndReturnEmpty204(String body) throws Exception {
        mvc.perform(post("/api/me/orders/7/cancel").with(jwt().authorities(new SimpleGrantedAuthority("MANAGE_ORDER")))
                .contentType("application/json").content(body)).andExpect(status().isNoContent())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(""));
    }

    @ParameterizedTest @ValueSource(strings={"null","[]","true"," ","{\"reason\":4}","{\"reason\":\"x\",\"reason\":\"y\"}","{\"ownerId\":2}","{} {}","{\"status\":\"COMPLETED\"}"})
    void cancelOrder_shouldRejectMalformedRequestEvenOnRepeat(String body) throws Exception {
        mvc.perform(post("/api/me/orders/7/cancel").with(jwt().authorities(new SimpleGrantedAuthority("MANAGE_ORDER")))
                .contentType("application/json").content(body)).andExpect(status().isBadRequest());
    }

    @Test void cancelOrder_shouldEnforceMediaTypeAndSecurityBeforeParsing() throws Exception {
        mvc.perform(post("/api/me/orders/7/cancel").contentType("text/plain").content("bad")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/me/orders/7/cancel").with(jwt()).contentType("text/plain").content("bad")).andExpect(status().isForbidden());
        mvc.perform(post("/api/me/orders/7/cancel").with(jwt().authorities(new SimpleGrantedAuthority("MANAGE_ORDER"))))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/me/orders/7/cancel").with(jwt().authorities(new SimpleGrantedAuthority("MANAGE_ORDER"))).content("{}"))
                .andExpect(status().isUnsupportedMediaType());
        mvc.perform(post("/api/me/orders/7/cancel").with(jwt().authorities(new SimpleGrantedAuthority("MANAGE_ORDER")))
                .contentType("text/plain").content("{}")).andExpect(status().isUnsupportedMediaType());
        org.mockito.Mockito.doThrow(new com.example.demo.order.exception.OrderConflictException()).when(service)
                .cancelOrder(eq(1L),eq(7L),any());
        mvc.perform(post("/api/me/orders/7/cancel").with(jwt().authorities(new SimpleGrantedAuthority("MANAGE_ORDER"))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ORDER_CONFLICT"));
    }
}
