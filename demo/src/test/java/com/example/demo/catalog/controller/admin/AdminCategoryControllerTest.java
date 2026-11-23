package com.example.demo.catalog.controller.admin;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import com.example.demo.catalog.dto.request.AdminCatalogRequestReader;
import com.example.demo.catalog.dto.request.AdminTaxonomyRequests;
import com.example.demo.catalog.dto.response.AdminCatalogResponses.Category;
import com.example.demo.catalog.dto.response.AdminCatalogResponses.CategoryMutation;
import com.example.demo.catalog.service.AdminCategoryService;
import com.example.demo.common.exception.AdminCatalogExceptionHandler;
import com.example.demo.common.security.CatalogSecurityConfiguration;
import com.example.demo.common.security.CatalogSecurityProblemHandler;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AdminCategoryController.class)
@Import({CatalogSecurityConfiguration.class,CatalogSecurityProblemHandler.class,AdminCatalogExceptionHandler.class,AdminCatalogRequestReader.class,AdminTaxonomyRequests.class})
class AdminCategoryControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean AdminCategoryService service;
    @MockitoBean com.example.demo.auth.security.CurrentStateJwtConverter converter;
    @MockitoBean org.springframework.security.oauth2.jwt.JwtDecoder decoder;
    final UUID id=UUID.randomUUID();final String base="/api/admin/catalog/categories";
    org.springframework.test.web.servlet.request.RequestPostProcessor admin(){return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));}
    @Test void create_shouldRequireTaxonomyRevision_andReturnCategoryEnvelopeLocation() throws Exception {
        when(service.create(any())).thenReturn(new CategoryMutation(new Category(id,"Laptop",null,"0"),"1",List.of()));
        mvc.perform(post(base).with(admin()).contentType("application/json").content("{\"expectedTaxonomyVersion\":\"0\",\"name\":\"Laptop\",\"parentCategoryId\":null}"))
                .andExpect(status().isCreated()).andExpect(header().string("Location",base+"/"+id)).andExpect(jsonPath("$.taxonomyVersion").value("1"));
        mvc.perform(post(base).with(admin()).contentType("application/json").content("{\"name\":\"Laptop\"}")).andExpect(status().isBadRequest());
    }
    @Test void patch_shouldDistinguishRootNullFromOmittedParent_andRejectEmptyEdit() throws Exception {
        mvc.perform(patch(base+"/"+id).with(admin()).contentType("application/json").content("{\"expectedVersion\":\"0\",\"expectedTaxonomyVersion\":\"0\",\"parentCategoryId\":null}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
        var argument=org.mockito.ArgumentCaptor.forClass(AdminTaxonomyRequests.CategoryPatch.class);verify(service).update(eq(id),argument.capture());
        org.assertj.core.api.Assertions.assertThat(argument.getValue().getParent().isSupplied()).isTrue();org.assertj.core.api.Assertions.assertThat(argument.getValue().getParent().getValue()).isNull();
        mvc.perform(patch(base+"/"+id).with(admin()).contentType("application/json").content("{\"expectedVersion\":\"0\",\"expectedTaxonomyVersion\":\"0\"}")).andExpect(status().isBadRequest());
    }
    @Test void delete_shouldRequireBothSingleQueryRevisions() throws Exception {
        mvc.perform(delete(base+"/"+id+"?expectedVersion=0&expectedTaxonomyVersion=2").with(admin())).andExpect(status().isNoContent());verify(service).delete(id,0,2);
        mvc.perform(delete(base+"/"+id+"?expectedVersion=0").with(admin())).andExpect(status().isBadRequest());
    }
}
