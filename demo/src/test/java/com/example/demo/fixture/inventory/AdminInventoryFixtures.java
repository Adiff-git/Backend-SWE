package com.example.demo.fixture.inventory;

import com.example.demo.catalog.entity.ProductStatus;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

public final class AdminInventoryFixtures {
    public static final String V27 = "UC037-SYN-V27-01";
    public static final String V28 = "UC037-SYN-V28-01";

    private AdminInventoryFixtures() {}

    public static UUID variant(JdbcTemplate jdbc, TransactionTemplate tx, ProductStatus lifecycle, boolean enabled) {
        return tx.execute(status -> {
            UUID category = UUID.randomUUID(), product = UUID.randomUUID(), variant = UUID.randomUUID();
            jdbc.update("INSERT INTO categories(id,name) VALUES (?,?)", category, V28 + "-" + category);
            jdbc.update("INSERT INTO products(id,category_id,name,status) VALUES (?,?,?,'DRAFT')",
                    product, category, V28 + " " + lifecycle);
            jdbc.update("""
                    INSERT INTO product_variants(id,product_id,sku,option_combination,selling_price,enabled)
                    VALUES (?,?,?,'{"model":"test"}',100,?)
                    """, variant, product, "INV-" + variant, enabled);
            if (lifecycle == ProductStatus.PUBLISHED) {
                if (!enabled) {
                    UUID sibling = UUID.randomUUID();
                    jdbc.update("""
                            INSERT INTO product_variants(id,product_id,sku,option_combination,selling_price,enabled)
                            VALUES (?,?,?,'{"model":"enabled-sibling"}',100,true)
                            """, sibling, product, "INV-" + sibling);
                }
                jdbc.update("""
                        INSERT INTO product_images(id,product_id,object_key,alt_text,content_type,byte_size,
                            width,height,display_order,is_primary,created_at)
                        VALUES (?,?,?,'Image','image/png',1,1,1,0,true,now())
                        """, UUID.randomUUID(), product, "test/" + product);
            }
            jdbc.update("UPDATE products SET status=? WHERE id=?", lifecycle.name(), product);
            return variant;
        });
    }
}
