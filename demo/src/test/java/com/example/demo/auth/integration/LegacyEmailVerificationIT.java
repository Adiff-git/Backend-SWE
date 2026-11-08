package com.example.demo.auth.integration;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

class LegacyEmailVerificationIT extends AuthPostgresTestSupport {
    @Autowired com.example.demo.user.service.UserService userService;
    @Autowired PasswordEncoder passwords;
    @Test void legacyAccount_shouldRetainEmailLessAccessAndVerifyItsExistingUnverifiedEmail(){
        Long owner=userService.createAuthenticationUser("Legacy",passwords.encode("Password 123"));
        try{
            Pair legacy=pair(mvc.perform(post("/api/auth/login").contentType("application/json").content("{\"identifier\":\"Legacy\",\"password\":\"Password 123\"}")).andExpect(status().isOk()).andReturn());
            mvc.perform(get("/api/me").header("Authorization",legacy.access)).andExpect(status().isOk()).andExpect(jsonPath("emailVerified").value(false));
            jdbc.update("UPDATE users SET email='legacy@example.com' WHERE id=?",owner);
            String challenge=json.readTree(mvc.perform(post("/api/me/email-verification-challenges").header("Authorization",legacy.access).contentType("application/json").content("{\"purpose\":\"REGISTRATION\",\"destination\":\" Legacy@Example.com \"}")).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asString();
            byte[] payload=jdbc.queryForObject("SELECT encrypted_payload FROM verification_mail_outbox WHERE challenge_id=?",byte[].class,java.util.UUID.fromString(challenge));String code=(String)json.readValue(identityProofs.decrypt(payload,challenge),Map.class).get("code");
            mvc.perform(post("/api/me/email-verification-challenges/"+challenge+"/confirm").header("Authorization",legacy.access).contentType("application/json").content(json.writeValueAsString(Map.of("code",code)))).andExpect(status().isOk()).andExpect(jsonPath("emailVerified").value(true));
            assertThat(users.findById(owner).orElseThrow().getUsername()).isEqualTo("Legacy");assertThat(users.count()).isOne();assertThat(sessions.count()).isOne();
        }catch(Exception failure){throw new AssertionError(failure);}
    }
}
