package com.example.demo.auth.validator;

import com.example.demo.auth.exception.IdentityException;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor
public class PendingRegistrationValidator {
    private final PasswordPolicyValidator passwords;
    private final jakarta.validation.Validator emailValidator;
    private static final java.util.regex.Pattern EMAIL=java.util.regex.Pattern.compile("[^\\s@]+@[^\\s@]+\\.[^\\s@]+");
    public String normalizeEmail(String value) {
        if(value==null)throw invalid();
        String normalized=value.strip().toLowerCase(Locale.ROOT);
        if(normalized.length()>254 || !EMAIL.matcher(normalized).matches() || !emailValidator.validateValue(com.example.demo.auth.dto.request.RegisterRequest.class,"email",normalized).isEmpty())throw invalid();
        return normalized;
    }
    public void password(String value){passwords.validate(value);}
    public void proof(String credential,java.util.UUID id,String code){
        if(credential==null || !credential.matches("[A-Za-z0-9_-]{43}") || id==null || code!=null && !code.matches("[0-9]{6}"))throw invalid();
    }
    private IdentityException invalid(){return new IdentityException(400,"REGISTRATION_VERIFICATION_FAILED","Registration verification failed");}
}
