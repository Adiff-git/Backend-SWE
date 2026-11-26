package com.example.demo.order.validator;

import com.example.demo.common.identity.IdentityUnavailableException;
import com.example.demo.order.dto.request.CancelOrderRequest;
import com.example.demo.order.entity.Order;
import com.example.demo.order.exception.InvalidOrderRequestException;
import com.example.demo.order.exception.OrderConflictException;
import com.example.demo.order.exception.OrderNotFoundException;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class OrderValidator {
    /** Validate a prepared aggregate's integrity; never fetch/reprice catalog inputs. */
    public void validateCreation(com.example.demo.order.dto.request.CreateOrderCommand command) {
        if (command == null || command.getCustomerId() == null || command.getCustomerId() <= 0
                || command.getPlacedAt() == null || blank(command.getRecipientName()) || blank(command.getRecipientPhone())
                || blank(command.getDeliveryAddress()) || command.getItems().isEmpty() || command.getMerchandiseTotal() == null
                || command.getMerchandiseTotal().signum() < 0) throw new InvalidOrderRequestException();
        java.math.BigInteger sum = java.math.BigInteger.ZERO;
        java.util.Set<Long> sources = new java.util.HashSet<>();
        for (com.example.demo.order.dto.request.CreateOrderCommand.Item item : command.getItems()) {
            if (item.getCartItemId() == null || item.getCartItemId() <= 0 || !sources.add(item.getCartItemId())
                    || item.getProductId() == null || item.getVariantId() == null || blank(item.getProductName()) || blank(item.getSku())
                    || item.getQuantity() < 1 || item.getQuantity() > 999 || item.getUnitPrice() == null || item.getUnitPrice().signum() < 0
                    || item.getLineTotal() == null || !item.getLineTotal().equals(item.getUnitPrice().multiply(java.math.BigInteger.valueOf(item.getQuantity()))))
                throw new InvalidOrderRequestException();
            var pricing = item.getPricing();
            if (pricing == null || pricing.getOriginalPrice() == null || pricing.getDirectUnitDiscount() == null
                    || pricing.getEffectivePrice() == null || pricing.getOriginalPrice().getAmount()==null
                    || pricing.getEffectivePrice().getAmount()==null || pricing.getDirectUnitDiscount().getAmount()==null
                    || !command.getPlacedAt().equals(pricing.getPricedAt())
                    || !"VND".equals(pricing.getOriginalPrice().getCurrency()) || !"VND".equals(pricing.getEffectivePrice().getCurrency())
                    || !"VND".equals(pricing.getDirectUnitDiscount().getCurrency())
                    || !item.getUnitPrice().equals(pricing.getEffectivePrice().getAmount())
                    || pricing.getOriginalPrice().getAmount().signum()<0 || pricing.getDirectUnitDiscount().getAmount().signum()<0
                    || !pricing.getOriginalPrice().getAmount().subtract(pricing.getDirectUnitDiscount().getAmount()).equals(item.getUnitPrice()))
                throw new InvalidOrderRequestException();
            if (pricing.getSalePercentage()==null) {
                if (pricing.getDirectUnitDiscount().getAmount().signum()!=0 || pricing.getSaleStartsAt()!=null || pricing.getSaleEndsAt()!=null)
                    throw new InvalidOrderRequestException();
            } else if (pricing.getSalePercentage().signum()<=0 || pricing.getSalePercentage().compareTo(new java.math.BigDecimal("100"))>0
                    || pricing.getSalePercentage().stripTrailingZeros().scale()>2
                    || (pricing.getSaleStartsAt()!=null && pricing.getPricedAt().isBefore(pricing.getSaleStartsAt()))
                    || (pricing.getSaleEndsAt()!=null && !pricing.getPricedAt().isBefore(pricing.getSaleEndsAt()))) throw new InvalidOrderRequestException();
            sum = sum.add(item.getLineTotal());
        }
        if (!sum.equals(command.getMerchandiseTotal()) || command.getVoucherDiscount()==null || command.getPayableTotal()==null
                || command.getVoucherDiscount().signum()<0 || command.getVoucherDiscount().compareTo(sum)>0
                || !sum.subtract(command.getVoucherDiscount()).equals(command.getPayableTotal())
                || (command.getVoucherSnapshot()==null && command.getVoucherDiscount().signum()!=0)) throw new InvalidOrderRequestException();
        var snapshot=command.getVoucherSnapshot();
        if(snapshot!=null && (snapshot.getWalletEntitlementId()==null || snapshot.getWalletEntitlementId()<=0
                || snapshot.getVoucherId()==null || snapshot.getVoucherId()<=0 || blank(snapshot.getCode()) || blank(snapshot.getTitle())
                || snapshot.getCalculationBase()==null || !"VND".equals(snapshot.getCalculationBase().getCurrency())
                || !sum.equals(snapshot.getCalculationBase().getAmount()) || snapshot.getAppliedDiscount()==null
                || !"VND".equals(snapshot.getAppliedDiscount().getCurrency())
                || !command.getVoucherDiscount().equals(snapshot.getAppliedDiscount().getAmount())
                || !command.getPlacedAt().equals(snapshot.getRedeemedAt()))) throw new InvalidOrderRequestException();
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }

    public void validateRequest(Long owner, Long id, CancelOrderRequest request) {
        if (owner == null) throw new IdentityUnavailableException();
        if (id == null || id < 1 || request == null || !request.isReasonValid()) throw new InvalidOrderRequestException();
    }

    public void validateCompletionRequest(Long owner, Long id) {
        if (owner == null) throw new IdentityUnavailableException();
        if (owner < 1 || id == null || id < 1) throw new InvalidOrderRequestException();
    }

    public boolean canComplete(Order order, Long owner) {
        if (order == null || !owner.equals(order.getCustomerId())) throw new OrderNotFoundException();
        return switch (order.getStatus()) {
            case SHIPPING -> true;
            case COMPLETED -> false;
            case PENDING, CONFIRMED, CANCELLED, FAILED -> throw new OrderConflictException();
        };
    }

    /** True means first transition; false is an idempotent repeat with no Clock sampling. */
    public boolean canCancel(Order order, Long owner) {
        if (order == null || !owner.equals(order.getCustomerId())) throw new OrderNotFoundException();
        return switch (order.getStatus()) {
            case PENDING, CONFIRMED -> true;
            case CANCELLED -> false;
            case SHIPPING, COMPLETED, FAILED -> throw new OrderConflictException();
        };
    }

    public void validateTime(Order order, Instant at) {
        if (at.isBefore(order.getPlacedAt())) throw new OrderConflictException();
    }
}
