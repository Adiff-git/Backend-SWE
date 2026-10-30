package com.example.demo.auth.integration;
import org.junit.jupiter.api.Test;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class AuthCorsApiIT extends AuthPostgresTestSupport {
    @Test void execute_shouldAllowAllOriginsMethodsAndHeadersWithoutCredentials() throws Exception {
        mvc.perform(options("/api/auth/login").header("Origin","http://localhost:3000").header("Access-Control-Request-Method","POST").header("Access-Control-Request-Headers","Authorization,Content-Type")).andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin","*")).andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
        mvc.perform(options("/api/me").header("Origin","http://localhost:3000").header("Access-Control-Request-Method","PUT").header("Access-Control-Request-Headers","Authorization")).andExpect(status().isOk());
        mvc.perform(options("/api/me").header("Origin","https://another.example").header("Access-Control-Request-Method","PATCH").header("Access-Control-Request-Headers","X-Custom-Header")).andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin","*"));
    }
    @Test void execute_shouldExposeAccessHeaderForBrowserClient() throws Exception {
        mvc.perform(post("/api/auth/register").header("Origin","http://localhost:3000").contentType("application/json").content("{\"username\":\"Alice\",\"password\":\"Password 123\"}")).andExpect(status().isCreated()).andExpect(header().string("Access-Control-Expose-Headers","Authorization")).andExpect(header().string("Cache-Control","no-store")).andExpect(cookie().doesNotExist("JSESSIONID"));
    }
}
