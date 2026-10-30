package com.example.demo.catalog.controller;
import com.example.demo.catalog.service.ProductImageUploadService;
import com.example.demo.catalog.exception.InvalidCatalogRequestException;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
@RestController @RequiredArgsConstructor @RequestMapping("/api/products/{id}/images/uploads")
public class ProductImageUploadController {
    private final ProductImageUploadService service;
    private UUID identifier(String value){if(!value.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))throw new InvalidCatalogRequestException("Invalid identifier");return UUID.fromString(value);}
    @PostMapping public ResponseEntity<?> prepare(@PathVariable String id,@AuthenticationPrincipal Jwt jwt,@RequestBody Map<String,Object> body){
        if(!body.keySet().equals(Set.of("contentType","byteSize","altText")) || !(body.get("contentType") instanceof String type) || !(body.get("altText") instanceof String alt) || !(body.get("byteSize") instanceof Number size) || size.doubleValue()!=size.longValue())throw new InvalidCatalogRequestException("Invalid image request");
        return ResponseEntity.status(201).body(service.prepare(identifier(id),jwt.getSubject(),type,size.longValue(),alt));
    }
    @PostMapping("/{uploadId}/complete") public ResponseEntity<?> complete(@PathVariable String id,@PathVariable String uploadId,@AuthenticationPrincipal Jwt jwt,@RequestBody(required=false) Map<String,Object> body){
        if(body==null)body=Map.of();
        if(!Set.of("isPrimary","displayOrder").containsAll(body.keySet()) || body.containsKey("isPrimary")&&!(body.get("isPrimary") instanceof Boolean) || body.containsKey("displayOrder")&&!(body.get("displayOrder") instanceof Integer))throw new InvalidCatalogRequestException("Invalid gallery selection");
        var result=service.complete(identifier(id),identifier(uploadId),jwt.getSubject(),(Boolean)body.get("isPrimary"),(Integer)body.get("displayOrder"));
        return ResponseEntity.status(result.replay()?200:201).body(result.image());
    }
}
