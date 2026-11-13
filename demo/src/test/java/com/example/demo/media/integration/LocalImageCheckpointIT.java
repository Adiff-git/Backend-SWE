package com.example.demo.media.integration;

import com.example.demo.storage.service.LocalImageCheckpoint;
import com.example.demo.storage.exception.StorageUnavailableException;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.utility.MountableFile;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.*;

@SpringBootTest @Import(LocalImageCheckpointIT.Configuration.class)
class LocalImageCheckpointIT extends SeededMediaTestSupport {
    static final Path DIRECTORY=temporary();
    static Path temporary() { try{return Files.createTempDirectory("image-checkpoint-it-");}catch(Exception e){throw new IllegalStateException(e);} }
    @TestConfiguration static class Configuration {
        @Bean @Primary LocalImageCheckpoint testCheckpoints() {
            MockEnvironment environment=new MockEnvironment();environment.setActiveProfiles("local");
            return new LocalImageCheckpoint(environment,DIRECTORY.toString());
        }
    }
    @MockitoSpyBean(name="testCheckpoints") LocalImageCheckpoint checkpoints;
    @Test void commonCompletionPersistsAvatarAndRestoresContentAfterStorageLoss() throws Exception {
        Long actor=owner();var image=prepare(actor);stage(image);media.completeImageUpload(actor,image.getId());
        String key=images.findById(image.getId()).orElseThrow().getFinalKey();
        Path manifest=DIRECTORY.resolve(java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(key.getBytes(java.nio.charset.StandardCharsets.UTF_8)))+".json");
        assertThat(manifest).exists();
        STORAGE.execInContainer("mkdir","-p","/tmp/image-checkpoints");
        STORAGE.copyFileToContainer(MountableFile.forHostPath(manifest),"/tmp/image-checkpoints/"+manifest.getFileName());
        STORAGE.copyFileToContainer(MountableFile.forHostPath(Path.of("../docker/localstack/init/restore_product_images.py").toAbsolutePath().normalize()),"/tmp/restore_images.py");
        s3.deleteObject(r->r.bucket(bucket).key(key));
        var result=STORAGE.execInContainer("python3","-c","import sys;sys.path.insert(0,'/tmp');from restore_images import restore;import boto3;assert restore('/tmp/image-checkpoints',boto3.client('s3',endpoint_url='http://localhost:4566',region_name='us-east-1',aws_access_key_id='test',aws_secret_access_key='test'),'"+bucket+"')==1");
        assertThat(result.getExitCode()).as(result.getStderr()).isZero();
        assertThat(s3.getObjectAsBytes(r->r.bucket(bucket).key(key)).asByteArray()).isEqualTo(com.example.demo.media.testdata.MediaTestData.fixture("valid.png"));
        assertThat(media.getImageDownload(actor,image.getId()).getFinalUrl()).isNotBlank();
    }
    @Test void checkpointFailureMustNotCommitCompletionOrDeleteStaging() {
        Long actor=owner();var image=prepare(actor);String staging=stage(image);
        doThrow(new StorageUnavailableException()).when(checkpoints).save(anyString(),anyString(),any());
        assertThatThrownBy(()->media.completeImageUpload(actor,image.getId())).isInstanceOf(StorageUnavailableException.class);
        assertThat(images.findById(image.getId()).orElseThrow().getStatus()).isEqualTo(com.example.demo.media.enums.ImageUploadStatus.PENDING);
        assertThat(s3.headObject(r->r.bucket(bucket).key(staging))).isNotNull();
    }
}
