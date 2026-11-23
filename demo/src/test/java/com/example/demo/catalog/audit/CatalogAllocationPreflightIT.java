package com.example.demo.catalog.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static com.example.demo.catalog.audit.V27AuditDatabase.execute;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Corrupt historical snapshots use TEMP copies; production constraints/triggers remain enabled. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CatalogAllocationPreflightIT {
    Path directory;
    private final V27AuditDatabase database=new V27AuditDatabase();
    private Connection connection;
    private final UUID variant=UUID.randomUUID(),other=UUID.randomUUID();
    @BeforeAll void start(@TempDir Path directory) throws Exception { this.directory=directory;database.start(directory); }
    @AfterAll void stop() throws Exception { if(connection!=null)connection.close();database.close(); }
    @BeforeEach void snapshot() throws Exception {
        if(connection!=null)connection.close();connection=database.owner();
        for(String table:List.of("orders","order_items","inventory_balances","inventory_reservations","product_variants"))
            execute(connection,"CREATE TEMP TABLE "+table+" AS SELECT * FROM public."+table+" WITH NO DATA");
        execute(connection,"INSERT INTO product_variants(id) VALUES (?)",variant);
        execute(connection,"INSERT INTO orders(id,status,placed_at,inventory_managed) VALUES (1,'PENDING','2026-01-01T00:00:00Z',TRUE)");
        execute(connection,"INSERT INTO order_items(id,order_id,product_variant_id,quantity) VALUES (1,1,?,1)",variant);
        execute(connection,"INSERT INTO inventory_balances(id,original_variant_id,product_variant_id,stock,reserved) VALUES (1,?,?,5,1)",variant,variant);
        execute(connection,"INSERT INTO inventory_reservations(id,order_id,order_item_id,inventory_balance_id,original_variant_id,quantity,state,reserved_at) VALUES (1,1,1,1,?,1,'ACTIVE','2026-01-01T00:00:00Z')",variant);
    }
    private List<List<Map<String,String>>> run() throws Exception {
        return V27AuditDatabase.script(connection,Path.of("../specs/013-admin-catalog-management/contracts/data-preflight-allocation-integrity.sql"));
    }
    private Set<String> reasons() throws Exception {
        return run().stream().skip(1).flatMap(List::stream).map(row->row.get("reason")).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
    }
    @ParameterizedTest @ValueSource(strings={"ACTIVE","RELEASED","CONSUMED","LEGACY"})
    void preflight_shouldAcceptValidActiveTerminalAndUnmanagedHistory_withPermittedNullReferences(String state) throws Exception {
        if(state.equals("LEGACY")) {
            execute(connection,"DELETE FROM inventory_reservations");execute(connection,"UPDATE orders SET inventory_managed=FALSE");execute(connection,"UPDATE inventory_balances SET reserved=0");
        } else if(!state.equals("ACTIVE")) {
            String status=state.equals("RELEASED")?"CANCELLED":"COMPLETED";
            execute(connection,"UPDATE orders SET status=?,cancelled_at=CASE WHEN ?='CANCELLED' THEN TIMESTAMPTZ '2026-01-02' END,completed_at=CASE WHEN ?='COMPLETED' THEN TIMESTAMPTZ '2026-01-02' END",status,status,status);
            execute(connection,"UPDATE inventory_reservations SET state=?,resolved_at='2026-01-02'",state);
            execute(connection,"UPDATE inventory_balances SET reserved=0,product_variant_id=NULL");execute(connection,"UPDATE order_items SET product_variant_id=NULL");execute(connection,"DELETE FROM product_variants");
        }
        var sets=run();assertThat(sets.getFirst().getFirst()).containsEntry("transaction_read_only","on");
        assertThat(sets.subList(1,sets.size())).allSatisfy(rows->assertThat(rows).isEmpty());
    }
    @ParameterizedTest @ValueSource(strings={"MANAGED_ORDER_WITHOUT_ITEMS","MANAGED_ITEM_WITHOUT_ALLOCATION","LEGACY_ORDER_HAS_ALLOCATION","ALLOCATION_ORDER_OR_ITEM_MISMATCH","ALLOCATION_QUANTITY_MISMATCH","ALLOCATION_LIFECYCLE_MISMATCH","ALLOCATION_BALANCE_ATTRIBUTION_MISMATCH","ALLOCATION_ITEM_VARIANT_MISMATCH","ACTIVE_ALLOCATION_WITHOUT_LIVE_VARIANT","DUPLICATE_ITEM_ALLOCATION","DUPLICATE_ORDER_VARIANT_ALLOCATION"})
    void preflight_shouldDetectEveryNamedAllocationViolation_withoutDisablingProductionGuards(String reason) throws Exception {
        switch(reason) {
            case "MANAGED_ORDER_WITHOUT_ITEMS" -> execute(connection,"DELETE FROM order_items");
            case "MANAGED_ITEM_WITHOUT_ALLOCATION" -> {execute(connection,"DELETE FROM inventory_reservations");execute(connection,"UPDATE inventory_balances SET reserved=0");}
            case "LEGACY_ORDER_HAS_ALLOCATION" -> execute(connection,"UPDATE orders SET inventory_managed=FALSE");
            case "ALLOCATION_ORDER_OR_ITEM_MISMATCH" -> execute(connection,"UPDATE inventory_reservations SET order_id=99");
            case "ALLOCATION_QUANTITY_MISMATCH" -> execute(connection,"UPDATE inventory_reservations SET quantity=1000");
            case "ALLOCATION_LIFECYCLE_MISMATCH" -> execute(connection,"UPDATE inventory_reservations SET reserved_at='2026-01-02'");
            case "ALLOCATION_BALANCE_ATTRIBUTION_MISMATCH" -> execute(connection,"UPDATE inventory_reservations SET original_variant_id=?",other);
            case "ALLOCATION_ITEM_VARIANT_MISMATCH" -> execute(connection,"UPDATE order_items SET product_variant_id=?",other);
            case "ACTIVE_ALLOCATION_WITHOUT_LIVE_VARIANT" -> execute(connection,"DELETE FROM product_variants");
            default -> execute(connection,"INSERT INTO inventory_reservations SELECT 2,order_id,order_item_id,inventory_balance_id,original_variant_id,quantity,state,reserved_at,resolved_at FROM inventory_reservations");
        }
        assertThat(reasons()).contains(reason);
    }
    @ParameterizedTest @ValueSource(strings={"state","resolved","early","terminal-time","quantity"})
    void preflight_shouldDetectLifecycleTimeAndCardinalityEdges(String defect) throws Exception {
        switch(defect) {
            case "state" -> execute(connection,"UPDATE inventory_reservations SET state='UNKNOWN'");
            case "resolved" -> execute(connection,"UPDATE inventory_reservations SET resolved_at='2026-01-02'");
            case "early" -> execute(connection,"UPDATE inventory_reservations SET state='CONSUMED',resolved_at='2025-01-01'");
            case "terminal-time" -> execute(connection,"UPDATE orders SET status='COMPLETED',completed_at=NULL");
            default -> execute(connection,"UPDATE inventory_reservations SET quantity=0");
        }
        assertThat(reasons()).contains(defect.equals("quantity")?"ALLOCATION_QUANTITY_MISMATCH":"ALLOCATION_LIFECYCLE_MISMATCH");
    }
    @ParameterizedTest @ValueSource(strings={"reserved","negative","attribution","duplicate-original","duplicate-live"})
    void preflight_shouldDetectEveryBalanceIntegrityResultSet(String defect) throws Exception {
        int resultIndex=4;
        switch(defect) {
            case "reserved" -> execute(connection,"UPDATE inventory_balances SET reserved=0");
            case "negative" -> execute(connection,"UPDATE inventory_balances SET stock=-1");
            case "attribution" -> execute(connection,"UPDATE inventory_balances SET product_variant_id=?",other);
            case "duplicate-original" -> {execute(connection,"INSERT INTO inventory_balances(id,original_variant_id,stock,reserved) VALUES (2,?,0,0)",variant);resultIndex=5;}
            case "duplicate-live" -> {execute(connection,"INSERT INTO inventory_balances(id,original_variant_id,product_variant_id,stock,reserved) VALUES (2,?,?,0,0)",other,variant);resultIndex=6;}
            default -> throw new AssertionError();
        }
        assertThat(run().get(resultIndex)).isNotEmpty();
    }
    @Test void snapshotFixtures_shouldLeaveRealV27TablesAndTriggersUnchanged() throws Exception {
        try(Connection owner=database.owner()) {
            String before=V27AuditDatabase.fingerprint(owner);
            execute(connection,"DELETE FROM inventory_reservations");assertThat(reasons()).contains("MANAGED_ITEM_WITHOUT_ALLOCATION");
            assertThat(V27AuditDatabase.fingerprint(owner)).isEqualTo(before);
            assertThat(V27AuditDatabase.scalar(owner,"SELECT COUNT(*) FROM pg_trigger WHERE tgname='ct_inventory_reservations_immutable' AND tgenabled='O'")).isEqualTo("1");
        }
    }
}
