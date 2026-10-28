package com.example.demo.user.validator;
import com.example.demo.user.dto.request.UpdateUserRequest;
import com.example.demo.user.exception.BusinessValidationException;
import com.example.demo.user.exception.IdentityConflictException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
class UserValidatorTest {
    private final UserValidator validator=new UserValidator(Clock.fixed(Instant.parse("2026-10-26T00:00:00Z"),ZoneOffset.UTC));
    @Test void validate_shouldRejectExplicitNullUsername() {
        UpdateUserRequest r=new UpdateUserRequest();
        r.setUsername(null);
        assertThatThrownBy(()->validator.validate(r,false,false)).isInstanceOf(BusinessValidationException.class);
    }
    @Test void validate_shouldPreserveOmittedUsername() {
        assertThatCode(()->validator.validate(new UpdateUserRequest(),false,false)).doesNotThrowAnyException();
    }
    @Test void validate_shouldRejectFutureBirthDate() {
        UpdateUserRequest r=new UpdateUserRequest();
        r.setDateOfBirth(LocalDate.of(2026,10,27));
        assertThatThrownBy(()->validator.validate(r,false,false)).isInstanceOf(BusinessValidationException.class);
    }
    @Test void validate_shouldAcceptToday() {
        UpdateUserRequest r=new UpdateUserRequest();
        r.setDateOfBirth(LocalDate.of(2026,10,26));
        assertThatCode(()->validator.validate(r,false,false)).doesNotThrowAnyException();
    }
    @Test void validate_shouldRejectIdentityConflict() {
        assertThatThrownBy(()->validator.validate(new UpdateUserRequest(),true,false)).isInstanceOf(IdentityConflictException.class);
    }
    @Test void validate_shouldRejectAtSignWithoutChangingOtherUsernameRules() {
        UpdateUserRequest r=new UpdateUserRequest();r.setUsername("bad@name");
        assertThatThrownBy(()->validator.validate(r,false,false)).isInstanceOf(BusinessValidationException.class);
        r.setUsername(" A  B ");assertThatCode(()->validator.validate(r,false,false)).doesNotThrowAnyException();
    }
}
