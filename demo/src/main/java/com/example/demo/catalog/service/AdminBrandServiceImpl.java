package com.example.demo.catalog.service;

import com.example.demo.catalog.converter.AdminCatalogConverter;
import com.example.demo.catalog.dto.request.AdminTaxonomyRequests.BrandCreate;
import com.example.demo.catalog.dto.request.AdminTaxonomyRequests.BrandRename;
import com.example.demo.catalog.dto.request.AdminTaxonomyRequests.Query;
import com.example.demo.catalog.dto.response.AdminCatalogResponses.Page;
import com.example.demo.catalog.entity.Brand;
import com.example.demo.catalog.exception.AdminCatalogException;
import com.example.demo.catalog.repository.BrandRepository;
import com.example.demo.catalog.repository.CatalogTaxonomyQueries;
import com.example.demo.catalog.repository.CatalogMutationRepository;
import com.example.demo.catalog.repository.CatalogMutationRepository.Resource;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AdminBrandServiceImpl implements AdminBrandService {
    private final BrandRepository brands;
    private final CatalogTaxonomyQueries queries;
    private final CatalogMutationRepository revisions;
    private final AdminCatalogConverter converter;
    private final TransactionTemplate transactions;
    public AdminBrandServiceImpl(BrandRepository brands,CatalogTaxonomyQueries queries,CatalogMutationRepository revisions,AdminCatalogConverter converter,PlatformTransactionManager manager){this.brands=brands;this.queries=queries;this.revisions=revisions;this.converter=converter;transactions=new TransactionTemplate(manager);transactions.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);}
    @Override public Page<com.example.demo.catalog.dto.response.AdminCatalogResponses.Brand> list(Query query){return queries.brands(query);}
    @Override public com.example.demo.catalog.dto.response.AdminCatalogResponses.Brand get(UUID id){CatalogAggregateMutation.requireCompatibleTransaction();return transactions.execute(status->{revisions.lockVersions(Resource.BRAND,List.of(id),false);return converter.brand(fresh(id));});}
    @Override public com.example.demo.catalog.dto.response.AdminCatalogResponses.Brand create(BrandCreate request){
        CatalogAggregateMutation.requireCompatibleTransaction();return transactions.execute(status->{
            if(request.getCode()==null || !request.getCode().matches("[a-z0-9]+(-[a-z0-9]+)*"))throw new AdminCatalogException(400,"CATALOG_INVALID_REQUEST","Invalid immutable Brand code");
            String name=name(request.getName());if(brands.findByCode(request.getCode()).isPresent() || queries.brandNameExists(name,null))throw conflict();
            Brand brand=brands.saveAndFlush(new Brand(request.getCode(),name));return converter.brand(brand);
        });
    }
    @Override public com.example.demo.catalog.dto.response.AdminCatalogResponses.Brand update(UUID id,BrandRename request){
        CatalogAggregateMutation.requireCompatibleTransaction();return transactions.execute(status->{
            compare(request.getVersion(),revisions.lockVersions(Resource.BRAND,List.of(id),true).get(id));Brand brand=fresh(id);String name=name(request.getName());
            if(queries.brandNameExists(name,id))throw conflict();if(name.equals(brand.getName()))return converter.brand(brand);
            revisions.claim(Resource.BRAND,id,request.getVersion());revisions.refresh(brand);brand.setName(name);brands.flush();return converter.brand(brand);
        });
    }
    @Override public void delete(UUID id,long version){
        CatalogAggregateMutation.requireCompatibleTransaction();transactions.executeWithoutResult(status->{
            compare(version,revisions.lockVersions(Resource.BRAND,List.of(id),true).get(id));Brand brand=fresh(id);
            if(queries.brandReferenced(id))throw new AdminCatalogException(409,"CATALOG_DEPENDENCY_CONFLICT","Brand still has Product references");
            revisions.claim(Resource.BRAND,id,version);revisions.refresh(brand);brands.delete(brand);brands.flush();
        });
    }
    private Brand fresh(UUID id){Brand brand=brands.findById(id).orElseThrow(()->new AdminCatalogException(404,"CATALOG_RESOURCE_NOT_FOUND","Brand not found"));revisions.refresh(brand);return brand;}
    private String name(String raw){if(raw==null || raw.isBlank())throw new AdminCatalogException(400,"CATALOG_INVALID_REQUEST","Brand name is required");String value=queries.normalizeName(raw);if(value==null || value.isEmpty())throw new AdminCatalogException(422,"CATALOG_VALIDATION_FAILED","Brand name is empty after normalization");return value;}
    private static void compare(long expected,long current){if(expected<0)throw new AdminCatalogException(400,"CATALOG_INVALID_REQUEST","Invalid revision");if(expected!=current)throw new AdminCatalogException(409,"CATALOG_STALE_VERSION","Brand changed; reload before retrying",Map.of("expectedVersion",Long.toString(expected),"currentVersion",Long.toString(current)));}
    private static AdminCatalogException conflict(){return new AdminCatalogException(409,"CATALOG_NAME_CONFLICT","Brand name or code is already in use");}
}
