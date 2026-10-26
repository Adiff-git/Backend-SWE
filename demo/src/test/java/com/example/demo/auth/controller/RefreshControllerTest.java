package com.example.demo.auth.controller;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class RefreshControllerTest extends AuthControllerTestSupport {
    @Test void execute_shouldRejectMissingNullEmptyWrongTypeAndOversizedSecret() throws Exception {
        for(String body:java.util.List.of("{\"refreshToken\":123}","{\"refreshToken\":true}","{}","{\"refreshToken\":null}","{\"refreshToken\":\"\"}","{\"refreshToken\":{}}","{\"refreshToken\":\""+"a".repeat(1025)+"\"}")) {
            mvc.perform(post("/api/auth/refresh").contentType("application/json").content(body)).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(auth);
    }
    @Test void execute_shouldMapUnknownCredentialToGeneric401() throws Exception {
        when(auth.refresh("secret")).thenThrow(new com.example.demo.auth.exception.AuthenticationFailedException());
        mvc.perform(post("/api/auth/refresh").header("Authorization","Bearer stale").contentType("application/json").content("{\"refreshToken\":\"secret\"}")).andExpect(status().isUnauthorized()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))));
    }
}
