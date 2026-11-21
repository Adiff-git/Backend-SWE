package com.example.demo.review.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.auth.config.AuthSecurityConfiguration;
import com.example.demo.auth.config.JwtProperties;
import com.example.demo.auth.security.ApiAccessDeniedHandler;
import com.example.demo.auth.security.ApiAuthenticationEntryPoint;
import com.example.demo.auth.security.DomainUserDetailsService;
import com.example.demo.auth.service.AuthSessionService;
import com.example.demo.common.exception.ApiErrorFactory;
import com.example.demo.common.identity.CurrentUserIdProvider;
import com.example.demo.review.service.ReviewService;
import com.example.demo.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ReviewController.class)
@Import({AuthSecurityConfiguration.class, JwtProperties.class, DomainUserDetailsService.class,
        ApiAuthenticationEntryPoint.class, ApiAccessDeniedHandler.class, ApiErrorFactory.class})
@TestPropertySource(properties = {"security.jwt.secret=controlled-test-key-with-at-least-32-bytes", "security.jwt.issuer=urn:test"})
class ReviewControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean ReviewService reviews;
    @MockitoBean UserService users;
    @MockitoBean AuthSessionService sessions;
    @MockitoBean CurrentUserIdProvider identity;
    @BeforeEach void actor() { org.mockito.Mockito.when(identity.getCurrentUserId()).thenReturn(1L); }
    @ParameterizedTest @ValueSource(strings = {"{}", "{\"rating\":null}", "{\"imageIds\":null}", "{\"rating\":6}", "{\"rating\":\"5\"}", "{\"orderItemId\":1}"})
    void update_shouldRejectEmptyInvalidOrImmutableInput(String body) throws Exception {
        mvc.perform(patch("/api/me/reviews/1").with(jwt().authorities(new SimpleGrantedAuthority("MANAGE_ORDER")))
                .contentType("application/json").content(body)).andExpect(status().isBadRequest());
    }
}
