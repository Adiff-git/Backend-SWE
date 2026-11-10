package com.example.demo.catalog.dto.response;

import java.util.UUID;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public final class BrandSummary {
    private final UUID id;
    private final String code;
    private final String name;
}
