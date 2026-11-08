package com.example.demo.catalog.controller;

import com.example.demo.catalog.exception.InvalidCatalogRequestException;
import com.example.demo.catalog.service.ProductImageService;
import com.example.demo.common.identity.CurrentUserIdProvider;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/products/{id}/images")
public class ProductImageController {
    private final ProductImageService service;
    private final CurrentUserIdProvider identity;

    private UUID identifier(String value) {
        if (value == null || !value.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
            throw new InvalidCatalogRequestException("Invalid identifier");
        return UUID.fromString(value);
    }

    @PostMapping
    public ResponseEntity<ProductImageService.Attached> attach(@PathVariable String id,
            @RequestBody Map<String, Object> body) {
        if (!Set.of("imageId", "altText", "isPrimary", "displayOrder").containsAll(body.keySet())
                || !(body.get("imageId") instanceof String imageId)
                || !(body.get("altText") instanceof String altText) || altText.isBlank()
                || body.containsKey("isPrimary") && !(body.get("isPrimary") instanceof Boolean)
                || body.containsKey("displayOrder") && !(body.get("displayOrder") instanceof Integer))
            throw new InvalidCatalogRequestException("Invalid image attachment");
        var outcome = service.attach(identifier(id), identifier(imageId), identity.getCurrentUserId(),
                altText, (Boolean) body.get("isPrimary"), (Integer) body.get("displayOrder"));
        return ResponseEntity.status(outcome.replay() ? 200 : 201).cacheControl(CacheControl.noStore())
                .body(outcome.image());
    }
}
