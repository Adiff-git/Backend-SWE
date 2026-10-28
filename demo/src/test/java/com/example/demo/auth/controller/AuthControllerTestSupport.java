package com.example.demo.auth.controller;
import com.example.demo.auth.config.*;
import com.example.demo.auth.security.*;
import com.example.demo.auth.service.*;
import com.example.demo.auth.converter.AuthConverter;
import com.example.demo.common.exception.ApiErrorFactory;
import com.example.demo.user.service.UserService;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
@WebMvcTest(AuthController.class)
@Import( {
    AuthSecurityConfiguration.class,JwtProperties.class,DomainUserDetailsService.class,SpringSecurityCurrentUserIdProvider.class,ApiAuthenticationEntryPoint.class,ApiAccessDeniedHandler.class,ApiErrorFactory.class,AuthConverter.class
}
)
@TestPropertySource(properties= {
    "security.jwt.secret=controlled-test-key-with-at-least-32-bytes","security.jwt.issuer=urn:test","security.cors.allowed-origins=http://localhost:3000"
}
)
abstract class AuthControllerTestSupport {
    @Autowired MockMvc mvc;
    @MockitoBean AuthService auth;
    @MockitoBean UserService users;
    @MockitoBean AuthSessionService sessions;
}
