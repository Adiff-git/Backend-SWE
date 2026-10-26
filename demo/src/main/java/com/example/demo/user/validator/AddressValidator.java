package com.example.demo.user.validator;
import com.example.demo.user.entity.Address;
import com.example.demo.user.exception.BusinessValidationException;
import org.springframework.stereotype.Component;
@Component
public class AddressValidator {
    public static final int MAX_ADDRESSES=20;
    public void validateCapacity(int count) {
        if(count>=MAX_ADDRESSES) throw new BusinessValidationException("Address book is limited to 20 addresses");
    }
    public void validateDefaultChange(Address address,Boolean flag) {
        if(Boolean.TRUE.equals(address.getIsDefault()) && Boolean.FALSE.equals(flag)) throw new BusinessValidationException("The current default cannot be unset");
    }
}
