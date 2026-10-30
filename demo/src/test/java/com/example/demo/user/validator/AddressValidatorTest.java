package com.example.demo.user.validator;
import com.example.demo.user.entity.Address;
import com.example.demo.user.exception.BusinessValidationException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
class AddressValidatorTest {
    private final AddressValidator validator=new AddressValidator();
    @Test void validateCapacity_shouldAcceptNineteen() {
        assertThatCode(()->validator.validateCapacity(19)).doesNotThrowAnyException();
    }
    @Test void validateCapacity_shouldRejectTwenty() {
        assertThatThrownBy(()->validator.validateCapacity(20)).isInstanceOf(BusinessValidationException.class);
    }
    @Test void validateDefaultChange_shouldRejectUnsettingDefault() {
        Address a=new Address();
        a.setIsDefault(true);
        assertThatThrownBy(()->validator.validateDefaultChange(a,false)).isInstanceOf(BusinessValidationException.class);
    }
    @Test void validateDefaultChange_shouldPreserveOnNull() {
        Address a=new Address();
        a.setIsDefault(true);
        assertThatCode(()->validator.validateDefaultChange(a,null)).doesNotThrowAnyException();
    }
}
