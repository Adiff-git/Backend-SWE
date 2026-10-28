package com.example.demo.media.validator;

import java.util.UUID;

import com.example.demo.media.exception.InvalidImageRequestException;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class ImageUploadValidator {
    private static final Set<String> TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    public void validateType(String type) {
        if (type == null || !TYPES.contains(type)) throw new InvalidImageRequestException();
    }
    public static UUID parseId(String value) {
        if (value == null || !value.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
            throw new InvalidImageRequestException();
        return UUID.fromString(value);
    }
    public void validateId(UUID id) {
        if (id == null) throw new InvalidImageRequestException();
    }
}
