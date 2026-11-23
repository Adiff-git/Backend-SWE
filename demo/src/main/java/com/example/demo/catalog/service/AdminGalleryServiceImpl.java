package com.example.demo.catalog.service;

import com.example.demo.catalog.converter.AdminCatalogConverter;
import com.example.demo.catalog.dto.request.AdminGalleryRequests.Attach;
import com.example.demo.catalog.dto.response.AdminCatalogResponses.Attachment;
import com.example.demo.catalog.dto.response.AdminCatalogResponses.Gallery;
import com.example.demo.catalog.entity.Product;
import com.example.demo.catalog.entity.ProductImage;
import com.example.demo.catalog.entity.ProductStatus;
import com.example.demo.catalog.exception.AdminCatalogException;
import com.example.demo.catalog.repository.ProductRepository;
import com.example.demo.catalog.repository.ProductImageRepository;
import com.example.demo.catalog.repository.CatalogMutationRepository;
import com.example.demo.catalog.storage.ProductImageStorage;
import com.example.demo.catalog.storage.ProductImageStorage.PreparedReadSigner;
import com.example.demo.media.service.MediaService;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class AdminGalleryServiceImpl implements AdminGalleryService {
    private final ProductRepository products;
    private final ProductImageRepository images;
    private final CatalogMutationRepository revisions;
    private final CatalogAggregateMutation mutations;
    private final CatalogAdminAggregateReader aggregates;
    private final CatalogImageAttachmentPreparer preparation;
    private final MediaService media;
    private final ProductImageStorage storage;
    private final Clock clock;
    private final AdminCatalogConverter converter;
    @Override public Gallery get(UUID product) {
        entry(product);try(PreparedReadSigner signer=signer(product,false)){return mutations.read(product,(taxonomy,current)->aggregates.gallery(current,taxonomy,signer));}
    }
    @Override public Attachment attach(UUID product,Long actor,Attach request) {
        entry(product);
        var proof=media.prepareProductImage(actor,request.getImageId());
        ProductImage peek=images.findByProduct_IdAndSourceImageId(product,request.getImageId()).orElse(null);
        var prepared=preparation.prepare(product,proof,request.getAltText(),peek);
        try(PreparedReadSigner signer=signer(product,true)) {
            return mutations.product(product,request.getExpectedVersion(),request.getExpectedTaxonomyVersion(),new CatalogAggregateMutation.ProductCommand<>() {
                @Override public boolean noOp(Product current) {
                    editable(current);List<ProductImage> rows=attachmentRows(product,request.getImageId());
                    ProductImage old=source(rows,request.getImageId());
                    if(old==null || !old.isActive()) return false;
                    if(!request.getAltText().equals(old.getAltText()) || request.getPrimary()!=null && request.getPrimary()!=old.isPrimary()
                            || request.getDisplayOrder()!=null && request.getDisplayOrder()!=old.getDisplayOrder()) throw conflict();
                    lock(product,rows);media.validatePreparedProductImage(actor,proof);validate(current,rows);return true;
                }
                @Override public Attachment unchanged(Product current) {
                    return new Attachment(view(source(attachmentRows(product,request.getImageId()),request.getImageId()),product,signer),true,Long.toString(current.getVersion()),Long.toString(request.getExpectedTaxonomyVersion()));
                }
                @Override public Attachment apply(Product current) {
                    List<ProductImage> rows=attachmentRows(product,request.getImageId());lock(product,rows);media.validatePreparedProductImage(actor,proof);
                    if(!prepared.contentVerified()) throw CatalogAdminAggregateReader.unavailable();
                    List<ProductImage> active=active(rows);
                    String hash=prepared.key().substring(prepared.key().lastIndexOf('/')+1);
                    if(active.size()>=10 || active.stream().anyMatch(i->i.getObjectKey().endsWith("/"+hash))) throw conflict();
                    ProductImage target=source(rows,request.getImageId());
                    if(target!=null && !target.getObjectKey().equals(prepared.key())) throw conflict();
                    boolean primary=request.getPrimary()==null?active.isEmpty():request.getPrimary();
                    if(active.isEmpty() && !primary) throw conflict();
                    int position=request.getDisplayOrder()==null?appendPosition(active):request.getDisplayOrder();
                    if(active.stream().anyMatch(i->i.getDisplayOrder()==position)) throw conflict();
                    if(primary) clearPrimary(active);
                    if(target==null) {target=BeanUtils.instantiateClass(ProductImage.class);target.setProduct(current);target.setSourceImageId(request.getImageId());target.setCreatedAt(clock.instant());rows.add(target);}
                    target.setObjectKey(prepared.key());target.setContentType(prepared.type());target.setByteSize(prepared.bytes());target.setWidth(prepared.width());target.setHeight(prepared.height());
                    target.setAltText(request.getAltText());target.setDisplayOrder(position);target.setActive(true);target.setPrimary(primary);
                    validate(current,rows);images.save(target);
                    return new Attachment(view(target,product,signer),false,Long.toString(current.getVersion()),Long.toString(request.getExpectedTaxonomyVersion()));
                }
            });
        }
    }
    @Override public Gallery primary(UUID product,UUID image,long version,long taxonomy) {
        return galleryMutation(product,version,taxonomy,rows->{ProductImage target=owned(rows,image);return target.isPrimary();},rows->{clearPrimary(active(rows));owned(rows,image).setPrimary(true);return null;});
    }
    @Override public Gallery reorder(UUID product,long version,long taxonomy,List<UUID> order) {
        return galleryMutation(product,version,taxonomy,rows->{permutation(rows,order);List<ProductImage> active=active(rows);for(int i=0;i<order.size();i++) if(!active.get(i).getId().equals(order.get(i)) || active.get(i).getDisplayOrder()!=i)return false;return true;},rows->{
            List<ProductImage> active=active(rows);Set<Integer> occupied=new HashSet<>();active.forEach(i->occupied.add(i.getDisplayOrder()));for(int i=0;i<active.size();i++)occupied.add(i);
            List<Integer> temporary=new ArrayList<>();for(int candidate=0;temporary.size()<active.size();candidate++) if(!occupied.contains(candidate))temporary.add(candidate);
            for(int i=0;i<active.size();i++)active.get(i).setDisplayOrder(temporary.get(i));revisions.flush();
            for(int i=0;i<order.size();i++)owned(rows,order.get(i)).setDisplayOrder(i);return null;
        });
    }
    @Override public void remove(UUID product,UUID image,long version,long taxonomy,UUID replacement) {
        entry(product);mutations.product(product,version,taxonomy,new CatalogAggregateMutation.ProductCommand<Void>() {
            @Override public boolean noOp(Product current){editable(current);return false;}
            @Override public Void unchanged(Product current){return null;}
            @Override public Void apply(Product current){
                List<ProductImage> rows=currentImages(product);lock(product,rows);ProductImage target=owned(rows,image);
                if(replacement!=null && replacement.equals(image)) throw invalid();
                if(target.isPrimary() && replacement==null && (active(rows).size()>1 || current.getStatus()==ProductStatus.PUBLISHED)) throw invalid();
                if(replacement!=null){ProductImage next=owned(rows,replacement);clearPrimary(active(rows));next.setPrimary(true);}
                target.setPrimary(false);target.setActive(false);validate(current,rows);return null;
            }
        });
    }
    private Gallery galleryMutation(UUID product,long version,long taxonomy,Predicate<List<ProductImage>> noOp,Function<List<ProductImage>,Void> change) {
        entry(product);try(PreparedReadSigner signer=signer(product,false)) {
            return mutations.product(product,version,taxonomy,new CatalogAggregateMutation.ProductCommand<>() {
                @Override public boolean noOp(Product current){editable(current);return noOp.test(currentImages(product));}
                @Override public Gallery unchanged(Product current){return aggregates.gallery(current,taxonomy,signer);}
                @Override public Gallery apply(Product current){List<ProductImage> rows=currentImages(product);lock(product,rows);change.apply(rows);validate(current,rows);return aggregates.gallery(current,taxonomy,signer);}
            });
        }
    }
    /** Current commands load at most the bounded active gallery, never retained history wholesale. */
    private List<ProductImage> currentImages(UUID product){List<ProductImage> rows=new ArrayList<>(images.findByProduct_IdAndActiveTrueOrderByDisplayOrderAscIdAsc(product));rows.forEach(revisions::refresh);return rows;}
    private List<ProductImage> attachmentRows(UUID product,UUID source){
        List<ProductImage> rows=currentImages(product);
        images.findByProduct_IdAndSourceImageId(product,source).ifPresent(retained->{
            revisions.refresh(retained);
            if(rows.stream().noneMatch(image->image.getId().equals(retained.getId())))rows.add(retained);
        });
        return rows;
    }
    private static List<ProductImage> active(List<ProductImage> rows){return rows.stream().filter(ProductImage::isActive).sorted(Comparator.comparingInt(ProductImage::getDisplayOrder).thenComparing(i->i.getId().toString())).toList();}
    private static ProductImage source(List<ProductImage> rows,UUID source){return rows.stream().filter(i->source.equals(i.getSourceImageId())).findFirst().orElse(null);}
    private static ProductImage owned(List<ProductImage> rows,UUID id){return rows.stream().filter(i->i.isActive() && id.equals(i.getId())).findFirst().orElseThrow(()->new AdminCatalogException(404,"CATALOG_RESOURCE_NOT_FOUND","Image not found under Product"));}
    private void lock(UUID product,List<ProductImage> rows){revisions.lockImages(product,rows.stream().map(ProductImage::getId).sorted(CatalogMutationRepository.UUID_ORDER).toList());}
    private void validate(Product product,List<ProductImage> rows){aggregates.validate(product,aggregates.variants(product),active(rows),product.getStatus()==ProductStatus.PUBLISHED);}
    private void clearPrimary(List<ProductImage> rows){rows.stream().filter(ProductImage::isPrimary).forEach(i->i.setPrimary(false));revisions.flush();}
    private static void permutation(List<ProductImage> rows,List<UUID> order){if(order==null || order.size()!=new HashSet<>(order).size() || !new HashSet<>(order).equals(active(rows).stream().map(ProductImage::getId).collect(java.util.stream.Collectors.toSet())))throw invalid();}
    private static int appendPosition(List<ProductImage> rows){int last=rows.stream().mapToInt(ProductImage::getDisplayOrder).max().orElse(-1);if(last==Integer.MAX_VALUE)throw invalid();return last+1;}
    private void entry(UUID product){if(TransactionSynchronizationManager.isActualTransactionActive())throw new AdminCatalogException(503,"CATALOG_MAINTENANCE_REQUIRED","Gallery preparation requires an independent boundary");if(!products.existsById(product))throw new AdminCatalogException(404,"CATALOG_RESOURCE_NOT_FOUND","Product not found");}
    private PreparedReadSigner signer(UUID product,boolean attaching){if(!attaching && !aggregates.hasImages(product))return null;try{var signer=storage.prepareReadSigner();if(signer==null)throw CatalogAdminAggregateReader.unavailable();return signer;}catch(RuntimeException failure){throw CatalogAdminAggregateReader.unavailable();}}
    private com.example.demo.catalog.dto.response.AdminCatalogResponses.Image view(ProductImage image,UUID product,PreparedReadSigner signer){try{String url=signer.signReadUrl(image.getObjectKey());if(url==null || url.isBlank())throw CatalogAdminAggregateReader.unavailable();return converter.image(image,product,url);}catch(RuntimeException failure){throw CatalogAdminAggregateReader.unavailable();}}
    private static void editable(Product current){if(current.getStatus()==ProductStatus.ARCHIVED)throw new AdminCatalogException(409,"CATALOG_INVALID_TRANSITION","Restore the product before editing");}
    private static AdminCatalogException conflict(){return new AdminCatalogException(409,"CATALOG_GALLERY_CONFLICT","Gallery membership or metadata conflicts");}
    private static AdminCatalogException invalid(){return new AdminCatalogException(422,"CATALOG_VALIDATION_FAILED","Invalid gallery order or primary replacement");}
}
