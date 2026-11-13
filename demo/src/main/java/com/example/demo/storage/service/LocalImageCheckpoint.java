package com.example.demo.storage.service;

import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;
import com.example.demo.storage.exception.StorageUnavailableException;

/** Synchronous local-only durability for all immutable, validated images. */
@Component
public class LocalImageCheckpoint {
    public static final int MAX_BYTES = 10_485_760;
    private final Path directory;
    public LocalImageCheckpoint(Environment environment,
            @Value("${storage.s3.checkpoint-directory:${catalog.images.checkpoint-directory:}}") String path) {
        directory=environment.acceptsProfiles(Profiles.of("local"))&&!path.isBlank()?Path.of(path):null;
    }
    public boolean enabled() { return directory!=null; }
    private Path file(String key) throws Exception {
        return directory.resolve(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8)))+".json");
    }
    public void save(String key,String type,byte[] bytes) {
        if(!enabled())return;
        Path temporary=null;
        try {
            if(key==null || key.getBytes(StandardCharsets.UTF_8).length>1024 || !key.matches("images/[A-Za-z0-9_-][A-Za-z0-9_.-]*(?:/[A-Za-z0-9_-][A-Za-z0-9_.-]*)*")
                    || !Set.of("image/png","image/jpeg","image/webp").contains(type)
                    || bytes==null || bytes.length==0 || bytes.length>MAX_BYTES)throw new IllegalArgumentException();
            Files.createDirectories(directory);
            String sha=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            String json="{\"key\":\""+key+"\",\"content_type\":\""+type+"\",\"sha256\":\""+sha+"\",\"bytes_base64\":\""+Base64.getEncoder().encodeToString(bytes)+"\"}";
            temporary=Files.getFileStore(directory).supportsFileAttributeView("posix")
                    ?Files.createTempFile(directory,"checkpoint-",".tmp",PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
                    :Files.createTempFile(directory,"checkpoint-",".tmp");
            try(var channel=FileChannel.open(temporary,StandardOpenOption.WRITE)) {
                ByteBuffer buffer=ByteBuffer.wrap(json.getBytes(StandardCharsets.UTF_8));
                while(buffer.hasRemaining())channel.write(buffer);channel.force(true);
            }
            Files.move(temporary,file(key),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);temporary=null;
            if(Files.getFileStore(directory).supportsFileAttributeView("posix"))
                try(var channel=FileChannel.open(directory,StandardOpenOption.READ)){channel.force(true);}
        }catch(Exception failure){throw new StorageUnavailableException();}
        finally{if(temporary!=null)try{Files.deleteIfExists(temporary);}catch(Exception ignored){}}
    }
    public void delete(String key) {
        if(!enabled())return;
        try{Files.deleteIfExists(file(key));}catch(Exception failure){throw new StorageUnavailableException();}
    }
}
