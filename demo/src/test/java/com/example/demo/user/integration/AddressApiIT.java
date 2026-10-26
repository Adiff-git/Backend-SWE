package com.example.demo.user.integration;
import com.example.demo.auth.integration.AuthPostgresTestSupport;
import org.junit.jupiter.api.Test;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class AddressApiIT extends AuthPostgresTestSupport {
    @Test void address_shouldRequireCurrentPermissionForAllFiveOperations() throws Exception {
        var p=register("Alice");
        var response=mvc.perform(post("/api/me/addresses").header("Authorization",p.access).contentType("application/json").content("{\"recipientName\":\"Alice\",\"recipientPhone\":\"0123456789\",\"addressText\":\"Home\"}")).andExpect(status().isCreated()).andReturn();
        long address=json.readTree(response.getResponse().getContentAsString()).get("id").asLong();
        Long owner=users.findAll().getFirst().getId();
        jdbc.update("DELETE FROM user_roles WHERE user_id=?",owner);
        mvc.perform(get("/api/me/addresses").header("Authorization",p.access)).andExpect(status().isForbidden());
        mvc.perform(get("/api/me/addresses/"+address).header("Authorization",p.access)).andExpect(status().isForbidden());
        mvc.perform(post("/api/me/addresses").header("Authorization",p.access).contentType("application/json").content("{}")).andExpect(status().isForbidden());
        mvc.perform(put("/api/me/addresses/"+address).header("Authorization",p.access).contentType("application/json").content("{}")).andExpect(status().isForbidden());
        mvc.perform(delete("/api/me/addresses/"+address).header("Authorization",p.access)).andExpect(status().isForbidden());
        org.assertj.core.api.Assertions.assertThat(addresses.count()).isEqualTo(1);
    }
}
