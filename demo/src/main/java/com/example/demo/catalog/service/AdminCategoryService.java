package com.example.demo.catalog.service;

import com.example.demo.catalog.dto.request.AdminTaxonomyRequests.CategoryCreate;
import com.example.demo.catalog.dto.request.AdminTaxonomyRequests.CategoryPatch;
import com.example.demo.catalog.dto.request.AdminTaxonomyRequests.Query;
import com.example.demo.catalog.dto.response.AdminCatalogResponses.CategoryPage;
import com.example.demo.catalog.dto.response.AdminCatalogResponses.CategoryDetail;
import com.example.demo.catalog.dto.response.AdminCatalogResponses.CategoryMutation;
import java.util.UUID;

public interface AdminCategoryService {
    CategoryPage list(Query query);
    CategoryDetail get(UUID id);
    CategoryMutation create(CategoryCreate request);
    CategoryMutation update(UUID id,CategoryPatch request);
    void delete(UUID id,long version,long taxonomy);
}
