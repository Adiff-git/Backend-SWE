package com.example.demo.catalog.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.example.demo.catalog.audit.V27AuditDatabase.execute;
import static com.example.demo.catalog.audit.V27AuditDatabase.scalar;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CatalogCompatibilityAuditIT {
    Path directory;
    private final V27AuditDatabase database=new V27AuditDatabase();
    @BeforeAll void start(@TempDir Path directory) throws Exception { this.directory=directory;database.start(directory); }
    @AfterAll void stop() { database.close(); }
    @BeforeEach void reset() throws Exception { database.clearCatalog(); }
    @Test void audit_shouldUseOnlySelectAccountAndReadOnlySnapshot_withoutChangingV27OrStartingMigration() throws Exception {
        seed("Laptop", "{\"schema_version\":1,\"values\":{\"physical_ram\":{\"value\":0,\"unit\":\"GB\"},\"feature\":false}}");
        String before;try(Connection owner=database.owner()) { before=V27AuditDatabase.fingerprint(owner); }
        List<String> statements=new ArrayList<>();AtomicInteger rollbacks=new AtomicInteger();
        StringWriter output=new StringWriter();
        int result=CatalogCompatibilityAudit.run(CatalogCompatibilityAudit.Settings.from(database.environment()),ignored->{
            Connection real=database.reader();
            return (Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{Connection.class},(proxy,method,args)->{
                if(method.getName().equals("commit")) throw new AssertionError("Auditor attempted commit");
                if(method.getName().equals("rollback")) rollbacks.incrementAndGet();
                if(method.getName().equals("prepareStatement")) { String sql=(String)args[0]; statements.add(sql);assertThat(sql).matches("(?s)^(SET |SELECT |WITH RECURSIVE ).*"); }
                try { return method.invoke(real,args); } catch(InvocationTargetException failure) { throw failure.getCause(); }
            });
        },image->{throw new AssertionError("No images in this fixture");},new PrintWriter(output,true));
        assertThat(result).isZero();assertThat(output.toString()).contains("\"status\":\"COMPLETE\"");assertThat(rollbacks.get()).isEqualTo(1);
        assertThat(String.join("\n",statements)).doesNotContain("catalog_taxonomy_state","v.enabled","i.active","last_restored_at");
        try(Connection owner=database.owner()) { assertThat(V27AuditDatabase.fingerprint(owner)).isEqualTo(before); }
        assertThat(Files.exists(Path.of("target/classes/db/migration/V28__admin_catalog_management.sql"))).isTrue();
    }
    @Test void postgres_shouldEnforceBothLeastPrivilegeAndTransactionReadOnly() throws Exception {
        try(Connection reader=database.reader()) {
            assertThatThrownBy(()->execute(reader,"UPDATE products SET name='forbidden'")).satisfies(e->assertThat(((SQLException)e).getSQLState()).isEqualTo("42501"));
        }
        try(Connection owner=database.owner()) {
            execute(owner,"BEGIN TRANSACTION READ ONLY");
            assertThatThrownBy(()->execute(owner,"CREATE TABLE public.forbidden_audit_write(id int)")).satisfies(e->assertThat(((SQLException)e).getSQLState()).isEqualTo("25006"));
            execute(owner,"ROLLBACK");assertThat(scalar(owner,"SELECT to_regclass('public.forbidden_audit_write') IS NULL")).isEqualTo("t");
        }
    }
    @Test void facts_shouldDetectPersistedWrongTypeAndUnit_butRetainNonapplicableGenericData() throws Exception {
        UUID invalid=seed("Laptop","{\"schema_version\":1,\"values\":{\"physical_ram\":\"private-invalid-fact\"}}");
        var result=database.audit(database.environment());assertThat(result.code()).isEqualTo(2);assertThat(result.output()).contains("SPECIFICATION_INVALID").doesNotContain("private-invalid-fact");
        try(Connection owner=database.owner()) { assertThatThrownBy(()->execute(owner,"UPDATE product_variants SET technical_specifications=NULL WHERE id=?",invalid)).satisfies(error->assertThat(((SQLException)error).getSQLState()).isEqualTo("23502"));
            execute(owner,"UPDATE product_variants SET technical_specifications=CAST(? AS jsonb) WHERE id=?","{\"schema_version\":1,\"values\":{\"physical_ram\":{\"value\":8,\"unit\":\"MB\"}}}",invalid); }
        assertThat(database.audit(database.environment()).code()).isEqualTo(2);
        try(Connection owner=database.owner()) { execute(owner,"UPDATE categories SET name='Tablet'"); }
        assertThat(database.audit(database.environment()).code()).isZero();
    }
    @Test void applicability_shouldFollowAncestryBoundaryAndGlobalAmbiguity_usingActualPostgresProjection() throws Exception {
        seed("Laptop","{\"schema_version\":1,\"values\":{\"physical_ram\":\"generic-on-tablet\"}}");
        UUID root,tablet=UUID.randomUUID(),child=UUID.randomUUID();
        try(Connection owner=database.owner()) {
            root=UUID.fromString(scalar(owner,"SELECT id FROM categories LIMIT 1"));
            execute(owner,"INSERT INTO categories(id,name,parent_category_id) VALUES (?,'Tablet',?)",tablet,root);
            execute(owner,"INSERT INTO categories(id,name,parent_category_id) VALUES (?,'Child',?)",child,tablet);
            execute(owner,"UPDATE products SET category_id=?",child);
            try(Statement query=owner.createStatement();var rows=query.executeQuery(CatalogCompatibilityAudit.VARIANTS_SQL)) {assertThat(rows.next()).isTrue();assertThat(rows.getString("families")).isEqualTo("tablet");}
        }
        assertThat(database.audit(database.environment()).code()).isZero();
        try(Connection owner=database.owner()) {execute(owner,"UPDATE products SET category_id=?",root);execute(owner,"INSERT INTO categories(id,name) VALUES (?,'Laptop')",UUID.randomUUID());}
        assertThat(database.audit(database.environment()).code()).isZero();
    }
    @Test void scripts_shouldParseAndExecuteReadOnlyAgainstActualV27_withoutModifyingRowsOrHistory() throws Exception {
        seed("Laptop","{\"schema_version\":1,\"values\":{}}");String before;
        try(Connection owner=database.owner()){before=V27AuditDatabase.fingerprint(owner);}
        for(String name:List.of("data-preflight.sql","data-preflight-allocation-integrity.sql")) {
            try(Connection reader=database.reader()) {var result=V27AuditDatabase.script(reader,Path.of("../specs/013-admin-catalog-management/contracts/"+name));assertThat(result).isNotEmpty();
                if(name.equals("data-preflight.sql")) {
                    assertThat(result).hasSize(27);
                    for(int index:List.of(3,4,7,8,9,10,11,12,13,14,15,16,19,20,21,22))assertThat(result.get(index)).isEmpty();
                } else assertThat(result.subList(1,result.size())).allSatisfy(rows->assertThat(rows).isEmpty());}
        }
        try(Connection owner=database.owner()){assertThat(V27AuditDatabase.fingerprint(owner)).isEqualTo(before);}
    }
    @Test void failures_shouldReportIncompleteForIdentityHistorySchemaAndRecordLimit() throws Exception {
        seed("Laptop","{\"schema_version\":1,\"values\":{}}");var environment=database.environment();environment.put("AUDIT_EXPECTED_DATABASE","wrong");
        assertThat(database.audit(environment).output()).contains("TARGET_OR_READ_ONLY_MISMATCH","INCOMPLETE");
        seed("Other","{\"schema_version\":1,\"values\":{}}");environment=database.environment();environment.put("AUDIT_MAX_RECORDS","1");
        var limited=database.audit(environment);assertThat(limited.code()).isEqualTo(3);assertThat(limited.output()).contains("INCOMPLETE");
        try(Connection owner=database.owner()) {
            execute(owner,"ALTER TABLE product_variants RENAME COLUMN technical_specifications TO unavailable_facts");
            try {assertThat(database.audit(database.environment()).code()).isEqualTo(3);} finally {execute(owner,"ALTER TABLE product_variants RENAME COLUMN unavailable_facts TO technical_specifications");}
            execute(owner,"BEGIN");execute(owner,"UPDATE flyway_schema_history SET checksum=checksum+1 WHERE version='27'");execute(owner,"COMMIT");
            assertThat(database.audit(database.environment()).output()).contains("SCHEMA_HISTORY_MISMATCH","INCOMPLETE");
            execute(owner,"UPDATE flyway_schema_history SET checksum=checksum-1 WHERE version='27'");
        }
    }
    @Test void standaloneCli_shouldAuditV27WithoutLoadingSpringHibernateOrFlyway() throws Exception {
        Path output=directory.resolve("cli.jsonl"),trace=directory.resolve("classes.log");
        ProcessBuilder command=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin/java").toString(),"-Xlog:class+load=info:file="+trace,
                "-cp",System.getProperty("java.class.path"),CatalogCompatibilityAudit.class.getName());
        command.environment().keySet().removeIf(key->key.startsWith("AUDIT_")||key.startsWith("AWS_"));command.environment().putAll(database.environment());
        command.redirectOutput(output.toFile());command.redirectError(directory.resolve("cli.err").toFile());
        Process process=command.start();boolean finished=process.waitFor(30,java.util.concurrent.TimeUnit.SECONDS);if(!finished)process.destroyForcibly();
        assertThat(finished).isTrue();assertThat(process.exitValue()).isZero();assertThat(Files.readString(output)).contains("\"status\":\"COMPLETE\"");
        assertThat(Files.readString(trace)).doesNotContain("org.springframework.boot.SpringApplication source:","org.flywaydb.core.Flyway source:","org.hibernate.internal.SessionFactoryImpl source:");
    }
    @Test void baselineSql_shouldExposeRealV27NameCollisionsAndPublishedReadinessFailures() throws Exception {
        seed("Laptop","{\"schema_version\":1,\"values\":{}}");
        try(Connection owner=database.owner()) {
            execute(owner,"INSERT INTO categories(id,name) VALUES (?,' laptop ')",UUID.randomUUID());
            execute(owner,"INSERT INTO brands(id,code,name) VALUES (?,'one','Brand'),(?,'two',' brand ')",UUID.randomUUID(),UUID.randomUUID());
            execute(owner,"UPDATE products SET status='PUBLISHED'");execute(owner,"UPDATE product_variants SET selling_price=0");
            String before=V27AuditDatabase.fingerprint(owner);
            try(Connection reader=database.reader()) {
                var sets=V27AuditDatabase.script(reader,Path.of("../specs/013-admin-catalog-management/contracts/data-preflight.sql"));
                assertThat(sets.get(3)).extracting(row->row.get("kind")).containsExactlyInAnyOrder("brand","category");
                assertThat(sets.get(14)).hasSize(1);assertThat(sets.get(14).getFirst()).containsEntry("invalid_sale_prices","1").containsEntry("images","0");
            }
            assertThat(V27AuditDatabase.fingerprint(owner)).isEqualTo(before);
        }
    }
    private UUID seed(String categoryName,String facts) throws Exception {
        UUID category=UUID.randomUUID(),product=UUID.randomUUID(),variant=UUID.randomUUID();
        try(Connection owner=database.owner()) {
            owner.setAutoCommit(false);execute(owner,"INSERT INTO categories(id,name) VALUES (?,?)",category,categoryName);
            execute(owner,"INSERT INTO products(id,category_id,name,status) VALUES (?,?,'Fixture','DRAFT')",product,category);
            execute(owner,"INSERT INTO product_variants(id,product_id,sku,option_combination,selling_price,technical_specifications) VALUES (?,?,?,'{}',10,CAST(? AS jsonb))",variant,product,"SKU-"+variant,facts);
            owner.commit();
        }return variant;
    }
}
