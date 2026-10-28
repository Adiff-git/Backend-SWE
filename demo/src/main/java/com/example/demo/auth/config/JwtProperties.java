package com.example.demo.auth.config;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
@Component
public class JwtProperties {
    private final byte[] secret;
    private final String issuer;
    private final String audience;
    public JwtProperties(@Value("${security.jwt.secret}") String secret,@Value("${security.jwt.issuer}") String issuer,@Value("${security.jwt.audience:}") String audience) {
        if(secret==null || secret.getBytes(StandardCharsets.UTF_8).length<32)throw new IllegalArgumentException("JWT signing configuration is invalid");
        if(issuer==null || issuer.isBlank())throw new IllegalArgumentException("JWT issuer is required");
        this.secret=secret.getBytes(StandardCharsets.UTF_8);
        this.issuer=issuer;
        this.audience=audience;
    }
    public byte[] secretBytes() {
        return secret.clone();
    }
    public String issuer() {
        return issuer;
    }
    public String audience() {
        return audience;
    }
}
