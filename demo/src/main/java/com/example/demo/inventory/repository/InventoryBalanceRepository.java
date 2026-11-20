package com.example.demo.inventory.repository;

import com.example.demo.inventory.entity.InventoryBalance;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

public interface InventoryBalanceRepository extends Repository<InventoryBalance, Long>, InventoryMutationRepository {
    interface BalanceRow {
        Long getId();
        UUID getOriginalVariantId();
        long getStock();
        long getReserved();
    }

    @Query(value = "SELECT id,original_variant_id AS originalVariantId,stock,reserved FROM inventory_balances WHERE product_variant_id IN (:ids) ORDER BY original_variant_id", nativeQuery = true)
    List<BalanceRow> findBalances(Collection<UUID> ids);

    @Query(value = "SELECT id,original_variant_id AS originalVariantId,stock,reserved FROM inventory_balances WHERE product_variant_id IN (:ids) ORDER BY original_variant_id FOR UPDATE", nativeQuery = true)
    List<BalanceRow> lockLiveBalances(Collection<UUID> ids);

    @Query(value = "SELECT id,original_variant_id AS originalVariantId,stock,reserved FROM inventory_balances WHERE original_variant_id IN (:ids) ORDER BY original_variant_id FOR UPDATE", nativeQuery = true)
    List<BalanceRow> lockBalances(Collection<UUID> ids);
}
