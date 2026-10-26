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
public class ApiAccessDeniedHandler implements org.springframework.security.web.access.AccessDeniedHandler {
    private final ApiErrorFactory errors;
    private final JsonMapper mapper;
    public void handle(HttpServletRequest request,HttpServletResponse response,org.springframework.security.access.AccessDeniedException failure) throws IOException {
        response.setStatus(403);
        response.setContentType("application/problem+json");
        mapper.writeValue(response.getOutputStream(),errors.create(403,"Access denied",request.getRequestURI(),Map.of()));
    }
}
