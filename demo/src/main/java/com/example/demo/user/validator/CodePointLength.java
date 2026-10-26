package com.example.demo.user.validator;
import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;
@Constraint(validatedBy=CodePointLengthValidator.class)
@Target( {
    ElementType.FIELD,ElementType.PARAMETER,ElementType.ANNOTATION_TYPE
}
) @Retention(RetentionPolicy.RUNTIME)
public @interface CodePointLength {
    String message() default "exceeds maximum Unicode code-point length";
    int max();
    Class<?>[] groups() default {
    }
    ;
    Class<? extends Payload>[] payload() default {
    }
    ;
}
