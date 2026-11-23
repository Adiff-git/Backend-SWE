package com.example.demo.catalog.controller.admin;

import com.example.demo.catalog.dto.request.AdminCatalogRequestReader;
import com.example.demo.catalog.dto.request.AdminGalleryRequests;
import com.example.demo.catalog.dto.response.AdminCatalogResponses.Attachment;
import com.example.demo.catalog.dto.response.AdminCatalogResponses.Gallery;
import com.example.demo.catalog.service.AdminGalleryService;
import com.example.demo.common.identity.CurrentUserIdProvider;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/catalog/products/{product}/images")
public class AdminGalleryController {
    private final AdminGalleryService service;
    private final AdminGalleryRequests requests;
    private final AdminCatalogRequestReader reader;
    private final CurrentUserIdProvider identity;
    @GetMapping public ResponseEntity<Gallery> get(@PathVariable String product,HttpServletRequest request){noQuery(request);return ok(service.get(reader.identifier(product)));}
    @PostMapping public ResponseEntity<Attachment> attach(@PathVariable String product,@RequestBody(required=false) byte[] body,HttpServletRequest request){
        noQuery(request);Attachment result=service.attach(reader.identifier(product),identity.getCurrentUserId(),requests.attachment(body));
        ResponseEntity.BodyBuilder response=ResponseEntity.status(result.isReplay()?200:201).cacheControl(CacheControl.noStore());
        if(!result.isReplay())response.location(URI.create("/api/admin/catalog/products/"+product+"/images"));return response.body(result);
    }
    @PutMapping(value="/primary") public ResponseEntity<Gallery> primary(@PathVariable String product,@RequestBody(required=false) byte[] body,HttpServletRequest request){
        noQuery(request);var command=requests.primary(body);return ok(service.primary(reader.identifier(product),command.getImage(),command.getVersion(),command.getTaxonomy()));
    }
    @PutMapping(value="/order") public ResponseEntity<Gallery> order(@PathVariable String product,@RequestBody(required=false) byte[] body,HttpServletRequest request){
        noQuery(request);var command=requests.order(body);return ok(service.reorder(reader.identifier(product),command.getVersion(),command.getTaxonomy(),command.getImages()));
    }
    @DeleteMapping("/{image}") public ResponseEntity<Void> remove(@PathVariable String product,@PathVariable String image,HttpServletRequest request){
        var command=requests.removal(request.getParameterMap());service.remove(reader.identifier(product),reader.identifier(image),command.getVersion(),command.getTaxonomy(),command.getReplacement());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
    private void noQuery(HttpServletRequest request){if(Set.of("POST","PATCH","PUT").contains(request.getMethod()))reader.requireJson(request.getContentType());reader.query(request.getParameterMap(),Set.of(),Set.of());}
    private static <T> ResponseEntity<T> ok(T value){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);}
}
