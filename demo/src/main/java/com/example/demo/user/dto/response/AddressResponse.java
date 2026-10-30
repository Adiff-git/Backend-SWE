package com.example.demo.user.dto.response;
import lombok.Getter;
import lombok.Setter;
@Getter @Setter
public class AddressResponse {
    private Long id;
    private String recipientName;
    private String recipientPhone;
    private String addressText;
    private Boolean isDefault;
}
