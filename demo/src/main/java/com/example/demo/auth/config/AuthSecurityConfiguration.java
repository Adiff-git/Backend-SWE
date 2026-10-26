package com.example.demo.auth.config;
import com.example.demo.auth.security.CurrentStateJwtConverter;
import com.example.demo.auth.security.DomainUserDetailsService;
import com.example.demo.auth.security.ApiAuthenticationEntryPoint;
import com.example.demo.auth.security.ApiAccessDeniedHandler;
import com.example.demo.auth.validator.JwtClaimsValidator;
import java.time.Clock;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
@Configuration @org.springframework.scheduling.annotation.EnableScheduling
public class AuthSecurityConfiguration {
    @Bean public Clock authClock() {
        return Clock.systemUTC();
    }
    @Bean public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }
    @Bean public JwtEncoder jwtEncoder(JwtProperties properties) {
        return new NimbusJwtEncoder(new com.nimbusds.jose.jwk.source.ImmutableSecret<com.nimbusds.jose.proc.SecurityContext>(properties.secretBytes()));
    }
    @Bean public JwtDecoder jwtDecoder(JwtProperties properties,Clock clock) {
        NimbusJwtDecoder decoder=NimbusJwtDecoder.withSecretKey(new SecretKeySpec(properties.secretBytes(),"HmacSHA256")).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(new JwtClaimsValidator(properties,clock));
        return decoder;
    }
    @Bean public AuthenticationManager authenticationManager(DomainUserDetailsService details,PasswordEncoder encoder) {
        DaoAuthenticationProvider provider=new DaoAuthenticationProvider(details);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }
    @Bean public CorsConfigurationSource corsConfigurationSource(@Value("${security.cors.allowed-origins}") List<String> origins) {
        if(origins.isEmpty() || origins.stream().anyMatch(x->x.isBlank() || x.contains("*")))throw new IllegalArgumentException("Explicit CORS origins are required");
        CorsConfiguration c=new CorsConfiguration();
        c.setAllowedOrigins(origins);
        c.setAllowedMethods(List.of("GET","POST","PUT","DELETE","OPTIONS"));
        c.setAllowedHeaders(List.of("Authorization","Content-Type"));
        c.setExposedHeaders(List.of("Authorization"));
        c.setAllowCredentials(false);
        UrlBasedCorsConfigurationSource source=new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**",c);
        return source;
    }
    @Bean @Order(1) public SecurityFilterChain credentialsChain(HttpSecurity http,ApiAuthenticationEntryPoint entry,ApiAccessDeniedHandler denied) throws Exception {
        RequestMatcher paths=request->"POST".equals(request.getMethod()) && List.of("/api/auth/register","/api/auth/login","/api/auth/refresh","/api/auth/logout").contains(request.getRequestURI().substring(request.getContextPath().length()));
        http.securityMatcher(paths).csrf(c->c.disable()).cors(org.springframework.security.config.Customizer.withDefaults())
            .sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .requestCache(c->c.disable())
            .formLogin(c->c.disable())
            .httpBasic(c->c.disable())
            .logout(c->c.disable())
            .exceptionHandling(e->e.authenticationEntryPoint(entry).accessDeniedHandler(denied))
            .authorizeHttpRequests(a->a.anyRequest().permitAll());
        return http.build();
    }
    @Bean @Order(2) public SecurityFilterChain bearerChain(HttpSecurity http,CurrentStateJwtConverter converter,ApiAuthenticationEntryPoint entry,ApiAccessDeniedHandler denied) throws Exception {
        DefaultBearerTokenResolver resolver=new DefaultBearerTokenResolver();
        resolver.setAllowFormEncodedBodyParameter(false);
        resolver.setAllowUriQueryParameter(false);
        http.csrf(c->c.disable()).cors(org.springframework.security.config.Customizer.withDefaults())
            .sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .requestCache(c->c.disable())
            .formLogin(c->c.disable())
            .httpBasic(c->c.disable())
            .logout(c->c.disable())
            .exceptionHandling(e->e.authenticationEntryPoint(entry).accessDeniedHandler(denied))
        .authorizeHttpRequests(a->a
                .requestMatchers("/api/me").hasAuthority("MANAGE_USER_PROFILE")
                .requestMatchers("/api/me/addresses","/api/me/addresses/**").hasAuthority("MANAGE_ADDRESS")
                .requestMatchers(org.springframework.http.HttpMethod.GET,"/api/users/*").authenticated()
                .requestMatchers(org.springframework.http.HttpMethod.PUT,"/api/auth/password").authenticated()
                .anyRequest().denyAll())
        .oauth2ResourceServer(o->o.bearerTokenResolver(resolver).authenticationEntryPoint(entry).accessDeniedHandler(denied).jwt(jwt->jwt.jwtAuthenticationConverter(converter)));
        return http.build();
    }
}
