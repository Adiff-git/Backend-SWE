package com.example.demo.catalog.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.catalog.exception.CatalogConflictException;
import com.example.demo.catalog.exception.ProductNotFoundException;
import com.example.demo.common.exception.ApiExceptionHandler;
import com.example.demo.common.security.CatalogSecurityConfiguration;
import com.example.demo.common.security.CatalogSecurityProblemHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = CatalogSecurityBaselineTest.ProbeController.class)
@Import({CatalogSecurityConfiguration.class, CatalogSecurityProblemHandler.class,
		ApiExceptionHandler.class, com.example.demo.common.exception.ApiErrorFactory.class,
		CatalogSecurityBaselineTest.ProbeController.class})
class CatalogSecurityBaselineTest {

	@Autowired
	private MockMvc mvc;

    @Test void sharedCorsAllowsArbitraryOriginsAndPreflightMethods() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options("/api/products")
                .header("Origin", "https://any.example")
                .header("Access-Control-Request-Method", "PATCH")
                .header("Access-Control-Request-Headers", "X-Custom"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Access-Control-Allow-Origin", "*"));
        mvc.perform(get("/api/products").header("Origin", "https://another.example"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Access-Control-Allow-Origin", "*"));
    }

	@Test
	void anonymousCatalogReadsArePermitted() throws Exception {
		mvc.perform(get("/api/products")).andExpect(status().isOk());
		mvc.perform(get("/api/products/00000000-0000-0000-0000-000000000001"))
				.andExpect(status().isOk());
	}

	@Test
	void anonymousUploadPreparationAndCompletionReceiveFilterChain401() throws Exception {
		assertUploadDeniedForAnonymous("/api/products/00000000-0000-0000-0000-000000000001/images/uploads");
		assertUploadDeniedForAnonymous("/api/products/00000000-0000-0000-0000-000000000001/images/uploads/00000000-0000-0000-0000-000000000002/complete");
	}

	@Test
	@WithMockUser(roles = "CUSTOMER")
	void customerUploadPreparationAndCompletionReceiveFilterChain403() throws Exception {
		assertUploadDeniedForAuthenticated("/api/products/00000000-0000-0000-0000-000000000001/images/uploads");
		assertUploadDeniedForAuthenticated("/api/products/00000000-0000-0000-0000-000000000001/images/uploads/00000000-0000-0000-0000-000000000002/complete");
	}

	@Test
	@WithMockUser(roles = "ADMIN")
	void mockAdministratorCannotActivateUploadsBeforeVerifiedIdentityIntegration() throws Exception {
		assertUploadDeniedForAuthenticated("/api/products/00000000-0000-0000-0000-000000000001/images/uploads");
	}

	@Test
	void mvcAdviceHandlesSafeApplicationFailuresSeparately() throws Exception {
		mvc.perform(get("/api/products/missing"))
				.andExpect(status().isNotFound())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.detail").value("Product not found"));
		mvc.perform(get("/api/products/conflict"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.status").value(409));
	}

	private void assertUploadDeniedForAnonymous(String path) throws Exception {
		mvc.perform(post(path))
				.andExpect(status().isUnauthorized())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(401))
				.andExpect(jsonPath("$.detail").value("Authentication is required"))
				.andExpect(content().string(not(containsString("probe controller reached"))));
	}

	private void assertUploadDeniedForAuthenticated(String path) throws Exception {
		mvc.perform(post(path))
				.andExpect(status().isForbidden())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(403))
				.andExpect(jsonPath("$.detail").value("Access is denied"))
				.andExpect(content().string(not(containsString("probe controller reached"))));
	}

	/** Test-only endpoints reveal whether a request reached MVC. */
	@RestController
	static class ProbeController {
		@GetMapping("/api/products")
		String list() {
			return "probe catalog read";
		}

		@GetMapping("/api/products/{id}")
		String detail(@PathVariable String id) {
			return switch (id) {
				case "missing" -> throw new ProductNotFoundException();
				case "conflict" -> throw new CatalogConflictException();
				default -> "probe catalog read";
			};
		}

		@PostMapping("/api/products/{id}/images/uploads")
		String prepare() {
			return "probe controller reached";
		}

		@PostMapping("/api/products/{id}/images/uploads/{uploadId}/complete")
		String complete() {
			return "probe controller reached";
		}
	}
}
