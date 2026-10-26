package com.example.demo.auth.controller;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class LogoutControllerTest extends AuthControllerTestSupport {
    @Test void execute_shouldIgnoreStaleBearerAndUsesOnlyExactBodyCredential() throws Exception {
        mvc.perform(post("/api/auth/logout").header("Authorization","Bearer stale").contentType("application/json").content("{\"refreshToken\":\" exact \"}")).andExpect(status().isNoContent()).andExpect(content().string(""));
        verify(auth).logout(" exact ");
    }
    @Test void execute_shouldRejectClientSelectedOwner() throws Exception {
        mvc.perform(post("/api/auth/logout").contentType("application/json").content("{\"refreshToken\":\"secret\",\"userId\":1}")).andExpect(status().isBadRequest());
        verifyNoInteractions(auth);
    }
}
