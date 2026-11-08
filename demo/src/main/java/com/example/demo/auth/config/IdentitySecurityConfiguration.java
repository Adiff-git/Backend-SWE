package com.example.demo.auth.config;

import com.example.demo.auth.security.ApiAccessDeniedHandler;
import com.example.demo.auth.security.ApiAuthenticationEntryPoint;
import com.example.demo.auth.security.CurrentStateJwtConverter;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration(proxyBeanMethods=false)
public class IdentitySecurityConfiguration {
    @Bean @Order(-3)
    public SecurityFilterChain registrationChain(HttpSecurity http,IdentityProperties properties) throws Exception {
        common(http,properties);
        http.securityMatcher("/api/auth/register","/api/auth/registration-verifications/confirm","/api/auth/registration-verifications/resend")
            .authorizeHttpRequests(a->a.requestMatchers(HttpMethod.POST,"/api/auth/register","/api/auth/registration-verifications/confirm","/api/auth/registration-verifications/resend").permitAll().anyRequest().denyAll());
        return http.build();
    }
    @Bean @Order(-2)
    public SecurityFilterChain identityProfileChain(HttpSecurity http,IdentityProperties properties,CurrentStateJwtConverter converter,
            ApiAuthenticationEntryPoint entry,ApiAccessDeniedHandler denied) throws Exception {
        common(http,properties);
        http.securityMatcher("/api/me/email-verification-challenges","/api/me/email-verification-challenges/**","/api/me/reauthentications","/api/me/password","/api/me/google-connection","/api/me/google-connection-attempts","/api/me/google-reauthentication-attempts")
            .exceptionHandling(e->e.authenticationEntryPoint(entry).accessDeniedHandler(denied))
            .authorizeHttpRequests(a->a.requestMatchers(HttpMethod.GET,"/api/me/email-verification-challenges/*","/api/me/google-connection").hasAuthority("MANAGE_USER_PROFILE")
                .requestMatchers(HttpMethod.POST,"/api/me/email-verification-challenges","/api/me/email-verification-challenges/*/confirm","/api/me/reauthentications","/api/me/password","/api/me/google-connection-attempts","/api/me/google-reauthentication-attempts").hasAuthority("MANAGE_USER_PROFILE").anyRequest().denyAll())
            .oauth2ResourceServer(o->o.authenticationEntryPoint(entry).accessDeniedHandler(denied).jwt(j->j.jwtAuthenticationConverter(converter)));
        return http.build();
    }
    private void common(HttpSecurity http,IdentityProperties properties) throws Exception {
        http.csrf(c->c.disable()).cors(c->c.configurationSource(cors(properties)))
            .sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .requestCache(c->c.disable()).formLogin(c->c.disable()).httpBasic(c->c.disable()).logout(c->c.disable())
            .headers(h->h.cacheControl(c->{}));
    }
    private UrlBasedCorsConfigurationSource cors(IdentityProperties properties){
        CorsConfiguration config=new CorsConfiguration();config.setAllowedOrigins(properties.getAllowedOrigins());config.setAllowCredentials(true);
        config.setAllowedMethods(List.of("GET","POST","OPTIONS"));config.setAllowedHeaders(List.of("Authorization","Content-Type"));config.setExposedHeaders(List.of("Authorization"));
        UrlBasedCorsConfigurationSource source=new UrlBasedCorsConfigurationSource();source.registerCorsConfiguration("/**",config);return source;
    }
}
