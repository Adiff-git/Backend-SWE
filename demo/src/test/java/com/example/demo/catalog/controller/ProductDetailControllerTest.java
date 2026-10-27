package com.example.demo.catalog.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.catalog.dto.response.CategorySummary;
import com.example.demo.catalog.dto.response.ProductDetail;
import com.example.demo.catalog.dto.response.SellingPrice;
import com.example.demo.catalog.dto.response.VariantSummary;
import com.example.demo.catalog.exception.ProductNotFoundException;
import com.example.demo.catalog.service.ProductCatalogService;
import com.example.demo.common.exception.ApiExceptionHandler;
import com.example.demo.common.security.CatalogSecurityConfiguration;
import com.example.demo.common.security.CatalogSecurityProblemHandler;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProductCatalogController.class)
@Import({CatalogSecurityConfiguration.class, CatalogSecurityProblemHandler.class,
		ApiExceptionHandler.class, ProductDetailControllerTest.StubConfiguration.class})
class ProductDetailControllerTest {

	private static final UUID PRODUCT_ID = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

	@Autowired private MockMvc mvc;
	@Autowired private StubService service;

	@BeforeEach
	void reset() {
		service.requestedId = null;
		service.found = true;
	}

	@Test
	void anonymousCanonicalIdReturnsDetailDto() throws Exception {
		mvc.perform(get("/api/products/{id}", PRODUCT_ID))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.id").value(PRODUCT_ID.toString()))
				.andExpect(jsonPath("$.category.name").value("Category"))
				.andExpect(jsonPath("$.variants.length()").value(1))
				.andExpect(jsonPath("$.images").isEmpty());
		assertThat(service.requestedId).isEqualTo(PRODUCT_ID);
	}

	@Test
	void invalidAndNoncanonicalIdsReturn400ProblemDetail() throws Exception {
		for (String id : List.of("not-a-uuid", "123E4567-E89B-12D3-A456-426614174000",
				"123e4567e89b12d3a456426614174000", "123e4567-e89b-12d3-a456-42661417400")) {
			mvc.perform(get("/api/products/{id}", id))
					.andExpect(status().isBadRequest())
					.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
					.andExpect(jsonPath("$.status").value(400));
		}
		assertThat(service.requestedId).isNull();
	}

	@Test
	void unknownCanonicalIdReturns404ProblemDetail() throws Exception {
		service.found = false;
		mvc.perform(get("/api/products/{id}", PRODUCT_ID))
				.andExpect(status().isNotFound())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.detail").value("Product not found"));
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class StubConfiguration {
		@Bean
		StubService detailServiceStub() {
			return new StubService();
		}
	}

	static class StubService implements ProductCatalogService {
		private UUID requestedId;
		private boolean found;

		@Override
		public com.example.demo.catalog.dto.response.ProductPage listProducts(int page, int size) {
			throw new UnsupportedOperationException();
		}

		@Override
		public ProductDetail getProduct(UUID id) {
			requestedId = id;
			if (!found) {
				throw new ProductNotFoundException();
			}
			return new ProductDetail(id, "Product", new CategorySummary(UUID.randomUUID(), "Category"),
					null, "Description", List.of(new VariantSummary(UUID.randomUUID(), "SKU-1",
						Map.of(), new SellingPrice(BigInteger.valueOf(1000), "VND"))), List.of());
		}
	}
}
