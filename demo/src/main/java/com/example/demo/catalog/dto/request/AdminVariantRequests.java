package com.example.demo.catalog.dto.request;

import com.example.demo.catalog.exception.AdminCatalogException;
import java.math.BigDecimal;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
@RequiredArgsConstructor
public class AdminVariantRequests {
    private final AdminCatalogRequestReader reader;
    @Value public static class Create {
        long expectedVersion; long expectedTaxonomyVersion; String sku; BigDecimal sellingPrice;
        Map<String,String> options; Map<String,Object> technicalSpecifications; boolean enabled;
    }
    @Value public static class Patch {
        long expectedVersion; long expectedTaxonomyVersion; CatalogField<String> sku; CatalogField<BigDecimal> sellingPrice;
        CatalogField<Map<String,String>> options; CatalogField<Map<String,Object>> technicalSpecifications;
    }
    @Value public static class Revisions { long version; long taxonomyVersion; }
    public Create create(byte[] bytes) {
        JsonNode n=reader.object(bytes,Set.of("expectedVersion","expectedTaxonomyVersion","sku","sellingPrice","options","technicalSpecifications","enabled"),Set.of("expectedVersion","expectedTaxonomyVersion","sku","sellingPrice"));
        return new Create(reader.revision(n.get("expectedVersion")),reader.revision(n.get("expectedTaxonomyVersion")),reader.text(n,"sku",false,true).getValue(),
                new BigDecimal(reader.price(n.get("sellingPrice")).getAmount()),n.has("options")?reader.options(n.get("options")):Map.of(),
                n.has("technicalSpecifications")?reader.specifications(n.get("technicalSpecifications")):Map.of("schema_version",1,"values",Map.of()),
                !n.has("enabled") || reader.bool(n.get("enabled")));
    }
    public Patch patch(byte[] bytes) {
        JsonNode n=reader.object(bytes,Set.of("expectedVersion","expectedTaxonomyVersion","sku","sellingPrice","options","technicalSpecifications"),Set.of("expectedVersion","expectedTaxonomyVersion"));
        if(n.propertyNames().stream().noneMatch(Set.of("sku","sellingPrice","options","technicalSpecifications")::contains)) throw malformed();
        return new Patch(reader.revision(n.get("expectedVersion")),reader.revision(n.get("expectedTaxonomyVersion")),reader.text(n,"sku",false,true),
                n.has("sellingPrice")?CatalogField.supplied(new BigDecimal(reader.price(n.get("sellingPrice")).getAmount())):CatalogField.absent(),
                n.has("options")?CatalogField.supplied(reader.options(n.get("options"))):CatalogField.absent(),
                n.has("technicalSpecifications")?CatalogField.supplied(reader.specifications(n.get("technicalSpecifications"))):CatalogField.absent());
    }
    public Patch specifications(byte[] bytes) {
        JsonNode n=reader.object(bytes,Set.of("expectedVersion","expectedTaxonomyVersion","technicalSpecifications"),Set.of("expectedVersion","expectedTaxonomyVersion","technicalSpecifications"));
        return new Patch(reader.revision(n.get("expectedVersion")),reader.revision(n.get("expectedTaxonomyVersion")),CatalogField.absent(),CatalogField.absent(),CatalogField.absent(),
                CatalogField.supplied(reader.specifications(n.get("technicalSpecifications"))));
    }
    public Revisions revisions(byte[] bytes) {
        JsonNode n=reader.object(bytes,Set.of("expectedVersion","expectedTaxonomyVersion"),Set.of("expectedVersion","expectedTaxonomyVersion"));
        return new Revisions(reader.revision(n.get("expectedVersion")),reader.revision(n.get("expectedTaxonomyVersion")));
    }
    private static AdminCatalogException malformed(){return new AdminCatalogException(400,"CATALOG_INVALID_REQUEST","Invalid variant request");}
}
