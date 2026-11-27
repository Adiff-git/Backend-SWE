package com.example.demo.auth.integration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.system.CapturedOutput;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@ExtendWith(OutputCaptureExtension.class)
class AuthenticationFlowIT extends AuthPostgresTestSupport {
    @org.springframework.beans.factory.annotation.Autowired com.example.demo.auth.config.JwtProperties jwtProperties;
    @Test void swaggerDocumentationIsPublicWhileCustomerEndpointsStayProtected() throws Exception {
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
        mvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/v3/api-docs/swagger-config")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.info.title").value("Backend SWE API"))
            .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
            .andExpect(jsonPath("$.paths['/api/me'].get.security[0].bearerAuth").isArray())
            .andExpect(jsonPath("$.paths['/api/products/{id}/images'].post.security[0].bearerAuth").isArray())
            .andExpect(jsonPath("$.paths['/api/auth/login'].post.responses['200'].headers.Authorization").exists())
            .andExpect(jsonPath("$.paths['/api/me/orders'].get.parameters[*].name")
                .value(org.hamcrest.Matchers.hasItems("page", "size", "status", "sort")))
            .andExpect(jsonPath("$.paths['/api/products/{productId}/reviews'].get.parameters[*].name")
                .value(org.hamcrest.Matchers.hasItems("productId", "page", "size", "rating", "hasImages", "hasComment")))
            .andExpect(jsonPath("$.paths['/api/me/wishlist/{productId}'].post.requestBody").doesNotExist())
            .andExpect(jsonPath("$.paths['/api/products'].get.security").doesNotExist());
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
        mvc.perform(post("/v3/api-docs")).andExpect(status().isUnauthorized());
    }
    @Test void execute_shouldVerifyFullFlowPreservesContractsWithoutLeakingCredentials(CapturedOutput output) throws Exception {
        Pair p=register("Alice");
        assertThat(access(p)).isEqualTo(200);
        Pair email=pair(mvc.perform(post("/api/auth/login").contentType("application/json").content("{\"identifier\":\"ALICE@example.com\",\"password\":\"Password 123\"}")).andExpect(status().isOk()).andReturn());
        Pair replacement=pair(refresh(p.refresh).andExpect(status().isOk()).andReturn());
        refresh(p.refresh).andExpect(status().isUnauthorized());
        assertThat(access(replacement)).isEqualTo(401);
        assertThat(access(email)).isEqualTo(200);
        mvc.perform(put("/api/auth/password").header("Authorization",email.access).contentType("application/json").content("{\"currentPassword\":\"Password 123\",\"newPassword\":\"Replacement 123\"}")).andExpect(status().isNoContent()).andExpect(content().string(""));
        assertThat(access(email)).isEqualTo(401);
        Pair fresh=pair(mvc.perform(post("/api/auth/login").contentType("application/json").content("{\"identifier\":\"Alice\",\"password\":\"Replacement 123\"}")).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("accessToken").doesNotExist()).andReturn());
        mvc.perform(post("/api/auth/logout").contentType("application/json").content(json.writeValueAsString(java.util.Map.of("refreshToken",fresh.refresh)))).andExpect(status().isNoContent());
        assertThat(access(fresh)).isEqualTo(401);
        assertThat(output.getAll()).doesNotContain(new String(jwtProperties.secretBytes(),java.nio.charset.StandardCharsets.UTF_8));
        for(var credential:credentials.findAll())assertThat(output.getAll()).doesNotContain(java.util.HexFormat.of().formatHex(credential.getTokenHash()));
        assertThat(output.getAll()).doesNotContain(p.refresh,p.access.substring(7),email.refresh,"Password 123","Replacement 123");
    }
}
