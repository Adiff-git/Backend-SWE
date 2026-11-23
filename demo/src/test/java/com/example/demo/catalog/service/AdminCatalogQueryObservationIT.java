package com.example.demo.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.demo.catalog.dto.request.AdminProductRequests;
import com.example.demo.config.SqlCapture;
import com.example.demo.config.SqlCaptureConfiguration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

/** Records actual JDBC including native claims/reads; intentionally introduces no Admin SLA. */
@Import(SqlCaptureConfiguration.class)
class AdminCatalogQueryObservationIT extends AdminCatalogIntegrationSupport {
    @Test void recordAdministrativeListAndDetailQueriesAtTwoFixtureSizes() throws Exception {
        var observations=new ArrayList<Map<String,Object>>();UUID first=null;
        for(int count=1;count<=20;count++){
            UUID id=ready();if(first==null)first=id;
            if(count!=1 && count!=20)continue;
            SqlCapture.begin();java.util.List<String> listSql;
            try{assertThat(products.list(new AdminProductRequests.Query(PageRequest.of(0,20),Set.of(),null,false,null,null,AdminProductRequests.Ordering.ID_ASC)).getTotalItems()).isEqualTo(count);}finally{listSql=SqlCapture.end();}
            SqlCapture.begin();java.util.List<String> detailSql;
            try{assertThat(products.get(first).getVariants()).hasSize(1);}finally{detailSql=SqlCapture.end();}
            assertThat(listSql).isNotEmpty();assertThat(detailSql).isNotEmpty();
            observations.add(Map.of("products",count,"listStatements",listSql.size(),"detailStatements",detailSql.size(),"listSql",listSql,"detailSql",detailSql));
        }
        Path output=Path.of("target","admin-catalog-query-observations.json");
        Files.writeString(output,tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(Map.of("boundary","Actual JDBC incl native statements;local signer;no Admin SLA asserted","observations",observations)));
    }
}
