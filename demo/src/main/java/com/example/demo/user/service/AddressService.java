package com.example.demo.user.service;
import com.example.demo.user.dto.request.CreateAddressRequest;
import com.example.demo.user.dto.request.UpdateAddressRequest;
import com.example.demo.user.dto.response.AddressResponse;
import java.util.List;
import jakarta.validation.Valid;
public interface AddressService {
    List<AddressResponse> getAddresses(Long actorId);
    AddressResponse getAddress(Long actorId,Long addressId);
    AddressResponse createAddress(Long actorId,@Valid CreateAddressRequest request);
    AddressResponse updateAddress(Long actorId,Long addressId,@Valid UpdateAddressRequest request);
    void deleteAddress(Long actorId,Long addressId);
}
