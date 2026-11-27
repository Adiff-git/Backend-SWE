package com.example.demo.inventory.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AdminInventoryMigrationIT {
    @ParameterizedTest
    @ValueSource(ints = {0, 27, 31})
    void cleanAndStagedUpgradePreserveStockAndEnforceJournal(int baseline) throws Exception {
        String url = "jdbc:tc:postgresql:17-alpine:///inventory_" + UUID.randomUUID().toString().replace("-", "");
        UUID category = UUID.randomUUID(), product = UUID.randomUUID(), variant = UUID.randomUUID();
        try (var connection = DriverManager.getConnection(url, "test", "test"); var db = connection.createStatement()) {
            if (baseline != 0) flyway(url, baseline).migrate();
            if (baseline == 0) flyway(url, 32).migrate();
            connection.setAutoCommit(false);
            db.execute("INSERT INTO categories(id,name) VALUES ('" + category + "','Test category')");
            db.execute("INSERT INTO products(id,category_id,name,status) VALUES ('" + product + "','" + category + "','Test product','DRAFT')");
            db.execute("INSERT INTO product_variants(id,product_id,sku,option_combination," + (baseline == 27 ? "selling_price" : "base_price") + ") VALUES ('"
                    + variant + "','" + product + "','INV','{}',100)");
            db.execute("UPDATE inventory_balances SET stock=7");
            connection.commit();
            connection.setAutoCommit(true);
            if (baseline == 27) flyway(url, 31).migrate();
            String before = scalar(db, "SELECT string_agg(version||':'||checksum,',' ORDER BY installed_rank) FROM flyway_schema_history WHERE version::int <= 31");
            if (baseline != 0) flyway(url, 32).migrate();
            flyway(url, 32).validate();
            assertThat(scalar(db, "SELECT string_agg(version||':'||checksum,',' ORDER BY installed_rank) FROM flyway_schema_history WHERE version::int <= 31")).isEqualTo(before);
            assertThat(scalar(db, "SELECT stock||':'||reserved FROM inventory_balances")).isEqualTo("7:0");
            assertThat(scalar(db, "SELECT count(*) FROM admin_inventory_operations")).isEqualTo("0");
            db.execute("""
                    INSERT INTO admin_inventory_operations
                        (admin_id,operation_id,inventory_balance_id,original_variant_id,operation_type,
                         delta,on_hand_before,on_hand_after,reserved_observed,occurred_at)
                    SELECT 1,gen_random_uuid(),id,original_variant_id,'REPLENISHMENT',1,6,7,0,now()
                    FROM inventory_balances
                    """);
            for (String sql : new String[] {"UPDATE admin_inventory_operations SET admin_id=2",
                    "DELETE FROM admin_inventory_operations", "TRUNCATE admin_inventory_operations"}) {
                assertThatThrownBy(() -> db.execute(sql)).isInstanceOfSatisfying(SQLException.class,
                        e -> assertThat(e.getSQLState()).isEqualTo("23514"));
            }
            db.execute("CREATE ROLE inventory_test_runtime");
            db.execute("GRANT SELECT, TRUNCATE ON admin_inventory_operations TO inventory_test_runtime");
            db.execute("SET ROLE inventory_test_runtime");
            try {
                assertThatThrownBy(() -> db.execute("TRUNCATE admin_inventory_operations"))
                        .isInstanceOfSatisfying(SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo("23514"));
            } finally {
                db.execute("RESET ROLE");
            }
            db.execute("DELETE FROM product_variants WHERE id='" + variant + "'");
            assertThat(scalar(db, "SELECT original_variant_id FROM admin_inventory_operations")).isEqualTo(variant.toString());
            db.execute("UPDATE inventory_balances SET stock=0");
            assertThatThrownBy(() -> db.execute("DELETE FROM inventory_balances")).isInstanceOfSatisfying(SQLException.class,
                    e -> assertThat(e.getSQLState()).isEqualTo("23503"));
        }
    }

    private Flyway flyway(String url, int target) {
        return Flyway.configure().dataSource(url, "test", "test").target(Integer.toString(target)).load();
    }

    private String scalar(java.sql.Statement statement, String query) throws SQLException {
        try (var rs = statement.executeQuery(query)) {
            rs.next();
            return rs.getString(1);
        }
    }
}
