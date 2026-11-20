package com.example.demo.order.controller;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Apply the owned-order privacy contract before authentication, parsing or business failures. */
@Component @Order(Ordered.HIGHEST_PRECEDENCE)
public class OrderCacheFilter extends OncePerRequestFilter {
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (path.equals("/api/me/orders") || path.startsWith("/api/me/orders/")) {
            response.setHeader("Cache-Control", "private, no-store");
        }
        chain.doFilter(request, response);
    }
}
