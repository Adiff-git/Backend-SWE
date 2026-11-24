package com.example.demo.inventory.repository;

import com.example.demo.inventory.dto.request.AdminInventoryCommand;
import com.example.demo.inventory.dto.response.AdminInventoryOperation;
import com.example.demo.inventory.dto.request.AdminInventoryCommand.OperationType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AdminInventoryOperationRepository {
    private final JdbcClient jdbc;

    public AdminInventoryOperation find(long actor, UUID operation) {
        return jdbc.sql("SELECT * FROM admin_inventory_operations WHERE admin_id = ? AND operation_id = ?")
                .param(actor).param(operation).query((rs, row) -> result(rs)).optional().orElse(null);
    }

    public boolean fence(long actor, UUID operation) {
        // The two-int keyspace is distinct from Checkout's one-bigint keyspace.
        return jdbc.sql("SELECT pg_try_advisory_xact_lock(?, ?)")
                .param(0x494E5654).param(31 * Long.hashCode(actor) + operation.hashCode())
                .query(Boolean.class).single();
    }

    public AdminInventoryOperation insert(long actor, AdminInventoryCommand command, long balance, long before, long after,
                            long reserved, Instant when) {
        return jdbc.sql("""
                INSERT INTO admin_inventory_operations
                    (admin_id, operation_id, inventory_balance_id, original_variant_id, operation_type,
                     delta, on_hand_before, on_hand_after, reserved_observed, reason, occurred_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT ON CONSTRAINT uk_admin_inventory_operations_actor_operation DO NOTHING RETURNING *
                """).param(actor).param(command.operationId()).param(balance).param(command.variantId())
                .param(command.type().name()).param(command.delta()).param(before).param(after)
                .param(reserved).param(command.reason()).param(when.atOffset(ZoneOffset.UTC))
                .query((rs, row) -> result(rs)).optional().orElse(null);
    }

    static AdminInventoryOperation result(ResultSet rs) throws SQLException {
        if (rs.getInt("response_version") != 1) throw new IllegalStateException("Unsupported inventory response version");
        long after = rs.getLong("on_hand_after");
        long reserved = rs.getLong("reserved_observed");
        return new AdminInventoryOperation(rs.getObject("operation_id", UUID.class), rs.getObject("original_variant_id", UUID.class),
                Long.toString(rs.getLong("admin_id")), OperationType.valueOf(rs.getString("operation_type")),
                Long.toString(rs.getLong("delta")), Long.toString(rs.getLong("on_hand_before")),
                Long.toString(after), Long.toString(reserved), Long.toString(after - reserved),
                rs.getString("reason"), rs.getObject("occurred_at", OffsetDateTime.class).toInstant());
    }
}
