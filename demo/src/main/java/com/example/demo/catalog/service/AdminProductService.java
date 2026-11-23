package com.example.demo.catalog.service;

import com.example.demo.catalog.dto.request.AdminProductRequests.*;
import com.example.demo.catalog.dto.response.AdminCatalogResponses.*;
import java.util.UUID;

public interface AdminProductService {
    ProductPage list(Query query);
    ProductDetail get(UUID id);
    ProductDetail create(Create request);
    ProductDetail update(UUID id,Patch request);
    ProductDetail transition(UUID id,long version,long taxonomyVersion,Action action);
}
