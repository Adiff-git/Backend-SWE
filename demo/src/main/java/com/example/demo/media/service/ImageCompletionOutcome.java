package com.example.demo.media.service;

import com.example.demo.media.entity.ImageUpload;
import lombok.Value;

@Value
public class ImageCompletionOutcome {
    boolean expired;
    Long id;
    String finalKey;

    public static ImageCompletionOutcome expired(ImageUpload image) {
        return new ImageCompletionOutcome(true, image.getId(), null);
    }
    public static ImageCompletionOutcome completed(ImageUpload image) {
        return new ImageCompletionOutcome(false, image.getId(), image.getFinalKey());
    }
}
