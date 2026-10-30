package com.example.demo.auth.integration;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class BearerAuthenticationApiIT extends AuthPostgresTestSupport {
    @Test void execute_shouldDenyAnonymousAndNonHeaderCredentials() throws Exception {
        Pair p=register("Alice");
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized()).andExpect(header().string("WWW-Authenticate","Bearer"));
        mvc.perform(get("/api/me").param("access_token",p.access.substring(7))).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me").cookie(new jakarta.servlet.http.Cookie("access_token",p.access.substring(7)))).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/login")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login/").header("Authorization","Bearer stale")).andExpect(status().isUnauthorized());
    }
    @Test void execute_shouldReadCurrentPermissionsStatusAndStableIdOnEveryRequest() throws Exception {
        Pair p=register("Alice");
        Long id=users.findAll().getFirst().getId();
        jdbc.update("DELETE FROM user_roles WHERE user_id=?",id);
        assertThat(access(p)).isEqualTo(403);
        mvc.perform(get("/api/users/"+id).header("Authorization",p.access)).andExpect(status().isOk()).andExpect(jsonPath("email").doesNotExist()).andExpect(jsonPath("phoneNumber").doesNotExist());
        jdbc.update("INSERT INTO user_roles(user_id,role_id) SELECT ?,id FROM roles WHERE name='USER'",id);
        jdbc.update("UPDATE users SET username='NewName' WHERE id=?",id);
        assertThat(access(p)).isEqualTo(200);
        jdbc.update("UPDATE users SET account_status='SUSPENDED' WHERE id=?",id);
        assertThat(access(p)).isEqualTo(401);
    }
    @Test void execute_shouldScopeAllAddressOperationsToCurrentOwner() throws Exception {
        Pair owner=register("Owner");
        Pair other=register("Other");
        var response=mvc.perform(post("/api/me/addresses").header("Authorization",owner.access).contentType("application/json").content("{\"recipientName\":\"Alice\",\"recipientPhone\":\"0123456789\",\"addressText\":\"Home\"}")).andExpect(status().isCreated()).andReturn();
        long address=json.readTree(response.getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(get("/api/me/addresses/"+address).header("Authorization",other.access)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/me/addresses/"+address).header("Authorization",other.access)).andExpect(status().isNotFound());
        assertThat(addresses.count()).isEqualTo(1);
    }
}
