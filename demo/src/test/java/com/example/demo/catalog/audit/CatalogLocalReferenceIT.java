package com.example.demo.catalog.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static com.example.demo.catalog.audit.V27AuditDatabase.execute;
import static com.example.demo.catalog.audit.V27AuditDatabase.scalar;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Synthetic local environment proof only; never a G1 target audit or a V28 migration rehearsal. */
class CatalogLocalReferenceIT {
    private static final UUID CATEGORY=UUID.fromString("00000000-0000-0000-0000-000000000011");
    private static final UUID PRODUCT=UUID.fromString("00000000-0000-0000-0000-000000000012");
    private static final UUID VARIANT=UUID.fromString("00000000-0000-0000-0000-000000000013");

    @Test void reference_shouldInitializeV27DeterministicallyAndRestoreTwoIsolatedCopies(@TempDir Path directory) throws Exception {
        try(var database=new V27AuditDatabase()){
            database.start(directory);
            String reference,structure,business;
            try(Connection owner=database.owner()){
                seed(owner);
                assertV27(owner);
                reference=V27AuditDatabase.fingerprint(owner);structure=structure(owner);business=business(owner);
            }
            assertThat(database.audit(database.environment()).code()).isZero();
            database.dumpReference();
            for(String name:List.of("admin013_copy_a","admin013_copy_b")){
                database.restoreCopy(name);
                try(Connection copy=database.copyOwner(name)){
                    assertV27(copy);
                    assertThat(V27AuditDatabase.fingerprint(copy)).isEqualTo(reference);
                    assertThat(structure(copy)).isEqualTo(structure);
                }
                var audit=database.auditCopy(name);
                assertThat(audit.code()).isZero();assertThat(audit.output()).contains("\"status\":\"COMPLETE\"");
                for(String script:List.of("data-preflight.sql","data-preflight-allocation-integrity.sql")){
                    try(Connection original=database.reader();Connection copy=database.copyReader(name)){
                        var expected=V27AuditDatabase.script(original,Path.of("../specs/013-admin-catalog-management/contracts/"+script));
                        var actual=V27AuditDatabase.script(copy,Path.of("../specs/013-admin-catalog-management/contracts/"+script));
                        // The first result carries this run's database identity/time, which must differ.
                        assertThat(actual.subList(1,actual.size())).isEqualTo(expected.subList(1,expected.size()));
                    }
                }
                try(Connection copy=database.copyOwner(name)){assertThat(V27AuditDatabase.fingerprint(copy)).isEqualTo(reference);}
            }
            database.initializeFreshV27("admin013_fresh_v27");
            try(Connection fresh=database.copyOwner("admin013_fresh_v27")){
                seed(fresh);assertV27(fresh);
                assertThat(structure(fresh)).isEqualTo(structure);
                assertThat(business(fresh)).isEqualTo(business);
            }
            Path artifact=database.exportSyntheticReference(Path.of("target","admin013-local-reference",UUID.randomUUID().toString()));
            assertThat(java.nio.file.Files.size(artifact.resolve("reference-v27.dump"))).isPositive();
            System.out.println("ADMIN013_LOCAL_REFERENCE "+artifact.toAbsolutePath());
            // Destructive fixture work is confined to one owned copy and cannot alter reference/other copy.
            try(Connection copy=database.copyOwner("admin013_copy_a")){execute(copy,"UPDATE products SET name='synthetic-copy-only'");}
            try(Connection owner=database.owner();Connection other=database.copyOwner("admin013_copy_b")){
                assertThat(V27AuditDatabase.fingerprint(owner)).isEqualTo(reference);
                assertThat(V27AuditDatabase.fingerprint(other)).isEqualTo(reference);
            }
        }
    }

    private void seed(Connection connection) throws Exception {
        connection.setAutoCommit(false);
        try{
            execute(connection,"INSERT INTO categories(id,name) VALUES (?,'Laptop')",CATEGORY);
            execute(connection,"INSERT INTO products(id,category_id,name,status) VALUES (?,?,'Synthetic reference V27','DRAFT')",PRODUCT,CATEGORY);
            execute(connection,"INSERT INTO product_variants(id,product_id,sku,selling_price,option_combination,technical_specifications) VALUES (?,?,'SYNTHETIC-REF-V27',1000,'{}','{\"schema_version\":1,\"values\":{\"physical_ram\":{\"value\":8,\"unit\":\"GB\"}}}')",VARIANT,PRODUCT);
            connection.commit();
        }catch(Exception failure){connection.rollback();throw failure;}
        finally{connection.setAutoCommit(true);}
    }
    private void assertV27(Connection connection) throws Exception {
        assertThat(connection.getMetaData().getDatabaseMajorVersion()).isEqualTo(17);
        assertThat(scalar(connection,"SELECT COUNT(*) FROM flyway_schema_history WHERE success")).isEqualTo("27");
        assertThat(scalar(connection,"SELECT MAX(version::integer) FROM flyway_schema_history")).isEqualTo("27");
        assertThat(scalar(connection,"SELECT to_regclass('public.catalog_taxonomy_state') IS NULL")).isEqualTo("t");
        assertThat(scalar(connection,"SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='product_variants' AND column_name='enabled'")).isEqualTo("0");
        assertThat(scalar(connection,"SELECT COUNT(*) FROM pg_extension WHERE extname='pg_trgm'")).isEqualTo("1");
        assertThat(scalar(connection,"SELECT stock||':'||reserved FROM inventory_balances WHERE original_variant_id='"+VARIANT+"'")).isEqualTo("0:0");
    }
    private String business(Connection connection) throws Exception {
        return scalar(connection,"SELECT row_to_json(p)::text FROM products p WHERE id='"+PRODUCT+"'")
                +scalar(connection,"SELECT row_to_json(v)::text FROM product_variants v WHERE id='"+VARIANT+"'")
                +scalar(connection,"SELECT row_to_json(b)::text FROM inventory_balances b WHERE original_variant_id='"+VARIANT+"'");
    }
    // pg_dump reparses varchar-array-to-text casts into per-element casts on restore.
    // Canonicalize only that known equivalent expression; retain all other schema text.
    private String canonicalDefinition(String definition) {
        var pattern=java.util.regex.Pattern.compile("\\(ARRAY\\[((?:'[^']*'::character varying(?:, )?)+)\\]\\)::text\\[\\]");
        var matcher=pattern.matcher(definition);
        return matcher.replaceAll(match -> java.util.regex.Matcher.quoteReplacement("ARRAY["+
                match.group(1).replaceAll("('[^']*'::character varying)","($1)::text")+"]"));
    }
    private String structure(Connection connection) throws Exception {
        List<String> rows=new ArrayList<>();
        for(String sql:List.of(
                "SELECT table_name||':'||column_name||':'||data_type||':'||is_nullable||':'||coalesce(column_default,'') FROM information_schema.columns WHERE table_schema='public' ORDER BY table_name,ordinal_position",
                "SELECT c.relname||':'||x.conname||':'||pg_get_constraintdef(x.oid) FROM pg_constraint x JOIN pg_class c ON c.oid=x.conrelid JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' ORDER BY c.relname,x.conname",
                "SELECT tablename||':'||indexname||':'||indexdef FROM pg_indexes WHERE schemaname='public' ORDER BY tablename,indexname",
                "SELECT pg_get_triggerdef(t.oid) FROM pg_trigger t JOIN pg_class c ON c.oid=t.tgrelid JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND NOT t.tgisinternal ORDER BY c.relname,t.tgname",
                "SELECT pg_get_functiondef(p.oid) FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname='public' AND p.prokind='f' ORDER BY p.proname,pg_get_function_identity_arguments(p.oid)",
                "SELECT sequencename||':'||coalesce(last_value::text,'unused') FROM pg_sequences WHERE schemaname='public' ORDER BY sequencename",
                "SELECT extname||':'||extversion FROM pg_extension ORDER BY extname")){
            try(Statement query=connection.createStatement();var result=query.executeQuery(sql)){while(result.next())rows.add(canonicalDefinition(result.getString(1)));}
        }
        return String.join("\n",rows);
    }
}
