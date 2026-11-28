package com.example.demo.catalog.dto.response;

import java.util.UUID;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public final class BrandListItem {
    private final UUID id;
    private final String code;
    private final String name;
    private final long productCount;
}
