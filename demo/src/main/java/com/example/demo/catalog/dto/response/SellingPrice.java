package com.example.demo.catalog.dto.response;

import java.math.BigInteger;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public final class SellingPrice {
	private final BigInteger amount;
	private final String currency;
}
