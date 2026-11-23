package com.example.demo.catalog.controller.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.example.demo.catalog.controller.ProductImageController;
import com.example.demo.catalog.dto.request.AdminCatalogRequestReader;
import com.example.demo.catalog.dto.request.AdminGalleryRequests;
import com.example.demo.catalog.dto.request.AdminProductRequests;
import com.example.demo.catalog.dto.request.AdminVariantRequests;
import com.example.demo.catalog.dto.response.AdminCatalogResponses;
import com.example.demo.catalog.dto.response.SellingPrice;
import com.example.demo.catalog.service.AdminGalleryService;
import com.example.demo.catalog.service.AdminProductService;
import com.example.demo.catalog.service.AdminVariantService;
import com.example.demo.catalog.service.ProductImageService;
import com.example.demo.common.exception.AdminCatalogExceptionHandler;
import com.example.demo.common.identity.CurrentUserIdProvider;
import com.example.demo.common.security.CatalogSecurityConfiguration;
import com.example.demo.common.security.CatalogSecurityProblemHandler;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** T040 ONLY: early18 contracted operations + legacy alias. Real session invalidation is T069. */
@WebMvcTest({AdminProductController.class,AdminVariantController.class,AdminGalleryController.class,ProductImageController.class})
@Import({CatalogSecurityConfiguration.class,CatalogSecurityProblemHandler.class,AdminCatalogExceptionHandler.class,
        AdminCatalogRequestReader.class,AdminProductRequests.class,AdminVariantRequests.class,AdminGalleryRequests.class})
class AdminCatalogSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean AdminProductService products;
    @MockitoBean AdminVariantService variants;
    @MockitoBean AdminGalleryService gallery;
    @MockitoBean ProductImageService legacy;
    @MockitoBean CurrentUserIdProvider identity;
    @MockitoBean com.example.demo.auth.security.CurrentStateJwtConverter converter;
    @MockitoBean org.springframework.security.oauth2.jwt.JwtDecoder decoder;
    static final UUID PRODUCT=UUID.fromString("00000000-0000-0000-0000-000000000001"),VARIANT=UUID.fromString("00000000-0000-0000-0000-000000000002"),IMAGE=UUID.fromString("00000000-0000-0000-0000-000000000003");
    static final String BASE="/api/admin/catalog/products",P=BASE+"/"+PRODUCT,V=P+"/variants",G=P+"/images";
    static final String REVISIONS="\"expectedVersion\":\"0\",\"expectedTaxonomyVersion\":\"0\"";
    record Route(String method,String path,String body,int success) { }
    @BeforeEach void setup(){
        var row=new AdminCatalogResponses.ProductRow(PRODUCT,"Draft","DRAFT","0",new AdminCatalogResponses.CategoryReference(IMAGE,"Category","0"),null,1,1);
        var detail=new AdminCatalogResponses.ProductDetail(row,null,null,List.of(),List.of(),"0",new AdminCatalogResponses.Readiness(false,List.of()));
        when(products.list(any())).thenReturn(new AdminCatalogResponses.ProductPage(List.of(row),0,20,1,1,"0"));when(products.get(any())).thenReturn(detail);
        when(products.create(any())).thenReturn(detail);when(products.update(any(),any())).thenReturn(detail);when(products.transition(any(),anyLong(),anyLong(),any())).thenReturn(detail);
        var variant=new AdminCatalogResponses.Variant(VARIANT,PRODUCT,"0","sku",true,Map.of(),new SellingPrice(BigInteger.ONE,"VND"),Map.of("schema_version",1,"values",Map.of()));
        var mutation=new AdminCatalogResponses.VariantMutation(variant,"0","0");when(variants.list(any())).thenReturn(new AdminCatalogResponses.VariantList(List.of(variant),"0","0"));
        when(variants.create(any(),any())).thenReturn(mutation);when(variants.update(any(),any(),any())).thenReturn(mutation);when(variants.setEnabled(any(),any(),anyLong(),anyLong(),org.mockito.ArgumentMatchers.anyBoolean())).thenReturn(mutation);
        var image=new AdminCatalogResponses.Image(IMAGE,PRODUCT,"https://images.test/signed","Front",2,2,0,true);var current=new AdminCatalogResponses.Gallery(List.of(image),"0","0");
        when(gallery.get(any())).thenReturn(current);when(gallery.attach(any(),anyLong(),any())).thenReturn(new AdminCatalogResponses.Attachment(image,false,"0","0"));
        when(gallery.primary(any(),any(),anyLong(),anyLong())).thenReturn(current);when(gallery.reorder(any(),anyLong(),anyLong(),any())).thenReturn(current);
        when(identity.getCurrentUserId()).thenReturn(8L);when(legacy.attach(any(),any(),anyLong(),anyString(),any(),any(),anyLong(),anyLong()))
                .thenReturn(new ProductImageService.Outcome(new ProductImageService.Attached(IMAGE,PRODUCT,"Front",2,2,0,true),false,"0","0"));
    }
    static Stream<Route> routes(){return Stream.of(
        new Route("GET",BASE,null,200),new Route("GET",P,null,200),new Route("POST",BASE,"{\"name\":\"Draft\",\"categoryId\":\""+IMAGE+"\"}",201),new Route("PATCH",P,"{"+REVISIONS+",\"name\":\"Rename\"}",200),
        new Route("POST",P+"/publish","{"+REVISIONS+"}",200),new Route("POST",P+"/unpublish","{"+REVISIONS+"}",200),new Route("POST",P+"/archive","{"+REVISIONS+"}",200),new Route("POST",P+"/restore","{"+REVISIONS+"}",200),
        new Route("GET",V,null,200),new Route("POST",V,"{"+REVISIONS+",\"sku\":\"sku\",\"sellingPrice\":{\"amount\":1,\"currency\":\"VND\"}}",201),new Route("PATCH",V+"/"+VARIANT,"{"+REVISIONS+",\"sku\":\"new\"}",200),
        new Route("POST",V+"/"+VARIANT+"/enable","{"+REVISIONS+"}",200),new Route("POST",V+"/"+VARIANT+"/disable","{"+REVISIONS+"}",200),
        new Route("GET",G,null,200),new Route("POST",G,"{"+REVISIONS+",\"imageId\":\""+IMAGE+"\",\"altText\":\"Front\"}",201),new Route("PUT",G+"/primary","{"+REVISIONS+",\"imageId\":\""+IMAGE+"\"}",200),
        new Route("PUT",G+"/order","{"+REVISIONS+",\"imageIds\":[\""+IMAGE+"\"]}",200),new Route("DELETE",G+"/"+IMAGE+"?expectedVersion=0&expectedTaxonomyVersion=0",null,204),
        new Route("POST","/api/products/"+PRODUCT+"/images","{"+REVISIONS+",\"imageId\":\""+IMAGE+"\",\"altText\":\"Front\"}",201));}
    @ParameterizedTest @MethodSource("routes") void roleMatrix_shouldRequireJwtAdministratorAtFilterChain(Route route) throws Exception {
        for(String role:List.of("anonymous","customer","password-admin","jwt-admin")){
            var request=MockMvcRequestBuilders.request(HttpMethod.valueOf(route.method()),route.path());
            if(route.body()!=null)request.contentType("application/json").content(route.body());
            if(role.equals("customer"))request.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")));
            if(role.equals("password-admin"))request.with(user("admin").roles("ADMIN"));
            if(role.equals("jwt-admin"))request.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")));
            var response=mvc.perform(request).andExpect(status().is(role.equals("anonymous")?401:role.equals("jwt-admin")?route.success():403)).andReturn().getResponse();
            if(role.equals("jwt-admin"))assertThat(response.getHeader("Cache-Control")).contains("no-store");
        }
    }
    static Stream<Route> bodyRoutes(){return routes().filter(route->route.body()!=null);}
    @ParameterizedTest @MethodSource("bodyRoutes") void mediaType_shouldReturnSafe415ForBodyMutations_afterAdminAuthorization(Route route) throws Exception {
        for(String type:List.of("text/plain","application/x-www-form-urlencoded","image/png"))
            mvc.perform(MockMvcRequestBuilders.request(HttpMethod.valueOf(route.method()),route.path()).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                    .contentType(type).content(route.body())).andExpect(status().isUnsupportedMediaType());
    }

}
