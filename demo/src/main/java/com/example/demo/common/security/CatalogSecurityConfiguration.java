package com.example.demo.common.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
@org.springframework.context.annotation.Import(SharedCorsConfiguration.class)
public class CatalogSecurityConfiguration {

	@Bean
	@Order(2)
	SecurityFilterChain catalogSecurityFilterChain(HttpSecurity http,
			CatalogSecurityProblemHandler problemHandler,
            com.example.demo.auth.security.CurrentStateJwtConverter converter) throws Exception {
		http
			.securityMatcher("/api/products", "/api/products/**")
			.cors(org.springframework.security.config.Customizer.withDefaults())
			// The planned catalog API uses Bearer credentials, not cookie authentication.
			.csrf(csrf -> csrf.ignoringRequestMatchers("/api/products/**"))
			.authorizeHttpRequests(authorize -> authorize
				.requestMatchers(HttpMethod.GET, "/api/products", "/api/products/{id}").permitAll()
				// Roles come from current database state through the shared JWT converter.
				.requestMatchers(HttpMethod.POST, "/api/products/{id}/images/uploads",
						"/api/products/{id}/images/uploads/{uploadId}/complete").access((authentication, context) -> {
                            var actor = authentication.get();
                            return new org.springframework.security.authorization.AuthorizationDecision(
                                actor instanceof org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
                                && actor.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
                        })
				.anyRequest().denyAll())
			.sessionManagement(s -> s.sessionCreationPolicy(org.springframework.security.config.http.SessionCreationPolicy.STATELESS))
            .oauth2ResourceServer(o -> o.authenticationEntryPoint(problemHandler).accessDeniedHandler(problemHandler)
                .jwt(j -> j.jwtAuthenticationConverter(converter)))
            .exceptionHandling(exceptions -> exceptions
				.authenticationEntryPoint(problemHandler)
				.accessDeniedHandler(problemHandler));
		return http.build();
	}
}
