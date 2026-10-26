package com.example.demo.user.validator;
import com.example.demo.user.dto.request.UpdateUserRequest;
import com.example.demo.user.exception.BusinessValidationException;
import com.example.demo.user.exception.IdentityConflictException;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Component;
@Component
public class UserValidator {
    private final Clock clock;
    public UserValidator() {
        this(Clock.systemUTC());
    }
    public UserValidator(Clock clock) {
        this.clock=clock;
    }
    public void validate(UpdateUserRequest request,boolean usernameConflict,boolean emailConflict) {
        if((request.isUsernamePresent() && request.getUsername()==null) || (request.isEmailPresent() && request.getEmail()==null)) throw new BusinessValidationException("Username and email cannot be null when supplied");
        if(request.getDateOfBirth()!=null && request.getDateOfBirth().isAfter(LocalDate.now(clock))) throw new BusinessValidationException("Birth date must not be in the future");
        if(usernameConflict || emailConflict) throw new IdentityConflictException();
    }
}
