package com.example.demo.catalog.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.demo.catalog.exception.InvalidCatalogRequestException;
import com.example.demo.catalog.search.ProductSearchNormalizer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Dedicated disposable instances only; no Spring context or project datasource is used. */
@Execution(ExecutionMode.SAME_THREAD)
class CatalogSearchMigrationIT {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final List<String> FUNCTIONS = List.of("catalog_search_normalize_v1",
            "catalog_search_alnum_v1", "catalog_search_digit_v1", "catalog_search_decimal_start_v1",
            "catalog_search_tokens_v1", "catalog_search_natural_name_v1");

    @Test
    void freshFlywayInstallProvidesImmutablePrimitivesAndExtension() throws Exception {
        try (PostgreSQLContainer<?> database = database()) {
            database.start();
            DriverManagerDataSource source = source(database);
            Flyway flyway = flyway(source, "15");
            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(15);
            flyway.validate();
            JdbcTemplate jdbc = new JdbcTemplate(source);
            assertSupport(jdbc);
            int compared = assertJavaParity(jdbc);
            evidence("fresh", database, jdbc, Map.of("parityCases", compared));
        }
    }

    @Test
    void v14UpgradePreservesEveryBusinessRowHistoryAndConstraint() throws Exception {
        try (PostgreSQLContainer<?> database = database()) {
            database.start();
            DriverManagerDataSource source = source(database);
            JdbcTemplate jdbc = new JdbcTemplate(source);
            flyway(source, "14").migrate();
            seed(source);
            Map<String, String> before = businessRows(jdbc);
            List<Map<String, Object>> history = oldHistory(jdbc);
            List<String> constraints = constraints(jdbc);
            List<String> triggers = jdbc.queryForList("SELECT pg_get_triggerdef(oid) FROM pg_trigger "
                    + "WHERE tgrelid IN (SELECT oid FROM pg_class WHERE relnamespace='public'::regnamespace) "
                    + "AND NOT tgisinternal ORDER BY tgname", String.class);
            List<String> indexes = jdbc.queryForList("SELECT indexdef FROM pg_indexes WHERE schemaname='public' ORDER BY indexname", String.class);
            assertThat(flyway(source, "15").migrate().migrationsExecuted).isEqualTo(1);
            flyway(source, "15").validate();
            assertThat(businessRows(jdbc)).isEqualTo(before);
            assertThat(oldHistory(jdbc)).isEqualTo(history);
            assertThat(constraints(jdbc)).isEqualTo(constraints);
            assertThat(jdbc.queryForList("SELECT pg_get_triggerdef(oid) FROM pg_trigger "
                    + "WHERE tgrelid IN (SELECT oid FROM pg_class WHERE relnamespace='public'::regnamespace) "
                    + "AND NOT tgisinternal ORDER BY tgname", String.class)).isEqualTo(triggers);
            assertThat(jdbc.queryForList("SELECT indexdef FROM pg_indexes WHERE schemaname='public' ORDER BY indexname", String.class)).isEqualTo(indexes);
            assertSupport(jdbc);
            assertThatThrownBy(() -> jdbc.update("UPDATE product_variants SET selling_price=1.5"))
                    .isInstanceOf(DataAccessException.class);
            assertThatThrownBy(() -> jdbc.update("UPDATE product_variants SET technical_specifications='{\"schema_version\":2,\"values\":{}}'::jsonb"))
                    .isInstanceOf(DataAccessException.class);
            assertThatThrownBy(() -> jdbc.update("INSERT INTO products(id,category_id,name,status) "
                    + "SELECT ?,id,'orphan','PUBLISHED' FROM categories LIMIT 1", UUID.randomUUID()))
                    .isInstanceOf(DataAccessException.class);
            assertThat(businessRows(jdbc)).isEqualTo(before);
            int compared = assertJavaParity(jdbc);
            Map<String, String> hashes = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : before.entrySet()) {
                hashes.put(entry.getKey(), HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                        .digest(entry.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8))));
            }
            evidence("upgrade", database, jdbc, Map.of("businessRowsUnchanged", true,
                    "beforeAfterRowHashes", hashes, "v1ToV14HistoryUnchanged", true,
                    "oldHistory", history, "constraintTriggerIndexDefinitionsUnchanged", true,
                    "parityCases", compared));
        }
    }

    @Test
    void v16FreshAndUpgradePreserveBusinessRowsAndOldChecksums() throws Exception {
        for (boolean upgrade : List.of(false,true)) {
            try (PostgreSQLContainer<?> database = database()) {
                database.start();
                var source=source(database);var jdbc=new JdbcTemplate(source);
                Map<String,String> before=null;List<Map<String,Object>> history=null;
                if(upgrade){flyway(source,"15").migrate();seed(source);before=businessRows(jdbc);
                    history=jdbc.queryForList("SELECT version,checksum,script FROM flyway_schema_history ORDER BY installed_rank");}
                assertThat(flyway(source,"16").migrate().migrationsExecuted).isEqualTo(upgrade?1:16);
                flyway(source,"16").validate();
                if(upgrade){assertThat(businessRows(jdbc)).isEqualTo(before);
                    assertThat(jdbc.queryForList("SELECT version,checksum,script FROM flyway_schema_history WHERE version::integer<=15 ORDER BY installed_rank")).isEqualTo(history);}
                assertThat(jdbc.queryForObject("SELECT to_regprocedure('public.catalog_search_phrase_v1(text,text)') IS NOT NULL",Boolean.class)).isTrue();
                assertThat(assertJavaParity(jdbc)).isEqualTo(53);
                assertThat(jdbc.queryForObject("SELECT catalog_search_protected_v1('galaxy',' galaxy ')",Boolean.class)).isTrue();
                assertThat(jdbc.queryForObject("SELECT catalog_search_phrase_v1('sony alpha device','sony alpha')",Boolean.class)).isTrue();
                assertThat(jdbc.queryForObject("SELECT catalog_search_phrase_v1('xsony alpha','sony alpha')",Boolean.class)).isFalse();
                evidence(upgrade?"v16-upgrade":"v16-fresh",database,jdbc,Map.of("v1ToV15ChecksumsPreserved",true,"businessRowsUnchanged",true));
            }
        }
    }

    @Test
    void transactionalSuccessorFailure_shouldRetainSearchPrefix_thenRetryIdenticalArtifactWithoutRepair() throws Exception {
        Path temporary=Files.createTempDirectory("catalog-search-recovery-");
        Path successor=temporary.resolve("V20__transient_recovery_fixture.sql");
        String artifact="CREATE TABLE recovery_successor(id integer); INSERT INTO recovery_successor VALUES (1); SELECT 1 / CASE WHEN allowed THEN 1 ELSE 0 END FROM recovery_condition;";
        Files.writeString(successor,artifact);
        try (PostgreSQLContainer<?> database=database()) {
            database.start();var source=source(database);var jdbc=new JdbcTemplate(source);
            flyway(source,"15").migrate();seed(source);var before=businessRows(jdbc);
            jdbc.execute("CREATE TABLE recovery_condition(allowed boolean)");jdbc.execute("INSERT INTO recovery_condition VALUES(false)");
            var migration=Flyway.configure().dataSource(source).locations("classpath:db/migration","filesystem:"+temporary).load();
            assertThatThrownBy(migration::migrate).hasStackTraceContaining("division by zero");
            assertThat(jdbc.queryForObject("SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1",String.class)).isEqualTo("19");
            assertThat(jdbc.queryForObject("SELECT to_regclass('public.recovery_successor') IS NULL",Boolean.class)).isTrue();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE version='20'",Integer.class)).isZero();
            for(var entry:before.entrySet())assertThat(businessRows(jdbc).get(entry.getKey())).isEqualTo(entry.getValue());
            jdbc.execute("UPDATE recovery_condition SET allowed=true");
            assertThat(Files.readString(successor)).isEqualTo(artifact);
            assertThat(migration.migrate().migrationsExecuted).isEqualTo(1);migration.validate();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM recovery_successor",Integer.class)).isOne();
            Files.writeString(successor,artifact+"-- intentionally mismatched disposable control");
            assertThatThrownBy(migration::validate).hasStackTraceContaining("checksum mismatch");
            evidence("successor-recovery",database,jdbc,Map.of("sameArtifactRetry",true,"successfulPrefixRetained",true,"mismatchedChecksumDetected",true,"repairUsed",false));
        } finally {Files.deleteIfExists(successor);Files.deleteIfExists(temporary);}
    }

    @Test
    void v17FreshAndUpgrade_shouldProvideConservativeTypoGuard_withoutChangingOldData()throws Exception{
        for(boolean upgrade:List.of(false,true))try(PostgreSQLContainer<?> database=database()){
            database.start();var source=source(database);var jdbc=new JdbcTemplate(source);
            Map<String,String> before=null;
            if(upgrade){flyway(source,"15").migrate();seed(source);before=businessRows(jdbc);}
            assertThat(flyway(source,"17").migrate().migrationsExecuted).isEqualTo(upgrade?2:17);flyway(source,"17").validate();
            if(upgrade)assertThat(businessRows(jdbc)).isEqualTo(before);
            assertThat(jdbc.queryForObject("SELECT to_regprocedure('public.catalog_search_typo_v1(text,text)') IS NOT NULL",Boolean.class)).isTrue();
            for(var pair:List.of(List.of("samsnug","samsung"),List.of("appel","apple"),List.of("lenvo","lenovo"),List.of("samzung","samsung")))assertThat(jdbc.queryForObject("SELECT catalog_search_typo_v1(?,?)",Boolean.class,pair.get(0),pair.get(1))).isTrue();
            for(var pair:List.of(List.of("samsung","samson"),List.of("sony","sonic"),List.of("lenovo","lenox")))assertThat(jdbc.queryForObject("SELECT catalog_search_typo_v1(?,?)",Boolean.class,pair.get(0),pair.get(1))).isFalse();
            assertThat(jdbc.queryForObject("SELECT provolatile='i' AND proparallel='s' FROM pg_proc WHERE oid='catalog_search_typo_v1(text,text)'::regprocedure",Boolean.class)).isTrue();
            evidence(upgrade?"v17-upgrade":"v17-fresh",database,jdbc,Map.of("businessRowsUnchanged",true,"guardVerified",true));
        }
    }

    @Test
    void v18FreshAndV15Upgrade_shouldPreserveDataAndProveNormalizationParity()throws Exception{
        for(boolean upgrade:List.of(false,true))try(PostgreSQLContainer<?> database=database()){
            database.start();var source=source(database);var jdbc=new JdbcTemplate(source);Map<String,String> before=null;
            if(upgrade){flyway(source,"15").migrate();seed(source);before=businessRows(jdbc);}
            assertThat(flyway(source,"18").migrate().migrationsExecuted).isEqualTo(upgrade?3:18);flyway(source,"18").validate();
            if(upgrade)assertThat(businessRows(jdbc)).isEqualTo(before);
            assertThat(jdbc.queryForObject("SELECT to_regprocedure('public.catalog_search_safe_normalize_v2(text)') IS NOT NULL",Boolean.class)).isTrue();
            var normalizer=new ProductSearchNormalizer();
            var inputs=JSON.readTree(Path.of(System.getProperty("basedir"),"..","specs","007-product-search-filter","evidence","normalization-parity-inputs.json").toFile());
            int compared=0;for(var value:inputs){String input=value.isNull()?null:value.asString();
                try{String expected=normalizer.normalize(input);assertThat(jdbc.queryForObject("SELECT catalog_search_normalize_v2(?)",String.class,input)).isEqualTo(expected);}
                catch(InvalidCatalogRequestException invalid){assertThatThrownBy(()->jdbc.queryForObject("SELECT catalog_search_normalize_v2(?)",String.class,input)).isInstanceOf(DataAccessException.class);}
                compared++;
            }
            int sourceCompared=0,invalidFields=0;
            if(!upgrade){var corpus=JSON.readTree(getClass().getResourceAsStream("/catalog/search/source-workload-v1.json"));
                for(var record:corpus.get("records"))for(String key:List.of("name","description","brand")){
                    String input=record.get(key).isNull()?null:record.get(key).asString();
                    try{var expected=normalizer.normalize(input);assertThat(jdbc.queryForObject("SELECT catalog_search_normalize_v2(?)",String.class,input)).isEqualTo(expected);}
                    catch(InvalidCatalogRequestException invalid){invalidFields++;assertThat(jdbc.queryForObject("SELECT catalog_search_safe_normalize_v2(?)",String.class,input)).isEmpty();}
                    sourceCompared++;
                }
            }
            assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_proc WHERE proname IN ('catalog_search_normalize_v2','catalog_search_safe_normalize_v2') AND provolatile='i' AND proparallel='s'",Integer.class)).isEqualTo(2);
            evidence(upgrade?"v18-upgrade":"v18-fresh",database,jdbc,Map.of("businessRowsUnchanged",true,"primitiveParityCases",compared,"sourceFieldsCompared",sourceCompared,"invalidSourceFieldsExcluded",invalidFields));
        }
    }

    @Test
    void v19FreshAndUpgrade_shouldBackfillAndMaintainExactGeneratedTokens_withoutChangingBusinessFields()throws Exception{
        for(int from:List.of(0,14,15))try(PostgreSQLContainer<?> database=database()){
            database.start();boolean upgrade=from>0;var source=source(database);var jdbc=new JdbcTemplate(source);Map<String,String> before=null;
            if(upgrade){flyway(source,Integer.toString(from)).migrate();seed(source);before=businessRows(jdbc);}
            assertThat(flyway(source,"19").migrate().migrationsExecuted).isEqualTo(19-from);flyway(source,"19").validate();
            if(upgrade)assertThat(businessRows(jdbc)).isEqualTo(before);
            assertThat(jdbc.queryForObject(com.example.demo.catalog.search.CatalogSearchCapabilityState.SQL,Boolean.class)).isTrue();
            if(upgrade){
                assertThat(jdbc.queryForObject("SELECT bool_and(catalog_description_tokens_v1=ARRAY(SELECT token FROM catalog_search_tokens_v1(description))) FROM products",Boolean.class)).isTrue();
                jdbc.update("UPDATE products SET description=?","ĐIỆN thoại C++ 16.0 GB");
                assertThat(jdbc.queryForObject("SELECT bool_and(catalog_description_tokens_v1=ARRAY['dien','thoai','c++','16.0','gb']) FROM products",Boolean.class)).isTrue();
                jdbc.update("UPDATE products SET description=?","bad"+(char)151+"source");
                assertThat(jdbc.queryForObject("SELECT bool_and(cardinality(catalog_description_tokens_v1)=0) FROM products",Boolean.class)).isTrue();
            }
            evidence(from==14?"v19-upgrade14":upgrade?"v19-upgrade":"v19-fresh",database,jdbc,Map.of("originalBusinessFieldsUnchangedBeforeExplicitFixtureUpdates",true,"generatedTokenBackfillAndMaintenanceVerified",true));
        }
    }

    @Test
    void missingInstallationPrivilegeBlocksV15WithoutInstallingObjects() throws Exception {
        try (PostgreSQLContainer<?> database = database()) {
            database.start();
            DriverManagerDataSource owner = source(database);
            flyway(owner, "14").migrate();
            JdbcTemplate jdbc = new JdbcTemplate(owner);
            jdbc.execute("CREATE ROLE t015_migrator LOGIN PASSWORD 'disposable-only' NOSUPERUSER NOCREATEDB NOCREATEROLE");
            jdbc.execute("GRANT CONNECT ON DATABASE " + database.getDatabaseName() + " TO t015_migrator");
            jdbc.execute("GRANT USAGE,CREATE ON SCHEMA public TO t015_migrator");
            jdbc.execute("GRANT SELECT,INSERT,UPDATE,DELETE ON ALL TABLES IN SCHEMA public TO t015_migrator");
            DriverManagerDataSource restricted = new DriverManagerDataSource(database.getJdbcUrl(), "t015_migrator", "disposable-only");
            assertThatThrownBy(() -> flyway(restricted, "15").migrate())
                    .hasStackTraceContaining("pg_trgm installation requires CREATE privilege");
            assertNoV15Objects(jdbc);
            evidence("missing-privilege", database, jdbc, Map.of("migrationBlocked", true));
        }
    }

    @Test
    void missingExtensionPackageBlocksV15WithoutFallback() throws Exception {
        try (PostgreSQLContainer<?> database = database()) {
            database.start();
            DriverManagerDataSource source = source(database);
            flyway(source, "14").migrate();
            String shared = database.execInContainer("pg_config", "--sharedir").getStdout().strip();
            assertThat(database.execInContainer("mv", shared + "/extension/pg_trgm.control", "/tmp/t015-pg_trgm.control")
                    .getExitCode()).isZero();
            JdbcTemplate jdbc = new JdbcTemplate(source);
            assertThatThrownBy(() -> flyway(source, "15").migrate())
                    .hasStackTraceContaining("required pg_trgm package is unavailable");
            assertNoV15Objects(jdbc);
            evidence("missing-package", database, jdbc, Map.of("migrationBlocked", true));
        }
    }

    @Test
    void missingPinnedCollationBlocksV15BeforeAnyInstall() throws Exception {
        try (PostgreSQLContainer<?> database = database()) {
            database.start();
            DriverManagerDataSource source = source(database);
            flyway(source, "14").migrate();
            JdbcTemplate jdbc = new JdbcTemplate(source);
            jdbc.execute("DROP COLLATION pg_catalog.\"und-x-icu\"");
            assertThatThrownBy(() -> flyway(source, "15").migrate())
                    .hasStackTraceContaining("requires verified deterministic ICU collation");
            assertNoV15Objects(jdbc);
            evidence("missing-collation", database, jdbc, Map.of("migrationBlocked", true));
        }
    }

    private PostgreSQLContainer<?> database() {
        return new PostgreSQLContainer<>("postgres:17-alpine")
                .withDatabaseName("t015_disposable").withLabel("speckit.task", "T015")
                .withTmpFs(Map.of("/var/lib/postgresql/data", "rw"))
                .withReuse(false);
    }

    private DriverManagerDataSource source(PostgreSQLContainer<?> database) {
        return new DriverManagerDataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword());
    }

    private Flyway flyway(DriverManagerDataSource source, String target) {
        return Flyway.configure().dataSource(source).locations("classpath:db/migration").target(target).load();
    }

    private void assertSupport(JdbcTemplate jdbc) {
        assertThat(jdbc.queryForObject("SELECT max(version::integer) FROM flyway_schema_history WHERE success", Integer.class)).isEqualTo(15);
        assertThat(jdbc.queryForObject("SELECT extversion FROM pg_extension WHERE extname='pg_trgm'", String.class)).isEqualTo("1.6");
        assertThat(jdbc.queryForObject("SELECT 'sony' % 'sont'", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_proc WHERE pronamespace='public'::regnamespace "
                + "AND proname LIKE 'catalog_search_%' AND provolatile='i' AND proparallel='s'", Integer.class))
                .isEqualTo(FUNCTIONS.size());
        for (String function : FUNCTIONS) {
            assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_proc WHERE pronamespace='public'::regnamespace AND proname=?", Integer.class, function)).isOne();
        }
    }

    private int assertJavaParity(JdbcTemplate jdbc) throws Exception {
        Path inputs = Path.of(System.getProperty("basedir"), "..", "specs", "007-product-search-filter", "evidence", "normalization-parity-inputs.json");
        JsonNode corpus = JSON.readTree(inputs.toFile());
        ProductSearchNormalizer normalizer = new ProductSearchNormalizer();
        for (JsonNode value : corpus) {
            String input = value.isNull() ? null : value.asString();
            String normalized;
            List<ProductSearchNormalizer.Token> tokens;
            try {
                normalized = normalizer.normalize(input);
                tokens = normalizer.tokenize(input);
            } catch (InvalidCatalogRequestException exception) {
                assertThatThrownBy(() -> jdbc.queryForObject("SELECT public.catalog_search_normalize_v1(?)", String.class, input))
                        .isInstanceOf(DataAccessException.class);
                continue;
            }
            assertThat(jdbc.queryForObject("SELECT public.catalog_search_normalize_v1(?)", String.class, input)).isEqualTo(normalized);
            List<Map<String, Object>> sqlTokens = jdbc.queryForList("SELECT token,structurally_protected FROM public.catalog_search_tokens_v1(?)", input);
            assertThat(sqlTokens).hasSize(tokens.size());
            for (int i = 0; i < tokens.size(); i++) {
                assertThat(sqlTokens.get(i).get("token")).isEqualTo(tokens.get(i).getText());
                assertThat(sqlTokens.get(i).get("structurally_protected")).isEqualTo(tokens.get(i).isStructurallyProtected());
            }
            String natural = tokens.stream().filter(token -> !token.isStructurallyProtected())
                    .map(ProductSearchNormalizer.Token::getText).collect(java.util.stream.Collectors.joining(" "));
            assertThat(jdbc.queryForObject("SELECT public.catalog_search_natural_name_v1(?)", String.class, input)).isEqualTo(natural);
        }
        assertThat(corpus.size()).isEqualTo(53);
        return corpus.size();
    }

    private void assertNoV15Objects(JdbcTemplate jdbc) {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE version='15'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_proc WHERE pronamespace='public'::regnamespace AND proname LIKE 'catalog_search_%'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_extension WHERE extname='pg_trgm'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT max(version::integer) FROM flyway_schema_history WHERE success", Integer.class)).isEqualTo(14);
    }

    private Map<String, String> businessRows(JdbcTemplate jdbc) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String table : jdbc.queryForList("SELECT tablename FROM pg_tables WHERE schemaname='public' AND tablename<>'flyway_schema_history' ORDER BY tablename", String.class)) {
            String quoted = "\"" + table.replace("\"", "\"\"") + "\"";
            result.put(table, jdbc.queryForObject("SELECT coalesce(jsonb_agg(to_jsonb(t)-'catalog_description_tokens_v1' ORDER BY (to_jsonb(t)-'catalog_description_tokens_v1')::text),'[]'::jsonb)::text FROM public." + quoted + " t", String.class));
        }
        return result;
    }

    private List<Map<String, Object>> oldHistory(JdbcTemplate jdbc) {
        return jdbc.queryForList("SELECT installed_rank,version,description,type,script,checksum,installed_by,installed_on,execution_time,success "
                + "FROM flyway_schema_history WHERE version::integer<=14 ORDER BY installed_rank");
    }

    private List<String> constraints(JdbcTemplate jdbc) {
        return jdbc.queryForList("SELECT conname||':'||pg_get_constraintdef(oid) FROM pg_constraint WHERE connamespace='public'::regnamespace ORDER BY conname", String.class);
    }

    private void evidence(String name, PostgreSQLContainer<?> database, JdbcTemplate jdbc, Map<String, Object> extra) throws Exception {
        Map<String, Object> data = new LinkedHashMap<>(extra);
        data.put("containerId", database.getContainerId());
        data.put("image", database.getDockerImageName());
        data.put("version", jdbc.queryForObject("SELECT version()", String.class));
        data.put("encoding", jdbc.queryForObject("SHOW server_encoding", String.class));
        data.put("imageId", database.getContainerInfo().getImageId());
        data.put("mounts", database.getContainerInfo().getMounts().stream().map(mount -> mount.getDestination().getPath()).toList());
        data.put("collation", jdbc.queryForList("SELECT collname,collprovider,collisdeterministic,collversion,pg_collation_actual_version(oid) AS actual_version FROM pg_collation WHERE collnamespace='pg_catalog'::regnamespace AND collname='und-x-icu'"));
        data.put("history", jdbc.queryForList("SELECT installed_rank,version,checksum,script,success FROM flyway_schema_history ORDER BY installed_rank"));
        data.put("functions", jdbc.queryForList("SELECT proname,provolatile,proparallel,proconfig::text AS proconfig FROM pg_proc WHERE pronamespace='public'::regnamespace AND proname LIKE 'catalog_search_%' ORDER BY proname"));
        Path output = Path.of("target", "t015-evidence");
        Files.createDirectories(output);
        Files.writeString(output.resolve(name + ".json"), JSON.writeValueAsString(data));
    }

    private void seed(DriverManagerDataSource source) {
        JdbcTemplate jdbc = new JdbcTemplate(source);
        new TransactionTemplate(new DataSourceTransactionManager(source)).executeWithoutResult(status -> {
            UUID category = UUID.randomUUID();
            UUID brand = UUID.randomUUID();
            jdbc.update("INSERT INTO categories(id,name) VALUES (?,'Điện tử')", category);
            jdbc.update("INSERT INTO brands(id,code,name) VALUES (?,'fixture-brand','Fixture Brand')", brand);
            Long user = jdbc.queryForObject("INSERT INTO users(username,email,password,account_status,joined_at) VALUES ('t015-fixture','t015@example.invalid','fixture-only','ACTIVE',now()) RETURNING id", Long.class);
            for (String publication : List.of("PUBLISHED", "DRAFT", "ARCHIVED")) {
                UUID product = UUID.randomUUID();
                UUID variant = UUID.randomUUID();
                jdbc.update("INSERT INTO products(id,category_id,brand_id,name,description,status) VALUES (?,?,?,'Điện thoại WH-1000XM5','Fixture description',?)", product, category, brand, publication);
                jdbc.update("INSERT INTO product_variants(id,product_id,sku,option_combination,selling_price,technical_specifications) VALUES (?,?,?,'{}'::jsonb,123456789012345678901234567890,'{\"schema_version\":1,\"values\":{\"cpu_model\":\"WH-1000XM5\",\"physical_ram\":{\"value\":16,\"unit\":\"GB\"},\"nfc_supported\":false,\"count\":0}}'::jsonb)", variant, product, "fixture-" + publication);
                jdbc.update("INSERT INTO product_images(id,product_id,object_key,alt_text,content_type,byte_size,width,height,display_order,is_primary,created_at) VALUES (?,?,?,'Fixture','image/png',100,10,10,0,true,now())", UUID.randomUUID(), product, "fixture/" + product);
                jdbc.update("INSERT INTO wishlist_items(user_id,product_id,added_at) VALUES (?,?,now())", user, product);
                jdbc.update("INSERT INTO catalog_source_links(id,source,source_key,source_url,product_variant_id,source_payload_sha256,normalized_content_sha256,normalization_version,approved_manifest_sha256,updated_at) VALUES (?,'fixture',?,'https://fixture.invalid/item',?,?,?,'fixture-v1',?,now())", UUID.randomUUID(), variant.toString(), variant, "a".repeat(64), "b".repeat(64), "c".repeat(64));
            }
        });
    }
}
