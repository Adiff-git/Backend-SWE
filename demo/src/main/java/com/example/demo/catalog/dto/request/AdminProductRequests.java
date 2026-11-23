package com.example.demo.catalog.dto.request;

import com.example.demo.catalog.entity.ProductStatus;
import com.example.demo.catalog.exception.AdminCatalogException;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
@RequiredArgsConstructor
public class AdminProductRequests {
    private final AdminCatalogRequestReader reader;
    public enum Action { PUBLISH, UNPUBLISH, ARCHIVE, RESTORE }
    public enum Ordering {
        ID_ASC("id ASC"), ID_DESC("id DESC"), NAME_ASC("name ASC, id ASC"), NAME_DESC("name DESC, id DESC");
        private final String sql;
        Ordering(String sql) { this.sql = sql; }
        public String sql() { return sql; }
    }
    @Value public static class Query {
        Pageable page; Set<ProductStatus> statuses; UUID categoryId; boolean descendants; UUID brandId; String q; Ordering ordering;
    }
    @Value public static class Create {
        @jakarta.validation.constraints.NotBlank String name;
        @jakarta.validation.constraints.NotNull UUID categoryId;
        CatalogField<UUID> brandId; Long expectedBrandVersion; CatalogField<String> description;
    }
    @Value public static class Patch {
        long expectedVersion; long expectedTaxonomyVersion; CatalogField<String> name; CatalogField<UUID> categoryId;
        CatalogField<UUID> brandId; Long expectedBrandVersion; CatalogField<String> description;
    }
    @Value public static class Revisions { long version; long taxonomyVersion; }

    public Create create(byte[] bytes) {
        JsonNode n = reader.object(bytes,Set.of("name","categoryId","brandId","expectedBrandVersion","description"),Set.of("name","categoryId"));
        reader.brandPrecondition(n,true);
        return new Create(reader.text(n,"name",false,true).getValue(),reader.identifier(n,"categoryId",false).getValue(),
                reader.identifier(n,"brandId",true),n.has("expectedBrandVersion")?reader.revision(n.get("expectedBrandVersion")):null,reader.text(n,"description",true,false));
    }
    public Patch patch(byte[] bytes) {
        JsonNode n = reader.patch(bytes,Set.of("name","categoryId","brandId","description"));
        reader.brandPrecondition(n,false); // Changed-versus-unchanged reference is decided under Product lock.
        return new Patch(reader.revision(n.get("expectedVersion")),reader.revision(n.get("expectedTaxonomyVersion")),
                reader.text(n,"name",false,true),reader.identifier(n,"categoryId",false),reader.identifier(n,"brandId",true),
                n.has("expectedBrandVersion")?reader.revision(n.get("expectedBrandVersion")):null,reader.text(n,"description",true,false));
    }
    public Revisions revisions(byte[] bytes) {
        JsonNode n = reader.object(bytes,Set.of("expectedVersion","expectedTaxonomyVersion"),Set.of("expectedVersion","expectedTaxonomyVersion"));
        return new Revisions(reader.revision(n.get("expectedVersion")),reader.revision(n.get("expectedTaxonomyVersion")));
    }
    public Query query(Map<String,String[]> parameters) {
        var q = reader.query(parameters,Set.of("page","size","status","categoryId","includeDescendants","brandId","q","sort"),Set.of("status"));
        Set<ProductStatus> states = new LinkedHashSet<>();
        try { q.getOrDefault("status",List.of()).forEach(value -> states.add(ProductStatus.valueOf(value))); }
        catch(IllegalArgumentException invalid) { throw malformed(); }
        UUID category = q.containsKey("categoryId")?reader.identifier(q.get("categoryId").getFirst()):null;
        boolean descendants = false;
        if(q.containsKey("includeDescendants")) {
            String value=q.get("includeDescendants").getFirst();
            if(category==null || !Set.of("true","false").contains(value)) throw malformed();
            descendants=Boolean.parseBoolean(value);
        }
        String keyword = q.containsKey("q")?q.get("q").getFirst():null;
        if(keyword!=null && keyword.isBlank()) throw malformed();
        Ordering order = switch(q.getOrDefault("sort",List.of("id,asc")).getFirst()) {
            case "id,asc" -> Ordering.ID_ASC; case "id,desc" -> Ordering.ID_DESC;
            case "name,asc" -> Ordering.NAME_ASC; case "name,desc" -> Ordering.NAME_DESC;
            default -> throw malformed();
        };
        return new Query(reader.page(q),Set.copyOf(states),category,descendants,
                q.containsKey("brandId")?reader.identifier(q.get("brandId").getFirst()):null,keyword,order);
    }
    private static AdminCatalogException malformed() { return new AdminCatalogException(400,"CATALOG_INVALID_REQUEST","Invalid product request"); }
}
