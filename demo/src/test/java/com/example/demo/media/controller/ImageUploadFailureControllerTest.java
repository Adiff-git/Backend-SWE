package com.example.demo.media.controller;

import com.example.demo.media.exception.ImageUploadNotFoundException;
import com.example.demo.media.exception.InvalidImageContentException;
import com.example.demo.media.exception.UploadExpiredException;
import com.example.demo.storage.exception.StorageUnavailableException;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ImageUploadFailureControllerTest extends MediaControllerTestSupport {
    static Stream<Arguments> failures() {
        return Stream.of(Arguments.of(new ImageUploadNotFoundException(), 404),
                Arguments.of(new UploadExpiredException(), 409), Arguments.of(new InvalidImageContentException(), 422),
                Arguments.of(new StorageUnavailableException(), 503), Arguments.of(new IllegalStateException("private internals"), 500));
    }
    @ParameterizedTest @MethodSource("failures")
    void complete_shouldUseSafeProblemDetail(RuntimeException failure, int expected) throws Exception {
        when(media.completeImageUpload(8L, 1L)).thenThrow(failure);
        mvc.perform(post("/api/image-uploads/1/complete").with(jwt()))
                .andExpect(status().is(expected)).andExpect(jsonPath("status").value(expected))
                .andExpect(jsonPath("timestamp").exists()).andExpect(jsonPath("fieldErrors").exists())
                .andExpect(jsonPath("path").value("/api/image-uploads/1/complete"));
    }
}
