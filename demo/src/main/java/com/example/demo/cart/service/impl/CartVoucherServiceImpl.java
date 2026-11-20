package com.example.demo.cart.service.impl;
import com.example.demo.cart.service.CartVoucherService;
import com.example.demo.cart.service.CartVoucherTransactionService;
import com.example.demo.cart.repository.CartItemRepository;
import com.example.demo.cart.validator.CartValidator;
import com.example.demo.cart.dto.response.CartResponse;
import com.example.demo.catalog.service.CatalogCartReadService;
import com.example.demo.storage.service.ImageUrlResolver;
import com.example.demo.storage.service.PreparedImageUrls;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
@Service @RequiredArgsConstructor
public class CartVoucherServiceImpl implements CartVoucherService {
    private final CartVoucherTransactionService transactions;
    private final CartItemRepository items;
    private final CatalogCartReadService catalog;
    private final ImageUrlResolver images;
    private final CartValidator validator;
    private final com.example.demo.cart.service.CartWalletReadService walletContext;
    private final com.example.demo.voucher.service.VoucherService vouchers;
    @Override public CartResponse applyVoucher(Long owner, Long grant) {
        validator.owner(owner);
        var ids = items.findVariantIdsForOwner(owner);
        PreparedImageUrls urls = ids.isEmpty() || !catalog.hasImageCandidates(ids) ? null : images.prepare();
        return transactions.applyVoucher(owner, grant, urls);
    }
    @Override public void removeVoucher(Long owner) { validator.owner(owner); transactions.removeVoucher(owner); }
    @Override public com.example.demo.voucher.dto.response.VoucherWalletPage getWalletPage(Long owner,int page,int size) {
        validator.owner(owner);return vouchers.getWalletPage(owner,page,size,walletContext.getEligibleSubtotal(owner));
    }
    @Override public com.example.demo.voucher.dto.response.VoucherWalletDetail getWalletEntitlement(Long owner,Long grant) {
        validator.owner(owner);return vouchers.getWalletEntitlement(owner,grant,walletContext.getEligibleSubtotal(owner));
    }
}
