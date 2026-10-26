package com.example.demo.auth.testdata;
import com.example.demo.auth.entity.*;
import java.time.*;
import java.util.*;
public final class AuthTestData {
    private AuthTestData() {
    }
    public static AuthenticationSession session(Long owner,Instant now) {
        var s=new AuthenticationSession();
        s.setId(UUID.randomUUID());
        s.setUserId(owner);
        s.setCreatedAt(now);
        s.setExpiresAt(now.plusSeconds(604800));
        return s;
    }
    public static RefreshCredential credential(UUID session,byte[] hash,Instant now) {
        var c=new RefreshCredential();
        c.setSessionId(session);
        c.setTokenHash(hash);
        c.setIssuedAt(now);
        return c;
    }
}
