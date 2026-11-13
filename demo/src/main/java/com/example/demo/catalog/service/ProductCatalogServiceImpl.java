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
		List<ProductImage> primaryImages = images.findPrimaryImagesForProducts(ids);
		Map<UUID, Product> eligible = indexProducts(
				products.findAllByIdsAndStatusWithCategory(ids, ProductStatus.PUBLISHED));
		Map<UUID, ProductImage> primaryByProduct = new HashMap<>();
		for (ProductImage image : primaryImages) {
			UUID id = image.getProduct().getId();
			if (eligible.containsKey(id)) {
				primaryByProduct.put(id, image);
			}
		}
		Map<UUID, String> urls = new HashMap<>();
		if (!primaryByProduct.isEmpty()) {
			try (PreparedReadSigner signer = imageStorage.prepareReadSigner()) {
				Set<UUID> published = lockPublishedImageProducts(primaryByProduct.keySet());
				for (UUID id : primaryByProduct.keySet()) {
					if (published.contains(id)) {
						urls.put(id, signer.signReadUrl(primaryByProduct.get(id).getObjectKey()));
					} else {
						eligible.remove(id);
					}
				}
			}
		}
		// Products without image locks still need a final publication revalidation.
		List<UUID> withoutImages = ids.stream()
				.filter(id -> !primaryByProduct.containsKey(id)).toList();
		if (!withoutImages.isEmpty()) {
			Map<UUID, Product> revalidated = indexProducts(
					products.findAllByIdsAndStatusWithCategory(withoutImages, ProductStatus.PUBLISHED));
			for (UUID id : withoutImages) {
				eligible.remove(id);
			}
			eligible.putAll(revalidated);
		}
		Map<UUID, ProductSummary> summaries = new HashMap<>();
		for (UUID id : ids) {
			Product product = eligible.get(id);
			if (product != null) {
				summaries.put(id, converter.toSummary(product, urls.get(id)));
			}
		}
		return Map.copyOf(summaries);
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

		// Batch image metadata first, then recheck publication immediately before projection.
		List<ProductImage> primaryImages = images.findPrimaryImagesForProducts(ids);
		Map<UUID, Product> eligible = indexProducts(
				products.findAllByIdsAndStatusWithCategory(ids, ProductStatus.PUBLISHED));
		Map<UUID, ProductImage> primaryByProduct = new HashMap<>();
		for (ProductImage image : primaryImages) {
			UUID productId = image.getProduct().getId();
			if (eligible.containsKey(productId)) {
				primaryByProduct.put(productId, image);
			}
		}

		Map<UUID, String> signedUrls = new HashMap<>();
		if (!primaryByProduct.isEmpty()) {
			// Credential resolution happens before taking locks. Signing itself is local.
			try (PreparedReadSigner signer = imageStorage.prepareReadSigner()) {
				Set<UUID> lockedPublished = lockPublishedImageProducts(primaryByProduct.keySet());
				for (UUID id : ids) {
					if (lockedPublished.contains(id)) {
						signedUrls.put(id, signer.signReadUrl(primaryByProduct.get(id).getObjectKey()));
					} else if (primaryByProduct.containsKey(id)) {
						eligible.remove(id);
					}
				}
			}
		}

        // Signer credential resolution may block;revalidate imageless rows after it as one batch.
        // Pictured rows are protected by the ordered publication locks above.
        List<UUID> withoutImages=ids.stream().filter(id->!primaryByProduct.containsKey(id)).toList();
        if(!withoutImages.isEmpty()){
            Map<UUID,Product> revalidated=indexProducts(products.findAllByIdsAndStatusWithCategory(withoutImages,ProductStatus.PUBLISHED));
            withoutImages.forEach(eligible::remove);eligible.putAll(revalidated);
        }

		List<ProductListingItem> summaries = new ArrayList<>();
		for (ProductCandidatePage.Candidate candidate : candidatePage.getItems()) {
			UUID id = candidate.getProductId();
			Product product = eligible.get(id);
			if (product != null) {
				summaries.add(converter.toListingItem(converter.toSummary(product, signedUrls.get(id)),
						candidate.getDisplayAmount(), candidate.getRepresentativeVariantId()));
			}
		}
		return new ProductPage(List.copyOf(summaries), request.getPage(), request.getSize(),
				candidatePage.getTotalElements(), totalPages);
	}

	@Override
	@Transactional // A gallery read may need FOR SHARE before local URL signing.
	public ProductDetail getProduct(UUID id) {
		Product product = products.findByIdAndStatusWithCategory(id, ProductStatus.PUBLISHED)
				.orElseThrow(ProductNotFoundException::new);
		List<ProductVariant> ownedVariants = variants.findByProduct_IdOrderByIdAsc(id);
		if (ownedVariants.isEmpty()) {
			throw new CatalogConflictException();
		}
		List<ProductImage> gallery = images.findByProduct_IdOrderByDisplayOrderAsc(id);
		Map<UUID, String> urls = new HashMap<>();
		if (gallery.isEmpty()) {
			// Recheck after the relationship reads, immediately before DTO projection.
			product = products.findByIdAndStatusWithCategory(id, ProductStatus.PUBLISHED)
					.orElseThrow(ProductNotFoundException::new);
		} else {
			// Resolve credentials before locking; signing performs no S3 network operation.
			try (PreparedReadSigner signer = imageStorage.prepareReadSigner()) {
				String status = products.lockStatusForShare(id).orElseThrow(ProductNotFoundException::new);
				if (!ProductStatus.PUBLISHED.name().equals(status)) {
					throw new ProductNotFoundException();
				}
				for (ProductImage image : gallery) {
					urls.put(image.getId(), signer.signReadUrl(image.getObjectKey()));
				}
			}
		}
		return converter.toDetail(product, ownedVariants, gallery, urls);
	}

	private Map<UUID, Product> indexProducts(List<Product> fetched) {
		Map<UUID, Product> byId = new HashMap<>();
		for (Product product : fetched) {
			byId.put(product.getId(), product);
		}
		return byId;
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
