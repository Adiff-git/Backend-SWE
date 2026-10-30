package com.example.demo.auth.validator;
import com.example.demo.user.exception.BusinessValidationException;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;
@Component
public class PasswordPolicyValidator {
    public void validate(String password) {
        if(password==null || password.codePointCount(0,password.length())<8 || password.getBytes(StandardCharsets.UTF_8).length>72)
        throw new BusinessValidationException("Password must have at least 8 characters and at most 72 UTF-8 bytes");
    }
}
