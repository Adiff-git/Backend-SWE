package com.example.demo.behavior;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonAnySetter;
@io.swagger.v3.oas.annotations.media.Schema(description="Only explicit Customer interactions; no identity or timestamp fields", additionalProperties=io.swagger.v3.oas.annotations.media.Schema.AdditionalPropertiesValue.FALSE)
public record BehaviorRequest(@io.swagger.v3.oas.annotations.media.Schema(allowableValues={"PRODUCT_VIEW","SEARCH","PRODUCT_CLICK"}, requiredMode=io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED) BehaviorType eventType, UUID productId, String searchQuery,
        @io.swagger.v3.oas.annotations.media.Schema(requiredMode=io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED) UUID clientEventId, SourceContext sourceContext) {
    @JsonAnySetter public void unknown(String name, Object value) { throw new BehaviorException(400, "Unsupported event field"); }
}
