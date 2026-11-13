package com.example.demo.catalog.storage;

import com.example.demo.storage.service.LocalImageCheckpoint;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Compatibility adapter for existing catalog verification and orphan cleanup. */
@Component @RequiredArgsConstructor
public class ProductImageCheckpoint {
    private final LocalImageCheckpoint checkpoints;
    public void save(String key,String type,byte[] bytes) { checkpoints.save(key,type,bytes); }
    public void delete(String key) { checkpoints.delete(key); }
}
