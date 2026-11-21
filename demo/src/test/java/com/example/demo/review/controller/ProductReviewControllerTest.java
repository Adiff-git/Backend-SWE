package com.example.demo.review.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.common.exception.ApiErrorFactory;
import com.example.demo.common.security.CatalogSecurityConfiguration;
import com.example.demo.common.security.CatalogSecurityProblemHandler;
import com.example.demo.review.dto.response.ReviewPage;
import com.example.demo.review.service.ReviewReadService;
import com.example.demo.review.service.ReviewStatisticsService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProductReviewController.class)
@Import({CatalogSecurityConfiguration.class, CatalogSecurityProblemHandler.class, ApiErrorFactory.class})
class ProductReviewControllerTest {
    private static final String PATH = "/api/products/11111111-1111-4111-8111-111111111111/reviews";
    @Autowired MockMvc mvc;
    @MockitoBean ReviewReadService reviews;
    @MockitoBean ReviewStatisticsService statistics;
    @MockitoBean com.example.demo.auth.security.CurrentStateJwtConverter converter;
    @MockitoBean org.springframework.security.oauth2.jwt.JwtDecoder decoder;
    @Test void list_shouldPermitAnonymousEmptyPage() throws Exception {
        when(reviews.getReviews(any(), any())).thenReturn(new ReviewPage(List.of(), 0, 20, 0, 0));
        mvc.perform(get(PATH)).andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
    }
    @ParameterizedTest @ValueSource(strings = {"size=0", "size=101", "page=-1", "page=x", "size=1&size=2", "rating=5", "sort=unknown,asc"})
    void list_shouldRejectUnsupportedParameters(String query) throws Exception {
        mvc.perform(get(PATH + "?" + query)).andExpect(status().isBadRequest());
    }
}
