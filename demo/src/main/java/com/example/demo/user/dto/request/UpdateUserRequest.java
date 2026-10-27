package com.example.demo.user.dto.request;
import com.example.demo.user.enums.Gender;
import com.example.demo.user.validator.CodePointLength;
import com.example.demo.user.exception.BusinessValidationException;
import jakarta.validation.constraints.Email;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
@Getter
public class UpdateUserRequest {
    @CodePointLength(max=100) private String displayName;
    @jakarta.validation.constraints.Pattern(regexp="[^@]*") @CodePointLength(max=50) private String username;
    @Email @CodePointLength(max=254) private String email;
    @CodePointLength(max=20) private String phoneNumber;
    @Setter private Gender gender;
    @Setter private LocalDate dateOfBirth;
    private boolean usernamePresent;
    private boolean emailPresent;
    private boolean profileImagePresent;
    private Long profileImageId;
    private static String normalize(String value) {
        if(value==null) return null;
        String normalized=value.strip();
        if(normalized.isBlank()) throw new BusinessValidationException("Supplied text must not be blank");
        return normalized;
    }
    public void setDisplayName(String value) {
        displayName=normalize(value);
    }
    public void setUsername(String value) {
        usernamePresent=true;
        username=normalize(value);
    }
    public void setEmail(String value) {
        emailPresent=true;
        email=normalize(value);
    }
    public void setPhoneNumber(String value) {
        phoneNumber=normalize(value);
    }
    public void setProfileImageId(tools.jackson.databind.JsonNode value) {
        profileImagePresent=true;
        if(value==null || value.isNull()) { profileImageId=null; return; }
        if(!value.isIntegralNumber() || !value.canConvertToLong() || value.asLong()<=0)
            throw new BusinessValidationException("Image ID must be a positive integer");
        profileImageId=value.asLong();
    }
    @com.fasterxml.jackson.annotation.JsonAnySetter
    public void rejectUnknownProperty(String property, tools.jackson.databind.JsonNode value) {
        throw new BusinessValidationException("Property is not editable");
    }
}
