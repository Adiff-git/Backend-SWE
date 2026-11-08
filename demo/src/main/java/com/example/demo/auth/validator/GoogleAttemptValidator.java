package com.example.demo.auth.validator;

import com.example.demo.auth.exception.IdentityException;
import java.util.Base64;
import org.springframework.stereotype.Component;

@Component
public class GoogleAttemptValidator {
    public void validateChallenge(String challenge,String method) {
        try {
            if (!"S256".equals(method) || challenge==null || !challenge.matches("[A-Za-z0-9_-]{43}")) throw invalid();
            byte[] bytes=Base64.getUrlDecoder().decode(challenge);
            if(bytes.length!=32 || !Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).equals(challenge))throw invalid();
        }catch(IllegalArgumentException malformed){throw invalid();}
    }
    private IdentityException invalid(){return new IdentityException(400,"PKCE_INVALID","Invalid client proof configuration");}
}
