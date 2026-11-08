package com.example.demo.auth.integration;
import com.example.demo.user.integration.PostgresTestSupport;
import com.example.demo.auth.repository.RefreshCredentialRepository;
import com.example.demo.auth.repository.AuthenticationSessionRepository;
import com.example.demo.user.repository.AddressRepository;
import com.example.demo.user.repository.UserRepository;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import java.util.Map;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
@SpringBootTest @AutoConfigureMockMvc
@org.springframework.test.context.ActiveProfiles({"test","identity-test"})
public abstract class AuthPostgresTestSupport extends PostgresTestSupport {
    @Autowired protected MockMvc mvc;
    @Autowired protected JsonMapper json;
    @Autowired protected com.example.demo.auth.service.IdentityProofService identityProofs;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected UserRepository users;
    @Autowired protected AddressRepository addresses;
    @Autowired protected AuthenticationSessionRepository sessions;
    @Autowired protected RefreshCredentialRepository credentials;
    @AfterEach void cleanup() {
        jdbc.update("DELETE FROM oauth_handoffs");jdbc.update("UPDATE oauth_browser_slots SET current_attempt_id=NULL");jdbc.update("DELETE FROM oauth_login_attempts");jdbc.update("DELETE FROM oauth_browser_slots");
        jdbc.update("DELETE FROM recent_auth_proofs");jdbc.update("DELETE FROM google_identities");
        jdbc.update("DELETE FROM verification_mail_outbox");jdbc.update("DELETE FROM email_verification_challenges");jdbc.update("DELETE FROM pending_registrations");

        credentials.deleteAll();
        sessions.deleteAll();
        addresses.deleteAll();
        jdbc.update("DELETE FROM user_roles");
        users.deleteAll();
    }
    protected Pair register(String username) throws Exception {
        org.springframework.test.web.servlet.MvcResult initial=mvc.perform(post("/api/auth/register").contentType("application/json").content(json.writeValueAsString(Map.of("email",username+"@example.com","password","Password 123")))).andExpect(status().isAccepted()).andReturn();
        return pair(confirmRegistration(initial,"Password 123"));
    }
    protected org.springframework.test.web.servlet.MvcResult confirmRegistration(org.springframework.test.web.servlet.MvcResult initial,String password) throws Exception {
        JsonNode pending=json.readTree(initial.getResponse().getContentAsString());String challenge=pending.get("challengeId").asString();
        byte[] payload=jdbc.queryForObject("SELECT encrypted_payload FROM verification_mail_outbox WHERE challenge_id=?",byte[].class,java.util.UUID.fromString(challenge));
        String code=(String)json.readValue(identityProofs.decrypt(payload,challenge),Map.class).get("code");
        return mvc.perform(post("/api/auth/registration-verifications/confirm").contentType("application/json").content(json.writeValueAsString(Map.of("registrationCredential",pending.get("registrationCredential").asString(),"challengeId",challenge,"code",code,"password",password)))).andExpect(status().isCreated()).andReturn();
    }
    protected Pair pair(org.springframework.test.web.servlet.MvcResult result) throws Exception {
        JsonNode body=json.readTree(result.getResponse().getContentAsString());
        return new Pair(result.getResponse().getHeader("Authorization"),body.get("refreshToken").asString());
    }
    protected int access(Pair pair) throws Exception {
        return mvc.perform(get("/api/me").header("Authorization",pair.access)).andReturn().getResponse().getStatus();
    }
    protected org.springframework.test.web.servlet.ResultActions refresh(String token) throws Exception {
        return mvc.perform(post("/api/auth/refresh").contentType("application/json").content(json.writeValueAsString(Map.of("refreshToken",token))));
    }
    protected static final class Pair {
        public final String access;
        public final String refresh;
        Pair(String access,String refresh) {
            this.access=access;
            this.refresh=refresh;
        }
    }
}
