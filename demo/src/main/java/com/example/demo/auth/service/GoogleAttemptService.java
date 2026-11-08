package com.example.demo.auth.service;

import com.example.demo.auth.entity.OAuthLoginAttempt;
import java.util.UUID;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

public interface GoogleAttemptService {
    GoogleAttemptStart start(String challenge,String method,String cookie,OAuthLoginAttempt.Purpose purpose,OAuthLoginAttempt.Operation operation,Long owner,UUID session,String accountProof);
    OAuthLoginAttempt.Purpose authorizationPurpose(String ticket,String cookie);
    void saveAuthorization(String ticket,String cookie,OAuth2AuthorizationRequest request);
    GoogleAuthorizationState loadAuthorization(String state,String cookie,boolean consume);
    String complete(UUID attempt,ValidatedGoogleIdentity identity);
    void fail(UUID attempt);
}
