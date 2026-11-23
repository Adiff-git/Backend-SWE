package com.example.demo.catalog.service;

import com.example.demo.catalog.dto.request.AdminGalleryRequests.Attach;
import com.example.demo.catalog.exception.AdminCatalogException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Legacy attachment facade. All supported calls share the versioned Admin gallery command. */
@Service
@RequiredArgsConstructor
public class ProductImageService {
    private final AdminGalleryService gallery;
    public record Attached(UUID imageId,UUID productId,String altText,int width,int height,int displayOrder,boolean isPrimary) { }
    public record Outcome(Attached image,boolean replay,String productVersion,String taxonomyVersion) {
        public Outcome(Attached image,boolean replay){this(image,replay,"0","0");}
    }
    public Outcome attach(UUID product,UUID source,Long actor,String alt,Boolean primary,Integer order,long version,long taxonomy) {
        var result=gallery.attach(product,actor,new Attach(version,taxonomy,source,alt,primary,order));var image=result.getImage();
        return new Outcome(new Attached(image.getId(),product,image.getAltText(),image.getWidth(),image.getHeight(),image.getDisplayOrder(),image.isPrimary()),result.isReplay(),result.getProductVersion(),result.getTaxonomyVersion());
    }
    /** Kept source-compatible for old internal clients, but never provides an unversioned write path. */
    @Deprecated public Outcome attach(UUID product,UUID source,Long actor,String alt,Boolean primary,Integer order) {
        throw new AdminCatalogException(400,"CATALOG_INVALID_REQUEST","Product and taxonomy revisions are required");
    }
}
