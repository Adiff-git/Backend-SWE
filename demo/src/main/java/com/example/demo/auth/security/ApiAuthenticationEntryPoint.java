package com.example.demo.auth.security;
import com.example.demo.common.exception.ApiErrorFactory;
import tools.jackson.databind.json.JsonMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
@Component @RequiredArgsConstructor
public class ApiAuthenticationEntryPoint implements org.springframework.security.web.AuthenticationEntryPoint {
    private final ApiErrorFactory errors;
    private final JsonMapper mapper;
    public void commence(HttpServletRequest request,HttpServletResponse response,org.springframework.security.core.AuthenticationException failure) throws IOException {
        response.setStatus(401);
        response.setContentType("application/problem+json");
        response.setHeader("WWW-Authenticate","Bearer");
        mapper.writeValue(response.getOutputStream(),errors.create(401,"Authentication failed",request.getRequestURI(),Map.of()));
    }
}
