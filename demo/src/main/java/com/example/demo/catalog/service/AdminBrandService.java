package com.example.demo.catalog.service;

import com.example.demo.catalog.dto.request.AdminTaxonomyRequests.BrandCreate;
import com.example.demo.catalog.dto.request.AdminTaxonomyRequests.BrandRename;
import com.example.demo.catalog.dto.request.AdminTaxonomyRequests.Query;
import com.example.demo.catalog.dto.response.AdminCatalogResponses.Brand;
import com.example.demo.catalog.dto.response.AdminCatalogResponses.Page;
import java.util.UUID;

public interface AdminBrandService {
    Page<Brand> list(Query query);
    Brand get(UUID id);
    Brand create(BrandCreate request);
    Brand update(UUID id,BrandRename request);
    void delete(UUID id,long version);
}
