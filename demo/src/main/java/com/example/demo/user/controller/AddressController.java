package com.example.demo.user.controller;
import com.example.demo.user.service.AddressService;
import com.example.demo.common.identity.CurrentUserIdProvider;
import com.example.demo.user.dto.request.CreateAddressRequest;
import com.example.demo.user.dto.request.UpdateAddressRequest;
import com.example.demo.user.dto.response.AddressResponse;
import java.util.List;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.http.ResponseEntity;
import lombok.RequiredArgsConstructor;
@RestController @RequestMapping("/api/me/addresses") @RequiredArgsConstructor
public class AddressController {
    private final AddressService addresses;
    private final CurrentUserIdProvider identity;
    @GetMapping public List<AddressResponse> list() {
        return addresses.getAddresses(identity.getCurrentUserId());
    }
    @GetMapping("/{id}") public AddressResponse get(@PathVariable Long id) {
        return addresses.getAddress(identity.getCurrentUserId(),id);
    }
    @PostMapping public ResponseEntity<AddressResponse> create(@Valid @RequestBody CreateAddressRequest request) {
        AddressResponse result=addresses.createAddress(identity.getCurrentUserId(),request);
        return ResponseEntity.created(java.net.URI.create("/api/me/addresses/"+result.getId())).body(result);
    }
    @PutMapping("/{id}") public AddressResponse update(@PathVariable Long id,@Valid @RequestBody UpdateAddressRequest request) {
        return addresses.updateAddress(identity.getCurrentUserId(),id,request);
    }
    @DeleteMapping("/{id}") public ResponseEntity<Void> delete(@PathVariable Long id) {
        addresses.deleteAddress(identity.getCurrentUserId(),id);
        return ResponseEntity.noContent().build();
    }
}
