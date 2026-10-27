package com.example.demo.media.validator;

import com.example.demo.media.exception.InvalidImageRequestException;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class ImageUploadValidator {
    private static final Set<String> TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    public void validateType(String type) {
        if (type == null || !TYPES.contains(type)) throw new InvalidImageRequestException();
    }
    public void validateId(Long id) {
        if (id == null || id <= 0) throw new InvalidImageRequestException();
    }
}
