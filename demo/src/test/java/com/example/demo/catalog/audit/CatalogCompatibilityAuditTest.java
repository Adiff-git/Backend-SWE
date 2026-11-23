package com.example.demo.catalog.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.CRC32;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** JDBC protocol tests only; real PostgreSQL safety/parity belongs to T086. */
class CatalogCompatibilityAuditTest {
    @TempDir Path temporary;
    private final List<String> sql = new ArrayList<>();
    private final List<Map<String, Object>> variants = new ArrayList<>();
    private final List<Map<String, Object>> images = new ArrayList<>();
    private final StringWriter output = new StringWriter();
    private final Map<String, String> environment = new HashMap<>();
    private Connection connection;

    @Test
    void run_shouldValidateFactsAndRollback_withDatabaseReadOnlyBeforeAnyDataQuery() throws Exception {
        setup();
        variants.add(variant("{\"schema_version\":1,\"values\":{\"physical_ram\":{\"value\":0,\"unit\":\"GB\"},\"nfc_supported\":false}}", "laptop"));
        assertThat(run()).isZero();
        assertThat(output.toString()).contains("\"status\":\"COMPLETE\"", "\"variants\":1");
        verify(connection).setReadOnly(true);
        verify(connection).setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
        verify(connection).setAutoCommit(false);
        verify(connection).rollback();
        verify(connection, never()).commit();
        org.mockito.InOrder boundary = org.mockito.Mockito.inOrder(connection);
        boundary.verify(connection).setReadOnly(true);
        boundary.verify(connection).setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
        boundary.verify(connection).setAutoCommit(false);
        boundary.verify(connection).prepareStatement("SET TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY");
        boundary.verify(connection).prepareStatement(CatalogCompatibilityAudit.IDENTITY_SQL);
        boundary.verify(connection).prepareStatement(CatalogCompatibilityAudit.HISTORY_SQL);
        boundary.verify(connection).prepareStatement(CatalogCompatibilityAudit.VARIANTS_SQL);
        boundary.verify(connection).rollback();
        boundary.verify(connection).close();
        assertThat(sql.getFirst()).isEqualTo("SET TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY");
        assertThat(sql.indexOf(CatalogCompatibilityAudit.IDENTITY_SQL))
                .isLessThan(sql.indexOf(CatalogCompatibilityAudit.VARIANTS_SQL));
        assertThat(sql).allMatch(query -> query.startsWith("SET ") || query.startsWith("SELECT ")
                || query.startsWith("WITH RECURSIVE "));
        assertThat(String.join("\n", sql)).doesNotContain("catalog_taxonomy_state", "last_restored_at", "v.enabled", "i.active");
    }

    @Test
    void run_shouldReportWrongTypesUnitsAndMissingDocument_withoutLeakingFacts() throws Exception {
        setup();
        variants.add(variant("{\"schema_version\":1,\"values\":{\"physical_ram\":\"private-fact\"}}", "laptop"));
        variants.add(variant("{\"schema_version\":1,\"values\":{\"physical_ram\":{\"value\":8,\"unit\":\"MB\"}}}", "laptop"));
        variants.add(variant(null, "laptop"));
        assertThat(run()).isEqualTo(2);
        assertThat(output.toString()).contains("SPECIFICATION_INVALID", "SPECIFICATION_MISSING", "\"status\":\"INVALID\"")
                .doesNotContain("private-fact", "private-password", "jdbc:");
        verify(connection).rollback();
    }

    @Test
    void run_shouldKeepEmptyAndNonapplicableFacts_withoutRequiringUniversalFacts() throws Exception {
        setup();
        variants.add(variant("{\"schema_version\":1,\"values\":{}}", "laptop"));
        variants.add(variant("{\"schema_version\":1,\"values\":{\"physical_ram\":\"generic\"}}", "tablet"));
        variants.add(variant("{\"schema_version\":1,\"values\":{\"physical_ram\":false}}", ""));
        assertThat(run()).isZero();
    }

    @Test
    void run_shouldRejectWrongIdentityAndHistory_beforeReadingVariants() throws Exception {
        setup();
        environment.put("AUDIT_EXPECTED_DATABASE", "different");
        assertThat(run()).isEqualTo(3);
        assertThat(output.toString()).contains("TARGET_OR_READ_ONLY_MISMATCH", "INCOMPLETE");
        assertThat(sql).doesNotContain(CatalogCompatibilityAudit.VARIANTS_SQL);
        verify(connection).rollback();
    }

    @Test
    void run_shouldRejectUnexpectedFutureMigration_evenWithMatchingCatalogColumns() throws Exception {
        setup();
        List<Map<String, Object>> history = history();
        history.add(Map.of("version", "28", "checksum", 1, "script", "V28__future.sql", "success", true, "type", "SQL"));
        PreparedStatement futureHistory = statement(CatalogCompatibilityAudit.HISTORY_SQL, history);
        when(connection.prepareStatement(CatalogCompatibilityAudit.HISTORY_SQL)).thenReturn(futureHistory);
        assertThat(run()).isEqualTo(3);
        assertThat(output.toString()).contains("SCHEMA_HISTORY_MISMATCH");
        assertThat(sql).doesNotContain(CatalogCompatibilityAudit.VARIANTS_SQL);
    }

    @Test
    void run_shouldFailClosed_whenSchemaProbeFails_withoutPrintingDriverMessage() throws Exception {
        setup();
        when(connection.prepareStatement(CatalogCompatibilityAudit.VARIANTS_SQL)).thenThrow(new SQLException("private-password jdbc:secret", "42703"));
        assertThat(run()).isEqualTo(3);
        assertThat(output.toString()).contains("DATABASE_FAILURE", "INCOMPLETE").doesNotContain("private-password", "jdbc:secret");
        verify(connection).rollback();
    }

    @Test
    void run_shouldReportPartialResultsAsIncomplete_whenRecordBudgetIsExceeded() throws Exception {
        setup();
        environment.put("AUDIT_MAX_RECORDS", "1");
        variants.add(variant(null, "laptop"));
        variants.add(variant("{\"schema_version\":1,\"values\":{}}", "laptop"));
        assertThat(run()).isEqualTo(3);
        assertThat(output.toString()).contains("SPECIFICATION_MISSING", "RECORD_LIMIT", "INCOMPLETE")
                .doesNotContain("\"status\":\"COMPLETE\"", "\"status\":\"INVALID\"");
    }

    @Test
    void run_shouldNeverReportComplete_whenRollbackFails() throws Exception {
        setup();
        org.mockito.Mockito.doThrow(new SQLException("secret")).when(connection).rollback();
        assertThat(run()).isEqualTo(3);
        assertThat(output.toString()).contains("INCOMPLETE").doesNotContain("\"status\":\"COMPLETE\"", "secret");
    }

    @Test
    void run_shouldRejectNonReadOnlyServerConfirmation_beforeHistoryOrCatalogReads() throws Exception {
        setup();
        Map<String, Object> identity = identity();
        identity.put("read_only", "off");
        PreparedStatement readWriteIdentity = statement(CatalogCompatibilityAudit.IDENTITY_SQL, List.of(identity));
        when(connection.prepareStatement(CatalogCompatibilityAudit.IDENTITY_SQL)).thenReturn(readWriteIdentity);
        assertThat(run()).isEqualTo(3);
        assertThat(sql).doesNotContain(CatalogCompatibilityAudit.HISTORY_SQL, CatalogCompatibilityAudit.VARIANTS_SQL);
    }

    @Test
    void execute_shouldRejectUnknownModesAndMissingConfiguration_withoutConnectingOrBootstrap() {
        assertThat(CatalogCompatibilityAudit.execute(new String[] {"--repair"}, Map.of(), new PrintWriter(output, true))).isEqualTo(3);
        assertThat(CatalogCompatibilityAudit.execute(new String[0], Map.of(), new PrintWriter(output, true))).isEqualTo(3);
        assertThat(output.toString()).contains("CONFIGURATION_INVALID").doesNotContain("Exception", "password");
    }

    @Test
    void run_shouldFailSafelyOnConnectionFailure() throws Exception {
        setup();
        int code = CatalogCompatibilityAudit.run(CatalogCompatibilityAudit.Settings.from(environment),
                settings -> { throw new SQLException("jdbc:password", "08001"); },
                image -> { throw new AssertionError(); }, new PrintWriter(output, true));
        assertThat(code).isEqualTo(3);
        assertThat(output.toString()).contains("DATABASE_FAILURE").doesNotContain("jdbc:password");
    }

    @Test
    void settings_shouldRejectInvalidManifestsAndNonPostgresOrInjectedUrls() throws Exception {
        setup();
        for (String url : List.of("jdbc:tc:postgresql:17:///db", "jdbc:h2:mem:test", "jdbc:postgresql://user:secret@localhost/db", "jdbc:postgresql://localhost/db?options=unsafe")) {
            environment.put("AUDIT_JDBC_URL", url);
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> CatalogCompatibilityAudit.Settings.from(environment))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        environment.put("AUDIT_JDBC_URL", "jdbc:postgresql://localhost:5432/catalog");
        Files.writeString(Path.of(environment.get("AUDIT_MANIFEST")), "27\t1\tV27__wrong.sql\n");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> CatalogCompatibilityAudit.Settings.from(environment))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void run_shouldReportOversizedDocumentsAsIncomplete_withoutClassifyingValidLargeFactsAsInvalid() throws Exception {
        setup();
        Map<String, Object> row = variant(null, "laptop");
        row.put("specification_oversize", "t");
        variants.add(row);
        assertThat(run()).isEqualTo(3);
        assertThat(output.toString()).contains("DOCUMENT_LIMIT", "INCOMPLETE").doesNotContain("SPECIFICATION_MISSING");
    }

    @Test
    void run_shouldRejectWrongChecksum_beforeCatalogReads() throws Exception {
        setup();
        List<Map<String, Object>> history = new ArrayList<>(history());
        Map<String, Object> altered = new HashMap<>(history.getFirst());
        altered.put("checksum", "0");
        history.set(0, altered);
        PreparedStatement mismatch = statement(CatalogCompatibilityAudit.HISTORY_SQL, history);
        when(connection.prepareStatement(CatalogCompatibilityAudit.HISTORY_SQL)).thenReturn(mismatch);
        assertThat(run()).isEqualTo(3);
        assertThat(sql).doesNotContain(CatalogCompatibilityAudit.VARIANTS_SQL);
    }

    @Test
    void run_shouldAcceptJdbcTextBooleanForSuccessfulHistory() throws Exception {
        setup();
        List<Map<String, Object>> history = new ArrayList<>();
        for (Map<String, Object> row : history()) {
            Map<String, Object> changed = new HashMap<>(row);
            changed.put("success", "t");
            history.add(changed);
        }
        PreparedStatement textBooleans = statement(CatalogCompatibilityAudit.HISTORY_SQL, history);
        when(connection.prepareStatement(CatalogCompatibilityAudit.HISTORY_SQL)).thenReturn(textBooleans);
        assertThat(run()).isZero();
    }

    @Test
    void run_shouldReturnIncomplete_whenOutputCannotBeWritten() throws Exception {
        setup();
        PrintWriter broken = new PrintWriter(new java.io.Writer() {
            @Override public void write(char[] chars, int offset, int length) throws java.io.IOException { throw new java.io.IOException("secret"); }
            @Override public void flush() { }
            @Override public void close() { }
        });
        assertThat(CatalogCompatibilityAudit.run(CatalogCompatibilityAudit.Settings.from(environment), settings -> connection,
                image -> null, broken)).isEqualTo(3);
        verify(connection).rollback();
    }

    @Test
    void main_shouldRejectWriteModeInFreshJvm_withoutApplicationOrDatabaseStartup() throws Exception {
        ProcessBuilder builder = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", System.getProperty("java.class.path"), CatalogCompatibilityAudit.class.getName(), "--migrate");
        builder.environment().clear();
        Process process = builder.redirectErrorStream(true).start();
        try {
            assertThat(process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            assertThat(process.exitValue()).isEqualTo(3);
            String console = new String(process.getInputStream().readNBytes(8192), java.nio.charset.StandardCharsets.UTF_8);
            assertThat(console).contains("CONFIGURATION_INVALID", "INCOMPLETE")
                    .doesNotContain("Spring Boot", "Hibernate", "Flyway", "Exception", "jdbc:");
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
    }

    @Test
    void run_shouldEmitObjectFindingsWithoutKeys_andStillRollback() throws Exception {
        setup();
        images.add(Map.of("id", UUID.randomUUID().toString(), "product_id", UUID.randomUUID().toString(),
                "object_key", "secret/object-key", "alt_text", "private alt", "content_type", "image/png",
                "byte_size", "10", "width", "2", "height", "2"));
        int code = CatalogCompatibilityAudit.run(CatalogCompatibilityAudit.Settings.from(environment), settings -> connection,
                image -> null, new PrintWriter(output, true));
        assertThat(code).isEqualTo(2);
        assertThat(output.toString()).contains("IMAGE_CONTENT_INVALID", "\"images\":1")
                .doesNotContain("secret/object-key", "private alt");
        verify(connection).rollback();
    }

    @Test
    void run_shouldReportStorageFailureAsIncomplete_evenAfterValidFacts() throws Exception {
        setup();
        variants.add(variant("{\"schema_version\":1,\"values\":{}}", "laptop"));
        images.add(Map.of("id", UUID.randomUUID().toString(), "product_id", UUID.randomUUID().toString(),
                "object_key", "secret/object-key", "alt_text", "private alt", "content_type", "image/png",
                "byte_size", "10", "width", "2", "height", "2"));
        int code = CatalogCompatibilityAudit.run(CatalogCompatibilityAudit.Settings.from(environment), settings -> connection,
                image -> { throw new IOException("storage credential secret"); }, new PrintWriter(output, true));
        assertThat(code).isEqualTo(3);
        assertThat(output.toString()).contains("INCOMPLETE").doesNotContain("storage credential secret", "\"status\":\"COMPLETE\"");
        verify(connection).rollback();
    }

    private int run() throws Exception {
        return CatalogCompatibilityAudit.run(CatalogCompatibilityAudit.Settings.from(environment), settings -> connection,
                image -> { throw new AssertionError("No image reads expected"); }, new PrintWriter(output, true));
    }

    private void setup() throws Exception {
        environment.putAll(Map.of("AUDIT_JDBC_URL", "jdbc:postgresql://localhost:5432/catalog", "AUDIT_DB_USER", "reader",
                "AUDIT_DB_PASSWORD", "private-password", "AUDIT_EXPECTED_DATABASE", "catalog", "AUDIT_EXPECTED_SCHEMA", "public",
                "AUDIT_EXPECTED_SERVER_ADDRESS", "127.0.0.1", "AUDIT_EXPECTED_SERVER_PORT", "5432"));
        Path manifest = temporary.resolve("manifest.tsv");
        StringBuilder text = new StringBuilder();
        for (Map<String, Object> entry : history()) text.append(entry.get("version")).append('\t').append(entry.get("checksum"))
                .append('\t').append(entry.get("script")).append('\n');
        Files.writeString(manifest, text);
        environment.put("AUDIT_MANIFEST", manifest.toString());
        connection = mock(Connection.class);
        when(connection.prepareStatement(anyString())).thenAnswer(call -> {
            String query = call.getArgument(0);
            List<Map<String, Object>> rows = query.equals(CatalogCompatibilityAudit.IDENTITY_SQL) ? List.of(identity())
                    : query.equals(CatalogCompatibilityAudit.HISTORY_SQL) ? history()
                    : query.equals(CatalogCompatibilityAudit.VARIANTS_SQL) ? variants
                    : query.equals(CatalogCompatibilityAudit.IMAGES_SQL) ? images : List.of();
            return statement(query, rows);
        });
    }

    private PreparedStatement statement(String query, List<Map<String, Object>> rows) throws Exception {
        PreparedStatement statement = mock(PreparedStatement.class);
        when(statement.execute()).thenAnswer(call -> { sql.add(query); return false; });
        when(statement.executeQuery()).thenAnswer(call -> {
            sql.add(query);
            ResultSet result = mock(ResultSet.class);
            int[] index = {-1};
            when(result.next()).thenAnswer(next -> ++index[0] < rows.size());
            when(result.getString(anyString())).thenAnswer(get -> {
                Object value = rows.get(index[0]).get(get.<String>getArgument(0));
                return value == null ? null : value.toString();
            });
            when(result.getCharacterStream(anyString())).thenAnswer(get -> {
                Object value = rows.get(index[0]).get(get.<String>getArgument(0));
                return value == null ? null : new StringReader(value.toString());
            });
            return result;
        });
        return statement;
    }

    private Map<String, Object> identity() {
        return new HashMap<>(Map.of("database_name", "catalog", "schema_name", "public", "server_address", "127.0.0.1",
                "server_port", "5432", "read_only", "on", "isolation", "repeatable read", "server_version", "170000", "superuser", "off"));
    }

    private Map<String, Object> variant(String facts, String families) {
        Map<String, Object> row = new HashMap<>(Map.of("id", UUID.randomUUID().toString(), "product_id", UUID.randomUUID().toString(),
                "families", families));
        row.put("technical_specifications", facts);
        return row;
    }

    private List<Map<String, Object>> history() throws Exception {
        List<Map<String, Object>> rows = new ArrayList<>();
        // Read only source resources, never invoke Flyway. This is the expected manifest fixture.
        try (java.util.stream.Stream<Path> paths = Files.list(Path.of("src/main/resources/db/migration"))) {
            for (Path path : paths.filter(p -> p.getFileName().toString().matches("V(?:[1-9]|1[0-9]|2[0-7])__.*\\.sql"))
                    .sorted(java.util.Comparator.comparingInt(p -> Integer.parseInt(p.getFileName().toString().split("__")[0].substring(1)))).toList()) {
                CRC32 crc = new CRC32();
                for (String line : Files.readString(path).split("\\R")) crc.update(line.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                rows.add(Map.of("version", path.getFileName().toString().split("__")[0].substring(1), "checksum", (int) crc.getValue(),
                        "script", path.getFileName().toString(), "type", "SQL", "success", true));
            }
        }
        return rows;
    }
}
