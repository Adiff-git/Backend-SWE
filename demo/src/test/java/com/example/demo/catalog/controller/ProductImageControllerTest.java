package com.example.demo.catalog.controller;

import com.example.demo.catalog.service.ProductImageService;
import com.example.demo.common.identity.CurrentUserIdProvider;
import com.example.demo.common.security.CatalogSecurityConfiguration;
import com.example.demo.common.security.CatalogSecurityProblemHandler;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProductImageController.class)
@Import({CatalogSecurityConfiguration.class, CatalogSecurityProblemHandler.class,
        com.example.demo.common.exception.ApiExceptionHandler.class, com.example.demo.common.exception.ApiErrorFactory.class})
class ProductImageControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean ProductImageService service;
    @MockitoBean CurrentUserIdProvider identity;
    @MockitoBean com.example.demo.auth.security.CurrentStateJwtConverter converter;
    @MockitoBean org.springframework.security.oauth2.jwt.JwtDecoder decoder;
    private final UUID product = UUID.randomUUID();
    private final UUID source = UUID.randomUUID();
    private String path() { return "/api/products/" + product + "/images"; }
    private String body() { return "{\"imageId\":\"" + source + "\",\"altText\":\"Front\"}"; }

    @Test void adminCanAttachAndReplayUsingServerIdentity() throws Exception {
        when(identity.getCurrentUserId()).thenReturn(8L);
        var attached = new ProductImageService.Attached(UUID.randomUUID(), product, "Front", 2, 2, 0, true);
        when(service.attach(product, source, 8L, "Front", null, null))
                .thenReturn(new ProductImageService.Outcome(attached, false), new ProductImageService.Outcome(attached, true));
        mvc.perform(post(path()).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .contentType("application/json").content(body()))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.imageId").value(attached.imageId().toString()))
                .andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(post(path()).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                .contentType("application/json").content(body())).andExpect(status().isOk());
    }

    @Test void anonymousAndCustomerCannotAttach() throws Exception {
        mvc.perform(post(path()).contentType("application/json").content(body())).andExpect(status().isUnauthorized());
        mvc.perform(post(path()).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                .contentType("application/json").content(body())).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test void rejectsUnknownFieldsInvalidIdsAndExplicitNull() throws Exception {
        for (String body : new String[]{"{}", body().replace("Front", " "),
                body().replace(source.toString(), "invalid"), body().replace("}", ",\"ownerId\":9}"),
                body().replace("}", ",\"isPrimary\":null}"), body().replace("}", ",\"displayOrder\":1.5}")}) {
            mvc.perform(post(path()).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                    .contentType("application/json").content(body)).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }

    @Test void oldUploadEndpointsAreDeniedEvenForAdmin() throws Exception {
        for (String suffix : new String[]{"/uploads", "/uploads/" + source + "/complete"})
            mvc.perform(post(path() + suffix).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                    .contentType("application/json").content("{}")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
}
