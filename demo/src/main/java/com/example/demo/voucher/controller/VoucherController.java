package com.example.demo.voucher.controller;
import com.example.demo.cart.service.CartVoucherService;
import com.example.demo.common.identity.CurrentUserIdProvider;
import com.example.demo.voucher.dto.response.VoucherWalletPage;
import com.example.demo.voucher.dto.response.VoucherWalletDetail;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
@RestController @RequestMapping("/api/me/vouchers") @RequiredArgsConstructor
public class VoucherController {
    private final CartVoucherService service;private final CurrentUserIdProvider identity;private final VoucherRequestReader reader;
    @GetMapping public VoucherWalletPage getWalletPage(HttpServletRequest request) {
        int[] page=reader.readPage(request);return service.getWalletPage(identity.getCurrentUserId(),page[0],page[1]);
    }
    @GetMapping("/{walletEntitlementId}") public VoucherWalletDetail getWalletEntitlement(@PathVariable String walletEntitlementId,HttpServletRequest request) {
        return service.getWalletEntitlement(identity.getCurrentUserId(),reader.readDetail(request,walletEntitlementId));
    }
}
