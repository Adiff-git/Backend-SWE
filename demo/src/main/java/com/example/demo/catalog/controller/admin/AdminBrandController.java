package com.example.demo.catalog.controller.admin;

import com.example.demo.catalog.dto.request.AdminCatalogRequestReader;
import com.example.demo.catalog.dto.request.AdminTaxonomyRequests;
import com.example.demo.catalog.dto.response.AdminCatalogResponses.Brand;
import com.example.demo.catalog.dto.response.AdminCatalogResponses.Page;
import com.example.demo.catalog.service.AdminBrandService;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/catalog/brands")
public class AdminBrandController {
    private final AdminBrandService service;
    private final AdminTaxonomyRequests requests;
    private final AdminCatalogRequestReader reader;
    @GetMapping public ResponseEntity<Page<Brand>> list(HttpServletRequest request){return ok(service.list(requests.query(request.getParameterMap(),false)));}
    @GetMapping("/{id}") public ResponseEntity<Brand> get(@PathVariable String id,HttpServletRequest request){noQuery(request);return ok(service.get(reader.identifier(id)));}
    @PostMapping public ResponseEntity<Brand> create(@RequestBody(required=false) byte[] body,HttpServletRequest request){noQuery(request);Brand result=service.create(requests.brandCreate(body));return ResponseEntity.created(URI.create("/api/admin/catalog/brands/"+result.getId())).cacheControl(CacheControl.noStore()).body(result);}
    @PatchMapping(value="/{id}") public ResponseEntity<Brand> update(@PathVariable String id,@RequestBody(required=false) byte[] body,HttpServletRequest request){noQuery(request);return ok(service.update(reader.identifier(id),requests.brandRename(body)));}
    @DeleteMapping("/{id}") public ResponseEntity<Void> delete(@PathVariable String id,HttpServletRequest request){var command=requests.deletion(request.getParameterMap(),false);service.delete(reader.identifier(id),command.getVersion());return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();}
    private void noQuery(HttpServletRequest request){if(Set.of("POST","PATCH","PUT").contains(request.getMethod()))reader.requireJson(request.getContentType());reader.query(request.getParameterMap(),Set.of(),Set.of());}
    private static <T> ResponseEntity<T> ok(T value){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);}
}
