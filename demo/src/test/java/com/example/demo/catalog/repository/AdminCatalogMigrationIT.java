package com.example.demo.catalog.repository;

import static org.assertj.core.api.Assertions.*;
import java.sql.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** OFFLINE-authored in T013. Execution is disposable PostgreSQL work owned by T014. */
class AdminCatalogMigrationIT {
    private String url() { return "jdbc:tc:postgresql:17-alpine:///admin_"+UUID.randomUUID().toString().replace("-",""); }
    private Flyway flyway(String url,String target) { return Flyway.configure().dataSource(url,"test","test").target(target).load(); }
    private Connection connection(String url) throws SQLException { return DriverManager.getConnection(url,"test","test"); }

    @Test void freshInstall_shouldAllowVariantlessDraftAndRetainZeroStockTrigger() throws Exception {
        String url=url();
        try(Connection db=connection(url)) {
            flyway(url,"28").migrate();
            UUID category=UUID.randomUUID(),product=UUID.randomUUID(),variant=UUID.randomUUID();
            execute(db,"INSERT INTO categories(id,name) VALUES (?,?)",category,"Draft category");
            execute(db,"INSERT INTO products(id,category_id,name,status) VALUES (?,?,?,'DRAFT')",product,category,"Draft");
            assertThat(scalar(db,"SELECT count(*) FROM product_variants")).isEqualTo("0");
            assertThat(scalar(db,"SELECT version FROM catalog_taxonomy_state WHERE id=1")).isEqualTo("0");
            execute(db,"INSERT INTO product_variants(id,product_id,sku,option_combination,selling_price) VALUES (?,?,?,'{}',0)",variant,product,"zero-preparation");
            assertThat(scalar(db,"SELECT enabled FROM product_variants WHERE id='"+variant+"'")).isEqualTo("t");
            assertThat(scalar(db,"SELECT stock||':'||reserved FROM inventory_balances WHERE product_variant_id='"+variant+"'")).isEqualTo("0:0");
            assertThatThrownBy(()->execute(db,"UPDATE products SET status='PUBLISHED' WHERE id=?",product)).satisfies(failure -> assertSqlState(failure,"23514"));
            assertThat(scalar(db,"SELECT status FROM products WHERE id='"+product+"'")).isEqualTo("DRAFT");
        }
    }

    @Test void upgrade_shouldPreserveIdentitiesLineageOrderSnapshotsAllocationsAndOldChecksums() throws Exception {
        String url=url();
        try(Connection db=connection(url)) {
            flyway(url,"27").migrate();
            UUID product=seed(db,true);
            String before=fingerprint(db);
            String history=history(db);
            flyway(url,"28").migrate();
            assertThat(fingerprint(db)).isEqualTo(before);
            assertThat(history(db)).isEqualTo(history);
            assertThat(scalar(db,"SELECT count(*) FROM pg_trigger WHERE tgname='ct_product_variants_inventory_initialize' AND NOT tgisinternal")).isEqualTo("1");
            assertThat(scalar(db,"SELECT version||':'||status FROM products WHERE id='"+product+"'")).isEqualTo("0:PUBLISHED");
            assertThat(scalar(db,"SELECT active FROM product_images WHERE product_id='"+product+"'")).isEqualTo("t");
            flyway(url,"28").validate();
        }
    }

    @ParameterizedTest @ValueSource(strings={"root-name","brand-name","imageless","price","cycle"})
    void upgrade_shouldRejectIncompatibilityAndRollBackSchemaRowsAndHistory(String kind) throws Exception {
        String url=url();
        try(Connection db=connection(url)) {
            flyway(url,"27").migrate();
            UUID product=seed(db,!kind.equals("imageless"));
            switch(kind) {
                case "root-name" -> execute(db,"INSERT INTO categories(id,name) VALUES (?,?)",UUID.randomUUID(),"  laptop  ");
                case "brand-name" -> execute(db,"INSERT INTO brands(id,code,name) VALUES (?,'another','  BRAND  ')",UUID.randomUUID());
                case "price" -> execute(db,"UPDATE product_variants SET selling_price=0 WHERE product_id=?",product);
                case "cycle" -> execute(db,"UPDATE categories SET parent_category_id=id");
                default -> { }
            }
            String before=fingerprint(db),history=history(db);
            assertThatThrownBy(()->flyway(url,"28").migrate()).isInstanceOf(org.flywaydb.core.api.FlywayException.class);
            assertThat(scalar(db,"SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='products' AND column_name='version'")).isEqualTo("0");
            assertThat(scalar(db,"SELECT max(version::int) FROM flyway_schema_history WHERE success")).isEqualTo("27");
            assertThat(fingerprint(db)).isEqualTo(before); assertThat(history(db)).isEqualTo(history);
            assertThat(scalar(db,"SELECT to_regclass('public.catalog_taxonomy_state') IS NULL")).isEqualTo("t");
        }
    }

    @Test void gallery_shouldRetainInactiveIdentityAndRequirePublishedPrimaryAtCommit() throws Exception {
        String url=url();
        try(Connection db=connection(url)) {
            flyway(url,"27").migrate(); UUID product=seed(db,true); flyway(url,"28").migrate();
            assertThatThrownBy(()->execute(db,"UPDATE product_images SET active=FALSE,is_primary=FALSE WHERE product_id=?",product)).satisfies(failure -> assertSqlState(failure,"23514"));
            assertThat(scalar(db,"SELECT active FROM product_images WHERE product_id='"+product+"'")).isEqualTo("t");
            db.setAutoCommit(false);
            execute(db,"UPDATE products SET status='UNPUBLISHED' WHERE id=?",product);
            execute(db,"UPDATE product_images SET active=FALSE,is_primary=FALSE WHERE product_id=?",product);
            db.commit(); db.setAutoCommit(true);
            image(db,product); // Position0 is available again, old association is retained.
            assertThat(scalar(db,"SELECT count(*) FROM product_images WHERE product_id='"+product+"'")).isEqualTo("2");
            assertThat(scalar(db,"SELECT count(*) FROM product_images WHERE product_id='"+product+"' AND active")).isEqualTo("1");
        }
    }

    @Test void nameKey_shouldResolveItsHelperUnderRestrictedIndexBuildSearchPath() throws Exception {
        String url=url();
        try(Connection db=connection(url)) {
            flyway(url,"28").migrate();
            execute(db,"SET search_path=pg_catalog");
            assertThat(scalar(db,"SELECT public.catalog_name_key(E'\\t MiXeD \\t')")).isEqualTo("mixed");
        }
    }

    private UUID seed(Connection db,boolean gallery) throws SQLException {
        UUID category=UUID.randomUUID(),brand=UUID.randomUUID(),product=UUID.randomUUID(),variant=UUID.randomUUID();
        db.setAutoCommit(false);
        execute(db,"INSERT INTO categories(id,name) VALUES (?,'Laptop')",category);
        execute(db,"INSERT INTO brands(id,code,name) VALUES (?,'brand','Brand')",brand);
        execute(db,"INSERT INTO products(id,category_id,brand_id,name,status) VALUES (?,?,?,'Legacy','PUBLISHED')",product,category,brand);
        execute(db,"INSERT INTO product_variants(id,product_id,sku,option_combination,selling_price) VALUES (?,?,?,'{}',1234)",variant,product,"sku-"+variant);
        if(gallery) image(db,product);
        String hash="a".repeat(64);
        execute(db,"INSERT INTO catalog_source_links(id,source,source_key,source_url,product_variant_id,source_payload_sha256,normalized_content_sha256,normalization_version,approved_manifest_sha256,updated_at) VALUES (?,'fixture','source','https://example.test/source',?,?,?,'1',?,CURRENT_TIMESTAMP)",UUID.randomUUID(),variant,hash,hash,hash);
        execute(db,"INSERT INTO users(username,password,account_status,joined_at) VALUES (?,'fixture','ACTIVE',CURRENT_TIMESTAMP)","migration-"+UUID.randomUUID());
        long user=Long.parseLong(scalar(db,"SELECT max(id) FROM users"));
        execute(db,"INSERT INTO orders(customer_id,order_number,placed_at,status,recipient_name_snapshot,recipient_phone_snapshot,delivery_address_snapshot,merchandise_total,payable_total,inventory_managed) VALUES (?,'fixture',CURRENT_TIMESTAMP,'PENDING','Historical','123','Historical address',1234,1234,TRUE)",user);
        long order=Long.parseLong(scalar(db,"SELECT max(id) FROM orders"));
        execute(db,"INSERT INTO order_items(order_id,product_id,product_variant_id,product_name_snapshot,variant_sku_snapshot,variant_options_snapshot,quantity,unit_price_snapshot,line_total) VALUES (?,?,?,'Historical name','Historical SKU','{}',1,1234,1234)",order,product,variant);
        long item=Long.parseLong(scalar(db,"SELECT max(id) FROM order_items"));
        execute(db,"UPDATE inventory_balances SET stock=2,reserved=1 WHERE product_variant_id=?",variant);
        execute(db,"INSERT INTO inventory_reservations(order_id,order_item_id,inventory_balance_id,original_variant_id,quantity,state,reserved_at) SELECT ?,?,b.id,?,1,'ACTIVE',o.placed_at FROM inventory_balances b CROSS JOIN orders o WHERE b.product_variant_id=? AND o.id=?",order,item,variant,variant,order);
        db.commit(); db.setAutoCommit(true); return product;
    }
    private void image(Connection db,UUID product) throws SQLException {
        execute(db,"INSERT INTO product_images(id,product_id,object_key,alt_text,content_type,byte_size,width,height,display_order,is_primary,created_at) VALUES (?,?,?,'Ready','image/png',100,10,10,0,TRUE,CURRENT_TIMESTAMP)",UUID.randomUUID(),product,"migration/"+UUID.randomUUID());
    }
    private String fingerprint(Connection db) throws SQLException {
        StringBuilder result=new StringBuilder();
        for(String selection:List.of("SELECT id,name,parent_category_id FROM categories ORDER BY id", "SELECT id,code,name FROM brands ORDER BY id", "SELECT id,category_id,brand_id,name,status FROM products ORDER BY id", "SELECT id,product_id,sku,option_combination,selling_price,technical_specifications FROM product_variants ORDER BY id",
                "SELECT id,product_id,object_key,source_image_id FROM product_images ORDER BY id","SELECT * FROM catalog_source_links ORDER BY id","SELECT * FROM order_items ORDER BY id","SELECT * FROM orders ORDER BY id","SELECT * FROM inventory_balances ORDER BY id","SELECT * FROM inventory_reservations ORDER BY id")) {
            try(Statement q=db.createStatement();ResultSet rows=q.executeQuery(selection)) {
                while(rows.next()) for(int c=1;c<=rows.getMetaData().getColumnCount();c++) result.append(rows.getString(c)).append('|');
            }
        }
        return result.toString();
    }
    private String history(Connection db) throws SQLException { return scalar(db,"SELECT string_agg(version||':'||checksum,',' ORDER BY installed_rank) FROM flyway_schema_history WHERE version::int<=27 AND success"); }
    private String scalar(Connection db,String sql) throws SQLException { try(Statement s=db.createStatement();ResultSet r=s.executeQuery(sql)) { assertThat(r.next()).isTrue(); return r.getString(1); } }
    private void assertSqlState(Throwable failure,String expected) {
        for(Throwable cause=failure;cause!=null;cause=cause.getCause()) if(cause instanceof SQLException sql) {
            assertThat(sql.getSQLState()).isEqualTo(expected); return;
        }
        throw new AssertionError("Expected SQLState "+expected,failure);
    }
    private void execute(Connection db,String sql,Object... values) throws SQLException { try(PreparedStatement s=db.prepareStatement(sql)) { for(int i=0;i<values.length;i++) s.setObject(i+1,values[i]); s.executeUpdate(); } }
}
