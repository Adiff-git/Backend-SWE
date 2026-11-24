package com.example.demo.inventory.repository;

import com.example.demo.inventory.dto.response.AdminInventoryOperation;
import com.example.demo.inventory.dto.response.AdminInventoryPage;
import com.example.demo.inventory.dto.response.AdminInventoryVariant;
import com.example.demo.inventory.converter.AdminInventoryStockStatusConverter;
import com.example.demo.catalog.entity.ProductStatus;
import com.example.demo.inventory.exception.AdminInventoryException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AdminInventoryReadRepository {
    private static final String VARIANTS = """
            SELECT v.id AS variant_id, p.id AS product_id, p.name AS product_name, v.sku, p.status, v.enabled,
                   (p.status = 'PUBLISHED' AND v.enabled AND v.selling_price > 0) AS sale_eligible,
                   b.stock, b.reserved
            FROM product_variants v JOIN products p ON p.id = v.product_id
            LEFT JOIN inventory_balances b ON b.product_variant_id = v.id
            """;
    private final JdbcClient jdbc;

    public AdminInventoryPage<AdminInventoryVariant> list(String query, Set<ProductStatus> statuses, int page, int size) {
        String pattern = query == null ? null : "%" + query.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        List<String> states = statuses.stream().map(Enum::name).toList();
        return jdbc.sql("""
                WITH filtered AS (
                """ + VARIANTS + """
                WHERE (CAST(:pattern AS TEXT) IS NULL OR p.name ILIKE :pattern ESCAPE '!' OR v.sku ILIKE :pattern ESCAPE '!')
                """ + (states.isEmpty() ? "" : " AND p.status IN (:statuses)") + """
                ), paged AS (
                    SELECT * FROM filtered ORDER BY product_name, variant_id LIMIT :size OFFSET :offset
                )
                SELECT totals.total, paged.* FROM (SELECT COUNT(*) AS total FROM filtered) totals
                LEFT JOIN paged ON true ORDER BY paged.product_name, paged.variant_id
                """).params(states.isEmpty() ? java.util.Map.of() : java.util.Map.of("statuses", states))
                .param("pattern", pattern).param("size", size).param("offset", (long) page * size)
                .query(rs -> {
                    rs.next();
                    long total = rs.getLong("total");
                    List<AdminInventoryVariant> items = new ArrayList<>();
                    do {
                        if (rs.getObject("variant_id") != null) items.add(variant(rs));
                    } while (rs.next());
                    return AdminInventoryPage.of(items, page, size, total);
                });
    }

    public AdminInventoryVariant detail(UUID id) {
        return jdbc.sql(VARIANTS + " WHERE v.id = ?").param(id)
                .query((rs, row) -> variant(rs)).optional().orElseThrow(this::notFound);
    }

    public AdminInventoryPage<AdminInventoryOperation> history(UUID id, int page, int size) {
        return jdbc.sql("""
                WITH filtered AS (
                    SELECT * FROM admin_inventory_operations WHERE original_variant_id = :variant
                ), paged AS (
                    SELECT * FROM filtered ORDER BY occurred_at DESC, id DESC LIMIT :size OFFSET :offset
                )
                SELECT totals.total, EXISTS(SELECT 1 FROM product_variants WHERE id = :variant) AS live, paged.*
                FROM (SELECT COUNT(*) AS total FROM filtered) totals
                LEFT JOIN paged ON true ORDER BY paged.occurred_at DESC, paged.id DESC
                """).param("variant", id).param("size", size).param("offset", (long) page * size)
                .query(rs -> {
                    rs.next();
                    long total = rs.getLong("total");
                    if (total == 0 && !rs.getBoolean("live")) throw notFound();
                    List<AdminInventoryOperation> items = new ArrayList<>();
                    do {
                        if (rs.getObject("id") != null) items.add(AdminInventoryOperationRepository.result(rs));
                    } while (rs.next());
                    return AdminInventoryPage.of(items, page, size, total);
                });
    }

    private AdminInventoryVariant variant(ResultSet rs) throws SQLException {
        Long onHand = rs.getObject("stock", Long.class);
        Long reserved = rs.getObject("reserved", Long.class);
        Long available = onHand == null ? null : onHand - reserved;
        return new AdminInventoryVariant(rs.getObject("variant_id", UUID.class), rs.getObject("product_id", UUID.class),
                rs.getString("product_name"), rs.getString("sku"), rs.getString("status"), rs.getBoolean("enabled"),
                rs.getBoolean("sale_eligible"), text(onHand), text(reserved), text(available), AdminInventoryStockStatusConverter.fromAvailable(available));
    }

    private String text(Long value) { return value == null ? null : value.toString(); }

    private AdminInventoryException notFound() {
        return new AdminInventoryException(404, "NOT_FOUND", "Inventory variant not found");
    }
}
