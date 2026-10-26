package com.example.demo.user.converter;
import com.example.demo.user.entity.Address;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
class AddressConverterTest {
    @Test void toResponse_shouldMapAddressWithoutOwner() {
        Address a=new Address();
        a.setId(4L);
        a.setRecipientName("Name");
        a.setIsDefault(true);
        assertThat(new AddressConverter().toResponse(a).getRecipientName()).isEqualTo("Name");
        assertThat(com.example.demo.user.dto.response.AddressResponse.class.getDeclaredFields()).extracting(java.lang.reflect.Field::getName).doesNotContain("user","userId");
    }
}
