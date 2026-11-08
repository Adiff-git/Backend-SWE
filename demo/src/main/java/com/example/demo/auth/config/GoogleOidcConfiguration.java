package com.example.demo.auth.config;

import com.example.demo.auth.entity.OAuthLoginAttempt;
import com.example.demo.auth.security.GoogleBoundaryFilter;
import com.example.demo.auth.security.GoogleCookieSupport;
import com.example.demo.auth.security.GoogleOidcFailureHandler;
import com.example.demo.auth.security.GoogleOidcSuccessHandler;
import com.example.demo.auth.security.PostgresAuthorizationRequestRepository;
import com.example.demo.auth.security.CurrentStateJwtConverter;
import com.example.demo.auth.security.ApiAuthenticationEntryPoint;
import com.example.demo.auth.security.ApiAccessDeniedHandler;
import com.example.demo.auth.security.IdentityProblemWriter;
import com.example.demo.auth.service.GoogleAttemptService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(name="security.identity.enabled",havingValue="true")
public class GoogleOidcConfiguration {
    @Bean
    public ClientRegistrationRepository googleClients(IdentityProperties properties,Environment environment) {
        String testIssuer=environment.getProperty("security.identity.test-issuer");
        ClientRegistration.Builder builder;
        if(testIssuer!=null) {
            java.net.URI issuer=java.net.URI.create(testIssuer);
            if(!environment.matchesProfiles("test") || !"http".equals(issuer.getScheme()) || !List.of("127.0.0.1","localhost").contains(issuer.getHost()))
                throw new IllegalArgumentException("Controlled provider override is test-only");
            builder=ClientRegistration.withRegistrationId("google").authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC).issuerUri(testIssuer)
                .authorizationUri(testIssuer+"/authorize").tokenUri(testIssuer+"/token").jwkSetUri(testIssuer+"/jwks")
                .userInfoUri(testIssuer+"/userinfo").userNameAttributeName("sub");
        }else builder=CommonOAuth2Provider.GOOGLE.getBuilder("google");
        return new InMemoryClientRegistrationRepository(builder.clientId(properties.getClientId()).clientSecret(properties.getClientSecret())
            .redirectUri(properties.getCallbackUri().toString()).scope("openid","email","profile").clientName("Google").build());
    }
    @Bean @Order(0)
    public SecurityFilterChain googlePublicChain(HttpSecurity http,IdentityProperties properties,ClientRegistrationRepository clients,
            GoogleAttemptService attempts,
            IdentityProblemWriter problems,ApiAuthenticationEntryPoint entry,ApiAccessDeniedHandler denied) throws Exception {
        DefaultOAuth2AuthorizationRequestResolver delegate=new DefaultOAuth2AuthorizationRequestResolver(clients,"/oauth2/authorization");
        delegate.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
        OAuth2AuthorizationRequestResolver resolver=new OAuth2AuthorizationRequestResolver(){
            public OAuth2AuthorizationRequest resolve(HttpServletRequest request){return customize(request,delegate.resolve(request));}
            public OAuth2AuthorizationRequest resolve(HttpServletRequest request,String id){return customize(request,delegate.resolve(request,id));}
            private OAuth2AuthorizationRequest customize(HttpServletRequest request,OAuth2AuthorizationRequest value){
                if(value==null)return null;
                OAuthLoginAttempt.Purpose purpose=attempts.authorizationPurpose(request.getParameter("attempt"),GoogleCookieSupport.read(request));
                if(purpose!=OAuthLoginAttempt.Purpose.REAUTH)return value;
                Map<String,Object> additional=new java.util.LinkedHashMap<>(value.getAdditionalParameters());additional.put("max_age",0);
                return OAuth2AuthorizationRequest.from(value).additionalParameters(additional).authorizationRequestUri((String)null).build();
            }
        };
        http.securityMatcher("/oauth2/authorization/google","/login/oauth2/code/google","/api/auth/google-login-attempts","/api/auth/google-code-exchanges")
            .csrf(c->c.disable()).cors(c->c.configurationSource(cors(properties)))
            .sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS)).securityContext(c->c.securityContextRepository(new NullSecurityContextRepository()))
            .requestCache(c->c.disable()).formLogin(c->c.disable()).httpBasic(c->c.disable()).logout(c->c.disable())
            .exceptionHandling(e->e.authenticationEntryPoint(entry).accessDeniedHandler(denied))
            .authorizeHttpRequests(a->a.requestMatchers(org.springframework.http.HttpMethod.GET,"/oauth2/authorization/google","/login/oauth2/code/google").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.POST,"/api/auth/google-login-attempts","/api/auth/google-code-exchanges").permitAll().anyRequest().denyAll())
            .oauth2Login(o->o.authorizationEndpoint(a->a.authorizationRequestResolver(resolver).authorizationRequestRepository(new PostgresAuthorizationRequestRepository(attempts)))
                .authorizedClientRepository(new DiscardingClients()).successHandler(new GoogleOidcSuccessHandler(attempts,properties)).failureHandler(new GoogleOidcFailureHandler(attempts,properties)))
            .addFilterBefore(new GoogleBoundaryFilter(properties,problems),OAuth2AuthorizationRequestRedirectFilter.class);
        return http.build();
    }
    private UrlBasedCorsConfigurationSource cors(IdentityProperties properties) {
        CorsConfiguration configuration=new CorsConfiguration();configuration.setAllowedOrigins(properties.getAllowedOrigins());configuration.setAllowCredentials(true);
        configuration.setAllowedMethods(List.of("GET","POST","OPTIONS"));configuration.setAllowedHeaders(List.of("Authorization","Content-Type"));configuration.setExposedHeaders(List.of("Authorization"));
        UrlBasedCorsConfigurationSource source=new UrlBasedCorsConfigurationSource();source.registerCorsConfiguration("/**",configuration);return source;
    }
    private static class DiscardingClients implements OAuth2AuthorizedClientRepository {
        public <T extends OAuth2AuthorizedClient>T loadAuthorizedClient(String registration,Authentication authentication,HttpServletRequest request){return null;}
        public void saveAuthorizedClient(OAuth2AuthorizedClient client,Authentication authentication,HttpServletRequest request,jakarta.servlet.http.HttpServletResponse response) { }
        public void removeAuthorizedClient(String registration,Authentication authentication,HttpServletRequest request,jakarta.servlet.http.HttpServletResponse response) { }
    }
}
