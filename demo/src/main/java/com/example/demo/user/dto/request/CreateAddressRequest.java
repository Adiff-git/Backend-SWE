package com.example.demo.user.dto.request;
import com.example.demo.user.validator.CodePointLength;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;
@Getter
public class CreateAddressRequest {
    @NotBlank @CodePointLength(max=100) private String recipientName;
    @NotBlank @Pattern(regexp="0[0-9]{9}") private String recipientPhone;
    @NotBlank @CodePointLength(max=1000) private String addressText;
    @Setter private Boolean isDefault;
    public void setRecipientName(String value) {
        recipientName=value==null?null:value.strip();
    }
    public void setRecipientPhone(String value) {
        recipientPhone=value==null?null:value.strip();
    }
    public void setAddressText(String value) {
        addressText=value==null?null:value.strip();
    }
}
