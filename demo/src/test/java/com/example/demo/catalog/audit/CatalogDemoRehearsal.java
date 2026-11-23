package com.example.demo.catalog.audit;

import static com.example.demo.catalog.audit.V27AuditDatabase.execute;
import static com.example.demo.catalog.audit.V27AuditDatabase.scalar;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import org.flywaydb.core.Flyway;
import tools.jackson.databind.json.JsonMapper;

/** Operator-only T014/T088 representative synthetic drill, never a demo-target migrator. */
public final class CatalogDemoRehearsal {
    private CatalogDemoRehearsal() { }
    public static void main(String[] args) throws Exception {
        if(args.length!=1)throw new IllegalArgumentException("Owned private registry required");
        Path registry=Path.of(args[0]),folder=registry.getParent();
        CatalogDemoPreparation prepared=new CatalogDemoPreparation(registry);
        prepared.verifyOwnership("rehearsal");
        Map<String,Object> env=prepared.environment("rehearsal");
        assertThat((String)env.get("project")).endsWith("-rehearsal");
        assertThat(env.get("database")).isEqualTo("admin013_rehearsal");
        assertThat(Files.exists(folder.resolve("g1-summary.json"))).isTrue();
        String container=((List<?>)env.get("containerIds")).getFirst().toString();
        String user=(String)env.get("username"),password=(String)env.get("password"),url=prepared.url("rehearsal");
        Map<String,String> before;
        try(Connection db=prepared.owner("rehearsal")){
            assertThat(scalar(db,"SELECT MAX(version::int) FROM flyway_schema_history WHERE success")).isEqualTo("27");
            before=preserved(db);
            assertThat(scalar(db,"SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='products' AND column_name='version'")).isEqualTo("0");
            Files.writeString(folder.resolve("rehearsal-before.json"),JsonMapper.builder().build().writeValueAsString(before));
            // New databases are confined to this verified, newly created rehearsal container.
            if(scalar(db,"SELECT COUNT(*) FROM pg_database WHERE datname='admin013_fresh28'").equals("0"))execute(db,"CREATE DATABASE admin013_fresh28 TEMPLATE template0");
            if(scalar(db,"SELECT COUNT(*) FROM pg_database WHERE datname='admin013_restored28'").equals("0"))execute(db,"CREATE DATABASE admin013_restored28 TEMPLATE template0");
        }
        List<Map<String,String>> locks=new ArrayList<>();long started=System.nanoTime();
        try(var executor=Executors.newSingleThreadExecutor()){
            var migration=executor.submit(()->Flyway.configure().dataSource(url,user,password).target("28").load().migrate());
            while(!migration.isDone()){
                try(Connection monitor=prepared.owner("rehearsal")){
                    locks.add(Map.of("elapsedMillis",Long.toString((System.nanoTime()-started)/1_000_000),
                            "locks",scalar(monitor,"SELECT COUNT(*) FROM pg_locks WHERE database=(SELECT oid FROM pg_database WHERE datname=CURRENT_DATABASE())"),
                            "waiting",scalar(monitor,"SELECT COUNT(*) FROM pg_locks WHERE database=(SELECT oid FROM pg_database WHERE datname=CURRENT_DATABASE()) AND NOT granted")));
                }
                Thread.sleep(10);
            }
            migration.get();
        }
        long elapsed=(System.nanoTime()-started)/1_000_000;
        Map<String,String> after;
        try(Connection db=prepared.owner("rehearsal")){
            after=preserved(db);assertThat(after).isEqualTo(before);integrity(db);
        }
        String prefix=url.substring(0,url.lastIndexOf('/')+1);
        Flyway.configure().dataSource(prefix+"admin013_fresh28",user,password).target("28").load().migrate();
        String schema=schema(container,"admin013_rehearsal",user);
        assertThat(schema(container,"admin013_fresh28",user)).isEqualTo(schema);
        String dump="/tmp/admin013-upgraded.dump";
        command("docker","exec",container,"pg_dump","-U",user,"-d","admin013_rehearsal","--format=custom","--no-owner","--no-acl","--file="+dump);
        command("docker","cp",container+":"+dump,folder.resolve("upgraded-v28.dump").toString());
        String sql=command("docker","exec",container,"pg_restore","--no-owner","--no-acl","--file=-",dump);
        String reset="SELECT pg_catalog.set_config('search_path', '', false);";
        assertThat(sql.split(java.util.regex.Pattern.quote(reset),-1)).hasSize(2);
        sql=sql.replace(reset,"SELECT pg_catalog.set_config('search_path', 'pg_catalog,public', false);");
        Path restore=folder.resolve("restore-v28.sql");Files.writeString(restore,sql);
        Process process=new ProcessBuilder("docker","exec","-i",container,"psql","-U",user,"-d","admin013_restored28","-X","-v","ON_ERROR_STOP=1","--single-transaction")
                .redirectOutput(folder.resolve("v28-backup-restore.log").toFile()).redirectErrorStream(true).start();
        try(var input=process.getOutputStream()){input.write(sql.getBytes(StandardCharsets.UTF_8));}
        assertThat(process.waitFor()).isZero();
        try(Connection db=DriverManager.getConnection(prefix+"admin013_restored28",user,password)){
            assertThat(preserved(db)).isEqualTo(after);integrity(db);
        }
        assertThat(schema(container,"admin013_restored28",user)).isEqualTo(schema);
        Map<String,Object> evidence=new LinkedHashMap<>();evidence.put("scope","SYNTHETIC_DEMO_REHEARSAL_ONLY");
        evidence.put("containerId",container);evidence.put("upgradeMillis",elapsed);evidence.put("lockSamples",locks);
        evidence.put("beforeV27",before);evidence.put("afterV28",after);evidence.put("restoredV28",after);
        evidence.put("schemaSha256",java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(schema.getBytes(StandardCharsets.UTF_8))));
        evidence.put("status","REHEARSAL_AND_POST_RESTORE_INTEGRITY_COMPLETE");
        Files.writeString(folder.resolve("rehearsal-integrity.json"),JsonMapper.builder().build().writeValueAsString(evidence));
        Files.writeString(folder.resolve("v28-schema.sql"),schema);
        System.out.println("OWNED_REHEARSAL_V28_FRESH_UPGRADE_RESTORE_PASS "+elapsed+"ms");
    }
    private static Map<String,String> preserved(Connection db) throws Exception {
        Map<String,String> hashes=new LinkedHashMap<>();
        List<String> tables=new ArrayList<>();
        try(Statement query=db.createStatement();var result=query.executeQuery("SELECT tablename FROM pg_tables WHERE schemaname='public' AND tablename<>'catalog_taxonomy_state' ORDER BY tablename")){
            while(result.next())tables.add(result.getString(1));
        }
        for(String table:tables){
            assertThat(table).matches("[a-z_]+");
            String columns=switch(table){case "products"->"- 'version' - 'last_restored_at'";case "product_variants"->"- 'enabled'";case "product_images"->"- 'active'";case "categories","brands"->"- 'version'";default->"";};
            String predicate=table.equals("flyway_schema_history")?" WHERE version::int<=27":"";
            hashes.put(table,scalar(db,"SELECT COUNT(*)||':'||COALESCE(MD5(STRING_AGG(value::text,',' ORDER BY value::text)),'empty') FROM (SELECT TO_JSONB(t) "+columns+" AS value FROM public.\""+table+"\" t"+predicate+") rows"));
        }
        return hashes;
    }
    private static void integrity(Connection db) throws Exception {
        db.setReadOnly(true);db.setAutoCommit(false);
        try{
            execute(db,"SET TRANSACTION READ ONLY");
            assertThat(scalar(db,"SELECT MAX(version::int) FROM flyway_schema_history WHERE success")).isEqualTo("28");
            assertThat(scalar(db,"SELECT COUNT(*) FROM catalog_taxonomy_state WHERE id=1 AND version=0")).isEqualTo("1");
            assertThat(scalar(db,"SELECT COUNT(*) FROM products WHERE version<>0 OR last_restored_at IS NOT NULL OR status NOT IN ('DRAFT','PUBLISHED','UNPUBLISHED','ARCHIVED')")).isEqualTo("0");
            assertThat(scalar(db,"SELECT COUNT(*) FROM product_variants WHERE NOT enabled")).isEqualTo("0");
            assertThat(scalar(db,"SELECT COUNT(*) FROM product_images WHERE NOT active")).isEqualTo("0");
            assertThat(scalar(db,"SELECT COUNT(*) FROM pg_constraint x JOIN pg_namespace n ON n.oid=x.connamespace WHERE n.nspname='public' AND NOT convalidated")).isEqualTo("0");
            assertThat(scalar(db,"""
                SELECT COUNT(*) FROM products p WHERE p.status='PUBLISHED' AND (
                NOT EXISTS(SELECT 1 FROM product_variants v WHERE v.product_id=p.id AND v.enabled)
                OR EXISTS(SELECT 1 FROM product_variants v WHERE v.product_id=p.id AND v.enabled AND v.selling_price<=0)
                OR NOT EXISTS(SELECT 1 FROM product_images i WHERE i.product_id=p.id AND i.active)
                OR (SELECT COUNT(*) FROM product_images i WHERE i.product_id=p.id AND i.active AND i.is_primary)<>1)
                """)).isEqualTo("0");
        }finally{db.rollback();db.setAutoCommit(true);db.setReadOnly(false);}
        // The script owns a fresh REPEATABLE READ transaction; never nest it after reads.
        var sets=V27AuditDatabase.script(db,Path.of("../specs/013-admin-catalog-management/contracts/data-preflight-allocation-integrity.sql"));
        for(var set:sets.subList(1,sets.size()))assertThat(set).isEmpty();
    }
    private static String schema(String container,String database,String user) throws Exception {
        String schema=command("docker","exec",container,"pg_dump","-U",user,"-d",database,"--schema-only","--no-owner","--no-acl");
        schema=schema.replaceAll("(?m)^--.*\\n","").replaceAll("(?m)^\\\\(?:un)?restrict .*\\n","");
        var pattern=java.util.regex.Pattern.compile("\\(ARRAY\\[((?:'[^']*'::character varying(?:, )?)+)\\]\\)::text\\[\\]");
        return pattern.matcher(schema).replaceAll(m->java.util.regex.Matcher.quoteReplacement("ARRAY["+m.group(1).replaceAll("('[^']*'::character varying)","($1)::text")+"]"));
    }
    private static String command(String... args) throws Exception {
        Process process=new ProcessBuilder(args).redirectErrorStream(true).start();
        String output=new String(process.getInputStream().readAllBytes(),StandardCharsets.UTF_8);
        assertThat(process.waitFor()).as("Owned rehearsal command exit").isZero();return output;
    }
}
