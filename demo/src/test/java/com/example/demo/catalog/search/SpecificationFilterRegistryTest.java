package com.example.demo.catalog.search;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.filter;
import com.example.demo.catalog.exception.InvalidCatalogRequestException;
import jakarta.validation.Validation;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
class SpecificationFilterRegistryTest {
    @Test void registry_shouldContainOnlyFourApprovedDefinitions_andNeverSourceCategoryUuids(){
        var registry=new SpecificationFilterRegistry();assertThat(registry.definitions()).hasSize(4).containsOnlyKeys("display_diagonal","physical_ram","installed_storage_gb","display_resolution");
        assertThat(registry.definitions().get("display_diagonal").categoryRoots()).containsExactly("Laptop","Tablet");
        assertThatThrownBy(()->registry.get("battery_capacity_mah")).isInstanceOf(InvalidCatalogRequestException.class);
    }
    @Test void filters_shouldEnforceTypesUnitsAndCategoryBeforeSql(){
        try(var vf=Validation.buildDefaultValidatorFactory()){
            var parser=new ProductSearchParameterParser(vf.getValidator());var registry=new SpecificationFilterRegistry();
            for(String unit:List.of("gb","MB","TB")){
                var request=parser.parse(Map.of("categoryId",List.of(UUID.randomUUID().toString()),"specFilters",List.of("[{\"key\":\"physical_ram\",\"alternatives\":[{\"op\":\"eq\",\"value\":16,\"unit\":\""+unit+"\"}]}]")));
                assertThatThrownBy(()->registry.validate(request.getSpecificationGroups(),UUID.fromString(request.getCategoryId()))).isInstanceOf(InvalidCatalogRequestException.class);
            }
        }
    }
    @org.junit.jupiter.api.Test void startup_shouldRejectUnapprovedOrMalformedDefinitions()throws Exception{
        String approved=new String(getClass().getResourceAsStream("/catalog/specification-filter-registry.json").readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
        var parsed=tools.jackson.databind.json.JsonMapper.builder().build().readTree(approved);
        String duplicated=approved.replace("\"definitions\": [","\"definitions\": ["+parsed.get("definitions").get(0).toString()+",");
        for(String invalid:java.util.List.of(duplicated,approved.replace("\"documentSchemaVersion\": 1","\"documentSchemaVersion\": 2"),approved.replace("APPROVED_BY_PROJECT_OWNER","PENDING"),approved.replace("\"searchable\": true","\"searchable\": false"),approved.replace("\"unit\": \"GB\"","\"unit\": \"MB\""),approved.replace("\"identifier\": false","\"identifier\": true"))){
            assertThatThrownBy(()->new SpecificationFilterRegistry(new java.io.ByteArrayInputStream(invalid.getBytes(java.nio.charset.StandardCharsets.UTF_8)))).isInstanceOf(IllegalStateException.class);
        }
    }
}
