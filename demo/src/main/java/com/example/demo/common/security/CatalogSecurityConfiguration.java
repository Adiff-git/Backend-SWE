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
			.securityMatcher("/api/products", "/api/products/**", "/api/admin/catalog", "/api/admin/catalog/**", "/api/admin/inventory", "/api/admin/inventory/**")
			.cors(org.springframework.security.config.Customizer.withDefaults())
			// The planned catalog API uses Bearer credentials, not cookie authentication.
			.csrf(csrf -> csrf.ignoringRequestMatchers("/api/products/**", "/api/admin/catalog", "/api/admin/catalog/**", "/api/admin/inventory", "/api/admin/inventory/**"))
			.authorizeHttpRequests(authorize -> authorize
                .requestMatchers(HttpMethod.GET, "/api/admin/inventory/variants",
                        "/api/admin/inventory/variants/{id}", "/api/admin/inventory/variants/{id}/history")
                    .access((authentication, context) -> administrator(authentication.get()))
                .requestMatchers(HttpMethod.POST, "/api/admin/inventory/variants/{id}/replenishments",
                        "/api/admin/inventory/variants/{id}/corrections")
                    .access((authentication, context) -> administrator(authentication.get()))
                .requestMatchers("/api/admin/catalog", "/api/admin/catalog/**")
                    .access((authentication, context) -> administrator(authentication.get()))
				.requestMatchers(HttpMethod.GET, "/api/products", "/api/products/{id}").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/products/{id}/reviews", "/api/products/{id}/rating-summary").permitAll()
				.requestMatchers(HttpMethod.GET, "/api/products/{id}/similar").permitAll()
				.requestMatchers(HttpMethod.POST, "/api/products/{id}/images")
                    .access((authentication, context) -> administrator(authentication.get()))
				.anyRequest().denyAll())
			.sessionManagement(s -> s.sessionCreationPolicy(org.springframework.security.config.http.SessionCreationPolicy.STATELESS))
            .oauth2ResourceServer(o -> o.authenticationEntryPoint(problemHandler).accessDeniedHandler(problemHandler)
                .jwt(j -> j.jwtAuthenticationConverter(converter)))
            .exceptionHandling(exceptions -> exceptions
				.authenticationEntryPoint(problemHandler)
				.accessDeniedHandler(problemHandler));
		return http.build();
	}

    private static org.springframework.security.authorization.AuthorizationDecision administrator(
            org.springframework.security.core.Authentication actor) {
        return new org.springframework.security.authorization.AuthorizationDecision(
                actor instanceof org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
                && actor.getAuthorities().stream().anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN")));
    }
}
