package com.example.demo.cart.dto.request;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
@Data
public class ApplyCartVoucherRequest { @NotNull @Positive private Long walletEntitlementId; }
