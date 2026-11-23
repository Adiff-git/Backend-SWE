package com.example.demo.catalog.audit;

import static org.assertj.core.api.Assertions.assertThat;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.testcontainers.containers.PostgreSQLContainer;

/** Only creates a fresh owned container; never accepts application/target JDBC configuration. */
final class V27AuditDatabase implements AutoCloseable {
    private final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("admin013_v27_audit").withUsername("fixture_owner").withPassword("disposable_fixture_only")
            .withLabel("admin013.scope", "batch5-t086").withReuse(false)
            .withTmpFs(Map.of("/var/lib/postgresql/data", "rw"));
    private Path manifest;
    private String address;
    void start(Path directory) throws Exception {
        postgres.start();
        Flyway.configure().dataSource(url(), postgres.getUsername(), postgres.getPassword()).target("27").load().migrate();
        try (Connection connection = owner()) {
            execute(connection, "CREATE ROLE audit_reader LOGIN PASSWORD 'read_only_fixture' NOSUPERUSER NOCREATEDB NOCREATEROLE");
            execute(connection, "GRANT USAGE ON SCHEMA public TO audit_reader");
            execute(connection, "GRANT SELECT ON ALL TABLES IN SCHEMA public TO audit_reader");
            address = scalar(connection, "SELECT INET_SERVER_ADDR()::TEXT");
            List<String> lines = new ArrayList<>();
            try (Statement query = connection.createStatement(); ResultSet rows = query.executeQuery(
                    "SELECT version,checksum,script FROM flyway_schema_history ORDER BY installed_rank")) {
                while (rows.next()) lines.add(rows.getString(1) + "\t" + rows.getString(2) + "\t" + rows.getString(3));
            }
            manifest = directory.resolve("v27.tsv"); Files.writeString(manifest, String.join("\n", lines) + "\n");
            assertThat(lines).hasSize(27);
            assertThat(scalar(connection, "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='public' AND ((table_name='product_variants' AND column_name='enabled') OR (table_name='products' AND column_name='last_restored_at'))")).isEqualTo("0");
            assertThat(scalar(connection, "SELECT to_regclass('public.catalog_taxonomy_state') IS NULL")).isEqualTo("t");
        }
    }
    String url() { return "jdbc:postgresql://" + postgres.getHost() + ":" + postgres.getMappedPort(5432) + "/" + postgres.getDatabaseName(); }
    Connection owner() throws Exception { return DriverManager.getConnection(url(), postgres.getUsername(), postgres.getPassword()); }
    Connection reader() throws java.sql.SQLException { return DriverManager.getConnection(url(), "audit_reader", "read_only_fixture"); }
    Map<String,String> environment() {
        return new LinkedHashMap<>(Map.of("AUDIT_JDBC_URL",url(),"AUDIT_DB_USER","audit_reader","AUDIT_DB_PASSWORD","read_only_fixture",
                "AUDIT_EXPECTED_DATABASE",postgres.getDatabaseName(),"AUDIT_EXPECTED_SCHEMA","public","AUDIT_EXPECTED_SERVER_ADDRESS",address,
                "AUDIT_EXPECTED_SERVER_PORT","5432","AUDIT_MANIFEST",manifest.toString(),"AUDIT_SSL_MODE","disable"));
    }
    /** Fixed names inside this harness's newly created container; never accepts a target URL. */
    private String copyUrl(String name) {
        if(!java.util.Set.of("admin013_copy_a","admin013_copy_b","admin013_fresh_v27").contains(name))throw new IllegalArgumentException("Unknown disposable database");
        return url().substring(0,url().lastIndexOf('/')+1)+name;
    }
    Connection copyOwner(String name) throws Exception {
        return DriverManager.getConnection(copyUrl(name),postgres.getUsername(),postgres.getPassword());
    }
    Connection copyReader(String name) throws java.sql.SQLException {
        return DriverManager.getConnection(copyUrl(name),"audit_reader","read_only_fixture");
    }
    void createEmptyCopy(String name) throws Exception {
        copyUrl(name); // Restrict identifier before interpolating fixed fixture-owned SQL.
        try(Connection owner=owner()){execute(owner,"CREATE DATABASE "+name+" TEMPLATE template0");}
    }
    void initializeFreshV27(String name) throws Exception {
        createEmptyCopy(name);
        Flyway.configure().dataSource(copyUrl(name),postgres.getUsername(),postgres.getPassword()).target("27").load().migrate();
    }
    void dumpReference() throws Exception {
        var result=postgres.execInContainer("pg_dump","-U",postgres.getUsername(),"-d",postgres.getDatabaseName(),"--format=custom","--file=/tmp/admin013_reference_v27.dump");
        assertThat(result.getExitCode()).as("Owned reference pg_dump").isZero();
    }
    Path exportSyntheticReference(Path folder) throws Exception {
        Files.createDirectories(folder);
        Path dump=folder.resolve("reference-v27.dump");
        postgres.copyFileFromContainer("/tmp/admin013_reference_v27.dump",dump.toString());
        Files.copy(manifest,folder.resolve("v27.tsv"));
        var metadata=new LinkedHashMap<String,Object>();
        metadata.put("scope","SYNTHETIC_V27_ONLY_NOT_G1_G2");metadata.put("migrationTarget",27);
        metadata.put("image",postgres.getDockerImageName());metadata.put("containerId",postgres.getContainerId());
        metadata.put("imageId",postgres.getDockerClient().inspectContainerCmd(postgres.getContainerId()).exec().getImageId());
        try(Connection owner=owner()){metadata.put("serverVersion",scalar(owner,"SHOW server_version"));}
        Files.writeString(folder.resolve("identity.json"),tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(metadata));
        StringBuilder checksums=new StringBuilder();
        for(String file:List.of("reference-v27.dump","v27.tsv","identity.json")){
            byte[] digest=java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(folder.resolve(file)));
            checksums.append(java.util.HexFormat.of().formatHex(digest)).append("  ").append(file).append("\n");
        }
        Files.writeString(folder.resolve("SHA256SUMS"),checksums.toString());
        return folder;
    }
    void restoreCopy(String name) throws Exception {
        createEmptyCopy(name);
        var result=postgres.execInContainer("pg_restore","-U",postgres.getUsername(),"--dbname="+name,"--no-owner","--no-acl","--exit-on-error","/tmp/admin013_reference_v27.dump");
        assertThat(result.getExitCode()).as("Owned copy pg_restore").isZero();
        try(Connection owner=copyOwner(name)){
            execute(owner,"GRANT USAGE ON SCHEMA public TO audit_reader");
            execute(owner,"GRANT SELECT ON ALL TABLES IN SCHEMA public TO audit_reader");
        }
    }
    AuditResult auditCopy(String name) throws Exception {
        var environment=environment();environment.put("AUDIT_JDBC_URL",copyUrl(name));environment.put("AUDIT_EXPECTED_DATABASE",name);
        StringWriter output=new StringWriter();
        int code=CatalogCompatibilityAudit.run(CatalogCompatibilityAudit.Settings.from(environment),ignored->copyReader(name),
                image->{throw new AssertionError("Synthetic reference contains no storage objects");},new PrintWriter(output,true));
        return new AuditResult(code,output.toString());
    }
    void clearCatalog() throws Exception {
        try(Connection connection=owner()) { execute(connection,"TRUNCATE categories, brands, products, product_variants, product_images CASCADE"); }
    }
    static void execute(Connection connection,String sql,Object... values) throws Exception {
        try(PreparedStatement query=connection.prepareStatement(sql)) { for(int i=0;i<values.length;i++) query.setObject(i+1,values[i]);query.execute(); }
    }
    static String scalar(Connection connection,String sql) throws Exception {
        try(Statement query=connection.createStatement();ResultSet rows=query.executeQuery(sql)) { assertThat(rows.next()).isTrue();return rows.getString(1); }
    }
    static String fingerprint(Connection connection) throws Exception {
        List<String> tables=new ArrayList<>();
        try(Statement query=connection.createStatement();ResultSet rows=query.executeQuery("SELECT tablename FROM pg_tables WHERE schemaname='public' ORDER BY tablename")) {
            while(rows.next()) tables.add(rows.getString(1));
        }
        StringBuilder value=new StringBuilder(scalar(connection,"SELECT MD5(STRING_AGG(ROW(table_name,column_name,data_type,is_nullable,column_default)::TEXT,',' ORDER BY table_name,ordinal_position)) FROM information_schema.columns WHERE table_schema='public'"));
        for(String table:tables) { if(!table.matches("[a-z_]+")) throw new IllegalStateException();
            value.append(table).append(scalar(connection,"SELECT COALESCE(MD5(STRING_AGG(row_to_json(r)::TEXT,',' ORDER BY row_to_json(r)::TEXT)), 'empty') FROM public.\""+table+"\" r")); }
        return value.toString();
    }
    record AuditResult(int code,String output) { }
    AuditResult audit(Map<String,String> environment) throws Exception {
        StringWriter output=new StringWriter();
        int code=CatalogCompatibilityAudit.run(CatalogCompatibilityAudit.Settings.from(environment),ignored->reader(),image->{throw new IllegalStateException("No storage authorized in this database-only fixture");},new PrintWriter(output,true));
        return new AuditResult(code,output.toString());
    }
    static List<List<Map<String,String>>> script(Connection connection,Path path) throws Exception {
        String source=Files.readString(path).replaceAll("(?m)^--.*$","");
        List<List<Map<String,String>>> result=new ArrayList<>();
        for(String part:source.split(";")) {
            String sql=part.strip();if(sql.isEmpty()) continue;
            assertThat(sql).matches("(?s)^(BEGIN TRANSACTION|SET LOCAL|SELECT|WITH|ROLLBACK).*" );
            try(Statement query=connection.createStatement()) {
                query.setQueryTimeout(60);
                if(query.execute(sql)) try(ResultSet rows=query.getResultSet()) {
                    List<Map<String,String>> set=new ArrayList<>();
                    while(rows.next()) { Map<String,String> row=new LinkedHashMap<>();for(int c=1;c<=rows.getMetaData().getColumnCount();c++) row.put(rows.getMetaData().getColumnLabel(c),rows.getString(c));set.add(row); }
                    result.add(set);
                }
            }
        }
        return result;
    }
    @Override public void close() { postgres.close(); }
}
