package com.example.demo.auth.controller;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import com.example.demo.auth.service.IssuedTokens;
import com.example.demo.auth.dto.response.TokenResponse;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class RegistrationLoginControllerTest extends AuthControllerTestSupport {
    @Test void execute_shouldPublishAccessOnlyInHeaderAndDoesNotCreateCookie() throws Exception {
        when(auth.register("Alice","Password 123")).thenReturn(new IssuedTokens("access",new TokenResponse("Bearer",900,"refresh",Instant.now().plusSeconds(604800))));
        mvc.perform(post("/api/auth/register").header("Authorization","Bearer stale").contentType("application/json").content("{\"username\":\" Alice \",\"password\":\"Password 123\"}")).andExpect(status().isCreated()).andExpect(header().string("Authorization","Bearer access")).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("accessToken").doesNotExist()).andExpect(jsonPath("refreshToken").value("refresh")).andExpect(cookie().doesNotExist("JSESSIONID"));
    }
    @Test void execute_shouldRejectMissingAndProtectedFieldsBeforeService() throws Exception {
        for(String body:java.util.List.of("{}","{\"username\":\"bad@name\",\"password\":\"Password 123\"}","{\"username\":\"Alice\",\"password\":\"Password 123\",\"role\":\"ADMIN\"}")) {
            mvc.perform(post("/api/auth/register").contentType("application/json").content(body)).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(auth);
    }
}
