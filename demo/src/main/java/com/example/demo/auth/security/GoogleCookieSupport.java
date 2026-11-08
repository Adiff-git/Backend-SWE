package com.example.demo.auth.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

public final class GoogleCookieSupport {
    public static final String NAME="google_handshake";
    public static final String ATTEMPT_ATTRIBUTE=GoogleCookieSupport.class.getName()+".attempt";
    private GoogleCookieSupport() { }
    public static String read(HttpServletRequest request) {
        String value=null;
        if(request.getCookies()!=null)for(Cookie cookie:request.getCookies())if(NAME.equals(cookie.getName())) {
            if(value!=null)return null;value=cookie.getValue();
        }
        return value;
    }
}
