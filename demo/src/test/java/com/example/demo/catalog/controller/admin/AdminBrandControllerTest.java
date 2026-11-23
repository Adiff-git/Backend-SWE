package com.example.demo.catalog.controller.admin;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import com.example.demo.catalog.dto.request.AdminCatalogRequestReader;
import com.example.demo.catalog.dto.request.AdminTaxonomyRequests;
import com.example.demo.catalog.dto.response.AdminCatalogResponses.Brand;
import com.example.demo.catalog.service.AdminBrandService;
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

@WebMvcTest(AdminBrandController.class)
@Import({CatalogSecurityConfiguration.class,CatalogSecurityProblemHandler.class,AdminCatalogExceptionHandler.class,AdminCatalogRequestReader.class,AdminTaxonomyRequests.class})
class AdminBrandControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean AdminBrandService service;
    @MockitoBean com.example.demo.auth.security.CurrentStateJwtConverter converter;
    @MockitoBean org.springframework.security.oauth2.jwt.JwtDecoder decoder;
    final UUID id=UUID.randomUUID();final String base="/api/admin/catalog/brands";
    org.springframework.test.web.servlet.request.RequestPostProcessor admin(){return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));}
    @Test void create_shouldReturnStableCodeAndCurrentOwnRevision() throws Exception {
        when(service.create(any())).thenReturn(new Brand(id,"stable","Brand","0"));
        mvc.perform(post(base).with(admin()).contentType("application/json").content("{\"code\":\"stable\",\"name\":\"Brand\"}"))
                .andExpect(status().isCreated()).andExpect(header().string("Location",base+"/"+id)).andExpect(jsonPath("$.code").value("stable")).andExpect(jsonPath("$.version").value("0"));
    }
    @Test void patch_shouldRejectImmutableCodeAndExtraProperties() throws Exception {
        for(String body:List.of("{\"expectedVersion\":\"0\",\"name\":\"Name\",\"code\":\"changed\"}","{\"expectedVersion\":\"0\"}","{\"expectedVersion\":\"0\",\"name\":null}"))
            mvc.perform(patch(base+"/"+id).with(admin()).contentType("application/json").content(body)).andExpect(status().isBadRequest());verifyNoInteractions(service);
    }
    @Test void delete_shouldRequireCurrentOwnToken_andReturnNoBody() throws Exception {
        mvc.perform(delete(base+"/"+id+"?expectedVersion=0").with(admin())).andExpect(status().isNoContent()).andExpect(content().string(""));verify(service).delete(id,0);
        mvc.perform(delete(base+"/"+id+"?expectedVersion=0&expectedTaxonomyVersion=0").with(admin())).andExpect(status().isBadRequest());
    }
}
