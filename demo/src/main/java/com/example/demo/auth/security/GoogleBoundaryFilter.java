package com.example.demo.auth.security;

import com.example.demo.auth.config.IdentityProperties;
import com.example.demo.auth.exception.IdentityException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.web.filter.OncePerRequestFilter;

@RequiredArgsConstructor
public class GoogleBoundaryFilter extends OncePerRequestFilter {
    private final IdentityProperties properties;
    private final IdentityProblemWriter problems;
    protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        response.setHeader("Cache-Control","no-store");response.setHeader("Referrer-Policy","no-referrer");
        try{chain.doFilter(request,response);}
        catch(IdentityException error){
            if(response.isCommitted())return;
            if("GET".equals(request.getMethod()))response.sendRedirect(properties.getFrontendFailureUri()+"?error=invalid_request");
            else problems.write(response,error);
        }
    }
}
