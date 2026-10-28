package com.example.demo.common.identity;
import com.example.demo.common.exception.ApplicationException;
/** A boundary cannot supply trusted identity; no production adapter is registered. */
public class IdentityUnavailableException extends ApplicationException {
    public IdentityUnavailableException() {
        super(401,"Authenticated identity is unavailable");
    }
}
