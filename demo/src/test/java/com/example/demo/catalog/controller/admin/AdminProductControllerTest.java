package com.example.demo.catalog.controller.admin;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.example.demo.catalog.dto.request.AdminProductRequests;
import com.example.demo.catalog.dto.request.AdminCatalogRequestReader;
import com.example.demo.catalog.dto.response.AdminCatalogResponses;
import com.example.demo.catalog.service.AdminProductService;
import com.example.demo.common.exception.*;
import com.example.demo.common.security.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminProductController.class)
@Import({CatalogSecurityConfiguration.class,CatalogSecurityProblemHandler.class,AdminCatalogExceptionHandler.class,
        AdminCatalogRequestReader.class,AdminProductRequests.class})
class AdminProductControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean AdminProductService service;
    @MockitoBean com.example.demo.auth.security.CurrentStateJwtConverter converter;
    @MockitoBean org.springframework.security.oauth2.jwt.JwtDecoder decoder;
    private final String base = "/api/admin/catalog/products";
    private final UUID id = UUID.randomUUID();
    private org.springframework.test.web.servlet.request.RequestPostProcessor admin() { return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")); }

    @Test void create_shouldReturnDraftLocationAndNoStore_withMinimalBody() throws Exception {
        var row = new AdminCatalogResponses.ProductRow(id,"Draft","DRAFT","0",null,null,0,0);
        when(service.create(any())).thenReturn(new AdminCatalogResponses.ProductDetail(row,null,null,List.of(),List.of(),"0",new AdminCatalogResponses.Readiness(false,List.of())));
        mvc.perform(post(base).with(admin()).contentType("application/json").content("{\"name\":\"Draft\",\"categoryId\":\""+id+"\"}"))
                .andExpect(status().isCreated()).andExpect(header().string("Location",base+"/"+id))
                .andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.variants").isEmpty());
    }

    @ParameterizedTest @ValueSource(strings={"publish","unpublish","archive","restore"})
    void transition_shouldRequireRevisionPair_andUseOnlyExplicitRoutes(String action) throws Exception {
        mvc.perform(post(base+"/"+id+"/"+action).with(admin()).contentType("application/json")
                .content("{\"expectedVersion\":\"0\",\"expectedTaxonomyVersion\":\"1\"}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
        verify(service).transition(id,0,1,AdminProductRequests.Action.valueOf(action.toUpperCase(Locale.ROOT)));
    }

    @ParameterizedTest @ValueSource(strings={"{}","{\"expectedVersion\":0,\"expectedTaxonomyVersion\":\"0\"}",
            "{\"expectedVersion\":\"0\",\"expectedTaxonomyVersion\":\"0\"}",
            "{\"expectedVersion\":\"0\",\"expectedTaxonomyVersion\":\"0\",\"name\":null}",
            "{\"expectedVersion\":\"0\",\"expectedTaxonomyVersion\":\"0\",\"status\":\"PUBLISHED\"}",
            "{\"expectedVersion\":\"0\",\"expectedTaxonomyVersion\":\"0\",\"name\":\"a\",\"name\":\"b\"}"})
    void patch_shouldRejectMalformedPresenceAndUnknownFields_beforeService(String body) throws Exception {
        mvc.perform(patch(base+"/"+id).with(admin()).contentType("application/json").content(body)).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test void list_shouldRejectUnknownRepeatedAndUnsupportedFilters() throws Exception {
        for(String query: List.of("?size=101","?page=1&page=2","?q=","?includeDescendants=true","?sort=price,asc","?state=DRAFT"))
            mvc.perform(get(base+query).with(admin())).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test void routes_shouldEnforceActualSecurityChain_andPreserveSafeAdvice() throws Exception {
        mvc.perform(get(base)).andExpect(status().isUnauthorized());
        mvc.perform(get(base).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))).andExpect(status().isForbidden());
        mvc.perform(get(base).with(user("admin").roles("ADMIN"))).andExpect(status().isForbidden());
        when(service.get(id)).thenThrow(new com.example.demo.catalog.exception.AdminCatalogException(409,"CATALOG_STALE_VERSION","Reload",Map.of("currentVersion","2")));
        mvc.perform(get(base+"/"+id).with(admin())).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CATALOG_STALE_VERSION"))
                .andExpect(jsonPath("$.currentVersion").value("2")).andExpect(header().string("Cache-Control","no-store"));
    }
    @Test void list_shouldDeduplicateStatusesAndPreserveOutOfRangeTotals() throws Exception {
        when(service.list(any())).thenReturn(new AdminCatalogResponses.ProductPage(List.of(),9,20,23,2,"4"));
        mvc.perform(get(base+"?page=9&status=DRAFT&status=ARCHIVED&status=DRAFT&categoryId="+id+"&includeDescendants=true&q=a%25_&sort=name,desc").with(admin()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty()).andExpect(jsonPath("$.totalItems").value(23))
                .andExpect(jsonPath("$.taxonomyVersion").value("4")).andExpect(header().string("Cache-Control","no-store"));
        var request=org.mockito.ArgumentCaptor.forClass(AdminProductRequests.Query.class);verify(service).list(request.capture());
        org.assertj.core.api.Assertions.assertThat(request.getValue().getStatuses()).containsExactlyInAnyOrder(com.example.demo.catalog.entity.ProductStatus.DRAFT,com.example.demo.catalog.entity.ProductStatus.ARCHIVED);
        org.assertj.core.api.Assertions.assertThat(request.getValue().isDescendants()).isTrue();
    }

}
