package com.example.demo.auth.controller;

import com.example.demo.auth.dto.response.PendingRegistrationResponse;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;

class RegistrationLoginControllerTest extends AuthControllerTestSupport {
    @Test void register_shouldAcceptPendingOnlyAndIgnoreUnrelatedBearer() throws Exception {
        when(registrations.initiate("Alice@example.test","Password 123")).thenReturn(new PendingRegistrationResponse("c".repeat(43),UUID.randomUUID(),Instant.now().plusSeconds(600)));
        mvc.perform(post("/api/auth/register").header("Authorization","Bearer stale").contentType("application/json").content("{\"email\":\"Alice@example.test\",\"password\":\"Password 123\"}"))
            .andExpect(status().isAccepted()).andExpect(header().doesNotExist("Authorization")).andExpect(header().string("Cache-Control","no-store"))
            .andExpect(jsonPath("accessToken").doesNotExist()).andExpect(jsonPath("refreshToken").doesNotExist()).andExpect(jsonPath("registrationCredential").exists()).andExpect(cookie().doesNotExist("JSESSIONID"));
        verifyNoInteractions(auth);
    }
    @Test void register_shouldRejectMissingWrongTypeAndProtectedFieldsBeforeService() throws Exception {
        for(String body:java.util.List.of("{}","{\"email\":42,\"password\":\"Password 123\"}","{\"email\":\"a@example.test\",\"password\":\"Password 123\",\"role\":\"ADMIN\"}","{\"username\":\"Alice\",\"password\":\"Password 123\"}"))
            mvc.perform(post("/api/auth/register").contentType("application/json").content(body)).andExpect(status().isBadRequest());
        verifyNoInteractions(auth,registrations);
    }
}
