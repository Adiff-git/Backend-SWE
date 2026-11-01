package com.example.demo.catalog.validator;

import com.example.demo.catalog.exception.InvalidProductImageException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;
import java.util.Locale;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.springframework.stereotype.Component;

/** Validates staged bytes before any object can be considered for publication. */
@Component
public final class ProductImageContentValidator {

	public static final int MAX_BYTES = 10_485_760;
	// A resource bound for decoding, not a catalog display-size rule.
	private static final long MAX_DECODED_PIXELS = 40_000_000L;

	public ValidatedImage validate(byte[] bytes, String claimedContentType, long claimedByteSize,
			String altText) {
		if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES
				|| claimedByteSize != bytes.length || altText == null || altText.isBlank()
				|| !isAllowedType(claimedContentType)) {
			throw new InvalidProductImageException();
		}
		try (MemoryCacheImageInputStream input = new MemoryCacheImageInputStream(
				new ByteArrayInputStream(bytes))) {
			Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
			if (!readers.hasNext()) {
				throw new InvalidProductImageException();
			}
			ImageReader reader = readers.next();
			try {
				reader.setInput(input, false, false);
                if (reader.getNumImages(true) != 1) throw new InvalidProductImageException();
                var warning = new java.util.concurrent.atomic.AtomicBoolean();
                reader.addIIOReadWarningListener((source, message) -> warning.set(true));
				String actualType = mimeType(reader.getFormatName());
				int width = reader.getWidth(0);
				int height = reader.getHeight(0);
				if (!claimedContentType.equals(actualType) || width <= 0 || height <= 0
						|| (long) width * height > MAX_DECODED_PIXELS || reader.read(0) == null || warning.get()) {
					throw new InvalidProductImageException();
				}
				return new ValidatedImage(actualType, bytes.length, width, height, sha256(bytes));
			} finally {
				reader.dispose();
			}
		} catch (IOException | RuntimeException failure) {
			if (failure instanceof InvalidProductImageException invalid) {
				throw invalid;
			}
			throw new InvalidProductImageException();
		}
	}

	private boolean isAllowedType(String type) {
		return "image/jpeg".equals(type) || "image/png".equals(type) || "image/webp".equals(type);
	}

	private String mimeType(String format) {
		return switch (format.toLowerCase(Locale.ROOT)) {
			case "jpeg", "jpg" -> "image/jpeg";
			case "png" -> "image/png";
			case "webp" -> "image/webp";
			default -> "";
		};
	}

	private byte[] sha256(byte[] bytes) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(bytes);
		} catch (NoSuchAlgorithmException impossible) {
			throw new IllegalStateException("SHA-256 is unavailable", impossible);
		}
	}

	public record ValidatedImage(String contentType, long byteSize, int width, int height, byte[] sha256) {
		public ValidatedImage {
			sha256 = sha256.clone();
		}

		@Override
		public byte[] sha256() {
			return sha256.clone();
		}
	}
}
