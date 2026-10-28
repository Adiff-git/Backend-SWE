package com.example.demo.auth.validator;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
class PasswordPolicyValidatorTest {
    private final PasswordPolicyValidator validator=new PasswordPolicyValidator();
    @Test void validate_shouldRejectFewerThanEightCodePoints() {
        assertThatThrownBy(()->validator.validate("1234567")).isInstanceOf(RuntimeException.class);
    }
    @Test void validate_shouldAllowExactSeventyTwoBytesWithoutTrimming() {
        assertThatCode(()->validator.validate(" "+"a".repeat(70)+" ")).doesNotThrowAnyException();
    }
    @Test void validate_shouldRejectSeventyThreeBytes() {
        assertThatThrownBy(()->validator.validate("a".repeat(73))).isInstanceOf(RuntimeException.class);
    }
    @Test void validate_shouldCountMultibyteCodePointsAndBytesSeparately() {
        String emoji=new String(Character.toChars(0x1f600));
        assertThatCode(()->validator.validate(emoji.repeat(18))).doesNotThrowAnyException();
        assertThatThrownBy(()->validator.validate(emoji.repeat(19))).isInstanceOf(RuntimeException.class);
    }
}
