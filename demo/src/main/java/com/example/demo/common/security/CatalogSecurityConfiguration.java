package com.example.demo.common.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
public class CatalogSecurityConfiguration {

	@Bean
	@Order(2)
	SecurityFilterChain catalogSecurityFilterChain(HttpSecurity http,
			CatalogSecurityProblemHandler problemHandler) throws Exception {
		http
			.securityMatcher("/api/products", "/api/products/**")
			// The planned catalog API uses Bearer credentials, not cookie authentication.
			.csrf(csrf -> csrf.ignoringRequestMatchers("/api/products/**"))
			.authorizeHttpRequests(authorize -> authorize
				.requestMatchers(HttpMethod.GET, "/api/products", "/api/products/{id}").permitAll()
				// No verified ADMIN principal exists yet. T052 may replace this only after integration.
				.requestMatchers(HttpMethod.POST, "/api/products/{id}/images/uploads",
						"/api/products/{id}/images/uploads/{uploadId}/complete").denyAll()
				.anyRequest().denyAll())
			.exceptionHandling(exceptions -> exceptions
				.authenticationEntryPoint(problemHandler)
				.accessDeniedHandler(problemHandler));
		return http.build();
	}
}
