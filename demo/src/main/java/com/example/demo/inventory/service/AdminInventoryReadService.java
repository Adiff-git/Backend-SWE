package com.example.demo.inventory.service;

import com.example.demo.catalog.entity.ProductStatus;
import com.example.demo.inventory.dto.response.AdminInventoryOperation;
import com.example.demo.inventory.dto.response.AdminInventoryPage;
import com.example.demo.inventory.dto.response.AdminInventoryVariant;
import com.example.demo.inventory.repository.AdminInventoryReadRepository;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminInventoryReadService {
    private final AdminInventoryReadRepository reads;

    public AdminInventoryPage<AdminInventoryVariant> list(String query, Set<ProductStatus> statuses, int page, int size) {
        return reads.list(query, statuses, page, size);
    }

    public AdminInventoryVariant detail(UUID variant) {
        return reads.detail(variant);
    }

    public AdminInventoryPage<AdminInventoryOperation> history(UUID variant, int page, int size) {
        return reads.history(variant, page, size);
    }
}
