package com.example.demo.user.service;
import com.example.demo.user.service.impl.AddressServiceImpl;
import com.example.demo.user.repository.AddressRepository;
import com.example.demo.user.converter.AddressConverter;
import com.example.demo.user.validator.AddressValidator;
import com.example.demo.user.entity.Address;
import com.example.demo.user.entity.User;
import com.example.demo.user.dto.request.CreateAddressRequest;
import com.example.demo.user.exception.ResourceNotFoundException;
import com.example.demo.user.testdata.UserTestData;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
class AddressServiceImplTest {
    private final AddressRepository repository=mock(AddressRepository.class);
    private final UserService users=mock(UserService.class);
    private final AddressServiceImpl service=new AddressServiceImpl(repository,users,new AddressValidator(),new AddressConverter());
    @Test void createAddress_shouldMakeFirstDefaultEvenWhenFalse() {
        User user=UserTestData.user("Alice");
        when(users.getUserEntityForUpdate(8L)).thenReturn(user);
        when(repository.findByUserIdOrderByIsDefaultDescIdDesc(8L)).thenReturn(List.of());
        when(repository.saveAndFlush(any(Address.class))).thenAnswer(i->i.getArgument(0));
        CreateAddressRequest r=new CreateAddressRequest();
        r.setRecipientName("Name");
        r.setRecipientPhone("0123456789");
        r.setAddressText("Street");
        r.setIsDefault(false);
        assertThat(service.createAddress(8L,r).getIsDefault()).isTrue();
        verify(users).getUserEntityForUpdate(8L);
    }
    @Test void getAddress_shouldNotExposeForeignOwnership() {
        when(repository.findByIdAndUserId(5L,8L)).thenReturn(Optional.empty());
        assertThatThrownBy(()->service.getAddress(8L,5L)).isInstanceOf(ResourceNotFoundException.class);
    }
    @Test void deleteAddress_shouldPromoteHighestRemainingId() {
        Address old=new Address();
        old.setId(1L);
        old.setIsDefault(true);
        Address a=new Address();
        a.setId(3L);
        a.setIsDefault(false);
        Address b=new Address();
        b.setId(5L);
        b.setIsDefault(false);
        when(repository.findByIdAndUserId(1L,8L)).thenReturn(Optional.of(old));
        when(repository.findByUserIdOrderByIsDefaultDescIdDesc(8L)).thenReturn(List.of(a,b));
        service.deleteAddress(8L,1L);
        assertThat(b.getIsDefault()).isTrue();
        assertThat(a.getIsDefault()).isFalse();
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans= {
        false,true
    }
    )
    void createAddress_shouldRespectExplicitFlagForLaterAddress(boolean flag) {
        Address old=new Address();
        old.setId(1L);
        old.setIsDefault(true);
        when(repository.findByUserIdOrderByIsDefaultDescIdDesc(8L)).thenReturn(List.of(old));
        when(repository.saveAndFlush(any(Address.class))).thenAnswer(i->i.getArgument(0));
        CreateAddressRequest r=new CreateAddressRequest();
        r.setIsDefault(flag);
        assertThat(service.createAddress(8L,r).getIsDefault()).isEqualTo(flag);
        assertThat(old.getIsDefault()).isEqualTo(!flag);
    }
    @Test void createAddress_shouldPreserveDefaultForOmittedFlag() {
        Address old=new Address();
        old.setIsDefault(true);
        when(repository.findByUserIdOrderByIsDefaultDescIdDesc(8L)).thenReturn(List.of(old));
        when(repository.saveAndFlush(any(Address.class))).thenAnswer(i->i.getArgument(0));
        assertThat(service.createAddress(8L,new CreateAddressRequest()).getIsDefault()).isFalse();
        assertThat(old.getIsDefault()).isTrue();
    }
    @Test void updateAddress_shouldPreserveDefaultWhenFlagOmitted() {
        Address a=new Address();
        a.setIsDefault(true);
        when(repository.findByIdAndUserId(5L,8L)).thenReturn(Optional.of(a));
        com.example.demo.user.dto.request.UpdateAddressRequest r=new com.example.demo.user.dto.request.UpdateAddressRequest();
        r.setRecipientName("New");
        service.updateAddress(8L,5L,r);
        assertThat(a.getIsDefault()).isTrue();
        assertThat(a.getRecipientName()).isEqualTo("New");
    }
    @Test void updateAddress_shouldClearOldDefaultBeforePromotingTarget() {
        Address old=new Address();
        old.setIsDefault(true);
        Address target=new Address();
        target.setIsDefault(false);
        when(repository.findByIdAndUserId(5L,8L)).thenReturn(Optional.of(target));
        when(repository.findByUserIdOrderByIsDefaultDescIdDesc(8L)).thenReturn(List.of(old,target));
        com.example.demo.user.dto.request.UpdateAddressRequest r=new com.example.demo.user.dto.request.UpdateAddressRequest();
        r.setIsDefault(true);
        service.updateAddress(8L,5L,r);
        assertThat(old.getIsDefault()).isFalse();
        assertThat(target.getIsDefault()).isTrue();
    }
    @Test void deleteAddress_shouldPreserveDefaultWhenNonDefaultDeleted() {
        Address a=new Address();
        a.setIsDefault(false);
        when(repository.findByIdAndUserId(5L,8L)).thenReturn(Optional.of(a));
        service.deleteAddress(8L,5L);
        verify(repository,org.mockito.Mockito.never()).findByUserIdOrderByIsDefaultDescIdDesc(8L);
    }
    @Test void deleteAddress_shouldAllowFinalDefaultDeletion() {
        Address a=new Address();
        a.setIsDefault(true);
        when(repository.findByIdAndUserId(5L,8L)).thenReturn(Optional.of(a));
        when(repository.findByUserIdOrderByIsDefaultDescIdDesc(8L)).thenReturn(List.of());
        service.deleteAddress(8L,5L);
        verify(repository).delete(a);
    }
}
