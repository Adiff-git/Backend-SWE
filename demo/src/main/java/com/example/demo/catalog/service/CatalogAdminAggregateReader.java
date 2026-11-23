package com.example.demo.catalog.service;

import com.example.demo.catalog.converter.AdminCatalogConverter;
import com.example.demo.catalog.dto.response.AdminCatalogResponses.ProductDetail;
import com.example.demo.catalog.dto.response.AdminCatalogResponses.ProductRow;
import com.example.demo.catalog.dto.response.AdminCatalogResponses.CategoryReference;
import com.example.demo.catalog.dto.response.AdminCatalogResponses.Brand;
import com.example.demo.catalog.dto.response.AdminCatalogResponses.Image;
import com.example.demo.catalog.dto.response.AdminCatalogResponses.Gallery;
import com.example.demo.catalog.dto.response.AdminCatalogResponses.Readiness;
import com.example.demo.catalog.entity.Product;
import com.example.demo.catalog.entity.ProductImage;
import com.example.demo.catalog.entity.ProductStatus;
import com.example.demo.catalog.entity.ProductVariant;
import com.example.demo.catalog.exception.AdminCatalogException;
import com.example.demo.catalog.repository.ProductVariantRepository;
import com.example.demo.catalog.repository.ProductImageRepository;
import com.example.demo.catalog.repository.CatalogSpecificationContextRepository;
import com.example.demo.catalog.repository.CatalogMutationRepository;
import com.example.demo.catalog.storage.ProductImageStorage;
import com.example.demo.catalog.storage.ProductImageStorage.PreparedReadSigner;
import com.example.demo.catalog.validator.AdminCatalogValidator;
import com.example.demo.catalog.validator.ProductPublicationValidator;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Internal prepared-aggregate loading. Caller owns taxonomy then Product locks. No external I/O. */
@Component
@RequiredArgsConstructor
public class CatalogAdminAggregateReader {
    private final ProductVariantRepository variants;
    private final ProductImageRepository images;
    private final CatalogSpecificationContextRepository contexts;
    private final CatalogMutationRepository revisions;
    private final AdminCatalogValidator basic;
    private final ProductPublicationValidator publication;
    private final AdminCatalogConverter converter;

    public List<ProductVariant> variants(Product product) {
        List<ProductVariant> rows=variants.findByProduct_IdOrderByIdAsc(product.getId());
        rows.forEach(revisions::refresh);
        return rows;
    }
    public List<ProductImage> images(Product product) {
        List<ProductImage> rows=images.findByProduct_IdAndActiveTrueOrderByDisplayOrderAscIdAsc(product.getId());
        rows.forEach(revisions::refresh);
        return rows;
    }
    public boolean hasImages(UUID id) { return images.existsByProduct_IdAndActiveTrue(id); }
    public void validateNew(Product product) { validate(product,List.of(),List.of(),false); }
    public void validateCurrent(Product product) { validate(product,variants(product),images(product),product.getStatus()==ProductStatus.PUBLISHED); }
    public void requirePublication(Product product) { validate(product,variants(product),images(product),true); }
    public void validate(Product product,List<ProductVariant> rows,List<ProductImage> gallery,boolean published) {
        Set<String> families=contexts.getApplicableFamilies(product.getCategory().getId());
        if(published) { publication.requireValid(product,rows,gallery,families); return; }
        try { basic.validateBasic(product,rows,gallery,families); }
        catch(IllegalArgumentException invalid) { throw new AdminCatalogException(422,"CATALOG_VALIDATION_FAILED","Invalid catalog data"); }
    }
    public ProductDetail detail(Product product,long taxonomy,PreparedReadSigner signer) {
        revisions.refresh(product.getCategory());
        if(product.getBrand()!=null) revisions.refresh(product.getBrand());
        List<ProductVariant> choices=variants(product);
        List<ProductImage> gallery=images(product);
        List<Image> views=new ArrayList<>();
        for(ProductImage image:gallery) {
            if(signer==null) throw unavailable(); // Gallery appeared after the credential-preparation peek. Fail closed, never do network under locks.
            try {
                String url=signer.signReadUrl(image.getObjectKey());
                if(url==null || url.isBlank()) throw unavailable();
                views.add(converter.image(image,product.getId(),url));
            } catch(RuntimeException failure) { throw unavailable(); }
        }
        var category=product.getCategory(); var brand=product.getBrand();
        ProductRow row=converter.productRow(product,new CategoryReference(category.getId(),category.getName(),Long.toString(category.getVersion())),
                brand==null?null:new Brand(brand.getId(),brand.getCode(),brand.getName(),Long.toString(brand.getVersion())),choices.size(),choices.stream().filter(ProductVariant::isEnabled).count());
        var failures=publication.violations(product,choices,gallery,contexts.getApplicableFamilies(category.getId()));
        return new ProductDetail(row,product.getDescription(),product.getLastRestoredAt(),
                choices.stream().map(v->converter.variant(v,product.getId(),product.getVersion())).toList(),List.copyOf(views),Long.toString(taxonomy),new Readiness(failures.isEmpty(),failures));
    }
    public Gallery gallery(Product product,long taxonomy,PreparedReadSigner signer) {
        List<Image> views=new ArrayList<>();
        for(ProductImage image:images(product)) {
            if(signer==null) throw unavailable();
            try {String url=signer.signReadUrl(image.getObjectKey());if(url==null || url.isBlank())throw unavailable();views.add(converter.image(image,product.getId(),url));}
            catch(RuntimeException failure){throw unavailable();}
        }
        return new Gallery(List.copyOf(views),Long.toString(product.getVersion()),Long.toString(taxonomy));
    }
    public static AdminCatalogException unavailable() { return new AdminCatalogException(503,"CATALOG_DEPENDENCY_UNAVAILABLE","Catalog image service is unavailable"); }
}
