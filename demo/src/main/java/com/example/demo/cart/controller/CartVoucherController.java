package com.example.demo.cart.controller;
import com.example.demo.cart.service.CartVoucherService;
import com.example.demo.cart.dto.request.ApplyCartVoucherRequest;
import com.example.demo.cart.dto.response.CartResponse;
import com.example.demo.common.identity.CurrentUserIdProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
@RestController @RequestMapping("/api/me/cart/voucher") @RequiredArgsConstructor
public class CartVoucherController {
    private final CartVoucherService service; private final CurrentUserIdProvider identity; private final CartRequestReader reader;
    @PutMapping public CartResponse applyVoucher(@Valid @RequestBody ApplyCartVoucherRequest body, HttpServletRequest request) {
        reader.noQuery(request); return service.applyVoucher(identity.getCurrentUserId(), body.getWalletEntitlementId());
    }
    @DeleteMapping public ResponseEntity<Void> removeVoucher(HttpServletRequest request) {
        reader.noBody(request); service.removeVoucher(identity.getCurrentUserId()); return ResponseEntity.noContent().build();
    }
}
