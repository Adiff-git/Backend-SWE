package com.example.demo.user.exception;

import com.example.demo.common.exception.ApplicationException;

public class EmailVerificationRequiredException extends ApplicationException {
    @Override public String getCode(){return "EMAIL_VERIFICATION_REQUIRED";}
    public EmailVerificationRequiredException(){super(400,"Email must be changed through email verification");}
}

