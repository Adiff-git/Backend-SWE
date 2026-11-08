package com.example.demo.auth.config;

import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties("security.identity")
public class IdentityProperties {
    private boolean enabled;
    private boolean local;
    private String clientId;
    private String clientSecret;
    private URI callbackUri;
    private URI frontendSuccessUri;
    private URI frontendFailureUri;
    private List<String> allowedOrigins = List.of();

    @PostConstruct
    public void validate() {
        if (!enabled) return;
        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
            throw new IllegalArgumentException("Google client configuration is required");
        }
        validateDestination(callbackUri);
        if (!callbackUri.getPath().endsWith("/login/oauth2/code/google")) throw new IllegalArgumentException("Invalid Google callback route");
        validateDestination(frontendSuccessUri);
        validateDestination(frontendFailureUri);
        if (allowedOrigins == null || allowedOrigins.isEmpty()) {
            throw new IllegalArgumentException("Explicit frontend origins are required");
        }
        for (String origin : allowedOrigins) {
            URI uri = URI.create(origin);
            validateDestination(uri);
            if (uri.getRawQuery() != null || (uri.getRawPath() != null && !uri.getRawPath().isEmpty())) {
                throw new IllegalArgumentException("Frontend origin must not contain a path or query");
            }
        }
        if (!allowedOrigins.contains(origin(frontendSuccessUri))
                || !allowedOrigins.contains(origin(frontendFailureUri))) {
            throw new IllegalArgumentException("Frontend destinations must use allowed origins");
        }
    }

    private void validateDestination(URI uri) {
        if (uri == null || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getFragment() != null || uri.getRawQuery() != null
                || !("https".equals(uri.getScheme()) || (local && "http".equals(uri.getScheme())
                    && ("localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost())
                        || "[::1]".equals(uri.getHost()))))) {
            throw new IllegalArgumentException("Invalid identity destination configuration");
        }
    }

    private String origin(URI uri) {
        return uri.getScheme() + "://" + uri.getRawAuthority();
    }
}
