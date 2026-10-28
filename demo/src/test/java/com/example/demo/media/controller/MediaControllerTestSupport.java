package com.example.demo.media.controller;

import com.example.demo.auth.config.AuthSecurityConfiguration;
import com.example.demo.auth.config.JwtProperties;
import com.example.demo.auth.security.ApiAccessDeniedHandler;
import com.example.demo.auth.security.ApiAuthenticationEntryPoint;
import com.example.demo.auth.security.CurrentStateJwtConverter;
import com.example.demo.auth.security.DomainUserDetailsService;
import com.example.demo.auth.service.AuthSessionService;
import com.example.demo.common.exception.ApiErrorFactory;
import com.example.demo.common.identity.CurrentUserIdProvider;
import com.example.demo.media.service.MediaService;
import com.example.demo.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;

@WebMvcTest(com.example.demo.media.controller.ImageUploadController.class)
@Import({AuthSecurityConfiguration.class, JwtProperties.class, DomainUserDetailsService.class,
        ApiAuthenticationEntryPoint.class, ApiAccessDeniedHandler.class, ApiErrorFactory.class})
@TestPropertySource(properties = {"security.jwt.secret=controlled-test-key-with-at-least-32-bytes",
        "security.jwt.issuer=urn:test", "security.cors.allowed-origins=http://localhost:3000"})
abstract class MediaControllerTestSupport {
    @Autowired MockMvc mvc;
    @MockitoBean MediaService media;
    @MockitoBean CurrentUserIdProvider identity;
    @MockitoBean UserService users;
    @MockitoBean AuthSessionService sessions;
    @BeforeEach void identity() { when(identity.getCurrentUserId()).thenReturn(8L); }
}
