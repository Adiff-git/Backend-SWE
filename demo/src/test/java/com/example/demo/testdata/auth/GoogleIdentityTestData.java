package com.example.demo.testdata.auth;

import java.util.LinkedHashMap;
import java.util.Map;

public final class GoogleIdentityTestData {
    private GoogleIdentityTestData() { }
    public static Map<String, Object> verified() {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", "google-subject-one");
        claims.put("email", "google-user@example.test");
        claims.put("email_verified", true);
        claims.put("name", "Google User");
        return claims;
    }
    public static Map<String, Object> unverified() {
        Map<String, Object> claims = verified();
        claims.put("email_verified", false);
        return claims;
    }
}
