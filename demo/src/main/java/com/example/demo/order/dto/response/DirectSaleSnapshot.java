package com.example.demo.order.dto.response;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Value;
@Value
public class DirectSaleSnapshot { BigDecimal percentage; Instant startsAt; Instant endsAt; }
