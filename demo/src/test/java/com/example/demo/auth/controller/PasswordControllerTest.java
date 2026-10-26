package com.example.demo.auth.controller;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class PasswordControllerTest extends AuthControllerTestSupport {
    @Test void execute_shouldRequireRealBearerBeforePasswordService() throws Exception {
        mvc.perform(put("/api/auth/password").contentType("application/json").content("{\"currentPassword\":\"old password\",\"newPassword\":\"new password\"}")).andExpect(status().isUnauthorized());
        verifyNoInteractions(auth);
    }
}
