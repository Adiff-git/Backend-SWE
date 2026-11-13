package com.example.demo.storage.service;

import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.*;
class LocalImageCheckpointTest {
    @TempDir Path directory;
    @Test void saveSupportsAllFinalNamespacesAndAtomicReplacement() throws Exception {
        MockEnvironment environment=new MockEnvironment();environment.setActiveProfiles("local");
        LocalImageCheckpoint store=new LocalImageCheckpoint(environment,directory.toString());
        for(String key:new String[]{"images/users/1/id","images/reviews/2/id","images/products/id/hash","images/banners/home.png"}) {
            store.save(key,"image/png",new byte[]{1});store.save(key,"image/png",new byte[]{2});
        }
        try(var files=Files.list(directory)) { assertThat(files.toList()).hasSize(4).allSatisfy(p->assertThat(p.toString()).endsWith(".json")); }
        try(var files=Files.list(directory)) { for(Path path:files.toList())assertThat(Files.readString(path)).contains("Ag=="); }
    }
    @Test void invalidCheckpointAndWriteFailureAreErrors() throws Exception {
        MockEnvironment environment=new MockEnvironment();environment.setActiveProfiles("local");
        LocalImageCheckpoint store=new LocalImageCheckpoint(environment,directory.toString());
        for(String key:new String[]{"staging/users/1/id","images/../secret","images/a\\b"})
            assertThatThrownBy(()->store.save(key,"image/png",new byte[]{1})).isInstanceOf(com.example.demo.storage.exception.StorageUnavailableException.class);
        Path file=Files.writeString(directory.resolve("file"),"blocked");
        assertThatThrownBy(()->new LocalImageCheckpoint(environment,file.toString()).save("images/users/1/id","image/png",new byte[]{1})).isInstanceOf(com.example.demo.storage.exception.StorageUnavailableException.class);
    }
    @Test void productionNeverWritesLocalCheckpoint() {
        LocalImageCheckpoint store=new LocalImageCheckpoint(new MockEnvironment(),directory.resolve("unused").toString());
        store.save("images/users/1/id","image/png",new byte[]{1});assertThat(store.enabled()).isFalse();assertThat(directory.resolve("unused")).doesNotExist();
    }
}
