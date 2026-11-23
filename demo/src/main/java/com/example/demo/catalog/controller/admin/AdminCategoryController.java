package com.example.demo.catalog.controller.admin;

import com.example.demo.catalog.dto.request.AdminCatalogRequestReader;
import com.example.demo.catalog.dto.request.AdminTaxonomyRequests;
import com.example.demo.catalog.dto.response.AdminCatalogResponses.CategoryPage;
import com.example.demo.catalog.dto.response.AdminCatalogResponses.CategoryDetail;
import com.example.demo.catalog.dto.response.AdminCatalogResponses.CategoryMutation;
import com.example.demo.catalog.service.AdminCategoryService;
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
@RequestMapping("/api/admin/catalog/categories")
public class AdminCategoryController {
    private final AdminCategoryService service;
    private final AdminTaxonomyRequests requests;
    private final AdminCatalogRequestReader reader;
    @GetMapping public ResponseEntity<CategoryPage> list(HttpServletRequest request){return ok(service.list(requests.query(request.getParameterMap(),true)));}
    @GetMapping("/{id}") public ResponseEntity<CategoryDetail> get(@PathVariable String id,HttpServletRequest request){noQuery(request);return ok(service.get(reader.identifier(id)));}
    @PostMapping public ResponseEntity<CategoryMutation> create(@RequestBody(required=false) byte[] body,HttpServletRequest request){noQuery(request);CategoryMutation result=service.create(requests.categoryCreate(body));return ResponseEntity.created(URI.create("/api/admin/catalog/categories/"+result.getCategory().getId())).cacheControl(CacheControl.noStore()).body(result);}
    @PatchMapping(value="/{id}") public ResponseEntity<CategoryMutation> update(@PathVariable String id,@RequestBody(required=false) byte[] body,HttpServletRequest request){noQuery(request);return ok(service.update(reader.identifier(id),requests.categoryPatch(body)));}
    @DeleteMapping("/{id}") public ResponseEntity<Void> delete(@PathVariable String id,HttpServletRequest request){var command=requests.deletion(request.getParameterMap(),true);service.delete(reader.identifier(id),command.getVersion(),command.getTaxonomy());return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();}
    private void noQuery(HttpServletRequest request){if(Set.of("POST","PATCH","PUT").contains(request.getMethod()))reader.requireJson(request.getContentType());reader.query(request.getParameterMap(),Set.of(),Set.of());}
    private static <T> ResponseEntity<T> ok(T value){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);}
}
