package com.example.demo.catalog.service;

import com.example.demo.catalog.entity.ProductImage;
import com.example.demo.catalog.exception.CatalogStorageUnavailableException;
import com.example.demo.catalog.exception.InvalidProductImageException;
import com.example.demo.catalog.storage.ProductImageCheckpoint;
import com.example.demo.catalog.validator.ProductImageContentValidator;
import com.example.demo.media.dto.response.PreparedProductImage;
import com.example.demo.storage.service.ObjectStorageService;
import java.io.IOException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Existing bounded decode/copy/hash/checkpoint protocol; never runs inside a Catalog transaction. */
@Component
@RequiredArgsConstructor
public class CatalogImageAttachmentPreparer {
    private final ObjectStorageService storage;
    private final ProductImageCheckpoint checkpoint;
    private final ProductImageContentValidator validator;
    public record Prepared(PreparedProductImage proof,String key,String type,long bytes,int width,int height,boolean contentVerified) { }
    public Prepared prepare(UUID product,PreparedProductImage proof,String alt,ProductImage existing) {
        if(TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Attachment preparation cannot hold database locks");
        if(existing!=null && existing.isActive()) return new Prepared(proof,existing.getObjectKey(),existing.getContentType(),existing.getByteSize(),existing.getWidth(),existing.getHeight(),false);
        var metadata=storage.inspect(proof.getFinalKey()).orElseThrow(CatalogStorageUnavailableException::new);
        if(metadata.getLength()<1 || metadata.getLength()>ProductImageContentValidator.MAX_BYTES || !proof.getContentType().equals(metadata.getContentType())) throw new InvalidProductImageException();
        ProductImageContentValidator.ValidatedImage valid;
        try(var content=storage.readBoundedIfMatch(proof.getFinalKey(),metadata.getEtag(),ProductImageContentValidator.MAX_BYTES)) {
            valid=validator.validate(Files.readAllBytes(content.getPath()),proof.getContentType(),metadata.getLength(),alt);
        } catch(IOException failure) { throw new CatalogStorageUnavailableException(); }
        String durable="images/products/"+product+"/"+proof.getId()+"/"+HexFormat.of().formatHex(valid.sha256());
        storage.copyIfMatch(proof.getFinalKey(),metadata.getEtag(),durable,valid.contentType());
        var stored=storage.inspect(durable).orElseThrow(CatalogStorageUnavailableException::new);
        if(stored.getLength()!=valid.byteSize() || !stored.getContentType().equals(valid.contentType())) throw new CatalogStorageUnavailableException();
        try(var content=storage.readBoundedIfMatch(durable,stored.getEtag(),ProductImageContentValidator.MAX_BYTES)) {
            byte[] bytes=Files.readAllBytes(content.getPath());var copied=validator.validate(bytes,valid.contentType(),valid.byteSize(),alt);
            if(!MessageDigest.isEqual(valid.sha256(),copied.sha256())) throw new CatalogStorageUnavailableException();
            checkpoint.save(durable,valid.contentType(),bytes);
        } catch(IOException failure) { throw new CatalogStorageUnavailableException(); }
        return new Prepared(proof,durable,valid.contentType(),valid.byteSize(),valid.width(),valid.height(),true);
    }
}
