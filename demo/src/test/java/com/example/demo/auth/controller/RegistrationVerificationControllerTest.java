package com.example.demo.auth.controller;

import com.example.demo.auth.dto.response.PendingRegistrationResponse;
import com.example.demo.auth.dto.response.TokenResponse;
import com.example.demo.auth.exception.IdentityException;
import com.example.demo.auth.service.IssuedTokens;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@Import(RegistrationVerificationController.class)
class RegistrationVerificationControllerTest extends AuthControllerTestSupport {
    private final UUID id=UUID.randomUUID();
    private final String secret="a".repeat(43);
    @Test void confirm_shouldIssueOnlyHeaderAccessAfterServiceCommitAndIgnoreUnrelatedBearer() throws Exception {
        when(registrations.confirm(secret,id,"000123","Final 1234")).thenReturn(new IssuedTokens("access",new TokenResponse("Bearer",900,"refresh",Instant.now().plusSeconds(604800))));
        mvc.perform(post("/api/auth/registration-verifications/confirm").header("Authorization","Bearer stale-invalid").contentType("application/json").content(body(true)))
            .andExpect(status().isCreated()).andExpect(header().string("Authorization","Bearer access")).andExpect(header().string("Cache-Control","no-store"))
            .andExpect(header().doesNotExist("Set-Cookie")).andExpect(jsonPath("refreshToken").value("refresh")).andExpect(jsonPath("accessToken").doesNotExist());
    }
    @Test void confirm_shouldRejectMissingWrongTypeAndUnknownProofFields() throws Exception {
        for(String input:java.util.List.of("{}",body(false),"{\"registrationCredential\":42}",body(true).replace("\"000123\"","123"),body(true).replace("}",",\"owner\":7}")))
            mvc.perform(post("/api/auth/registration-verifications/confirm").contentType("application/json").content(input)).andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("REGISTRATION_VERIFICATION_FAILED"));
        verifyNoInteractions(registrations);
    }
    @Test void resend_shouldReturnOnlyReplacementPendingMetadataAndMapOutageSafely() throws Exception {
        when(registrations.resend(secret,id)).thenReturn(new PendingRegistrationResponse("b".repeat(43),UUID.randomUUID(),Instant.now().plusSeconds(600)));
        mvc.perform(post("/api/auth/registration-verifications/resend").contentType("application/json").content(body(false))).andExpect(status().isAccepted()).andExpect(header().doesNotExist("Authorization")).andExpect(jsonPath("refreshToken").doesNotExist()).andExpect(jsonPath("registrationCredential").value("b".repeat(43)));
        org.mockito.Mockito.doThrow(new IdentityException(503,"IDENTITY_UNAVAILABLE","Service unavailable")).when(registrations).resend(secret,id);
        mvc.perform(post("/api/auth/registration-verifications/resend").contentType("application/json").content(body(false))).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("code").value("IDENTITY_UNAVAILABLE"));
    }
    private String body(boolean confirmation){return "{\"registrationCredential\":\""+secret+"\",\"challengeId\":\""+id+"\""+(confirmation?",\"code\":\"000123\",\"password\":\"Final 1234\"":"")+"}";}
}
