package com.example.demo.checkout.service.impl;

import com.example.demo.cart.dto.response.CheckoutCartSeed;
import com.example.demo.cart.service.CartCheckoutService;
import com.example.demo.catalog.dto.response.CheckoutVariantState;
import com.example.demo.catalog.service.CatalogCheckoutService;
import com.example.demo.checkout.calculator.CheckoutCalculator;
import com.example.demo.checkout.converter.CheckoutConverter;
import com.example.demo.checkout.dto.request.CheckoutRequest;
import com.example.demo.checkout.dto.response.CheckoutCalculation;
import com.example.demo.checkout.dto.response.CheckoutReceiptResponse;
import com.example.demo.checkout.dto.response.SkippedCheckoutItem;
import com.example.demo.checkout.entity.CheckoutReceipt;
import com.example.demo.checkout.exception.CheckoutInProgressException;
import com.example.demo.checkout.exception.NoEligibleCheckoutItemsException;
import com.example.demo.checkout.repository.CheckoutReceiptRepository;
import com.example.demo.checkout.service.CheckoutService;
import com.example.demo.checkout.validator.CheckoutValidator;
import com.example.demo.common.identity.IdentityUnavailableException;
import com.example.demo.order.dto.request.CreateOrderCommand;
import com.example.demo.order.dto.response.CreatedOrderSeed;
import com.example.demo.order.service.OrderCreationService;
import com.example.demo.user.dto.response.CheckoutAddressSnapshot;
import com.example.demo.user.exception.ResourceNotFoundException;
import com.example.demo.user.service.AddressService;
import com.example.demo.user.service.UserService;
import jakarta.annotation.PostConstruct;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CheckoutServiceImpl implements CheckoutService {
    private static final String OPERATION = "CHECKOUT";
    private final CheckoutReceiptRepository receipts;
    private final UserService users;
    private final AddressService addresses;
    private final CartCheckoutService carts;
    private final CatalogCheckoutService catalog;
    private final OrderCreationService orders;
    private final CheckoutValidator validator;
    private final CheckoutCalculator calculator;
    private final CheckoutConverter converter;
    private final Clock clock;
    @Value("${checkout.lock-timeout-ms:5000}") private long lockTimeoutMs = 5000;

    @PostConstruct void validateConfiguration() {
        if (lockTimeoutMs <= 0) throw new IllegalArgumentException("Checkout lock timeout must be positive");
    }

    @Override
    @Transactional
    public CheckoutReceiptResponse checkout(Long owner, String key, CheckoutRequest request) {
        validator.request(owner,key,request);
        CheckoutReceipt existing = lookup(owner,key);
        if (existing != null) return replay(existing,request);
        boolean acquired = receipts.tryFence(validator.fenceKey(owner,key));
        existing = lookup(owner,key); // New READ_COMMITTED statement on both acquired and busy branches.
        if (existing != null) return replay(existing,request);
        if (!acquired) throw new CheckoutInProgressException();

        try { users.getUserEntityForUpdate(owner); }
        catch (ResourceNotFoundException unavailable) { throw new IdentityUnavailableException(); }
        receipts.setLockTimeout(lockTimeoutMs + "ms"); // User service installs its own timeout; reapply ours.
        CheckoutAddressSnapshot address = addresses.getCheckoutAddressSnapshot(owner,request.getAddressId());
        CheckoutCartSeed seed = carts.getCheckoutCartSeed(owner);
        Map<UUID, CheckoutVariantState> states = catalog.getCheckoutVariantStates(seed.getRows().stream().map(CheckoutCartSeed.Row::getVariantId).toList());
        List<CheckoutCartSeed.Row> included = new ArrayList<>();
        List<SkippedCheckoutItem> skipped = new ArrayList<>();
        for (CheckoutCartSeed.Row row : seed.getRows()) {
            SkippedCheckoutItem.Reason reason = validator.reason(row,states.get(row.getVariantId()));
            if (reason == null) included.add(row);
            else skipped.add(new SkippedCheckoutItem(row.getId(),reason));
        }
        skipped.sort(Comparator.comparing(SkippedCheckoutItem::getCartItemId));
        if (included.isEmpty()) throw new NoEligibleCheckoutItemsException(skipped);
        CheckoutCalculation calculation = calculator.calculate(included,states);
        Instant at = clock.instant().truncatedTo(ChronoUnit.MICROS);
        List<CreateOrderCommand.Item> items = calculation.getLines().stream().map(line -> new CreateOrderCommand.Item(
                line.getRow().getId(),line.getState().getProductId(),line.getState().getVariantId(),line.getState().getProductName(),
                line.getState().getSku(),line.getState().getOptions(),line.getRow().getQuantity(),line.getUnitPrice(),line.getLineTotal())).toList();
        CreatedOrderSeed created = orders.createOrder(new CreateOrderCommand(owner,at,address.getRecipientName(),
                address.getRecipientPhone(),address.getDeliveryAddress(),items,calculation.getTotal()));
        carts.consumeCheckoutItems(owner,seed,included);
        CheckoutReceiptResponse response = converter.toReceipt(created,skipped);
        String body = converter.encode(response);
        converter.decode(body); // Prove durable representation is decodable before committing any outcome.
        receipts.insertSuccess(owner,key,request.getAddressId(),validator.fingerprint(request.getAddressId()),
                created.getId(),"/api/me/orders/"+created.getId(),body,at);
        return response;
    }

    private CheckoutReceipt lookup(Long owner, String key) {
        return receipts.findByCustomerIdAndOperationAndIdempotencyKey(owner,OPERATION,key).orElse(null);
    }

    private CheckoutReceiptResponse replay(CheckoutReceipt receipt, CheckoutRequest request) {
        validator.matching(receipt,request.getAddressId());
        return converter.decode(receipt.getReceiptBody());
    }
}
