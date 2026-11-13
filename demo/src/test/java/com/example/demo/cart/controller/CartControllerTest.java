package com.example.demo.cart.controller;

import com.example.demo.auth.config.AuthSecurityConfiguration;
import com.example.demo.auth.config.JwtProperties;
import com.example.demo.auth.security.ApiAccessDeniedHandler;
import com.example.demo.auth.security.ApiAuthenticationEntryPoint;
import com.example.demo.auth.security.DomainUserDetailsService;
import com.example.demo.auth.service.AuthSessionService;
import com.example.demo.cart.dto.response.AddCartResult;
import com.example.demo.cart.dto.response.CartResponse;
import com.example.demo.cart.service.CartService;
import com.example.demo.catalog.dto.response.SellingPrice;
import com.example.demo.common.exception.ApiErrorFactory;
import com.example.demo.common.identity.CurrentUserIdProvider;
import com.example.demo.user.service.UserService;
import java.math.BigInteger;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@WebMvcTest(CartController.class)
@Import({AuthSecurityConfiguration.class, JwtProperties.class, DomainUserDetailsService.class,
        ApiAuthenticationEntryPoint.class, ApiAccessDeniedHandler.class, ApiErrorFactory.class, CartRequestReader.class})
@TestPropertySource(properties = {"security.jwt.secret=controlled-test-key-with-at-least-32-bytes", "security.jwt.issuer=urn:test"})
class CartControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean CartService cart;
    @MockitoBean UserService users;
    @MockitoBean AuthSessionService sessions;
    @MockitoBean CurrentUserIdProvider identity;
    final String valid = "{\"variantId\":\"00000000-0000-4000-8000-000000000001\",\"quantity\":2}";
    final CartResponse empty = new CartResponse(List.of(), 0, 0, new SellingPrice(BigInteger.ZERO, "VND"), new SellingPrice(BigInteger.ZERO, "VND"));

    @BeforeEach void prepare() { when(identity.getCurrentUserId()).thenReturn(1L); }

    @Test void getAndAdd_shouldReturnPrivateCartAndCreationLocation() throws Exception {
        when(cart.getCart(1L)).thenReturn(empty);
        mvc.perform(get("/api/me/cart").with(jwt().authorities(new SimpleGrantedAuthority("MANAGE_CART"))))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(jsonPath("$.finalTotal.amount").value(0));
        for (boolean created : new boolean[]{true, false}) {
            when(cart.addItem(eq(1L), any())).thenReturn(new AddCartResult(created, 9L, empty));
            var result = mvc.perform(post("/api/me/cart/items").with(jwt().authorities(new SimpleGrantedAuthority("MANAGE_CART")))
                    .contentType("application/json").content(valid)).andExpect(status().is(created ? 201 : 200));
            if (created) result.andExpect(header().string("Location", "/api/me/cart/items/9"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings={"", "null", "[]", "{}", "{\"quantity\":2}", "{\"variantId\":null,\"quantity\":2}",
            "{\"variantId\":\"00000000-0000-4000-8000-000000000001\",\"quantity\":\"2\"}",
            "{\"variantId\":\"00000000-0000-4000-8000-000000000001\",\"quantity\":2.0}",
            "{\"variantId\":\"00000000-0000-4000-8000-000000000001\",\"quantity\":2e0}",
            "{\"variantId\":\"00000000-0000-4000-8000-000000000001\",\"quantity\":0}",
            "{\"variantId\":\"00000000-0000-4000-8000-000000000001\",\"quantity\":1001}",
            "{\"variantId\":\"00000000-0000-4000-8000-00000000000A\",\"quantity\":2}",
            "{\"variantId\":\"00000000-0000-4000-8000-000000000001\",\"quantity\":2,\"quantity\":3}",
            "{\"variantId\":\"00000000-0000-4000-8000-000000000001\",\"quantity\":2,\"price\":1}"})
    void add_shouldRejectInvalidShapeWithoutCallingService(String body) throws Exception {
        mvc.perform(post("/api/me/cart/items").with(jwt().authorities(new SimpleGrantedAuthority("MANAGE_CART")))
                .contentType("application/json").content(body)).andExpect(status().isBadRequest());
        verifyNoInteractions(cart);
    }

    @Test void security_shouldPrecedeBindingAndDenyUnsupportedMethods() throws Exception {
        mvc.perform(post("/api/me/cart/items").contentType("text/plain").content("bad")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/me/cart/items").with(jwt()).contentType("text/plain").content("bad")).andExpect(status().isForbidden());
        mvc.perform(patch("/api/me/cart").with(jwt().authorities(new SimpleGrantedAuthority("MANAGE_CART")))).andExpect(status().isForbidden());
        mvc.perform(post("/api/me/cart/items").with(jwt().authorities(new SimpleGrantedAuthority("MANAGE_CART")))
                .contentType("text/plain").content("bad")).andExpect(status().isUnsupportedMediaType());
        verifyNoInteractions(cart);
    }

    @Test void get_shouldRejectBodyAndOwnerQuery() throws Exception {
        mvc.perform(get("/api/me/cart?userId=2").with(jwt().authorities(new SimpleGrantedAuthority("MANAGE_CART")))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/me/cart").with(jwt().authorities(new SimpleGrantedAuthority("MANAGE_CART"))).content("{}")).andExpect(status().isBadRequest());
        verifyNoInteractions(cart);
    }

    @ParameterizedTest
    @ValueSource(strings={"{}", "{\"quantity\":2,\"variantId\":null}", "{\"quantity\":\"2\"}",
            "{\"quantity\":2.0}", "{\"quantity\":2,\"quantity\":3}", "{\"quantity\":1001}", "{\"quantity\":2,\"owner\":1}"})
    void update_shouldRejectInvalidBody(String body) throws Exception {
        mvc.perform(put("/api/me/cart/items/1").with(jwt().authorities(new SimpleGrantedAuthority("MANAGE_CART")))
                .contentType("application/json").content(body)).andExpect(status().isBadRequest());
        verifyNoInteractions(cart);
    }

    @Test void delete_shouldRejectBodyAndMalformedPath() throws Exception {
        mvc.perform(delete("/api/me/cart/items/1").with(jwt().authorities(new SimpleGrantedAuthority("MANAGE_CART")))
                .content("{}")).andExpect(status().isBadRequest());
        mvc.perform(delete("/api/me/cart/items/9223372036854775808").with(jwt().authorities(new SimpleGrantedAuthority("MANAGE_CART"))))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(cart);
    }

    @ParameterizedTest
    @ValueSource(strings={"{}", "null", "[]", "{\"selected\":null}", "{\"selected\":\"true\"}", "{\"selected\":1}",
            "{\"selected\":true,\"selected\":false}", "{\"selected\":true,\"quantity\":2}"})
    void selection_shouldRejectInvalidBooleanOnBothRoutes(String body) throws Exception {
        for (String route : List.of("/api/me/cart/selection", "/api/me/cart/items/1/selection")) {
            mvc.perform(put(route).with(jwt().authorities(new SimpleGrantedAuthority("MANAGE_CART")))
                    .contentType("application/json").content(body)).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(cart);
    }
}
