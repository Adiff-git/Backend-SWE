package com.example.demo.catalog.service;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CatalogInventoryTargetService {
    private final JdbcClient jdbc;

    public enum Target { WRITABLE, ARCHIVED, MISSING, CHANGED }

    @Transactional(propagation = Propagation.MANDATORY)
    public Target lock(UUID variant) {
        UUID product = jdbc.sql("SELECT product_id FROM product_variants WHERE id = ?")
                .param(variant).query(UUID.class).optional().orElse(null);
        if (product == null) return Target.MISSING;
        String status = jdbc.sql("SELECT status FROM products WHERE id = ? FOR SHARE")
                .param(product).query(String.class).optional().orElse(null);
        if (status == null) return Target.CHANGED;
        UUID lockedParent = jdbc.sql("SELECT product_id FROM product_variants WHERE id = ? FOR SHARE")
                .param(variant).query(UUID.class).optional().orElse(null);
        if (!product.equals(lockedParent)) return Target.CHANGED;
        return "ARCHIVED".equals(status) ? Target.ARCHIVED : Target.WRITABLE;
    }
}
