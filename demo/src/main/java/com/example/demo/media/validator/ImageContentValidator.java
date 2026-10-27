package com.example.demo.media.validator;

import com.example.demo.media.exception.InvalidImageContentException;
import com.example.demo.storage.dto.response.StoredObjectContent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.springframework.stereotype.Component;

@Component
public class ImageContentValidator {
    public void validate(StoredObjectContent content, String declared) {
        if (content.getMetadata().getLength() <= 0 || !declared.equals(content.getMetadata().getContentType()))
            throw new InvalidImageContentException();
        try (ImageInputStream input = ImageIO.createImageInputStream(content.getPath().toFile())) {
            if (input == null) throw new InvalidImageContentException();
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new InvalidImageContentException();
            ImageReader reader = readers.next();
            try {
                String mime = switch (reader.getFormatName().toLowerCase(Locale.ROOT)) {
                    case "jpeg", "jpg" -> "image/jpeg";
                    case "png" -> "image/png";
                    case "webp" -> "image/webp";
                    default -> "unsupported";
                };
                if (!declared.equals(mime)) throw new InvalidImageContentException();
                AtomicBoolean warning = new AtomicBoolean();
                reader.addIIOReadWarningListener((source, message) -> warning.set(true));
                reader.setInput(input);
                int count = reader.getNumImages(true);
                if (count <= 0) throw new InvalidImageContentException();
                for (int index = 0; index < count; index++) {
                    BufferedImage image = reader.read(index);
                    if (image == null || warning.get()) throw new InvalidImageContentException();
                    image.flush();
                }
            } finally { reader.dispose(); }
        } catch (InvalidImageContentException failure) { throw failure; }
        catch (IOException | RuntimeException failure) { throw new InvalidImageContentException(); }
    }
}
