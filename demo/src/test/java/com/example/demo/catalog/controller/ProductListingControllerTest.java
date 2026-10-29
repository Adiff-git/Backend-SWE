package com.example.demo.catalog.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.catalog.dto.response.ProductPage;
import com.example.demo.catalog.service.ProductCatalogService;
import com.example.demo.common.exception.ApiExceptionHandler;
import com.example.demo.common.security.CatalogSecurityConfiguration;
import com.example.demo.common.security.CatalogSecurityProblemHandler;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProductCatalogController.class)
@Import({CatalogSecurityConfiguration.class, CatalogSecurityProblemHandler.class,
		ApiExceptionHandler.class, com.example.demo.common.exception.ApiErrorFactory.class,
		ProductListingControllerTest.StubConfiguration.class})
class ProductListingControllerTest {
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private com.example.demo.auth.security.CurrentStateJwtConverter catalogJwtConverter;
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private org.springframework.security.oauth2.jwt.JwtDecoder catalogJwtDecoder;


	@Autowired
	private MockMvc mvc;

	@Autowired
	private StubService service;

	@BeforeEach
	void resetStub() {
		service.requestedPage = -1;
		service.requestedSize = -1;
	}

	@Test
	void anonymousDefaultAndOutOfRangePagesUseCanonicalResponse() throws Exception {
		service.response = new ProductPage(List.of(), 0, 20, 0, 0);
		mvc.perform(get("/api/products"))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.items").isEmpty())
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.size").value(20))
				.andExpect(jsonPath("$.totalItems").value(0))
				.andExpect(jsonPath("$.totalPages").value(0));
		org.assertj.core.api.Assertions.assertThat(service.requestedPage).isZero();
		org.assertj.core.api.Assertions.assertThat(service.requestedSize).isEqualTo(20);

		service.response = new ProductPage(List.of(), 999, 1, 3, 3);
		mvc.perform(get("/api/products?page=999&size=1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items").isEmpty())
				.andExpect(jsonPath("$.totalItems").value(3));
	}

	@Test
	void acceptsSizeBoundaryValues() throws Exception {
		service.response = new ProductPage(List.of(), 0, 1, 0, 0);
		mvc.perform(get("/api/products?size=1")).andExpect(status().isOk());
		service.response = new ProductPage(List.of(), 0, 100, 0, 0);
		mvc.perform(get("/api/products?size=100")).andExpect(status().isOk());
	}

	@Test
	void rejectsInvalidDuplicateAndUnapprovedParametersAsProblemDetail() throws Exception {
		for (String query : List.of("page=-1", "page=nope", "page=0&page=1", "size=0",
				"size=101", "size=nope", "size=1&size=2", "sort=name", "filter=status")) {
			mvc.perform(get("/api/products?" + query))
					.andExpect(status().isBadRequest())
					.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
					.andExpect(jsonPath("$.status").value(400))
					.andExpect(jsonPath("$.detail").isNotEmpty());
		}
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class StubConfiguration {
		@Bean
		StubService catalogListingStub() {
			return new StubService();
		}
	}

	static class StubService implements ProductCatalogService {
		private int requestedPage;
		private int requestedSize;
		private ProductPage response;

		@Override
		public ProductPage listProducts(int page, int size) {
			requestedPage = page;
			requestedSize = size;
			return response;
		}

		@Override
		public com.example.demo.catalog.dto.response.ProductDetail getProduct(java.util.UUID id) {
			throw new UnsupportedOperationException();
		}
	}
}
