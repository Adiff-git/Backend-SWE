package com.example.demo.catalog.service;

import com.example.demo.catalog.exception.*;
import com.example.demo.catalog.validator.ProductImageContentValidator;
import com.example.demo.storage.service.ObjectStorageService;
import com.example.demo.storage.config.S3StorageProperties;
import com.example.demo.common.exception.ApplicationException;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import lombok.RequiredArgsConstructor;

/** Short database transactions fence completion; S3 work never holds database locks. */
@Service @RequiredArgsConstructor
public class ProductImageUploadService {
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(ProductImageUploadService.class);
    private final JdbcTemplate db;
    private final TransactionTemplate transactions;
    private final ObjectStorageService storage;
    private final S3StorageProperties properties;
    private final software.amazon.awssdk.services.s3.S3Client s3;
    private final com.example.demo.catalog.storage.ProductImageCheckpoint checkpoint;
    private final ProductImageContentValidator validator = new ProductImageContentValidator();
    public record Prepared(UUID uploadId,String uploadUrl,String method,Map<String,String> requiredHeaders,Instant expiresAt) {}
    public record Completed(UUID imageId,UUID productId,String altText,int width,int height,int displayOrder,boolean isPrimary) {}
    public record Outcome(Completed image,boolean replay) {}
    private static final class Failure extends ApplicationException { Failure(int status,String message){super(status,message);} }
    private Map<String,Object> session(UUID product,UUID upload,String actor) {
        var rows=db.queryForList("SELECT * FROM product_image_uploads WHERE id=? AND product_id=?",upload,product);
        if(rows.isEmpty())throw new Failure(404,"Upload not found");
        var row=rows.getFirst();
        if(!actor.equals(row.get("preparer_subject")))throw new Failure(403,"Upload preparer mismatch");
        return row;
    }
    private Completed result(UUID image) {
        return db.queryForObject("SELECT * FROM product_images WHERE id=?",(rs,n)->new Completed(image,rs.getObject("product_id",UUID.class),rs.getString("alt_text"),rs.getInt("width"),rs.getInt("height"),rs.getInt("display_order"),rs.getBoolean("is_primary")),image);
    }
    @org.springframework.scheduling.annotation.Scheduled(fixedDelayString="${catalog.upload-cleanup-delay-ms:60000}")
    public void cleanup() {
        db.update("UPDATE product_image_uploads SET state='EXPIRED',claim_token=NULL,claim_expires_at=NULL WHERE state IN ('PREPARED','VERIFIED') AND expires_at<=clock_timestamp() AND (claim_expires_at IS NULL OR claim_expires_at<=clock_timestamp())");
        for(var row:db.queryForList("SELECT * FROM product_image_uploads WHERE state IN ('PUBLISHED','EXPIRED','FAILED')")) {
            try {
                storage.deleteStaging((String)row.get("staging_key"));
                String key=(String)row.get("durable_key");
                if(!"PUBLISHED".equals(row.get("state")) && key!=null && key.startsWith("images/products/"+row.get("product_id")+"/"+row.get("id")+"/") && db.queryForObject("SELECT count(*) FROM product_images WHERE object_key=?",Integer.class,key)==0)
                    { s3.deleteObject(r->r.bucket(properties.getBucket()).key(key)); checkpoint.delete(key); }
            } catch(RuntimeException failure) { LOG.warn("Product upload cleanup pending for session {}",row.get("id")); }
        }
    }
    public Prepared prepare(UUID product,String actor,String type,long size,String alt) {
        if(!Set.of("image/jpeg","image/png","image/webp").contains(type) || size<1 || size>10485760 || alt==null || alt.isBlank())throw new Failure(400,"Invalid image metadata");
        if(db.queryForObject("SELECT count(*) FROM products WHERE id=?",Integer.class,product)!=1)throw new ProductNotFoundException();
        UUID id=UUID.randomUUID();String key="staging/products/"+product+"/"+id;
        Instant now=Instant.now();Instant deadline=now.plusSeconds(900);
        var signed=storage.prepareUpload(key,type,deadline);
        // Deadline is bounded by both the database session and the actual presigned PUT.
        Instant expires=signed.getExpiresAt();
        Instant preparedAt=expires.minusSeconds(900);
        db.update("INSERT INTO product_image_uploads(id,product_id,preparer_subject,staging_key,proposed_content_type,proposed_byte_size,alt_text,state,prepared_at,expires_at) VALUES (?,?,?,?,?,?,?,'PREPARED',?,?)",id,product,actor,key,type,size,alt,java.sql.Timestamp.from(preparedAt),java.sql.Timestamp.from(expires));
        return new Prepared(id,signed.getUrl(),"PUT",Map.of("Content-Type",type),expires);
    }
    public Outcome complete(UUID product,UUID upload,String actor,Boolean primary,Integer order) {
        var observed=session(product,upload,actor);
        if("PUBLISHED".equals(observed.get("state")))return new Outcome(result((UUID)observed.get("product_image_id")),true);
        if(!Set.of("PREPARED","VERIFIED").contains(observed.get("state")))throw new CatalogConflictException();
        String staging=(String)observed.get("staging_key");
        var metadata=storage.inspect(staging).orElseThrow(CatalogConflictException::new);
        if(metadata.getLength()>10485760 || !observed.get("proposed_content_type").equals(metadata.getContentType()))throw new InvalidProductImageException();
        ProductImageContentValidator.ValidatedImage valid;
        try(var content=storage.readBoundedIfMatch(staging,metadata.getEtag(),10485760)) {
            if(content.getMetadata().getLength()>10485760)throw new InvalidProductImageException();
            valid=validator.validate(java.nio.file.Files.readAllBytes(content.getPath()),(String)observed.get("proposed_content_type"),((Number)observed.get("proposed_byte_size")).longValue(),(String)observed.get("alt_text"));
        }catch(java.io.IOException failure){throw new CatalogStorageUnavailableException();}
        String sha=HexFormat.of().formatHex(valid.sha256());
        String durable="images/products/"+product+"/"+upload+"/"+sha;
        UUID token=UUID.randomUUID();
        // A retry can only reclaim the SAME validated bytes. A mutated staged object cannot replace them.
        int claimed=db.update("UPDATE product_image_uploads SET state='VERIFIED',claim_token=?,claim_expires_at=clock_timestamp()+interval '120 seconds',durable_key=?,verified_at=clock_timestamp() WHERE id=? AND product_id=? AND preparer_subject=? AND clock_timestamp()<expires_at AND (state='PREPARED' OR (state='VERIFIED' AND claim_expires_at<=clock_timestamp() AND durable_key=?))",token,durable,upload,product,actor,durable);
        if(claimed!=1) {
            var current=session(product,upload,actor);
            if("PUBLISHED".equals(current.get("state")))return new Outcome(result((UUID)current.get("product_image_id")),true);
            throw new CatalogConflictException();
        }
        try {
            storage.copyIfMatch(staging,metadata.getEtag(),durable,valid.contentType());
            var stored=storage.inspect(durable).orElseThrow(CatalogStorageUnavailableException::new);
            if(stored.getLength()!=valid.byteSize() || !valid.contentType().equals(stored.getContentType()))throw new CatalogStorageUnavailableException();
            try(var copy=storage.readBoundedIfMatch(durable,stored.getEtag(),10485760)) {
                byte[] verifiedBytes=java.nio.file.Files.readAllBytes(copy.getPath());
                var checked=validator.validate(verifiedBytes,valid.contentType(),valid.byteSize(),(String)observed.get("alt_text"));
                if(!java.security.MessageDigest.isEqual(valid.sha256(),checked.sha256()))throw new CatalogStorageUnavailableException();
                checkpoint.save(durable,valid.contentType(),verifiedBytes);
            }
        } catch(java.io.IOException | RuntimeException failure) {
            // Retain the session/key for safe recovery; release only our own claim for immediate retry.
            db.update("UPDATE product_image_uploads SET claim_expires_at=clock_timestamp() WHERE id=? AND state='VERIFIED' AND claim_token=?",upload,token);
            if(failure instanceof ApplicationException known)throw known;
            throw new CatalogStorageUnavailableException();
        }
        Outcome outcome;
        try { outcome=transactions.execute(status -> {
            if(db.queryForList("SELECT id FROM products WHERE id=? FOR UPDATE",product).isEmpty())throw new ProductNotFoundException();
            var current=session(product,upload,actor);
            if("PUBLISHED".equals(current.get("state")))return new Outcome(result((UUID)current.get("product_image_id")),true);
            var gallery=db.queryForList("SELECT * FROM product_images WHERE product_id=? ORDER BY display_order",product);
            int count=gallery.size();
            if(count>=10 || gallery.stream().anyMatch(i->((String)i.get("object_key")).endsWith("/"+sha)))throw new CatalogConflictException();
            boolean isPrimary=primary==null?count==0:primary;
            if(count==0&&!isPrimary)throw new CatalogConflictException();
            if(count>0&&isPrimary)db.update("UPDATE product_images SET is_primary=false WHERE product_id=? AND is_primary",product);
            int position=order==null?(count==0?0:((Number)gallery.getLast().get("display_order")).intValue()+1):order;
            if(position<0 || gallery.stream().anyMatch(i->((Number)i.get("display_order")).intValue()==position))throw new CatalogConflictException();
            UUID image=UUID.randomUUID();
            db.update("INSERT INTO product_images(id,product_id,object_key,alt_text,content_type,byte_size,width,height,display_order,is_primary,created_at) VALUES (?,?,?,?,?,?,?,?,?,?,clock_timestamp())",image,product,durable,observed.get("alt_text"),valid.contentType(),valid.byteSize(),valid.width(),valid.height(),position,isPrimary);
            int published=db.update("WITH sampled AS MATERIALIZED (SELECT clock_timestamp() AS checked_at) UPDATE product_image_uploads SET state='PUBLISHED',product_image_id=?,published_at=sampled.checked_at,claim_token=NULL,claim_expires_at=NULL FROM sampled WHERE id=? AND state='VERIFIED' AND claim_token=? AND durable_key=? AND sampled.checked_at<expires_at AND sampled.checked_at<claim_expires_at",image,upload,token,durable);
            if(published!=1)throw new CatalogConflictException();
            return new Outcome(result(image),false);
        });
        } catch(RuntimeException failure) {
            db.update("UPDATE product_image_uploads SET claim_expires_at=clock_timestamp() WHERE id=? AND state='VERIFIED' AND claim_token=?",upload,token);
            if(failure instanceof org.springframework.dao.DataIntegrityViolationException)throw new CatalogConflictException();
            throw failure;
        }
        try{storage.deleteStaging(staging);}catch(RuntimeException failure){ LOG.warn("Product upload staging cleanup pending for session {}",upload); }
        return outcome;
    }
}
