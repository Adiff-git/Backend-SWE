package com.example.demo.cart.service;

import com.example.demo.cart.service.impl.CartVoucherServiceImpl;
import com.example.demo.cart.repository.CartItemRepository;
import com.example.demo.catalog.service.CatalogCartReadService;
import com.example.demo.storage.service.ImageUrlResolver;
import com.example.demo.cart.validator.CartValidator;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class CartVoucherServiceImplTest {
    @Mock CartVoucherTransactionService transactions;
    @Mock CartItemRepository items;
    @Mock CatalogCartReadService catalog;
    @Mock ImageUrlResolver images;
    @Mock CartValidator validator;
    @Mock CartWalletReadService walletContext;
    @Mock com.example.demo.voucher.service.VoucherService vouchers;
    @InjectMocks CartVoucherServiceImpl service;
    @Test void removeVoucher_shouldDelegateWithoutPreparingImagesOrCatalogMetadata() {
        service.removeVoucher(1L);
        verify(transactions).removeVoucher(1L);
        verifyNoInteractions(items, catalog, images);
    }
    @Test void wallet_shouldPrepareContextBeforeVoucherReadWithoutImagesOrMutation() {
        org.mockito.Mockito.when(walletContext.getEligibleSubtotal(1L)).thenReturn(java.math.BigInteger.TEN);
        service.getWalletPage(1L,0,20);
        var order=org.mockito.Mockito.inOrder(walletContext,vouchers);
        order.verify(walletContext).getEligibleSubtotal(1L);order.verify(vouchers).getWalletPage(1L,0,20,java.math.BigInteger.TEN);
        verifyNoInteractions(transactions,items,catalog,images);
    }
}
