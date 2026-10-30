package com.example.demo.media.testdata;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class MediaFixturesTest {
    @ParameterizedTest
    @ValueSource(strings = {"valid.jpg", "valid.png", "lossy.webp", "lossless.webp", "alpha.webp"})
    void fixture_shouldDecodeActualPixels_whenSupportedFormat(String name) throws Exception {
        try (ByteArrayInputStream input = new ByteArrayInputStream(MediaTestData.fixture(name))) {
            BufferedImage image = ImageIO.read(input);
            assertThat(image).isNotNull();
            assertThat(image.getWidth()).isPositive();
            assertThat(image.getHeight()).isPositive();
        }
    }
}
