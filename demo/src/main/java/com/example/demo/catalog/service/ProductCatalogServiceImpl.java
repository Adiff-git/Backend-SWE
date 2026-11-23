package com.example.demo.catalog.service;

import com.example.demo.catalog.converter.ProductCatalogConverter;
import com.example.demo.catalog.dto.request.ProductSearchRequest;
import com.example.demo.catalog.dto.response.ProductListingItem;
import com.example.demo.catalog.search.ProductCandidatePage;
import com.example.demo.catalog.search.ProductSearchCriteria;
import com.example.demo.catalog.search.ProductSearchNormalizer;
import jakarta.validation.Validator;
import org.springframework.transaction.annotation.Isolation;
import com.example.demo.catalog.dto.response.ProductPage;
import com.example.demo.catalog.dto.response.ProductDetail;
import com.example.demo.catalog.dto.response.ProductSummary;
import com.example.demo.catalog.entity.Product;
import com.example.demo.catalog.entity.ProductImage;
import com.example.demo.catalog.entity.ProductStatus;
import com.example.demo.catalog.entity.ProductVariant;
import com.example.demo.catalog.exception.CatalogConflictException;
import com.example.demo.catalog.exception.InvalidCatalogRequestException;
import com.example.demo.catalog.exception.ProductNotFoundException;
import com.example.demo.catalog.repository.ProductImageRepository;
import com.example.demo.catalog.repository.ProductRepository;
import com.example.demo.catalog.repository.ProductVariantRepository;
import com.example.demo.catalog.storage.ProductImageStorage;
import com.example.demo.catalog.storage.ProductImageStorage.PreparedReadSigner;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductCatalogServiceImpl implements ProductCatalogService {

	private final ProductRepository products;
	private final ProductImageRepository images;
	private final ProductVariantRepository variants;
	private final ProductImageStorage imageStorage;
	private final ProductCatalogConverter converter;
	private final Validator validator;
    private final com.example.demo.inventory.service.InventoryService inventory;

	@Override
	@Transactional(readOnly = true)
	public Product getPublishedProductEntity(UUID id) {
		return products.findByIdAndStatus(id, ProductStatus.PUBLISHED)
				.orElseThrow(ProductNotFoundException::new);
	}

	@Override
	@Transactional // Existing publication-safe image signing uses FOR SHARE.
	public Map<UUID, ProductSummary> getProductSummaries(Collection<UUID> requestedIds) {
		if (requestedIds == null || requestedIds.size() > MAX_SIZE
				|| requestedIds.stream().anyMatch(java.util.Objects::isNull)) {
			throw new InvalidCatalogRequestException("Invalid summary identifiers");
		}
		List<UUID> ids = requestedIds.stream().distinct().toList();
		if (ids.isEmpty()) {
			return Map.of();
		}
        boolean pictured=!images.findCartPrimaryImages(ids).isEmpty();
        try(PreparedReadSigner signer=pictured?imageStorage.prepareReadSigner():null){
            Set<UUID> published=lockPublishedImageProducts(ids);
            List<UUID> currentIds=ids.stream().filter(published::contains).toList();
            if(currentIds.isEmpty())return Map.of();
            var eligible=products.findPublicSummaries(currentIds).stream().collect(java.util.stream.Collectors.toMap(
                    com.example.demo.catalog.repository.CatalogPublicViews.Product::getId,java.util.function.Function.identity()));
            Map<UUID,String> urls=new HashMap<>();
            for(Object[] row:images.findCartPrimaryImages(currentIds)){
                if(signer==null)throw new com.example.demo.catalog.exception.CatalogStorageUnavailableException();
                urls.put((UUID)row[0],signer.signReadUrl((String)row[1]));
            }
            Map<UUID,ProductSummary> summaries=new HashMap<>();
            for(UUID id:ids){var product=eligible.get(id);if(product!=null)summaries.put(id,converter.toSummary(product,urls.get(id)));}
		return Map.copyOf(summaries);
        }
	}

	@Override
	@Transactional(isolation = Isolation.READ_COMMITTED)
	public ProductPage listProducts(int page, int size) {
		ProductSearchRequest request = new ProductSearchRequest();
		request.setPage(page);
		request.setSize(size);
		return listProducts(request);
	}

	@Override
	@Transactional(isolation = Isolation.READ_COMMITTED) // Publication signing needs FOR SHARE.
	public ProductPage listProducts(ProductSearchRequest request) {
		if (request == null || !validator.validate(request).isEmpty()
				|| request.getSpecFilters() != null && request.getSpecificationGroups() == null) {
			throw new InvalidCatalogRequestException("Invalid search request");
		}
		com.example.demo.catalog.validator.ProductSearchValidator.validate(request);
		ProductSearchCriteria criteria = ProductSearchCriteria.builder()
				.page(request.getPage()).size(request.getSize())
				.keyword(new ProductSearchNormalizer().normalizeQuery(request.getQ()))
				.categoryId(request.getCategoryId() == null ? null : UUID.fromString(request.getCategoryId()))
				.brandIds(request.getBrandIds() == null ? null : request.getBrandIds().stream().map(UUID::fromString).toList())
				.minPrice(request.getMinPrice()).maxPrice(request.getMaxPrice()).sort(request.getSort())
				.specificationGroups(request.getSpecificationGroups()).build();
		ProductCandidatePage candidatePage = products.findPublishedCandidates(criteria);
		List<UUID> ids = candidatePage.getItems().stream().map(ProductCandidatePage.Candidate::getProductId).toList();
		int totalPages = (int) Math.min(Integer.MAX_VALUE,
				candidatePage.getTotalElements() / request.getSize()
						+ (candidatePage.getTotalElements() % request.getSize() == 0 ? 0 : 1));
		if (ids.isEmpty()) {
			return new ProductPage(List.of(), request.getPage(), request.getSize(), candidatePage.getTotalElements(), totalPages);
		}

        // Only a scalar peek precedes credential preparation; no pre-lock entity/gallery is projected.
        boolean pictured=!images.findCartPrimaryImages(ids).isEmpty();
        try(PreparedReadSigner signer=pictured?imageStorage.prepareReadSigner():null){
            Set<UUID> published=lockPublishedImageProducts(ids);
            List<ProductCandidatePage.Candidate> selected=candidatePage.getItems().stream()
                    .filter(c -> published.contains(c.getProductId())).toList();
            var current=selected.isEmpty()?List.<com.example.demo.catalog.repository.ProductSearchRepository.CurrentCandidate>of()
                    :products.recheckPublishedCandidates(criteria,selected);
            var byId=current.stream().collect(java.util.stream.Collectors.toMap(
                    com.example.demo.catalog.repository.ProductSearchRepository.CurrentCandidate::productId,java.util.function.Function.identity()));
            List<UUID> representativeIds=current.stream().map(com.example.demo.catalog.repository.ProductSearchRepository.CurrentCandidate::variantId)
                    .distinct().sorted(java.util.Comparator.comparing(UUID::toString)).toList();
            var availability=representativeIds.isEmpty()?Map.<UUID,com.example.demo.inventory.dto.response.InventoryAvailability>of():inventory.getAvailability(representativeIds);
            List<ProductListingItem> summaries=new ArrayList<>();
            for(var candidate:candidatePage.getItems()){
                var row=byId.get(candidate.getProductId());
                // Never substitute a different representative or refill a page after revalidation.
                if(row==null || !row.variantId().equals(candidate.getRepresentativeVariantId()))continue;
                String url=null;
                if(row.imageKey()!=null){
                    if(signer==null)throw new com.example.demo.catalog.exception.CatalogStorageUnavailableException();
                    url=signer.signReadUrl(row.imageKey());
                }
                var summary=new ProductSummary(row.productId(),row.name(),new com.example.demo.catalog.dto.response.CategorySummary(row.categoryId(),row.categoryName()),url);
                summaries.add(converter.toListingItem(summary,row.amount(),row.variantId(),availability.get(row.variantId())));
            }
            return new ProductPage(List.copyOf(summaries),request.getPage(),request.getSize(),candidatePage.getTotalElements(),totalPages);
        }
	}

	@Override
	@Transactional // A gallery read may need FOR SHARE before local URL signing.
	public ProductDetail getProduct(UUID id) {
        boolean pictured=!images.findCartPrimaryImages(List.of(id)).isEmpty();
        try(PreparedReadSigner signer=pictured?imageStorage.prepareReadSigner():null){
            var product=products.findPublishedForShare(id).orElseThrow(ProductNotFoundException::new);
            var enabled=variants.findPublicVariants(id).stream().filter(com.example.demo.catalog.repository.CatalogPublicViews.Variant::getEnabled).toList();
            if(enabled.isEmpty() || enabled.stream().anyMatch(v -> !positiveWhole(v.getPrice())))throw new CatalogConflictException();
            var gallery=images.findPublicGallery(id);
            if(gallery.isEmpty() || gallery.size()>10 || gallery.stream().filter(com.example.demo.catalog.repository.CatalogPublicViews.Image::getPrimaryImage).count()!=1)throw new CatalogConflictException();
            Map<UUID,String> urls=new HashMap<>();
            for(var image:gallery){
                if(signer==null)throw new com.example.demo.catalog.exception.CatalogStorageUnavailableException();
                urls.put(image.getId(),signer.signReadUrl(image.getObjectKey()));
            }
            return converter.toPublicDetail(product,enabled,gallery,urls,inventory.getAvailability(enabled.stream().map(com.example.demo.catalog.repository.CatalogPublicViews.Variant::getId).toList()));
        }
    }

    private boolean positiveWhole(java.math.BigDecimal price){
        if(price==null || price.signum()<=0)return false;
        try{price.toBigIntegerExact();return true;}catch(ArithmeticException invalid){return false;}
    }

	private Set<UUID> lockPublishedImageProducts(Collection<UUID> imageProductIds) {
		Set<UUID> published = new HashSet<>();
		// The PostgreSQL query orders UUIDs before taking row locks.
		for (Object[] row : products.lockStatusesForShare(imageProductIds)) {
			if (ProductStatus.PUBLISHED.name().equals(row[1])) {
				published.add((UUID) row[0]);
			}
		}
		return published;
	}

}
