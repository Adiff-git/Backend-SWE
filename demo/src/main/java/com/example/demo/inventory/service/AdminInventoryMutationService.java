package com.example.demo.inventory.service;

import com.example.demo.inventory.dto.request.AdminInventoryCommand;
import com.example.demo.inventory.dto.response.AdminInventoryOperation;
import com.example.demo.catalog.service.CatalogInventoryTargetService;
import com.example.demo.inventory.exception.AdminInventoryException;
import com.example.demo.inventory.repository.AdminInventoryOperationRepository;
import com.example.demo.inventory.repository.InventoryBalanceRepository;
import com.example.demo.inventory.validator.AdminInventoryValidator;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.sql.SQLException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AdminInventoryMutationService {
    private final JdbcClient jdbc;
    private final InventoryBalanceRepository balances;
    private final AdminInventoryOperationRepository journal;
    private final CatalogInventoryTargetService catalog;
    private final Clock clock;
    private final TransactionTemplate transactions;
    private final String lockTimeout;

    public AdminInventoryMutationService(JdbcClient jdbc, InventoryBalanceRepository balances,
            AdminInventoryOperationRepository journal, CatalogInventoryTargetService catalog, Clock clock,
            PlatformTransactionManager manager, @Value("${checkout.lock-timeout-ms:5000}") long timeout) {
        this.jdbc = jdbc;
        this.balances = balances;
        this.journal = journal;
        this.catalog = catalog;
        this.clock = clock;
        this.transactions = new TransactionTemplate(manager);
        transactions.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.lockTimeout = timeout + "ms";
    }

    public AdminInventoryOperation execute(long actor, AdminInventoryCommand command) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Admin inventory requires its own outer transaction");
        }
        try {
            return transactions.execute(status -> mutate(actor, command));
        } catch (JournalCollision collision) {
            AdminInventoryOperation committed = transactions.execute(status -> journal.find(actor, command.operationId()));
            if (committed != null) return replay(committed, command);
            throw concurrent();
        } catch (RuntimeException failure) {
            for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
                if (cause instanceof SQLException postgres) {
                    if ("55P03".equals(postgres.getSQLState()) || "40P01".equals(postgres.getSQLState())) {
                        throw concurrent();
                    }
                }
            }
            throw failure;
        }
    }

    private AdminInventoryOperation mutate(long actor, AdminInventoryCommand command) {
        AdminInventoryOperation existing = journal.find(actor, command.operationId());
        if (existing != null) return replay(existing, command);
        boolean acquired = journal.fence(actor, command.operationId());
        existing = journal.find(actor, command.operationId());
        if (existing != null) return replay(existing, command);
        if (!acquired) throw concurrent();
        jdbc.sql("SELECT set_config('lock_timeout', ?, true)").param(lockTimeout).query(String.class).single();
        switch (catalog.lock(command.variantId())) {
            case MISSING -> throw new AdminInventoryException(404, "NOT_FOUND", "Inventory variant not found");
            case CHANGED -> throw concurrent();
            case ARCHIVED -> throw new AdminInventoryException(409, "ARCHIVED", "Archived inventory is read-only");
            case WRITABLE -> { }
        }
        var rows = balances.lockLiveBalances(List.of(command.variantId()));
        if (rows.isEmpty()) throw new AdminInventoryException(409, "BALANCE_UNAVAILABLE", "Inventory balance unavailable");
        var balance = rows.getFirst();
        long before = balance.getStock();
        long reserved = balance.getReserved();
        if (reserved < 0 || before < reserved) throw new IllegalStateException("Invalid inventory counters");
        if (command.delta() < reserved - before) {
            throw new AdminInventoryException(409, "RESERVATION_FLOOR", "Adjustment would fall below reserved stock",
                    Map.of("onHand", Long.toString(before), "reserved", Long.toString(reserved),
                            "available", Long.toString(before - reserved)));
        }
        if (command.delta() > Long.MAX_VALUE - before) throw AdminInventoryValidator.invalidQuantity();
        long after = before + command.delta();
        int updated = jdbc.sql("UPDATE inventory_balances SET stock = ? WHERE id = ? AND stock = ? AND reserved = ?")
                .param(after).param(balance.getId()).param(before).param(reserved).update();
        if (updated != 1) throw concurrent();
        AdminInventoryOperation operation = journal.insert(actor, command, balance.getId(), before, after, reserved,
                clock.instant().truncatedTo(ChronoUnit.MICROS));
        if (operation == null) throw new JournalCollision();
        return operation;
    }

    private static final class JournalCollision extends RuntimeException {}

    private AdminInventoryOperation replay(AdminInventoryOperation existing, AdminInventoryCommand command) {
        if (!existing.variantId().equals(command.variantId()) || existing.operationType() != command.type()
                || !existing.delta().equals(Long.toString(command.delta()))
                || !Objects.equals(existing.reason(), command.reason())) {
            throw new AdminInventoryException(409, "OPERATION_CONFLICT", "Operation already used with different input");
        }
        return existing;
    }

    private AdminInventoryException concurrent() {
        return new AdminInventoryException(409, "CONCURRENT_CONFLICT", "Concurrent inventory operation; retry");
    }
}
