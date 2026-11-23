package com.example.demo.catalog.controller.admin;

import com.example.demo.catalog.dto.request.AdminCatalogRequestReader;
import com.example.demo.catalog.dto.request.AdminProductRequests;
import com.example.demo.catalog.dto.request.AdminProductRequests.Action;
import com.example.demo.catalog.dto.response.AdminCatalogResponses.ProductDetail;
import com.example.demo.catalog.dto.response.AdminCatalogResponses.ProductPage;
import com.example.demo.catalog.service.AdminProductService;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/catalog/products")
public class AdminProductController {
    private final AdminProductService service;
    private final AdminProductRequests requests;
    private final AdminCatalogRequestReader reader;
    @GetMapping public ResponseEntity<ProductPage> list(HttpServletRequest request) { return ok(service.list(requests.query(request.getParameterMap()))); }
    @GetMapping("/{id}") public ResponseEntity<ProductDetail> get(@PathVariable String id,HttpServletRequest request) {
        noQuery(request); return ok(service.get(reader.identifier(id)));
    }
    @PostMapping public ResponseEntity<ProductDetail> create(@RequestBody(required=false) byte[] body,HttpServletRequest request) {
        noQuery(request); ProductDetail result=service.create(requests.create(body));
        return ResponseEntity.created(URI.create("/api/admin/catalog/products/"+result.getId())).cacheControl(CacheControl.noStore()).body(result);
    }
    @PatchMapping(value="/{id}") public ResponseEntity<ProductDetail> update(@PathVariable String id,@RequestBody(required=false) byte[] body,HttpServletRequest request) {
        noQuery(request); return ok(service.update(reader.identifier(id),requests.patch(body)));
    }
    @PostMapping(value="/{id}/publish") public ResponseEntity<ProductDetail> publish(@PathVariable String id,@RequestBody(required=false) byte[] body,HttpServletRequest request) { return transition(id,body,request,Action.PUBLISH); }
    @PostMapping(value="/{id}/unpublish") public ResponseEntity<ProductDetail> unpublish(@PathVariable String id,@RequestBody(required=false) byte[] body,HttpServletRequest request) { return transition(id,body,request,Action.UNPUBLISH); }
    @PostMapping(value="/{id}/archive") public ResponseEntity<ProductDetail> archive(@PathVariable String id,@RequestBody(required=false) byte[] body,HttpServletRequest request) { return transition(id,body,request,Action.ARCHIVE); }
    @PostMapping(value="/{id}/restore") public ResponseEntity<ProductDetail> restore(@PathVariable String id,@RequestBody(required=false) byte[] body,HttpServletRequest request) { return transition(id,body,request,Action.RESTORE); }
    private ResponseEntity<ProductDetail> transition(String id,byte[] body,HttpServletRequest request,Action action) {
        noQuery(request); var revisions=requests.revisions(body);
        return ok(service.transition(reader.identifier(id),revisions.getVersion(),revisions.getTaxonomyVersion(),action));
    }
    private void noQuery(HttpServletRequest request) { if(Set.of("POST","PATCH","PUT").contains(request.getMethod()))reader.requireJson(request.getContentType());reader.query(request.getParameterMap(),Set.of(),Set.of()); }
    private static <T> ResponseEntity<T> ok(T value) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value); }
}
