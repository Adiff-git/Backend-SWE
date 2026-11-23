package com.example.demo.catalog.service;

import com.example.demo.catalog.dto.request.AdminProductRequests.*;
import com.example.demo.catalog.dto.response.AdminCatalogResponses.ProductDetail;
import com.example.demo.catalog.dto.response.AdminCatalogResponses.ProductPage;
import com.example.demo.catalog.entity.*;
import com.example.demo.catalog.exception.AdminCatalogException;
import com.example.demo.catalog.repository.*;
import com.example.demo.catalog.repository.CatalogMutationRepository.Resource;
import com.example.demo.catalog.storage.ProductImageStorage;
import com.example.demo.catalog.storage.ProductImageStorage.PreparedReadSigner;
import java.time.Clock;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class AdminProductServiceImpl implements AdminProductService {
    private final ProductRepository products;
    private final CategoryRepository categories;
    private final BrandRepository brands;
    private final CatalogMutationRepository revisions;
    private final CatalogAggregateMutation mutations;
    private final CatalogAdminAggregateReader aggregates;
    private final ProductImageStorage storage;
    private final Clock clock;

    @Override public ProductPage list(Query query) { return products.findAdminPage(query); }
    @Override public ProductDetail get(UUID id) {
        try(PreparedReadSigner signer=prepare(id)) {
            return mutations.read(id,(taxonomy,product)->aggregates.detail(product,taxonomy,signer));
        }
    }
    @Override public ProductDetail create(Create request) {
        return mutations.create(taxonomy->{
            if(request==null || request.getName()==null || request.getName().isBlank() || request.getCategoryId()==null) throw malformed();
            Product product=BeanUtils.instantiateClass(Product.class);
            product.setName(request.getName()); product.setStatus(ProductStatus.DRAFT);
            product.setCategory(category(request.getCategoryId()));
            if(request.getBrandId().isSupplied()) product.setBrand(brand(request.getBrandId().getValue(),request.getExpectedBrandVersion()));
            else if(request.getExpectedBrandVersion()!=null) throw malformed();
            if(request.getDescription().isSupplied()) product.setDescription(request.getDescription().getValue());
            aggregates.validateNew(product);
            product=products.saveAndFlush(product);
            return aggregates.detail(product,taxonomy,null);
        });
    }
    @Override public ProductDetail update(UUID id,Patch request) {
        try(PreparedReadSigner signer=prepare(id)) {
            return mutations.product(id,request.getExpectedVersion(),request.getExpectedTaxonomyVersion(),new CatalogAggregateMutation.ProductCommand<>() {
                @Override public boolean noOp(Product current) {
                    editable(current);
                    boolean sameBrand=!request.getBrandId().isSupplied() || Objects.equals(request.getBrandId().getValue(),current.getBrand()==null?null:current.getBrand().getId());
                    if(request.getExpectedBrandVersion()!=null && (!request.getBrandId().isSupplied() || request.getBrandId().getValue()==null)) throw malformed();
                    if(!sameBrand && request.getBrandId().getValue()!=null && request.getExpectedBrandVersion()==null) throw malformed();
                    boolean same=(!request.getName().isSupplied() || Objects.equals(request.getName().getValue(),current.getName()))
                            && (!request.getDescription().isSupplied() || Objects.equals(request.getDescription().getValue(),current.getDescription()))
                            && (!request.getCategoryId().isSupplied() || Objects.equals(request.getCategoryId().getValue(),current.getCategory().getId())) && sameBrand;
                    if(same && request.getExpectedBrandVersion()!=null) brand(request.getBrandId().getValue(),request.getExpectedBrandVersion());
                    return same;
                }
                @Override public ProductDetail unchanged(Product current) { return aggregates.detail(current,request.getExpectedTaxonomyVersion(),signer); }
                @Override public ProductDetail apply(Product current) {
                    if(request.getName().isSupplied()) current.setName(request.getName().getValue());
                    if(request.getCategoryId().isSupplied()) current.setCategory(category(request.getCategoryId().getValue()));
                    if(request.getBrandId().isSupplied()) {
                        UUID selected=request.getBrandId().getValue();
                        boolean changed=!Objects.equals(selected,current.getBrand()==null?null:current.getBrand().getId());
                        if(selected==null) current.setBrand(null);
                        else if(changed || request.getExpectedBrandVersion()!=null) current.setBrand(brand(selected,request.getExpectedBrandVersion()));
                    }
                    if(request.getDescription().isSupplied()) current.setDescription(request.getDescription().getValue());
                    aggregates.validateCurrent(current);
                    return aggregates.detail(current,request.getExpectedTaxonomyVersion(),signer);
                }
            });
        }
    }
    @Override public ProductDetail transition(UUID id,long version,long taxonomyVersion,Action action) {
        try(PreparedReadSigner signer=prepare(id)) {
            return mutations.product(id,version,taxonomyVersion,new CatalogAggregateMutation.ProductCommand<>() {
                private ProductStatus target;
                @Override public boolean noOp(Product current) {
                    ProductStatus source=current.getStatus();
                    target=switch(action) { case PUBLISH -> ProductStatus.PUBLISHED; case UNPUBLISH -> ProductStatus.UNPUBLISHED; case ARCHIVE -> ProductStatus.ARCHIVED; case RESTORE -> ProductStatus.DRAFT; };
                    boolean allowed=action==Action.ARCHIVE || action==Action.PUBLISH && source!=ProductStatus.ARCHIVED
                            || action==Action.UNPUBLISH && Set.of(ProductStatus.PUBLISHED,ProductStatus.UNPUBLISHED).contains(source)
                            || action==Action.RESTORE && Set.of(ProductStatus.DRAFT,ProductStatus.ARCHIVED).contains(source);
                    if(!allowed) throw new AdminCatalogException(409,"CATALOG_INVALID_TRANSITION","Invalid catalog lifecycle transition");
                    if(source==target && action==Action.PUBLISH) aggregates.requirePublication(current);
                    return source==target;
                }
                @Override public ProductDetail unchanged(Product current) { return aggregates.detail(current,taxonomyVersion,signer); }
                @Override public ProductDetail apply(Product current) {
                    current.setStatus(target);
                    if(action==Action.RESTORE) current.setLastRestoredAt(clock.instant());
                    aggregates.validateCurrent(current);
                    return aggregates.detail(current,taxonomyVersion,signer);
                }
            });
        }
    }
    private PreparedReadSigner prepare(UUID id) {
        // An ambient transaction could already hold Catalog locks before credentials are resolved.
        if(TransactionSynchronizationManager.isActualTransactionActive()) throw new AdminCatalogException(503,"CATALOG_MAINTENANCE_REQUIRED","Catalog request requires an independent transaction boundary");
        if(!products.existsById(id)) throw new AdminCatalogException(404,"CATALOG_RESOURCE_NOT_FOUND","Product not found");
        if(!aggregates.hasImages(id)) return null;
        try { var signer=storage.prepareReadSigner(); if(signer==null) throw CatalogAdminAggregateReader.unavailable(); return signer; }
        catch(RuntimeException failure) { throw CatalogAdminAggregateReader.unavailable(); }
    }
    private Category category(UUID id) {
        if(id==null) throw malformed();
        Category category=categories.findById(id).orElseThrow(()->new AdminCatalogException(404,"CATALOG_RESOURCE_NOT_FOUND","Category not found"));
        revisions.refresh(category); return category;
    }
    private Brand brand(UUID id,Long expected) {
        if(id==null) { if(expected!=null) throw malformed(); return null; }
        if(expected==null || expected<0) throw malformed();
        long current=revisions.lockVersions(Resource.BRAND,List.of(id),false).get(id);
        if(current!=expected) throw new AdminCatalogException(409,"CATALOG_STALE_VERSION","Brand changed; reload before retrying",Map.of("expectedVersion",Long.toString(expected),"currentVersion",Long.toString(current)));
        Brand brand=brands.findById(id).orElseThrow(()->new AdminCatalogException(404,"CATALOG_RESOURCE_NOT_FOUND","Brand not found"));
        revisions.refresh(brand); return brand;
    }
    private static void editable(Product product) { if(product.getStatus()==ProductStatus.ARCHIVED) throw new AdminCatalogException(409,"CATALOG_INVALID_TRANSITION","Restore the product before editing"); }
    private static AdminCatalogException malformed() { return new AdminCatalogException(400,"CATALOG_INVALID_REQUEST","Invalid product request"); }
}
