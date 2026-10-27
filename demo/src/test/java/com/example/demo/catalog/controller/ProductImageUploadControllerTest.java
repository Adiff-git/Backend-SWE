package com.example.demo.catalog.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.catalog.dto.response.ProductDetail;
import com.example.demo.catalog.dto.response.ProductPage;
import com.example.demo.catalog.service.ProductCatalogService;
import com.example.demo.common.exception.ApiExceptionHandler;
import com.example.demo.common.security.CatalogSecurityConfiguration;
import com.example.demo.common.security.CatalogSecurityProblemHandler;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/** Upload-route security contract while the production POST controller is deliberately absent. */
@WebMvcTest(ProductCatalogController.class)
@Import({CatalogSecurityConfiguration.class, CatalogSecurityProblemHandler.class,
		ApiExceptionHandler.class, ProductImageUploadControllerTest.StubConfiguration.class})
class ProductImageUploadControllerTest {

	private static final String PRODUCT = "00000000-0000-0000-0000-000000000001";
	private static final String UPLOAD = "00000000-0000-0000-0000-000000000002";
	private static final String PREPARE_PATH = "/api/products/" + PRODUCT + "/images/uploads";
	private static final String COMPLETE_PATH = PREPARE_PATH + "/" + UPLOAD + "/complete";
	private static final String PREPARE_BODY = """
			{"contentType":"image/png","byteSize":123,"altText":"Front view"}
			""";

	@Autowired private MockMvc mvc;

	@Test
	void anonymousUploadRequestsShouldReceiveFilterChain401() throws Exception {
		for (String path : List.of(PREPARE_PATH, COMPLETE_PATH)) {
			mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(PREPARE_BODY))
					.andExpect(status().isUnauthorized())
					.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
					.andExpect(jsonPath("$.status").value(401))
					.andExpect(jsonPath("$.detail").value("Authentication is required"));
		}
	}

	@Test
	@WithMockUser(username = "customer-a", roles = "CUSTOMER")
	void mockCustomerShouldReceiveFilterChain403() throws Exception {
		for (String path : List.of(PREPARE_PATH, COMPLETE_PATH)) {
			mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(PREPARE_BODY))
					.andExpect(status().isForbidden())
					.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
					.andExpect(jsonPath("$.status").value(403));
		}
	}

	@Test
	@WithMockUser(username = "admin-a", roles = "ADMIN")
	void mockAdminShouldNotActivateUploadsBeforeVerifiedIdentityIntegration() throws Exception {
		for (String path : List.of(PREPARE_PATH, COMPLETE_PATH)) {
			mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(PREPARE_BODY))
					.andExpect(status().isForbidden())
					.andExpect(jsonPath("$.detail").value("Access is denied"));
		}
	}

	@Test
	void clientSuppliedAdminClaimsShouldNotProveIdentity() throws Exception {
		mvc.perform(post(PREPARE_PATH).header("X-Role", "ADMIN")
				.header("X-User-Id", "admin-a")
				.contentType(MediaType.APPLICATION_JSON).content(PREPARE_BODY))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void anonymousProductReadShouldRemainAvailable() throws Exception {
		mvc.perform(get("/api/products"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items").isEmpty());
	}

	@Test
	@Disabled("Pending T052/T054/T055 verified actor, upload service and production controller")
	@WithMockUser(username = "admin-a", roles = "ADMIN")
	void verifiedAdminPreparationShouldReturnStagingContract() throws Exception {
		mvc.perform(post(PREPARE_PATH).contentType(MediaType.APPLICATION_JSON).content(PREPARE_BODY))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.uploadId").isNotEmpty())
				.andExpect(jsonPath("$.uploadUrl").isNotEmpty())
				.andExpect(jsonPath("$.method").value("PUT"))
				.andExpect(jsonPath("$.requiredHeaders").isMap())
				.andExpect(jsonPath("$.expiresAt").isNotEmpty());
	}

	@Test
	@Disabled("Pending T052/T057/T058 service ownership check and production controller")
	@WithMockUser(username = "admin-b", roles = "ADMIN")
	void differentAdminCompletionShouldReceiveService403() throws Exception {
		mvc.perform(post(COMPLETE_PATH).contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isForbidden())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.detail")
						.value("Only the administrator who prepared the upload may complete it"));
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class StubConfiguration {
		@Bean
		ProductCatalogService productCatalogService() {
			return new ProductCatalogService() {
				@Override
				public ProductPage listProducts(int page, int size) {
					assertThat(page).isZero();
					return new ProductPage(List.of(), 0, size, 0, 0);
				}

				@Override
				public ProductDetail getProduct(UUID id) {
					throw new UnsupportedOperationException();
				}
			};
		}
	}
}
