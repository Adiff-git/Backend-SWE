package com.example.demo.catalog.dto.request;

import com.example.demo.catalog.exception.AdminCatalogException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
@RequiredArgsConstructor
public class AdminTaxonomyRequests {
    private final AdminCatalogRequestReader reader;
    public enum Ordering {
        ID_ASC("id ASC"),ID_DESC("id DESC"),NAME_ASC("name ASC, id ASC"),NAME_DESC("name DESC, id DESC"),CODE_ASC("code ASC, id ASC"),CODE_DESC("code DESC, id DESC");
        private final String sql;Ordering(String sql){this.sql=sql;}public String sql(){return sql;}
    }
    @Value public static class Query {Pageable page;String q;Ordering ordering;boolean parentSupplied;UUID parent;}
    @Value public static class CategoryCreate {long taxonomy;@jakarta.validation.constraints.NotBlank String name;UUID parent;}
    @Value public static class CategoryPatch {long version;long taxonomy;CatalogField<String> name;CatalogField<UUID> parent;}
    @Value public static class BrandCreate {@jakarta.validation.constraints.Pattern(regexp="[a-z0-9]+(-[a-z0-9]+)*") String code;@jakarta.validation.constraints.NotBlank String name;}
    @Value public static class BrandRename {long version;@jakarta.validation.constraints.NotBlank String name;}
    @Value public static class Deletion {long version;long taxonomy;}
    public CategoryCreate categoryCreate(byte[] bytes){
        JsonNode n=reader.object(bytes,Set.of("expectedTaxonomyVersion","name","parentCategoryId"),Set.of("expectedTaxonomyVersion","name"));
        return new CategoryCreate(reader.revision(n.get("expectedTaxonomyVersion")),reader.text(n,"name",false,true).getValue(),reader.identifier(n,"parentCategoryId",true).getValue());
    }
    public CategoryPatch categoryPatch(byte[] bytes){
        JsonNode n=reader.object(bytes,Set.of("expectedVersion","expectedTaxonomyVersion","name","parentCategoryId"),Set.of("expectedVersion","expectedTaxonomyVersion"));
        if(!n.has("name") && !n.has("parentCategoryId"))throw malformed();
        return new CategoryPatch(reader.revision(n.get("expectedVersion")),reader.revision(n.get("expectedTaxonomyVersion")),reader.text(n,"name",false,true),reader.identifier(n,"parentCategoryId",true));
    }
    public BrandCreate brandCreate(byte[] bytes){JsonNode n=reader.object(bytes,Set.of("code","name"),Set.of("code","name"));String code=reader.text(n,"code",false,true).getValue();if(!code.matches("[a-z0-9]+(-[a-z0-9]+)*"))throw malformed();return new BrandCreate(code,reader.text(n,"name",false,true).getValue());}
    public BrandRename brandRename(byte[] bytes){JsonNode n=reader.object(bytes,Set.of("expectedVersion","name"),Set.of("expectedVersion","name"));return new BrandRename(reader.revision(n.get("expectedVersion")),reader.text(n,"name",false,true).getValue());}
    public Deletion deletion(Map<String,String[]> parameters,boolean category){
        Map<String,List<String>> q=reader.query(parameters,category?Set.of("expectedVersion","expectedTaxonomyVersion"):Set.of("expectedVersion"),Set.of());
        return new Deletion(reader.revision(q.getOrDefault("expectedVersion",List.of("")).getFirst()),category?reader.revision(q.getOrDefault("expectedTaxonomyVersion",List.of("")).getFirst()):0);
    }
    public Query query(Map<String,String[]> parameters,boolean category){
        Map<String,List<String>> q=reader.query(parameters,category?Set.of("page","size","q","sort","parentCategoryId"):Set.of("page","size","q","sort"),Set.of());
        String keyword=q.containsKey("q")?q.get("q").getFirst():null;if(keyword!=null && keyword.isBlank())throw malformed();
        String sort=q.getOrDefault("sort",List.of("id,asc")).getFirst();
        Ordering ordering=switch(sort){case "id,asc"->Ordering.ID_ASC;case "id,desc"->Ordering.ID_DESC;case "name,asc"->Ordering.NAME_ASC;case "name,desc"->Ordering.NAME_DESC;case "code,asc"->{if(category)throw malformed();yield Ordering.CODE_ASC;}case "code,desc"->{if(category)throw malformed();yield Ordering.CODE_DESC;}default->throw malformed();};
        String parent=q.containsKey("parentCategoryId")?q.get("parentCategoryId").getFirst():null;
        return new Query(reader.page(q),keyword,ordering,q.containsKey("parentCategoryId"),parent==null || parent.equals("root")?null:reader.identifier(parent));
    }
    private static AdminCatalogException malformed(){return new AdminCatalogException(400,"CATALOG_INVALID_REQUEST","Invalid taxonomy request");}
}
