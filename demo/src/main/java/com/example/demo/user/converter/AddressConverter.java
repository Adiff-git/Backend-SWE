package com.example.demo.user.converter;
import com.example.demo.user.entity.Address;
import com.example.demo.user.dto.response.AddressResponse;
import org.springframework.stereotype.Component;
@Component
public class AddressConverter {
    public AddressResponse toResponse(Address a) {
        AddressResponse d=new AddressResponse();
        d.setId(a.getId());
        d.setRecipientName(a.getRecipientName());
        d.setRecipientPhone(a.getRecipientPhone());
        d.setAddressText(a.getAddressText());
        d.setIsDefault(a.getIsDefault());
        return d;
    }
}
