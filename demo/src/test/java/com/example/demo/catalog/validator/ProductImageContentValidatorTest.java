package com.example.demo.catalog.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.demo.catalog.exception.InvalidProductImageException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Base64;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class ProductImageContentValidatorTest {

	private final ProductImageContentValidator validator = new ProductImageContentValidator();

	@Test
	void acceptsDecodedJpegPngAndWebpWithVerifiedDimensions() throws IOException {
		for (String format : new String[] {"png", "jpeg"}) {
			byte[] bytes = image(format);
			ProductImageContentValidator.ValidatedImage result = validator.validate(bytes,
					"image/" + format, bytes.length, "Front view");
			assertThat(result.contentType()).isEqualTo("image/" + format);
			assertThat(result.width()).isEqualTo(2);
			assertThat(result.height()).isEqualTo(2);
			assertThat(result.byteSize()).isEqualTo(bytes.length);
			assertThat(result.sha256()).hasSize(32);
		}
		byte[] webp = Base64.getDecoder().decode(
				"UklGRjwAAABXRUJQVlA4IDAAAADQAQCdASoCAAIAAUAmJaACdLoB+AADsAD+8ut//NgVzXPv9//S4P0uD9Lg/9KQAAA=");
		ProductImageContentValidator.ValidatedImage result = validator.validate(webp,
				"image/webp", webp.length, "Front view");
		assertThat(result.contentType()).isEqualTo("image/webp");
		assertThat(result.width()).isEqualTo(2);
		assertThat(result.height()).isEqualTo(2);
	}

	@Test
	void rejectsClaimMismatchBlankAltUnsupportedAndMalformedBytes() throws IOException {
		byte[] png = image("png");
		assertThatThrownBy(() -> validator.validate(png, "image/jpeg", png.length, "Front"))
				.isInstanceOf(InvalidProductImageException.class);
		assertThatThrownBy(() -> validator.validate(png, "image/png", png.length, "  "))
				.isInstanceOf(InvalidProductImageException.class);
		assertThatThrownBy(() -> validator.validate(png, "image/gif", png.length, "Front"))
				.isInstanceOf(InvalidProductImageException.class);
		assertThatThrownBy(() -> validator.validate(new byte[] {1, 2, 3}, "image/png", 3, "Front"))
				.isInstanceOf(InvalidProductImageException.class);
		assertThatThrownBy(() -> validator.validate(Arrays.copyOf(png, png.length - 15),
				"image/png", png.length - 15, "Front"))
				.isInstanceOf(InvalidProductImageException.class);
	}

	@Test
	void rejectsWrongByteCountOversizeAndDecompressionBombDimensions() throws IOException {
		byte[] png = image("png");
		assertThatThrownBy(() -> validator.validate(png, "image/png", png.length + 1, "Front"))
				.isInstanceOf(InvalidProductImageException.class);
		byte[] oversize = new byte[10_485_761];
		assertThatThrownBy(() -> validator.validate(oversize, "image/png", oversize.length, "Front"))
				.isInstanceOf(InvalidProductImageException.class);
		// Keep the IHDR CRC valid so rejection is caused by the pixel budget,
		// rather than a malformed PNG header.
		byte[] bomb = png.clone();
		bomb[16] = 0; bomb[17] = 1; bomb[18] = (byte) 0x86; bomb[19] = (byte) 0xa0;
		bomb[20] = 0; bomb[21] = 1; bomb[22] = (byte) 0x86; bomb[23] = (byte) 0xa0;
		CRC32 crc = new CRC32();
		crc.update(bomb, 12, 17);
		long checksum = crc.getValue();
		for (int index = 0; index < 4; index++) {
			bomb[29 + index] = (byte) (checksum >>> (24 - 8 * index));
		}
		assertThatThrownBy(() -> validator.validate(bomb, "image/png", bomb.length, "Front"))
				.isInstanceOf(InvalidProductImageException.class);
	}

	private byte[] image(String format) throws IOException {
		BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		assertThat(ImageIO.write(image, format, output)).isTrue();
		return output.toByteArray();
	}
}
