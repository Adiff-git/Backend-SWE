package com.example.demo.user.controller;

import com.example.demo.auth.config.AuthSecurityConfiguration;
import com.example.demo.auth.config.JwtProperties;
import com.example.demo.auth.security.ApiAccessDeniedHandler;
import com.example.demo.auth.security.ApiAuthenticationEntryPoint;
import com.example.demo.auth.security.CurrentStateJwtConverter;
import com.example.demo.auth.security.DomainUserDetailsService;
import com.example.demo.auth.service.AuthSessionService;
import com.example.demo.common.exception.ApiErrorFactory;
import com.example.demo.common.identity.CurrentUserIdProvider;
import com.example.demo.user.service.UserService;
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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import({AuthSecurityConfiguration.class, JwtProperties.class, DomainUserDetailsService.class,
        CurrentStateJwtConverter.class, ApiAuthenticationEntryPoint.class, ApiAccessDeniedHandler.class, ApiErrorFactory.class})
@TestPropertySource(properties = {"security.jwt.secret=controlled-test-key-with-at-least-32-bytes",
        "security.jwt.issuer=urn:test", "security.cors.allowed-origins=http://localhost:3000"})
class UserImageControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean UserService users;
    @MockitoBean CurrentUserIdProvider identity;
    @MockitoBean AuthSessionService sessions;

    @ParameterizedTest @ValueSource(strings = {"0", "-1", "1.5", "true", "[]", "\"1\"", "9223372036854775808"})
    void update_shouldRejectMalformedImageBeforeService(String value) throws Exception {
        mvc.perform(put("/api/me").with(jwt().authorities(new SimpleGrantedAuthority("MANAGE_USER_PROFILE")))
                .contentType("application/json").content("{\"profileImageId\":" + value + "}"))
                .andExpect(status().isBadRequest());
        org.mockito.Mockito.verifyNoInteractions(users);
    }
    @Test void update_shouldPreserveProfilePermissionRequirement() throws Exception {
        mvc.perform(put("/api/me").with(jwt()).contentType("application/json").content("{\"profileImageId\":null}"))
                .andExpect(status().isForbidden());
    }
}
