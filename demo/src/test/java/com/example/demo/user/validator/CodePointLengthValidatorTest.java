package com.example.demo.user.validator;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
class CodePointLengthValidatorTest {
    static class Input {
        @CodePointLength(max=2) String value;
        Input(String value) {
            this.value=value;
        }
    }
    @ParameterizedTest @ValueSource(strings= {
        "ab","\uD83D\uDE00\uD83D\uDE00","e\u0301"
    }
    )
    void isValid_shouldCountCodePointsRatherThanUtf16Units(String value) {
        try(ValidatorFactory factory=Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(new Input(value))).isEmpty();
        }
    }
    @Test void isValid_shouldRejectThirdCodePoint() {
        try(ValidatorFactory factory=Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(new Input("\uD83D\uDE00\uD83D\uDE00a"))).hasSize(1);
        }
    }
    @ParameterizedTest
    @ValueSource(ints={20,50,100,254,1000})
    void isValid_shouldEnforceEveryConfiguredMaximum(int maximum) {
        CodePointLength annotation=org.mockito.Mockito.mock(CodePointLength.class);
        org.mockito.Mockito.when(annotation.max()).thenReturn(maximum);
        CodePointLengthValidator validator=new CodePointLengthValidator();validator.initialize(annotation);
        String character=new String(Character.toChars(0x1F600));
        assertThat(validator.isValid(character.repeat(maximum),null)).isTrue();
        assertThat(validator.isValid(character.repeat(maximum+1),null)).isFalse();
    }
}
