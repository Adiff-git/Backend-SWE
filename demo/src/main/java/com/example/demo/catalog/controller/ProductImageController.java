package com.example.demo.catalog.controller;

import com.example.demo.catalog.dto.request.AdminCatalogRequestReader;
import com.example.demo.catalog.dto.request.AdminGalleryRequests;
import com.example.demo.catalog.dto.response.AdminCatalogResponses.LegacyAttachment;
import com.example.demo.catalog.service.ProductImageService;
import com.example.demo.common.identity.CurrentUserIdProvider;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/products/{id}/images")
public class ProductImageController {
    private final ProductImageService service;
    private final CurrentUserIdProvider identity;
    private final AdminGalleryRequests requests;
    private final AdminCatalogRequestReader reader;
    @PostMapping public ResponseEntity<LegacyAttachment> attach(@org.springframework.web.bind.annotation.PathVariable String id,@RequestBody(required=false) byte[] body,HttpServletRequest request){
        reader.requireJson(request.getContentType());reader.query(request.getParameterMap(),Set.of(),Set.of());var input=requests.attachment(body);
        var result=service.attach(reader.identifier(id),input.getImageId(),identity.getCurrentUserId(),input.getAltText(),input.getPrimary(),input.getDisplayOrder(),input.getExpectedVersion(),input.getExpectedTaxonomyVersion());
        var image=result.image();
        ResponseEntity.BodyBuilder response=ResponseEntity.status(result.replay()?200:201).cacheControl(CacheControl.noStore());
        if(!result.replay())response.location(java.net.URI.create("/api/admin/catalog/products/"+id+"/images"));
        return response.body(new LegacyAttachment(image.imageId(),image.productId(),image.altText(),image.width(),image.height(),image.displayOrder(),image.isPrimary(),result.productVersion(),result.taxonomyVersion(),result.replay()));
    }
}
