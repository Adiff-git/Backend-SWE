package com.example.demo.catalog.service;

import com.example.demo.catalog.converter.AdminCatalogConverter;
import com.example.demo.catalog.dto.request.AdminVariantRequests.*;
import com.example.demo.catalog.dto.response.AdminCatalogResponses.*;
import com.example.demo.catalog.entity.*;
import com.example.demo.catalog.exception.AdminCatalogException;
import com.example.demo.catalog.repository.*;
import java.math.BigDecimal;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminVariantServiceImpl implements AdminVariantService {
    private final CatalogAggregateMutation mutations;
    private final CatalogMutationRepository revisions;
    private final ProductVariantRepository variants;
    private final CatalogAdminAggregateReader aggregates;
    private final AdminCatalogConverter converter;

    @Override public VariantList list(UUID productId) {
        return mutations.read(productId,(taxonomy,product)->new VariantList(aggregates.variants(product).stream()
                .map(v->converter.variant(v,productId,product.getVersion())).toList(),Long.toString(product.getVersion()),Long.toString(taxonomy)));
    }
    @Override public Specifications specifications(UUID productId,UUID variantId) {
        return mutations.read(productId,(taxonomy,product)->new Specifications(Map.copyOf(owned(product,variantId).getTechnicalSpecifications()),
                Long.toString(product.getVersion()),Long.toString(taxonomy)));
    }
    @Override public VariantMutation replaceSpecifications(UUID productId,UUID variantId,Patch request) {
        if(!request.getTechnicalSpecifications().isSupplied() || request.getTechnicalSpecifications().getValue()==null
                || request.getSku().isSupplied() || request.getSellingPrice().isSupplied() || request.getOptions().isSupplied())
            throw new AdminCatalogException(400,"CATALOG_INVALID_REQUEST","Replace only the addressed specification document");
        return update(productId,variantId,request);
    }
    @Override public VariantMutation create(UUID productId,Create request) {
        return mutations.product(productId,request.getExpectedVersion(),request.getExpectedTaxonomyVersion(),new CatalogAggregateMutation.ProductCommand<>() {
            @Override public boolean noOp(Product product) { editable(product,false); return false; }
            @Override public VariantMutation unchanged(Product product) { throw new IllegalStateException("Creation is never a no-op"); }
            @Override public VariantMutation apply(Product product) {
                ProductVariant variant=BeanUtils.instantiateClass(ProductVariant.class);
                variant.setProduct(product); variant.setSku(request.getSku()); variant.setSellingPrice(request.getSellingPrice());
                variant.setEnabled(request.isEnabled()); variant.setOptionCombination(request.getOptions()); variant.setTechnicalSpecifications(request.getTechnicalSpecifications());
                List<ProductVariant> siblings=new ArrayList<>(aggregates.variants(product));
                integrity(variant,siblings); siblings.add(variant);
                aggregates.validate(product,siblings,aggregates.images(product),product.getStatus()==ProductStatus.PUBLISHED);
                variants.save(variant);
                return response(variant,product,request.getExpectedTaxonomyVersion());
            }
        });
    }
    @Override public VariantMutation update(UUID productId,UUID variantId,Patch request) {
        return mutate(productId,variantId,request.getExpectedVersion(),request.getExpectedTaxonomyVersion(),false,
                variant->(!request.getSku().isSupplied() || Objects.equals(request.getSku().getValue(),variant.getSku()))
                        && (!request.getSellingPrice().isSupplied() || equalPrice(request.getSellingPrice().getValue(),variant.getSellingPrice()))
                        && (!request.getOptions().isSupplied() || Objects.equals(request.getOptions().getValue(),variant.getOptionCombination()))
                        && (!request.getTechnicalSpecifications().isSupplied() || sameDocument(request.getTechnicalSpecifications().getValue(),variant.getTechnicalSpecifications())),
                variant->{
                    if(request.getSku().isSupplied()) variant.setSku(request.getSku().getValue());
                    if(request.getSellingPrice().isSupplied()) variant.setSellingPrice(request.getSellingPrice().getValue());
                    if(request.getOptions().isSupplied()) variant.setOptionCombination(request.getOptions().getValue());
                    if(request.getTechnicalSpecifications().isSupplied()) variant.setTechnicalSpecifications(request.getTechnicalSpecifications().getValue());
                });
    }
    @Override public VariantMutation setEnabled(UUID productId,UUID variantId,long version,long taxonomyVersion,boolean enabled) {
        return mutate(productId,variantId,version,taxonomyVersion,enabled,variant->variant.isEnabled()==enabled,variant->variant.setEnabled(enabled));
    }
    private VariantMutation mutate(UUID productId,UUID variantId,long version,long taxonomyVersion,boolean archivedEnable,
            java.util.function.Predicate<ProductVariant> same,java.util.function.Consumer<ProductVariant> apply) {
        return mutations.product(productId,version,taxonomyVersion,new CatalogAggregateMutation.ProductCommand<>() {
            @Override public boolean noOp(Product product) {
                editable(product,archivedEnable);
                ProductVariant variant=owned(product,variantId);
                if(!same.test(variant)) return false;
                aggregates.validateCurrent(product); // Explicit Enable still validates its current context.
                return true;
            }
            @Override public VariantMutation unchanged(Product product) { return response(owned(product,variantId),product,taxonomyVersion); }
            @Override public VariantMutation apply(Product product) {
                revisions.lockVariants(productId,List.of(variantId));
                List<ProductVariant> siblings=aggregates.variants(product);
                ProductVariant variant=owned(siblings,variantId);
                apply.accept(variant); integrity(variant,siblings);
                aggregates.validate(product,siblings,aggregates.images(product),product.getStatus()==ProductStatus.PUBLISHED);
                return response(variant,product,taxonomyVersion);
            }
        });
    }
    private void integrity(ProductVariant changed,List<ProductVariant> siblings) {
        if(changed.getSku()==null || changed.getSku().isBlank() || changed.getSellingPrice()==null) throw invalid();
        try { changed.getSellingPrice().toBigIntegerExact(); } catch(ArithmeticException fractional) { throw invalid(); }
        if(variants.hasSkuConflict(changed.getSku(),changed.getId())) throw new AdminCatalogException(409,"CATALOG_SKU_CONFLICT","SKU is already in use");
        if(siblings.stream().anyMatch(other->other!=changed && Objects.equals(changed.getOptionCombination(),other.getOptionCombination())))
            throw new AdminCatalogException(409,"CATALOG_OPTION_CONFLICT","Variant options are already in use");
    }
    private ProductVariant owned(Product product,UUID id){return owned(aggregates.variants(product),id);}
    private static ProductVariant owned(List<ProductVariant> rows,UUID id){return rows.stream().filter(v->Objects.equals(v.getId(),id)).findFirst().orElseThrow(()->new AdminCatalogException(404,"CATALOG_RESOURCE_NOT_FOUND","Variant not found under Product"));}
    private VariantMutation response(ProductVariant variant,Product product,long taxonomy){return new VariantMutation(converter.variant(variant,product.getId(),product.getVersion()),Long.toString(product.getVersion()),Long.toString(taxonomy));}
    /** JSON numeric representation (Integer/BigInteger/BigDecimal) does not create a business edit. */
    private static boolean sameDocument(Object first,Object second) {
        if(first instanceof Number a && second instanceof Number b) return new BigDecimal(a.toString()).compareTo(new BigDecimal(b.toString()))==0;
        if(first instanceof Map<?,?> a && second instanceof Map<?,?> b) return a.keySet().equals(b.keySet()) && a.entrySet().stream().allMatch(e->sameDocument(e.getValue(),b.get(e.getKey())));
        return Objects.equals(first,second);
    }
    private static boolean equalPrice(BigDecimal first,BigDecimal second){return first!=null && second!=null && first.compareTo(second)==0;}
    private static void editable(Product product,boolean enable){if(product.getStatus()==ProductStatus.ARCHIVED && !enable) throw new AdminCatalogException(409,"CATALOG_INVALID_TRANSITION","Restore the product before editing");}
    private static AdminCatalogException invalid(){return new AdminCatalogException(422,"CATALOG_VALIDATION_FAILED","Variant requires valid SKU and whole-VND price");}
}
