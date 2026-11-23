package com.example.demo.catalog.dto.request;

import com.example.demo.catalog.exception.AdminCatalogException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
@RequiredArgsConstructor
public class AdminGalleryRequests {
    private final AdminCatalogRequestReader reader;
    @Value public static class Attach { long expectedVersion; long expectedTaxonomyVersion; UUID imageId; String altText; Boolean primary; Integer displayOrder; }
    @Value public static class Primary { long version; long taxonomy; UUID image; }
    @Value public static class Order { long version; long taxonomy; List<UUID> images; }
    @Value public static class Removal { long version; long taxonomy; UUID replacement; }
    public Attach attachment(byte[] bytes) {
        JsonNode n=reader.object(bytes,Set.of("expectedVersion","expectedTaxonomyVersion","imageId","altText","isPrimary","displayOrder"),Set.of("expectedVersion","expectedTaxonomyVersion","imageId","altText"));
        Integer order=null;
        if(n.has("displayOrder")) {
            JsonNode value=n.get("displayOrder");
            if(!value.isIntegralNumber() || value.bigIntegerValue().signum()<0 || value.bigIntegerValue().compareTo(java.math.BigInteger.valueOf(Integer.MAX_VALUE))>0) throw malformed();
            order=value.intValue();
        }
        return new Attach(reader.revision(n.get("expectedVersion")),reader.revision(n.get("expectedTaxonomyVersion")),reader.identifier(n,"imageId",false).getValue(),
                reader.text(n,"altText",false,true).getValue(),n.has("isPrimary")?reader.bool(n.get("isPrimary")):null,order);
    }
    public Primary primary(byte[] bytes) {
        JsonNode n=reader.object(bytes,Set.of("expectedVersion","expectedTaxonomyVersion","imageId"),Set.of("expectedVersion","expectedTaxonomyVersion","imageId"));
        return new Primary(reader.revision(n.get("expectedVersion")),reader.revision(n.get("expectedTaxonomyVersion")),reader.identifier(n,"imageId",false).getValue());
    }
    public Order order(byte[] bytes) {
        JsonNode n=reader.object(bytes,Set.of("expectedVersion","expectedTaxonomyVersion","imageIds"),Set.of("expectedVersion","expectedTaxonomyVersion","imageIds"));
        return new Order(reader.revision(n.get("expectedVersion")),reader.revision(n.get("expectedTaxonomyVersion")),reader.identifiers(n.get("imageIds")));
    }
    public Removal removal(Map<String,String[]> parameters) {
        Map<String,List<String>> query=reader.query(parameters,Set.of("expectedVersion","expectedTaxonomyVersion","replacementImageId"),Set.of());
        return new Removal(reader.revision(query.getOrDefault("expectedVersion",List.of("")).getFirst()),reader.revision(query.getOrDefault("expectedTaxonomyVersion",List.of("")).getFirst()),
                query.containsKey("replacementImageId")?reader.identifier(query.get("replacementImageId").getFirst()):null);
    }
    private static AdminCatalogException malformed(){return new AdminCatalogException(400,"CATALOG_INVALID_REQUEST","Invalid gallery request");}
}
