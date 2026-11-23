package com.example.demo.catalog.audit;

import com.example.demo.catalog.repository.CatalogApplicabilitySql;
import com.example.demo.catalog.search.SpecificationFilterRegistry;
import com.example.demo.catalog.validator.PersistedSpecificationValidator;
import com.example.demo.catalog.validator.TechnicalSpecificationValidator;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.zip.CRC32;
import tools.jackson.core.type.TypeReference;
import tools.jackson.core.exc.StreamConstraintsException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** Operator-only V27 audit. No application bootstrap, ORM, migration runner or database writes. */
public final class CatalogCompatibilityAudit {
    private static final int FETCH_SIZE = 100;
    private static final int QUERY_SECONDS = 60;
    private static final int MAX_DOCUMENT_CHARS = 1_048_576;
    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .enable(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS).build();
    static final String IDENTITY_SQL = """
            SELECT CURRENT_DATABASE() AS database_name, CURRENT_SCHEMA() AS schema_name,
                   INET_SERVER_ADDR()::TEXT AS server_address, INET_SERVER_PORT()::TEXT AS server_port,
                   CURRENT_SETTING('transaction_read_only') AS read_only,
                   CURRENT_SETTING('transaction_isolation') AS isolation,
                   CURRENT_SETTING('server_version_num') AS server_version,
                   CURRENT_SETTING('is_superuser') AS superuser
            """;
    static final String HISTORY_SQL = "SELECT version, checksum, script, success, type FROM public.flyway_schema_history ORDER BY installed_rank";
    static final String VARIANTS_SQL = "WITH RECURSIVE " + CatalogApplicabilitySql.registryCtes() + """
             SELECT v.id::TEXT AS id, v.product_id::TEXT AS product_id,
                    CASE WHEN OCTET_LENGTH(v.technical_specifications::TEXT) <= 1048576
                         THEN v.technical_specifications::TEXT ELSE NULL END AS technical_specifications,
                    (OCTET_LENGTH(v.technical_specifications::TEXT) > 1048576)::TEXT AS specification_oversize,
                    COALESCE((SELECT STRING_AGG(rc.family, ',' ORDER BY rc.family)
                              FROM registry_categories rc WHERE rc.id = p.category_id), '') AS families
             FROM public.product_variants v LEFT JOIN public.products p ON p.id = v.product_id ORDER BY v.id
            """;
    static final String IMAGES_SQL = """
            SELECT id::TEXT AS id, product_id::TEXT AS product_id,
                   CASE WHEN OCTET_LENGTH(object_key) <= 1048576 THEN object_key END AS object_key,
                   CASE WHEN OCTET_LENGTH(alt_text) <= 1048576 THEN alt_text END AS alt_text, content_type,
                   ((OCTET_LENGTH(object_key) > 1048576) OR (OCTET_LENGTH(alt_text) > 1048576))::TEXT AS metadata_oversize,
                   byte_size::TEXT AS byte_size, width::TEXT AS width, height::TEXT AS height
            FROM public.product_images ORDER BY id
            """;
    private static final String[] SCHEMA_PROBES = {
        "SELECT id, name, parent_category_id FROM public.categories LIMIT 0",
        "SELECT id, category_id, brand_id, name, status FROM public.products LIMIT 0",
        "SELECT id, product_id, sku, selling_price, option_combination, technical_specifications FROM public.product_variants LIMIT 0",
        "SELECT id, product_id, source_image_id, object_key, alt_text, content_type, byte_size, width, height, display_order, is_primary FROM public.product_images LIMIT 0",
        "SELECT id, code, name FROM public.brands LIMIT 0"
    };
    private static final Map<String, String> FINGERPRINTS = Map.of(
        "products", "SELECT id::TEXT AS id, MD5(ROW(category_id,brand_id,name,description,status)::TEXT) AS fingerprint FROM public.products ORDER BY id",
        "product_variants", "SELECT id::TEXT AS id, MD5(ROW(product_id,sku,option_combination,selling_price,technical_specifications)::TEXT) AS fingerprint FROM public.product_variants ORDER BY id",
        "product_images", "SELECT id::TEXT AS id, MD5(ROW(product_id,source_image_id,object_key,alt_text,content_type,byte_size,width,height,display_order,is_primary)::TEXT) AS fingerprint FROM public.product_images ORDER BY id",
        "catalog_source_links", "SELECT id::TEXT AS id, MD5(ROW(source,source_key,source_url,source_record_id,product_variant_id,source_payload_sha256,normalized_content_sha256,normalization_version,approved_manifest_sha256,updated_at)::TEXT) AS fingerprint FROM public.catalog_source_links ORDER BY id",
        "orders", "SELECT id::TEXT AS id, MD5(ROW(customer_id,order_number,placed_at,status,recipient_name_snapshot,recipient_phone_snapshot,delivery_address_snapshot,merchandise_total,payable_total,cancelled_at,completed_at,inventory_managed)::TEXT) AS fingerprint FROM public.orders ORDER BY id",
        "order_items", "SELECT id::TEXT AS id, MD5(ROW(product_name_snapshot,variant_sku_snapshot,variant_options_snapshot,quantity,unit_price_snapshot,line_total)::TEXT) AS fingerprint FROM public.order_items ORDER BY id",
        "checkout_receipts", "SELECT id::TEXT AS id, MD5(receipt_body::TEXT) AS fingerprint FROM public.checkout_receipts ORDER BY id",
        "inventory_reservations", "SELECT id::TEXT AS id, MD5(ROW(order_id,order_item_id,inventory_balance_id,original_variant_id,quantity,state,reserved_at,resolved_at)::TEXT) AS fingerprint FROM public.inventory_reservations ORDER BY id");

    private CatalogCompatibilityAudit() { }

    public static void main(String[] args) {
        System.exit(execute(args, System.getenv(), new PrintWriter(System.out, true, StandardCharsets.UTF_8)));
    }

    static int execute(String[] args, Map<String, String> environment, PrintWriter output) {
        if (Arrays.equals(args, new String[] {"--help"})) {
            output.println("V27 read-only audit; see specs/013-admin-catalog-management/auditor-operations.md. No write modes.");
            return 0;
        }
        try {
            if (args.length != 0) throw new IllegalArgumentException();
            Settings settings = Settings.from(environment);
            try (AuditImageReader images = new AuditImageReader(environment)) {
                return run(settings, CatalogCompatibilityAudit::connect, images::inspect, output);
            }
        } catch (Exception failure) {
            new Report(output).finish("INCOMPLETE", "CONFIGURATION_INVALID");
            return 3;
        }
    }

    private static Connection connect(Settings settings) throws SQLException {
        Properties properties = new Properties();
        properties.setProperty("user", settings.user);
        properties.setProperty("password", settings.password);
        properties.setProperty("connectTimeout", "10");
        properties.setProperty("loginTimeout", "10");
        properties.setProperty("socketTimeout", "65");
        properties.setProperty("readOnlyMode", "transaction");
        properties.setProperty("sslmode", settings.sslMode);
        return DriverManager.getConnection(settings.url, properties);
    }

    @FunctionalInterface
    interface ConnectionFactory { Connection open(Settings settings) throws SQLException; }
    @FunctionalInterface
    interface ImageInspector { String inspect(Image image) throws Exception; }
    record Image(String id, String productId, String key, String altText, String contentType,
                 long bytes, int width, int height) { }

    static int run(Settings settings, ConnectionFactory connections, ImageInspector images, PrintWriter output) {
        Report report = new Report(output, settings.maxSeconds);
        String failureCode = null;
        try (Connection connection = connections.open(settings)) {
            try {
                connection.setReadOnly(true);
                connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
                connection.setAutoCommit(false);
                control(connection, "SET TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY");
                control(connection, "SET LOCAL statement_timeout = '60s'");
                control(connection, "SET LOCAL lock_timeout = '2s'");
                control(connection, "SET LOCAL search_path = public, pg_catalog");
                control(connection, "SET LOCAL TIME ZONE 'UTC'");
                control(connection, "SET LOCAL DateStyle = 'ISO, YMD'");
                checkIdentity(connection, settings);
                checkHistory(connection, settings);
                for (String probe : SCHEMA_PROBES) {
                    report.budget(0, settings.maxRecords);
                    try (PreparedStatement statement = query(connection, probe); ResultSet ignored = statement.executeQuery()) {
                        // No rows requested: fail closed if any required V27 column/relation is absent.
                    }
                }
                auditVariants(connection, settings, report);
                auditImages(connection, settings, images, report);
                for (Map.Entry<String, String> entry : FINGERPRINTS.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey()).toList()) {
                    report.budget(0, settings.maxRecords);
                    try (PreparedStatement statement = query(connection, entry.getValue()); ResultSet rows = statement.executeQuery()) {
                        long count = 0;
                        while (rows.next()) {
                            report.budget(++count, settings.maxRecords);
                            report.emit(Map.of("type", "FINGERPRINT", "relation", entry.getKey(),
                                    "id", rows.getString("id"), "fingerprint", rows.getString("fingerprint")));
                        }
                        report.emit(Map.of("type", "COUNT", "relation", entry.getKey(), "count", count));
                    }
                }
                report.budget(0, settings.maxRecords);
            } finally {
                connection.rollback(); // Mandatory even on invalid data or a failed query; never commit.
            }
        } catch (Incomplete failure) {
            failureCode = failure.code;
        } catch (SQLException failure) {
            failureCode = "DATABASE_FAILURE"; // Never print SQLException message, URL, username or SQL.
        } catch (Exception failure) {
            failureCode = "DEPENDENCY_OR_OUTPUT_FAILURE";
        }
        String status = failureCode != null ? "INCOMPLETE" : report.violations > 0 ? "INVALID" : "COMPLETE";
        report.finish(status, failureCode == null ? "AUDIT_FINISHED" : failureCode);
        return output.checkError() || failureCode != null ? 3 : report.violations > 0 ? 2 : 0;
    }

    private static void checkIdentity(Connection connection, Settings settings) throws SQLException {
        try (PreparedStatement statement = query(connection, IDENTITY_SQL); ResultSet row = statement.executeQuery()) {
            if (!row.next() || !settings.database.equals(row.getString("database_name"))
                    || !"public".equals(row.getString("schema_name"))
                    || !settings.address.equals(row.getString("server_address"))
                    || !settings.port.equals(row.getString("server_port"))
                    || !"on".equals(row.getString("read_only"))
                    || !"repeatable read".equals(row.getString("isolation"))
                    || !"off".equals(row.getString("superuser"))
                    || !row.getString("server_version").matches("17[0-9]{4}") || row.next()) {
                throw new Incomplete("TARGET_OR_READ_ONLY_MISMATCH");
            }
        }
    }

    private static void checkHistory(Connection connection, Settings settings) throws SQLException {
        try (PreparedStatement statement = query(connection, HISTORY_SQL); ResultSet rows = statement.executeQuery()) {
            int version = 0;
            while (rows.next()) {
                version++;
                Migration expected = settings.manifest.get(version);
                if (expected == null || !Integer.toString(version).equals(rows.getString("version"))
                        || !Integer.toString(expected.checksum()).equals(rows.getString("checksum"))
                        || !expected.script().equals(rows.getString("script"))
                        || !("true".equalsIgnoreCase(rows.getString("success")) || "t".equals(rows.getString("success")))
                        || !"SQL".equals(rows.getString("type"))) {
                    throw new Incomplete("SCHEMA_HISTORY_MISMATCH");
                }
            }
            if (version != 27) throw new Incomplete("SCHEMA_HISTORY_MISMATCH");
        }
    }

    private static void auditVariants(Connection connection, Settings settings, Report report) throws Exception {
        PersistedSpecificationValidator validator = new PersistedSpecificationValidator(
                new TechnicalSpecificationValidator(), new SpecificationFilterRegistry());
        try (PreparedStatement statement = query(connection, VARIANTS_SQL); ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                report.budget(++report.variants, settings.maxRecords);
                String id = rows.getString("id");
                String oversized = rows.getString("specification_oversize");
                if ("true".equalsIgnoreCase(oversized) || "t".equals(oversized)) throw new Incomplete("DOCUMENT_LIMIT");
                String facts = boundedText(rows.getCharacterStream("technical_specifications"));
                if (facts == null) {
                    report.invalid("SPECIFICATION_MISSING", "variant", id);
                    continue;
                }
                String familyText = rows.getString("families");
                if (familyText == null) throw new Incomplete("APPLICABILITY_INCOMPLETE");
                Set<String> families = familyText.isEmpty() ? Set.of() : Set.copyOf(Arrays.asList(familyText.split(",")));
                Map<String, Object> document;
                try {
                    document = JSON.readValue(facts, new TypeReference<Map<String, Object>>() { });
                } catch (StreamConstraintsException limit) {
                    throw new Incomplete("DOCUMENT_LIMIT");
                } catch (RuntimeException invalidJson) {
                    report.invalid("SPECIFICATION_INVALID", "variant", id);
                    continue;
                }
                try {
                    validator.validate(document, families);
                } catch (IllegalArgumentException invalidFacts) {
                    report.invalid("SPECIFICATION_INVALID", "variant", id);
                }
            }
        }
    }

    private static void auditImages(Connection connection, Settings settings, ImageInspector inspector, Report report) throws Exception {
        try (PreparedStatement statement = query(connection, IMAGES_SQL); ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                report.budget(++report.images, settings.maxRecords);
                String oversized = rows.getString("metadata_oversize");
                if ("true".equalsIgnoreCase(oversized) || "t".equals(oversized)) throw new Incomplete("DOCUMENT_LIMIT");
                Image image;
                try {
                    image = new Image(rows.getString("id"), rows.getString("product_id"),
                            rows.getString("object_key"), rows.getString("alt_text"), rows.getString("content_type"),
                            Long.parseLong(rows.getString("byte_size")), Integer.parseInt(rows.getString("width")),
                            Integer.parseInt(rows.getString("height")));
                } catch (NumberFormatException malformedMetadata) {
                    report.invalid("IMAGE_METADATA_INVALID", "image", rows.getString("id"));
                    continue;
                }
                String hash = inspector.inspect(image);
                if (hash == null) report.invalid("IMAGE_CONTENT_INVALID", "image", image.id());
                else report.emit(Map.of("type", "OBJECT_FINGERPRINT", "id", image.id(), "sha256", hash));
            }
        }
    }

    private static String boundedText(Reader source) throws IOException {
        if (source == null) return null;
        try (Reader reader = source) {
            StringBuilder result = new StringBuilder();
            char[] buffer = new char[4096];
            int count;
            while ((count = reader.read(buffer)) != -1) {
                if (result.length() + count > MAX_DOCUMENT_CHARS) throw new Incomplete("DOCUMENT_LIMIT");
                result.append(buffer, 0, count);
            }
            return result.toString();
        }
    }

    private static PreparedStatement query(Connection connection, String sql) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(sql);
        try {
            statement.setQueryTimeout(QUERY_SECONDS);
            statement.setFetchSize(FETCH_SIZE); // PostgreSQL server cursor: autoCommit is false.
            return statement;
        } catch (SQLException failure) {
            statement.close();
            throw failure;
        }
    }

    private static void control(Connection connection, String sql) throws SQLException {
        try (PreparedStatement statement = query(connection, sql)) { statement.execute(); }
    }

    private static final class Report {
        private final PrintWriter output;
        private final String runId = UUID.randomUUID().toString();
        private long variants;
        private long images;
        private long violations;
        private final long deadline;
        private Report(PrintWriter output) { this(output, 600); }
        private Report(PrintWriter output, int maximumSeconds) {
            this.output = output;
            this.deadline = System.nanoTime() + java.time.Duration.ofSeconds(maximumSeconds).toNanos();
        }
        private void budget(long count, int maximum) {
            if (count > maximum) throw new Incomplete("RECORD_LIMIT");
            if (System.nanoTime() > deadline) throw new Incomplete("TIME_LIMIT");
        }
        private void emit(Map<String, Object> value) {
            Map<String, Object> row = new LinkedHashMap<>(value);
            row.put("runId", runId);
            output.println(JSON.writeValueAsString(row));
            if (output.checkError()) throw new Incomplete("OUTPUT_FAILURE");
        }
        private void invalid(String code, String entity, String id) {
            violations++;
            emit(Map.of("type", "FINDING", "code", code, "entity", entity, "id", id));
        }
        private void finish(String status, String code) {
            // A broken output stream cannot be made complete by another write; exit3 remains mandatory.
            try {
                emit(Map.of("type", "SUMMARY", "status", status, "code", code, "variants", variants,
                        "images", images, "violations", violations, "baseline", "V27", "finishedAt", Instant.now().toString(),
                        "scope", "TYPED_FACTS_OBJECTS_FINGERPRINTS"));
            } catch (RuntimeException unavailableOutput) { /* exit code3 + absent final summary is incomplete */ }
        }
    }

    private static final class Incomplete extends RuntimeException {
        private final String code;
        private Incomplete(String code) { super(code); this.code = code; }
    }

    record Migration(int checksum, String script) { }

    /** No generated toString/accessors that could disclose credentials. */
    static final class Settings {
        private String url;
        private String user;
        private String password;
        private String database;
        private String address;
        private String port;
        private String sslMode;
        private int maxRecords;
        private int maxSeconds;
        private Map<Integer, Migration> manifest;

        static Settings from(Map<String, String> environment) throws IOException {
            Settings settings = new Settings();
            settings.url = required(environment, "AUDIT_JDBC_URL");
            if (!settings.url.startsWith("jdbc:postgresql://")) throw new IllegalArgumentException();
            URI uri = URI.create(settings.url.substring(5));
            if (uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                    || uri.getPath() == null || !uri.getPath().matches("/[^/]+")) throw new IllegalArgumentException();
            settings.user = required(environment, "AUDIT_DB_USER");
            settings.password = required(environment, "AUDIT_DB_PASSWORD");
            settings.database = required(environment, "AUDIT_EXPECTED_DATABASE");
            settings.address = required(environment, "AUDIT_EXPECTED_SERVER_ADDRESS");
            settings.port = required(environment, "AUDIT_EXPECTED_SERVER_PORT");
            if (!settings.port.matches("[0-9]{1,5}") || Integer.parseInt(settings.port) < 1
                    || Integer.parseInt(settings.port) > 65535 || !"public".equals(required(environment, "AUDIT_EXPECTED_SCHEMA"))) {
                throw new IllegalArgumentException();
            }
            settings.sslMode = environment.getOrDefault("AUDIT_SSL_MODE", "verify-full");
            if (!Set.of("verify-full", "require", "disable").contains(settings.sslMode)) throw new IllegalArgumentException();
            settings.maxRecords = Integer.parseInt(environment.getOrDefault("AUDIT_MAX_RECORDS", "1000000"));
            if (settings.maxRecords < 1 || settings.maxRecords > 10_000_000) throw new IllegalArgumentException();
            settings.maxSeconds = Integer.parseInt(environment.getOrDefault("AUDIT_MAX_SECONDS", "600"));
            if (settings.maxSeconds < 1 || settings.maxSeconds > 3600) throw new IllegalArgumentException();
            settings.manifest = readManifest(Path.of(required(environment, "AUDIT_MANIFEST")));
            return settings;
        }

        private static String required(Map<String, String> environment, String key) {
            String value = environment.get(key);
            if (value == null || value.isBlank()) throw new IllegalArgumentException();
            return value;
        }

        private static Map<Integer, Migration> readManifest(Path path) throws IOException {
            String document;
            try (InputStream input = Files.newInputStream(path)) {
                byte[] bytes = input.readNBytes(65_537);
                if (bytes.length > 65_536) throw new IllegalArgumentException();
                document = new String(bytes, StandardCharsets.UTF_8);
            }
            Map<Integer, Migration> manifest = new LinkedHashMap<>();
            for (String line : document.lines().toList()) {
                String[] fields = line.split("\t", -1);
                if (fields.length != 3 || !fields[0].matches("[1-9][0-9]?")
                        || !fields[2].matches("V" + fields[0] + "__[A-Za-z0-9_]+\\.sql")) throw new IllegalArgumentException();
                int version = Integer.parseInt(fields[0]);
                int checksum = Integer.parseInt(fields[1]);
                if (version != manifest.size() + 1 || version > 27 || checksum != resourceChecksum(fields[2])) {
                    throw new IllegalArgumentException();
                }
                manifest.put(version, new Migration(checksum, fields[2]));
            }
            if (manifest.size() != 27) throw new IllegalArgumentException();
            return Map.copyOf(manifest);
        }

        private static int resourceChecksum(String script) throws IOException {
            InputStream source = CatalogCompatibilityAudit.class.getResourceAsStream("/db/migration/" + script);
            if (source == null) throw new IllegalArgumentException();
            CRC32 checksum = new CRC32();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(source, StandardCharsets.UTF_8))) {
                String line;
                boolean first = true;
                while ((line = reader.readLine()) != null) {
                    if (first && line.startsWith("\uFEFF")) line = line.substring(1);
                    first = false;
                    checksum.update(line.getBytes(StandardCharsets.UTF_8));
                }
            }
            return (int) checksum.getValue();
        }

        @Override public String toString() { return "AuditSettings[redacted]"; }
    }
}
