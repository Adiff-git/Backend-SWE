package com.example.demo.review.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.auth.config.AuthSecurityConfiguration;
import com.example.demo.auth.config.JwtProperties;
import com.example.demo.auth.security.ApiAccessDeniedHandler;
import com.example.demo.auth.security.ApiAuthenticationEntryPoint;
import com.example.demo.auth.security.DomainUserDetailsService;
import com.example.demo.auth.service.AuthSessionService;
import com.example.demo.common.exception.ApiErrorFactory;
import com.example.demo.common.identity.CurrentUserIdProvider;
import com.example.demo.review.dto.response.ReviewResponse;
import com.example.demo.review.service.ReviewService;
import com.example.demo.user.dto.response.ReviewAuthorSummary;
import com.example.demo.user.service.UserService;
import java.time.Instant;
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

@WebMvcTest(OrderItemReviewController.class)
@Import({AuthSecurityConfiguration.class, JwtProperties.class, DomainUserDetailsService.class,
        ApiAuthenticationEntryPoint.class, ApiAccessDeniedHandler.class, ApiErrorFactory.class})
@TestPropertySource(properties = {"security.jwt.secret=controlled-test-key-with-at-least-32-bytes", "security.jwt.issuer=urn:test"})
class OrderItemReviewControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean ReviewService reviews;
    @MockitoBean UserService users;
    @MockitoBean AuthSessionService sessions;
    @MockitoBean CurrentUserIdProvider identity;
    @BeforeEach void actor() { when(identity.getCurrentUserId()).thenReturn(1L); }
    @Test void create_shouldReturn201AndLocation() throws Exception {
        when(reviews.create(eq(1L), eq(2L), eq(3L), any())).thenReturn(new ReviewResponse(7L, 5, null, List.of(), ReviewAuthorSummary.formerCustomer(), Instant.EPOCH, Instant.EPOCH, true));
        mvc.perform(post("/api/me/orders/2/items/3/reviews").with(jwt().authorities(new SimpleGrantedAuthority("MANAGE_ORDER")))
                .contentType("application/json").content("{\"rating\":5}"))
                .andExpect(status().isCreated()).andExpect(header().string("Location", "/api/me/reviews/7"))
                .andExpect(jsonPath("$.verifiedPurchase").value(true)).andExpect(jsonPath("$.authorId").doesNotExist());
    }
    @ParameterizedTest @ValueSource(strings = {"{}", "{\"rating\":null}", "{\"rating\":0}", "{\"rating\":\"5\"}", "{\"rating\":5.5}", "{\"rating\":5,\"imageIds\":null}", "{\"rating\":5,\"authorId\":2}"})
    void create_shouldRejectInvalidBody(String body) throws Exception {
        mvc.perform(post("/api/me/orders/2/items/3/reviews").with(jwt().authorities(new SimpleGrantedAuthority("MANAGE_ORDER")))
                .contentType("application/json").content(body)).andExpect(status().isBadRequest());
    }
    @Test void create_shouldRequireAuthenticationAndPermission() throws Exception {
        mvc.perform(post("/api/me/orders/2/items/3/reviews").contentType("application/json").content("{\"rating\":5}")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/me/orders/2/items/3/reviews").with(jwt()).contentType("application/json").content("{\"rating\":5}")).andExpect(status().isForbidden());
    }
}
