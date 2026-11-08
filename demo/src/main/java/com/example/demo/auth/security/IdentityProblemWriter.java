package com.example.demo.auth.security;

import com.example.demo.auth.exception.IdentityException;
import com.example.demo.auth.exception.IdentityExceptionHandler;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
public class IdentityProblemWriter {
    private final JsonMapper json;
    public void write(HttpServletResponse response,IdentityException error) throws IOException {
        response.setStatus(error.getStatus());response.setContentType("application/problem+json");
        response.setHeader("Cache-Control","no-store");
        if(error.getStatus()==401)response.setHeader("WWW-Authenticate","Bearer");
        json.writeValue(response.getOutputStream(),IdentityExceptionHandler.problem(error));
    }
}
