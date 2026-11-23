package com.example.demo.catalog.controller.admin;

import com.example.demo.catalog.dto.request.AdminCatalogRequestReader;
import com.example.demo.catalog.dto.request.AdminVariantRequests;
import com.example.demo.catalog.dto.response.AdminCatalogResponses.VariantList;
import com.example.demo.catalog.dto.response.AdminCatalogResponses.VariantMutation;
import com.example.demo.catalog.dto.response.AdminCatalogResponses.Specifications;
import com.example.demo.catalog.service.AdminVariantService;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/catalog/products/{productId}/variants")
public class AdminVariantController {
    private final AdminVariantService service;
    private final AdminVariantRequests requests;
    private final AdminCatalogRequestReader reader;
    @GetMapping public ResponseEntity<VariantList> list(@PathVariable String productId,HttpServletRequest request){ noQuery(request);return ok(service.list(reader.identifier(productId))); }
    @PostMapping public ResponseEntity<VariantMutation> create(@PathVariable String productId,@RequestBody(required=false) byte[] body,HttpServletRequest request){
        noQuery(request);var result=service.create(reader.identifier(productId),requests.create(body));
        return ResponseEntity.created(URI.create("/api/admin/catalog/products/"+productId+"/variants")).cacheControl(CacheControl.noStore()).body(result);
    }
    @PatchMapping(value="/{variantId}") public ResponseEntity<VariantMutation> update(@PathVariable String productId,@PathVariable String variantId,@RequestBody(required=false) byte[] body,HttpServletRequest request){
        noQuery(request);return ok(service.update(reader.identifier(productId),reader.identifier(variantId),requests.patch(body)));
    }
    @PostMapping(value="/{variantId}/enable") public ResponseEntity<VariantMutation> enable(@PathVariable String productId,@PathVariable String variantId,@RequestBody(required=false) byte[] body,HttpServletRequest request){return toggle(productId,variantId,body,request,true);}
    @PostMapping(value="/{variantId}/disable") public ResponseEntity<VariantMutation> disable(@PathVariable String productId,@PathVariable String variantId,@RequestBody(required=false) byte[] body,HttpServletRequest request){return toggle(productId,variantId,body,request,false);}
    @GetMapping("/{variantId}/specifications") public ResponseEntity<Specifications> specifications(@PathVariable String productId,@PathVariable String variantId,HttpServletRequest request){
        noQuery(request);return ok(service.specifications(reader.identifier(productId),reader.identifier(variantId)));
    }
    @PutMapping(value="/{variantId}/specifications") public ResponseEntity<VariantMutation> replaceSpecifications(@PathVariable String productId,@PathVariable String variantId,@RequestBody(required=false) byte[] body,HttpServletRequest request){
        noQuery(request);return ok(service.replaceSpecifications(reader.identifier(productId),reader.identifier(variantId),requests.specifications(body)));
    }
    private ResponseEntity<VariantMutation> toggle(String product,String variant,byte[] body,HttpServletRequest request,boolean enabled){
        noQuery(request);var r=requests.revisions(body);return ok(service.setEnabled(reader.identifier(product),reader.identifier(variant),r.getVersion(),r.getTaxonomyVersion(),enabled));
    }
    private void noQuery(HttpServletRequest request){if(Set.of("POST","PATCH","PUT").contains(request.getMethod()))reader.requireJson(request.getContentType());reader.query(request.getParameterMap(),Set.of(),Set.of());}
    private static <T> ResponseEntity<T> ok(T value){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);}
}
