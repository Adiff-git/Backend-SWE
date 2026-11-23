package com.example.demo.catalog.repository;

import com.example.demo.catalog.dto.request.AdminProductRequests.Query;
import com.example.demo.catalog.dto.response.AdminCatalogResponses.ProductPage;

/** Administrative metadata only, separate from customer search/relevance. */
public interface AdminProductRepository {
    ProductPage findAdminPage(Query query);
}
