package com.example.demo.auth.integration;
import javax.sql.DataSource;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import static org.assertj.core.api.Assertions.*;
class LegacyMigrationIT extends AuthPostgresTestSupport {
    @Autowired DataSource datasource;
    @Test void migrate_shouldBackfillUserRoleWithoutChangingLegacyFields() {
        String schema="upgrade_"+UUID.randomUUID().toString().replace("-","");
        try {
            Flyway.configure().dataSource(datasource).schemas(schema).defaultSchema(schema).target("2").load().migrate();
            jdbc.update("INSERT INTO "+schema+".users(username,password,account_status,joined_at) VALUES ('Alice','existing-encoded-value','ACTIVE',CURRENT_TIMESTAMP)");
            Flyway.configure().dataSource(datasource).schemas(schema).defaultSchema(schema).load().migrate();
            assertThat(jdbc.queryForObject("SELECT password FROM "+schema+".users",String.class)).isEqualTo("existing-encoded-value");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM "+schema+".user_roles",Long.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM "+schema+".permissions",Long.class)).isEqualTo(2);
        }
        finally {
            jdbc.execute("DROP SCHEMA IF EXISTS "+schema+" CASCADE");
        }
    }
    @Test void migrate_shouldFailOnLegacyAtSignWithoutRenamingOrDeleting() {
        String schema="upgrade_"+UUID.randomUUID().toString().replace("-","");
        try {
            Flyway.configure().dataSource(datasource).schemas(schema).defaultSchema(schema).target("2").load().migrate();
            jdbc.update("INSERT INTO "+schema+".users(username,password,account_status,joined_at) VALUES ('bad@name','existing-encoded-value','ACTIVE',CURRENT_TIMESTAMP)");
            assertThatThrownBy(()->Flyway.configure().dataSource(datasource).schemas(schema).defaultSchema(schema).load().migrate()).isInstanceOf(org.flywaydb.core.api.FlywayException.class);
            assertThat(jdbc.queryForObject("SELECT username FROM "+schema+".users",String.class)).isEqualTo("bad@name");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM "+schema+".users",Long.class)).isEqualTo(1);
        }
        finally {
            jdbc.execute("DROP SCHEMA IF EXISTS "+schema+" CASCADE");
        }
    }
}
