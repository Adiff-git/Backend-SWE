package com.example.demo.user.integration;
import com.example.demo.auth.integration.AuthPostgresTestSupport;
import org.junit.jupiter.api.Test;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class UserApiIT extends AuthPostgresTestSupport {
    @Test void updateUser_shouldEnforcePermissionAndRejectMissingImage() throws Exception {
        var p=register("Alice");
        Long id=users.findAll().getFirst().getId();
        mvc.perform(put("/api/me").header("Authorization",p.access).contentType("application/json").content("{\"username\":\"Renamed\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/me").header("Authorization",p.access)).andExpect(status().isOk()).andExpect(jsonPath("username").value("Renamed"));
        mvc.perform(put("/api/me").header("Authorization",p.access).contentType("application/json").content("{\"profileImageId\":123}")).andExpect(status().isNotFound());
        jdbc.update("DELETE FROM user_roles WHERE user_id=?",id);
        mvc.perform(put("/api/me").header("Authorization",p.access).contentType("application/json").content("{\"displayName\":\"Forbidden\"}")).andExpect(status().isForbidden());
        org.assertj.core.api.Assertions.assertThat(users.findById(id).orElseThrow().getDisplayName()).isNull();
    }
}
