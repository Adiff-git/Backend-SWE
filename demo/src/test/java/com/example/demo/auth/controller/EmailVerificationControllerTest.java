package com.example.demo.auth.controller;

import com.example.demo.auth.dto.response.EmailVerificationChallengeResponse;
import com.example.demo.auth.exception.IdentityException;
import com.example.demo.auth.service.EmailVerificationService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@Import(EmailVerificationController.class)
@TestPropertySource(properties={"security.verification.enabled=true","security.verification.hmac-key=AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=","security.verification.encryption-key=AgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgI=","security.verification.mail-from=sender@example.test"})
class EmailVerificationControllerTest extends AuthControllerTestSupport {
    @MockitoBean EmailVerificationService verification;
    @Test void get_shouldEnforceAuthenticationAuthorityAndOwnedChallengePrivacy() throws Exception {
        UUID id=UUID.randomUUID();mvc.perform(get("/api/me/email-verification-challenges/"+id)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me/email-verification-challenges/"+id).with(jwt())).andExpect(status().isForbidden());
        when(verification.get(7L,id)).thenThrow(new IdentityException(404,"CHALLENGE_NOT_FOUND","Challenge not found"));
        mvc.perform(get("/api/me/email-verification-challenges/"+id).with(actor())).andExpect(status().isNotFound()).andExpect(header().string("Cache-Control","no-store"));
        org.mockito.Mockito.doReturn(new EmailVerificationChallengeResponse(id,"REGISTRATION",Instant.now().plusSeconds(600),"QUEUED")).when(verification).get(7L,id);
        mvc.perform(get("/api/me/email-verification-challenges/"+id).with(actor())).andExpect(status().isOk()).andExpect(jsonPath("code").doesNotExist()).andExpect(jsonPath("deliveryStatus").value("QUEUED"));
    }
    @Test void create_shouldRejectNullWrongTypeAndProtectedFieldsBeforeService() throws Exception {
        for(String body:java.util.List.of("{}","{\"purpose\":\"REGISTRATION\",\"destination\":null}","{\"purpose\":42,\"destination\":\"a@example.com\"}","{\"purpose\":\"EMAIL_CHANGE\",\"destination\":\"a@example.com\",\"owner\":7}"))
            mvc.perform(post("/api/me/email-verification-challenges").with(actor()).contentType("application/json").content(body)).andExpect(status().isBadRequest());
        verifyNoInteractions(verification);
    }
    private org.springframework.test.web.servlet.request.RequestPostProcessor actor(){return jwt().jwt(token->token.subject("7").claim("sid",UUID.randomUUID().toString())).authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("MANAGE_USER_PROFILE"));}
}
