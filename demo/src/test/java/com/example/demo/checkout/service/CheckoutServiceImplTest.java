package com.example.demo.checkout.service;

import com.example.demo.cart.service.CartCheckoutService;
import com.example.demo.catalog.service.CatalogCheckoutService;
import com.example.demo.checkout.calculator.CheckoutCalculator;
import com.example.demo.checkout.converter.CheckoutConverter;
import com.example.demo.checkout.dto.request.CheckoutRequest;
import com.example.demo.checkout.exception.CheckoutInProgressException;
import com.example.demo.checkout.repository.CheckoutReceiptRepository;
import com.example.demo.checkout.service.impl.CheckoutServiceImpl;
import com.example.demo.checkout.validator.CheckoutValidator;
import com.example.demo.order.service.OrderCreationService;
import com.example.demo.user.service.AddressService;
import com.example.demo.user.service.UserService;
import java.time.Clock;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;

class CheckoutServiceImplTest {
    private final CheckoutReceiptRepository receipts = mock(CheckoutReceiptRepository.class);
    private final UserService users = mock(UserService.class);
    private final AddressService addresses = mock(AddressService.class);
    private final CartCheckoutService carts = mock(CartCheckoutService.class);
    private final CatalogCheckoutService catalog = mock(CatalogCheckoutService.class);
    private final OrderCreationService orders = mock(OrderCreationService.class);

    @Test void checkout_shouldReturnProcessingOnlyAfterBothCommittedLookupsMiss() {
        when(receipts.findByCustomerIdAndOperationAndIdempotencyKey(1L,"CHECKOUT","Processing_key_123")).thenReturn(Optional.empty());
        when(receipts.tryFence(anyLong())).thenReturn(false);
        CheckoutServiceImpl service = new CheckoutServiceImpl(receipts,users,addresses,carts,catalog,orders,
                new CheckoutValidator(),new CheckoutCalculator(),new CheckoutConverter(),Clock.systemUTC());
        assertThatThrownBy(() -> service.checkout(1L,"Processing_key_123",new CheckoutRequest(1L)))
                .isInstanceOf(CheckoutInProgressException.class);
        org.mockito.Mockito.verify(receipts,org.mockito.Mockito.times(2))
                .findByCustomerIdAndOperationAndIdempotencyKey(1L,"CHECKOUT","Processing_key_123");
        verifyNoInteractions(users,addresses,carts,catalog,orders);
    }

    @Test void checkout_shouldSampleClockOnceAndPrepareExactHistoricalCommand() {
        java.util.UUID product=java.util.UUID.randomUUID();
        java.util.UUID variant=java.util.UUID.randomUUID();
        com.example.demo.cart.dto.response.CheckoutCartSeed.Row row=new com.example.demo.cart.dto.response.CheckoutCartSeed.Row(3L,product,variant,2,true);
        com.example.demo.cart.dto.response.CheckoutCartSeed seed=new com.example.demo.cart.dto.response.CheckoutCartSeed(1L,2L,java.util.List.of(row));
        Clock clock=mock(Clock.class);
        when(clock.instant()).thenReturn(java.time.Instant.parse("2026-11-18T03:00:00.123456789Z"));
        when(receipts.tryFence(anyLong())).thenReturn(true);
        when(carts.getCheckoutCartSeed(1L)).thenReturn(seed);
        when(addresses.getCheckoutAddressSnapshot(1L,1L)).thenReturn(new com.example.demo.user.dto.response.CheckoutAddressSnapshot("Recipient","0123456789","Address"));
        when(catalog.getCheckoutVariantStates(java.util.List.of(variant))).thenReturn(java.util.Map.of(variant,
                new com.example.demo.catalog.dto.response.CheckoutVariantState(variant,product,com.example.demo.catalog.entity.ProductStatus.PUBLISHED,
                        java.math.BigDecimal.valueOf(500),"Current name","SKU",java.util.Map.of())));
        when(orders.createOrder(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
            com.example.demo.order.dto.request.CreateOrderCommand command=invocation.getArgument(0);
            return new com.example.demo.order.dto.response.CreatedOrderSeed(9L,"ORD-9",command,
                    java.util.List.of(new com.example.demo.order.dto.response.CreatedOrderSeed.Item(11L,command.getItems().getFirst())));
        });
        CheckoutServiceImpl service=new CheckoutServiceImpl(receipts,users,addresses,carts,catalog,orders,
                new CheckoutValidator(),new CheckoutCalculator(),new CheckoutConverter(),clock);
        com.example.demo.checkout.dto.response.CheckoutReceiptResponse receipt=service.checkout(1L,"Clock_sample_12345",new CheckoutRequest(1L));
        assertThat(receipt.getOrder().getPlacedAt()).isEqualTo(java.time.Instant.parse("2026-11-18T03:00:00.123456Z"));
        assertThat(receipt.getOrder().getPayableTotal().getAmount()).isEqualTo(java.math.BigInteger.valueOf(1000));
        org.mockito.Mockito.verify(clock).instant();
        org.mockito.Mockito.verify(carts).consumeCheckoutItems(1L,seed,java.util.List.of(row));
    }

    private com.example.demo.checkout.entity.CheckoutReceipt storedReceipt(Long address) {
        com.example.demo.checkout.entity.CheckoutReceipt receipt=org.springframework.beans.BeanUtils.instantiateClass(com.example.demo.checkout.entity.CheckoutReceipt.class);
        org.springframework.test.util.ReflectionTestUtils.setField(receipt,"requestAddressId",address);
        org.springframework.test.util.ReflectionTestUtils.setField(receipt,"requestFingerprint",new CheckoutValidator().fingerprint(address));
        org.springframework.test.util.ReflectionTestUtils.setField(receipt,"receiptVersion",1);
        org.springframework.test.util.ReflectionTestUtils.setField(receipt,"responseStatus",201);
        String body=tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(
                com.example.demo.testdata.checkout.CheckoutTestData.receipt(5L,7L,com.example.demo.fixture.checkout.CheckoutFixtures.PLACED_AT));
        org.springframework.test.util.ReflectionTestUtils.setField(receipt,"receiptBody",body);
        return receipt;
    }

    private CheckoutServiceImpl replayService(Clock clock) {
        return new CheckoutServiceImpl(receipts,users,addresses,carts,catalog,orders,
                new CheckoutValidator(),new CheckoutCalculator(),new CheckoutConverter(),clock);
    }

    @Test void checkout_shouldReplayCommittedReceiptWithoutFenceClockOrMutableDomainCalls() {
        when(receipts.findByCustomerIdAndOperationAndIdempotencyKey(1L,"CHECKOUT","Completed_key_1234"))
                .thenReturn(Optional.of(storedReceipt(1L)));
        Clock clock=mock(Clock.class);
        assertThat(replayService(clock).checkout(1L,"Completed_key_1234",new CheckoutRequest(1L)).getOrder().getId()).isEqualTo(5L);
        org.mockito.Mockito.verify(receipts,org.mockito.Mockito.never()).tryFence(anyLong());
        verifyNoInteractions(clock,users,addresses,carts,catalog,orders);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans={true,false})
    void checkout_shouldReplayReceiptCommittedBeforeAcquiredOrBusyFenceRecheck(boolean acquired) {
        when(receipts.findByCustomerIdAndOperationAndIdempotencyKey(1L,"CHECKOUT","Race_replay_12345"))
                .thenReturn(Optional.empty(),Optional.of(storedReceipt(1L)));
        when(receipts.tryFence(anyLong())).thenReturn(acquired);
        Clock clock=mock(Clock.class);
        assertThat(replayService(clock).checkout(1L,"Race_replay_12345",new CheckoutRequest(1L)).getOrder().getId()).isEqualTo(5L);
        org.mockito.Mockito.verify(receipts,org.mockito.Mockito.times(2))
                .findByCustomerIdAndOperationAndIdempotencyKey(1L,"CHECKOUT","Race_replay_12345");
        verifyNoInteractions(clock,users,addresses,carts,catalog,orders);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints={0,1,2})
    void checkout_shouldRejectDifferentCommandAtEveryCommittedReceiptHit(int branch) {
        if(branch==0) when(receipts.findByCustomerIdAndOperationAndIdempotencyKey(1L,"CHECKOUT","Mismatch_key_1234"))
                .thenReturn(Optional.of(storedReceipt(2L)));
        else {
            when(receipts.findByCustomerIdAndOperationAndIdempotencyKey(1L,"CHECKOUT","Mismatch_key_1234"))
                    .thenReturn(Optional.empty(),Optional.of(storedReceipt(2L)));
            when(receipts.tryFence(anyLong())).thenReturn(branch==1);
        }
        assertThatThrownBy(() -> replayService(Clock.systemUTC()).checkout(1L,"Mismatch_key_1234",new CheckoutRequest(1L)))
                .isInstanceOf(com.example.demo.checkout.exception.CheckoutKeyConflictException.class);
        verifyNoInteractions(users,addresses,carts,catalog,orders);
    }

    @Test void checkout_shouldRejectUnsupportedReceiptVersionWithoutReexecuting() {
        com.example.demo.checkout.entity.CheckoutReceipt receipt=storedReceipt(1L);
        org.springframework.test.util.ReflectionTestUtils.setField(receipt,"receiptVersion",2);
        when(receipts.findByCustomerIdAndOperationAndIdempotencyKey(1L,"CHECKOUT","Unsupported_12345")).thenReturn(Optional.of(receipt));
        assertThatThrownBy(() -> replayService(Clock.systemUTC()).checkout(1L,"Unsupported_12345",new CheckoutRequest(1L)))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(users,addresses,carts,catalog,orders);
        org.mockito.Mockito.verify(receipts,org.mockito.Mockito.never()).tryFence(anyLong());
    }

    @Test void validateConfiguration_shouldRejectNonpositiveCheckoutWait() {
        CheckoutServiceImpl service=replayService(Clock.systemUTC());
        for(long value : new long[]{0,-1}) {
            org.springframework.test.util.ReflectionTestUtils.setField(service,"lockTimeoutMs",value);
            assertThatThrownBy(() -> org.springframework.test.util.ReflectionTestUtils.invokeMethod(service,"validateConfiguration"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test void checkout_shouldFailCorruptStoredBodyWithoutFallingBackToNewCheckout() {
        com.example.demo.checkout.entity.CheckoutReceipt receipt=storedReceipt(1L);
        org.springframework.test.util.ReflectionTestUtils.setField(receipt,"receiptBody","{}");
        when(receipts.findByCustomerIdAndOperationAndIdempotencyKey(1L,"CHECKOUT","Corrupt_body_12345")).thenReturn(Optional.of(receipt));
        assertThatThrownBy(() -> replayService(Clock.systemUTC()).checkout(1L,"Corrupt_body_12345",new CheckoutRequest(1L)))
                .isInstanceOf(RuntimeException.class);
        verifyNoInteractions(users,addresses,carts,catalog,orders);
        org.mockito.Mockito.verify(receipts,org.mockito.Mockito.never()).tryFence(anyLong());
    }
}
