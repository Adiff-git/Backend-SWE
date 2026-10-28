package com.example.demo.media.integration;

import com.example.demo.media.dto.request.CreateImageUploadRequest;
import com.example.demo.media.dto.response.ImageUploadResponse;
import com.example.demo.media.repository.ImageUploadRepository;
import com.example.demo.media.service.MediaService;
import com.example.demo.media.testdata.MediaTestData;
import com.example.demo.user.repository.UserRepository;
import com.example.demo.user.testdata.UserTestData;
import org.springframework.beans.factory.annotation.Autowired;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;

public abstract class SeededMediaTestSupport extends MediaInfrastructureTestSupport {
    @Autowired protected MediaService media;
    @Autowired protected UserRepository users;
    @Autowired protected ImageUploadRepository images;
    @Autowired protected S3Client s3;

    protected Long owner() { return users.saveAndFlush(UserTestData.user("Owner")).getId(); }
    protected ImageUploadResponse prepare(Long actor) {
        CreateImageUploadRequest request = new CreateImageUploadRequest();
        request.setContentType("image/png");
        return media.createImageUpload(actor, request);
    }
    protected String stage(ImageUploadResponse image) {
        String key = images.findById(image.getId()).orElseThrow().getStagingKey();
        s3.putObject(request -> request.bucket(bucket).key(key).contentType("image/png"),
                RequestBody.fromBytes(MediaTestData.fixture("valid.png")));
        return key;
    }
}
