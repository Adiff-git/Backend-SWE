package com.example.demo.catalog.service;

import com.example.demo.catalog.dto.request.AdminVariantRequests.*;
import com.example.demo.catalog.dto.response.AdminCatalogResponses.*;
import java.util.UUID;

public interface AdminVariantService {
    VariantList list(UUID productId);
    Specifications specifications(UUID productId,UUID variantId);
    VariantMutation replaceSpecifications(UUID productId,UUID variantId,Patch request);
    VariantMutation create(UUID productId,Create request);
    VariantMutation update(UUID productId,UUID variantId,Patch request);
    VariantMutation setEnabled(UUID productId,UUID variantId,long version,long taxonomyVersion,boolean enabled);
}
