package com.example.demo.voucher.dto.response;
import java.math.BigInteger;
import lombok.Value;
@Value
public class VoucherPreview { VoucherSummary voucher; BigInteger discount; String reason; }
