package com.example.demo.catalog.repository;

import com.example.demo.catalog.entity.ProductStatus;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** One bound UUID array and explicit instant; no per-variant queries or application pricing formula. */
@RequiredArgsConstructor
public class CatalogPricingRepositoryImpl implements CatalogPricingRepository {
    private final JdbcTemplate jdbc;
    private static final JsonMapper JSON = JsonMapper.builder().enable(tools.jackson.databind.DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();
    private static final TypeReference<Map<String, String>> OPTIONS = new TypeReference<>() {};
    private static final String PRICING_SQL = """
        WITH priced AS MATERIALIZED (
            SELECT v.id AS variant_id,p.id AS product_id,p.status,p.name,v.sku,v.option_combination::TEXT AS options,
                CASE WHEN v.base_price >= 0 AND v.base_price NOT IN ('NaN'::NUMERIC,'Infinity'::NUMERIC,'-Infinity'::NUMERIC) AND v.base_price=TRUNC(v.base_price) THEN v.base_price END AS original,event.pricing_at,
                catalog_effective_price_v1(v.base_price,p.sale_percentage,p.sale_enabled,p.sale_starts_at,p.sale_ends_at,event.pricing_at) AS effective,
                catalog_active_sale_percentage_v1(p.sale_percentage,p.sale_enabled,p.sale_starts_at,p.sale_ends_at,event.pricing_at) AS percentage,
                p.sale_starts_at,p.sale_ends_at%s
            FROM product_variants v JOIN products p ON p.id=v.product_id
            CROSS JOIN (SELECT ?::TIMESTAMPTZ AS pricing_at) event WHERE %s
        ) SELECT *,original-effective AS discount,
            CASE WHEN percentage IS NOT NULL THEN sale_starts_at END AS active_start,
            CASE WHEN percentage IS NOT NULL THEN sale_ends_at END AS active_end
          FROM priced ORDER BY variant_id
        """;

    private static final String SQL=PRICING_SQL.formatted("","v.id=ANY(?)");
    private static final String DETAIL_SQL=PRICING_SQL.formatted("""
        ,v.technical_specifications::TEXT AS specifications,
        (SELECT COALESCE(JSONB_OBJECT_AGG(catalog_option_identity_v1(e.key),
            JSONB_BUILD_OBJECT('label',e.key,'value',catalog_option_identity_v1(e.value #>> '{}'),'valueLabel',e.value #>> '{}')),
            '{}'::JSONB)::TEXT FROM JSONB_EACH(v.option_combination) e) AS canonical_labels
        ""","v.product_id=?");

    @Override public List<CatalogDetailVariantRow> findProductPricedVariants(UUID product,Instant at) {
        if(at==null) throw new IllegalArgumentException("A pricing instant is required");
        return jdbc.query(connection -> {
            PreparedStatement statement=connection.prepareStatement(DETAIL_SQL);
            statement.setTimestamp(1,Timestamp.from(at));statement.setObject(2,product);return statement;
        },(row,number) -> new CatalogDetailVariantRow(pricingRow(row,at),
                JSON.readValue(row.getString("specifications"),new TypeReference<Map<String,Object>>() {}),
                JSON.readValue(row.getString("canonical_labels"),new TypeReference<Map<String,Map<String,String>>>() {})));
    }

    @Override
    public List<CatalogPricingRow> findPricedVariants(Collection<UUID> ids, Instant pricingInstant) {
        return findRows(SQL, ids, pricingInstant);
    }

    @Override
    public List<CatalogPricingRow> findPricingForProducts(Collection<UUID> ids, Instant pricingInstant) {
        return findRows(PRICING_SQL.formatted("", "v.product_id=ANY(?)"), ids, pricingInstant);
    }

    private List<CatalogPricingRow> findRows(String sql, Collection<UUID> ids, Instant pricingInstant) {
        if (pricingInstant == null) throw new IllegalArgumentException("A pricing instant is required");
        if (ids.isEmpty()) return List.of();
        UUID[] distinct = ids.stream().distinct().toArray(UUID[]::new);
        return jdbc.query(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql);
            statement.setTimestamp(1, Timestamp.from(pricingInstant));
            statement.setArray(2, connection.createArrayOf("uuid", distinct));
            return statement;
        }, (row, number) -> pricingRow(row,pricingInstant));
    }

    private CatalogPricingRow pricingRow(java.sql.ResultSet row,Instant pricingInstant) throws java.sql.SQLException {

            OffsetDateTime start = row.getObject("active_start", OffsetDateTime.class);
            OffsetDateTime end = row.getObject("active_end", OffsetDateTime.class);
            return new CatalogPricingRow(row.getObject("variant_id", UUID.class), row.getObject("product_id", UUID.class),
                    ProductStatus.valueOf(row.getString("status")), row.getString("name"), row.getString("sku"),
                    JSON.readValue(row.getString("options"), OPTIONS), row.getBigDecimal("original"), row.getBigDecimal("effective"),
                    row.getBigDecimal("discount"), row.getBigDecimal("percentage"),
                    start == null ? null : start.toInstant(), end == null ? null : end.toInstant(), pricingInstant);
    }
}
