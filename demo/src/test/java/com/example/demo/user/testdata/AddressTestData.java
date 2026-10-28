package com.example.demo.user.testdata;
import com.example.demo.user.dto.request.CreateAddressRequest;
public final class AddressTestData {
    private AddressTestData() {
    }
    public static CreateAddressRequest request(Boolean flag) {
        CreateAddressRequest r=new CreateAddressRequest();
        r.setRecipientName("Recipient");
        r.setRecipientPhone("0123456789");
        r.setAddressText("Street");
        r.setIsDefault(flag);
        return r;
    }
}
