package com.example.demo.auth.controller;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class EmailLoginControllerTest extends AuthControllerTestSupport {
    @Test void execute_shouldVerifyTrimsIdentifierAndPreservesExactPasswordWithGenericError() throws Exception {
        when(auth.login("Alice@Example.com"," exact ")).thenThrow(new com.example.demo.auth.exception.AuthenticationFailedException());
        mvc.perform(post("/api/auth/login").contentType("application/json").content("{\"identifier\":\" Alice@Example.com \",\"password\":\" exact \"}")).andExpect(status().isUnauthorized());
        verify(auth).login("Alice@Example.com"," exact ");
    }
}
