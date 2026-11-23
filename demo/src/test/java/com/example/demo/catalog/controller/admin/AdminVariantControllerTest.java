package com.example.demo.catalog.controller.admin;

import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.example.demo.catalog.dto.request.*;
import com.example.demo.catalog.service.AdminVariantService;
import com.example.demo.common.exception.AdminCatalogExceptionHandler;
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

@WebMvcTest(AdminVariantController.class)
@Import({CatalogSecurityConfiguration.class,CatalogSecurityProblemHandler.class,AdminCatalogExceptionHandler.class,AdminCatalogRequestReader.class,AdminVariantRequests.class})
class AdminVariantControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean AdminVariantService service;
    @MockitoBean com.example.demo.auth.security.CurrentStateJwtConverter converter;
    @MockitoBean org.springframework.security.oauth2.jwt.JwtDecoder decoder;
    final UUID product=UUID.randomUUID(),variant=UUID.randomUUID();
    String base(){return "/api/admin/catalog/products/"+product+"/variants";}
    org.springframework.test.web.servlet.request.RequestPostProcessor admin(){return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));}
    String revisions(){return "\"expectedVersion\":\"0\",\"expectedTaxonomyVersion\":\"0\"";}
    @Test void create_shouldPreserveLosslessWholePrice_andSetListLocation() throws Exception {
        mvc.perform(post(base()).with(admin()).contentType("application/json").content("{"+revisions()+",\"sku\":\"new\",\"sellingPrice\":{\"amount\":999999999999999999999999,\"currency\":\"VND\"}}"))
                .andExpect(status().isCreated()).andExpect(header().string("Location",base())).andExpect(header().string("Cache-Control","no-store"));
        var request=org.mockito.ArgumentCaptor.forClass(AdminVariantRequests.Create.class);
        verify(service).create(eq(product),request.capture());
        org.assertj.core.api.Assertions.assertThat(request.getValue().getSellingPrice()).isEqualByComparingTo("999999999999999999999999");
    }
    @ParameterizedTest @ValueSource(strings={"null","{\"amount\":1.5,\"currency\":\"VND\"}","{\"amount\":1,\"currency\":\"USD\"}"})
    void price_shouldRejectInvalidTransportOrSemantics(String price) throws Exception {
        mvc.perform(patch(base()+"/"+variant).with(admin()).contentType("application/json").content("{"+revisions()+",\"sellingPrice\":"+price+"}"))
                .andExpect(status().is(price.equals("null")?400:422));verifyNoInteractions(service);
    }
    @Test void patch_shouldRejectNullOptionsAndUnversionedOrUnknownMutations() throws Exception {
        for(String body:List.of("{}","{"+revisions()+"}","{"+revisions()+",\"options\":null}","{"+revisions()+",\"enabled\":false}"))
            mvc.perform(patch(base()+"/"+variant).with(admin()).contentType("application/json").content(body)).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void routes_shouldDenyAnonymousCustomerAndNonJwtAdmin() throws Exception {
        mvc.perform(get(base())).andExpect(status().isUnauthorized());
        mvc.perform(get(base()).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))).andExpect(status().isForbidden());
        mvc.perform(get(base()).with(user("admin").roles("ADMIN"))).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void toggle_shouldUseExplicitCommandAndExposeSafeConflictAdvice() throws Exception {
        when(service.setEnabled(product,variant,0,0,false)).thenThrow(new com.example.demo.catalog.exception.AdminCatalogException(409,"CATALOG_STALE_VERSION","Reload",Map.of("currentVersion","1")));
        mvc.perform(post(base()+"/"+variant+"/disable").with(admin()).contentType("application/json").content("{"+revisions()+"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CATALOG_STALE_VERSION"))
                .andExpect(jsonPath("$.currentVersion").value("1")).andExpect(header().string("Cache-Control","no-store"));
    }
    @Test void specifications_shouldExposeCurrentTokens_andDelegateCompleteReplacement() throws Exception {
        when(service.specifications(product,variant)).thenReturn(new com.example.demo.catalog.dto.response.AdminCatalogResponses.Specifications(
                Map.of("schema_version",1,"values",Map.of("feature",false)),"9","4"));
        mvc.perform(get(base()+"/"+variant+"/specifications").with(admin()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.productVersion").value("9"))
                .andExpect(jsonPath("$.technicalSpecifications.values.feature").value(false)).andExpect(header().string("Cache-Control","no-store"));
        mvc.perform(put(base()+"/"+variant+"/specifications").with(admin()).contentType("application/json")
                .content("{"+revisions()+",\"technicalSpecifications\":{\"schema_version\":1,\"values\":{}}}"))
                .andExpect(status().isOk());
        var request=org.mockito.ArgumentCaptor.forClass(AdminVariantRequests.Patch.class);
        verify(service).replaceSpecifications(eq(product),eq(variant),request.capture());
        org.assertj.core.api.Assertions.assertThat(request.getValue().getTechnicalSpecifications().getValue()).containsEntry("values",Map.of());
        org.assertj.core.api.Assertions.assertThat(request.getValue().getSellingPrice().isSupplied()).isFalse();
    }
    @ParameterizedTest @ValueSource(strings={"null","{}","{\"schema_version\":2,\"values\":{}}","{\"schema_version\":1,\"values\":{\"feature\":null}}","{\"schema_version\":1,\"values\":{},\"unexpected\":true}"})
    void specifications_shouldRejectNullUnsupportedEnvelopeAndValues(String document) throws Exception {
        mvc.perform(put(base()+"/"+variant+"/specifications").with(admin()).contentType("application/json")
                .content("{"+revisions()+",\"technicalSpecifications\":"+document+"}"))
                .andExpect(status().is(document.equals("null")?400:422));
        verifyNoInteractions(service);
    }
    @Test void specifications_shouldPreserveFalseZeroAndGenericKeys_forPatchAndPut() throws Exception {
        String document="{\"schema_version\":1,\"values\":{\"feature\":false,\"counter\":0,\"generic_text\":\"value\"}}";
        for(boolean put:List.of(false,true)) {
            var call=put?put(base()+"/"+variant+"/specifications"):patch(base()+"/"+variant);
            mvc.perform(call.with(admin()).contentType("application/json").content("{"+revisions()+",\"technicalSpecifications\":"+document+"}"))
                    .andExpect(status().isOk());
        }
        var request=org.mockito.ArgumentCaptor.forClass(AdminVariantRequests.Patch.class);
        verify(service).update(eq(product),eq(variant),request.capture());
        org.assertj.core.api.Assertions.assertThat((Map<?,?>)request.getValue().getTechnicalSpecifications().getValue().get("values"))
                .hasSize(3);
    }

}
