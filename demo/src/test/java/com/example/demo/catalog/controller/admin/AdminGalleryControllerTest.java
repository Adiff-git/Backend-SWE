package com.example.demo.catalog.controller.admin;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import com.example.demo.catalog.dto.request.AdminCatalogRequestReader;
import com.example.demo.catalog.dto.request.AdminGalleryRequests;
import com.example.demo.catalog.service.AdminGalleryService;
import com.example.demo.common.identity.CurrentUserIdProvider;
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

@WebMvcTest(AdminGalleryController.class)
@Import({CatalogSecurityConfiguration.class,CatalogSecurityProblemHandler.class,AdminCatalogExceptionHandler.class,AdminCatalogRequestReader.class,AdminGalleryRequests.class})
class AdminGalleryControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean AdminGalleryService service;
    @MockitoBean CurrentUserIdProvider identity;
    @MockitoBean com.example.demo.auth.security.CurrentStateJwtConverter converter;
    @MockitoBean org.springframework.security.oauth2.jwt.JwtDecoder decoder;
    final UUID product=UUID.randomUUID(),image=UUID.randomUUID();
    String base(){return "/api/admin/catalog/products/"+product+"/images";}
    org.springframework.test.web.servlet.request.RequestPostProcessor admin(){return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));}
    @Test void requests_shouldRejectMissingNullDuplicateAndUntrustedStorageFields() throws Exception {
        for(String body:List.of("{}","{\"expectedVersion\":\"0\",\"expectedTaxonomyVersion\":\"0\",\"imageId\":\""+image+"\",\"altText\":\"Front\",\"objectKey\":\"private\"}",
                "{\"expectedVersion\":\"0\",\"expectedTaxonomyVersion\":\"0\",\"imageId\":\""+image+"\",\"altText\":\"Front\",\"isPrimary\":null}"))
            mvc.perform(post(base()).with(admin()).contentType("application/json").content(body)).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void delete_shouldRequireSingleRevisionTokens_andUseNoResponseBody() throws Exception {
        mvc.perform(delete(base()+"/"+image+"?expectedVersion=0&expectedTaxonomyVersion=2").with(admin())).andExpect(status().isNoContent()).andExpect(content().string(""));
        verify(service).remove(product,image,0,2,null);
        mvc.perform(delete(base()+"/"+image+"?expectedVersion=0&expectedVersion=1&expectedTaxonomyVersion=2").with(admin())).andExpect(status().isBadRequest());
    }
    @Test void commands_shouldBindPrimaryAndExactOrderIntent_withNoStore() throws Exception {
        String revisions="\"expectedVersion\":\"0\",\"expectedTaxonomyVersion\":\"2\"";
        mvc.perform(put(base()+"/primary").with(admin()).contentType("application/json").content("{"+revisions+",\"imageId\":\""+image+"\"}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
        verify(service).primary(product,image,0,2);
        mvc.perform(put(base()+"/order").with(admin()).contentType("application/json").content("{"+revisions+",\"imageIds\":[\""+image+"\"]}"))
                .andExpect(status().isOk());verify(service).reorder(product,0,2,List.of(image));
    }
    @Test void attach_shouldUseServerIdentity_andReturnCreatedOrCurrentReplay_withGalleryLocation() throws Exception {
        when(identity.getCurrentUserId()).thenReturn(8L);
        var attached=new com.example.demo.catalog.dto.response.AdminCatalogResponses.Image(image,product,"https://images.test/signed","Front",2,2,0,true);
        when(service.attach(org.mockito.ArgumentMatchers.eq(product),org.mockito.ArgumentMatchers.eq(8L),any()))
                .thenReturn(new com.example.demo.catalog.dto.response.AdminCatalogResponses.Attachment(attached,false,"1","0"),
                        new com.example.demo.catalog.dto.response.AdminCatalogResponses.Attachment(attached,true,"1","0"));
        String body="{\"expectedVersion\":\"0\",\"expectedTaxonomyVersion\":\"0\",\"imageId\":\""+image+"\",\"altText\":\"Front\"}";
        mvc.perform(post(base()).with(admin()).contentType("application/json").content(body)).andExpect(status().isCreated())
                .andExpect(header().string("Location",base())).andExpect(jsonPath("$.productVersion").value("1"));
        mvc.perform(post(base()).with(admin()).contentType("application/json").content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.replay").value(true));
    }

}
