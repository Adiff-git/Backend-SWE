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
public abstract class AuthPostgresTestSupport extends PostgresTestSupport {
    @Autowired protected MockMvc mvc;
    @Autowired protected JsonMapper json;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected UserRepository users;
    @Autowired protected AddressRepository addresses;
    @Autowired protected AuthenticationSessionRepository sessions;
    @Autowired protected RefreshCredentialRepository credentials;
    @AfterEach void cleanup() {
        credentials.deleteAll();
        sessions.deleteAll();
        addresses.deleteAll();
        jdbc.update("DELETE FROM user_roles");
        users.deleteAll();
    }
    protected Pair register(String username) throws Exception {
        org.springframework.test.web.servlet.MvcResult result=mvc.perform(post("/api/auth/register").contentType("application/json").content(json.writeValueAsString(Map.of("username",username,"password","Password 123")))).andExpect(status().isCreated()).andReturn();
        return pair(result);
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
