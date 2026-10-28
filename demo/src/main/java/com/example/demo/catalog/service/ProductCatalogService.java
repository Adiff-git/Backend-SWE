package com.example.demo.catalog.service;

import com.example.demo.catalog.dto.response.ProductPage;
import com.example.demo.catalog.dto.response.ProductDetail;
import java.util.UUID;

public interface ProductCatalogService {

	int DEFAULT_PAGE = 0;
	int DEFAULT_SIZE = 20;
	int MAX_SIZE = 100;

	ProductPage listProducts(int page, int size);

	ProductDetail getProduct(UUID id);
}
