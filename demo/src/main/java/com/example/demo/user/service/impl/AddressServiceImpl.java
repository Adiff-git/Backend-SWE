package com.example.demo.user.service.impl;
import com.example.demo.user.service.AddressService;
import com.example.demo.user.service.UserService;
import com.example.demo.user.repository.AddressRepository;
import com.example.demo.user.validator.AddressValidator;
import com.example.demo.user.converter.AddressConverter;
import com.example.demo.user.entity.Address;
import com.example.demo.user.entity.User;
import com.example.demo.user.dto.request.CreateAddressRequest;
import com.example.demo.user.dto.request.UpdateAddressRequest;
import com.example.demo.user.dto.response.AddressResponse;
import com.example.demo.user.exception.ResourceNotFoundException;
import java.util.List;
import java.util.Comparator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import org.springframework.transaction.annotation.Transactional;
@Service @Validated @RequiredArgsConstructor @Transactional(readOnly=true)
public class AddressServiceImpl implements AddressService {
    private final AddressRepository repository;
    private final UserService users;
    private final AddressValidator validator;
    private final AddressConverter converter;
    public List<AddressResponse> getAddresses(Long actorId) {
        users.getUserEntity(actorId);
        return repository.findByUserIdOrderByIsDefaultDescIdDesc(actorId).stream().map(converter::toResponse).toList();
    }
    public AddressResponse getAddress(Long actorId,Long id) {
        users.getUserEntity(actorId);
        return converter.toResponse(getAddressEntity(actorId,id));
    }
    @Transactional public AddressResponse createAddress(Long actorId,CreateAddressRequest request) {
        User owner=users.getUserEntityForUpdate(actorId);
        List<Address> addresses=repository.findByUserIdOrderByIsDefaultDescIdDesc(actorId);
        validator.validateCapacity(addresses.size());
        boolean makeDefault=addresses.isEmpty() || Boolean.TRUE.equals(request.getIsDefault());
        if(makeDefault) clearDefault(addresses);
        Address address=new Address();
        address.setUser(owner);
        address.setRecipientName(request.getRecipientName());
        address.setRecipientPhone(request.getRecipientPhone());
        address.setAddressText(request.getAddressText());
        address.setIsDefault(makeDefault);
        return converter.toResponse(repository.saveAndFlush(address));
    }
    @Transactional public AddressResponse updateAddress(Long actorId,Long id,UpdateAddressRequest request) {
        users.getUserEntityForUpdate(actorId);
        Address address=getAddressEntity(actorId,id);
        validator.validateDefaultChange(address,request.getIsDefault());
        if(Boolean.TRUE.equals(request.getIsDefault()) && !Boolean.TRUE.equals(address.getIsDefault())) {
            clearDefault(repository.findByUserIdOrderByIsDefaultDescIdDesc(actorId));
            address.setIsDefault(true);
        }
        address.setRecipientName(request.getRecipientName());
        address.setRecipientPhone(request.getRecipientPhone());
        address.setAddressText(request.getAddressText());
        repository.flush();
        return converter.toResponse(address);
    }
    @Transactional public void deleteAddress(Long actorId,Long id) {
        users.getUserEntityForUpdate(actorId);
        Address address=getAddressEntity(actorId,id);
        boolean wasDefault=Boolean.TRUE.equals(address.getIsDefault());
        repository.delete(address);
        repository.flush();
        if(wasDefault) {
            repository.findByUserIdOrderByIsDefaultDescIdDesc(actorId).stream().max(Comparator.comparing(Address::getId)).ifPresent(a->a.setIsDefault(true));
            repository.flush();
        }
    }
    public Address getAddressEntity(Long actorId,Long id) {
        return repository.findByIdAndUserId(id,actorId).orElseThrow(ResourceNotFoundException::new);
    }
    private void clearDefault(List<Address> addresses) {
        addresses.stream().filter(a->Boolean.TRUE.equals(a.getIsDefault())).forEach(a->a.setIsDefault(false));
        repository.flush();
    }
}
