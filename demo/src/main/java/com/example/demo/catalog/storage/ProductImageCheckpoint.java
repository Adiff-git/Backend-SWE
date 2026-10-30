package com.example.demo.catalog.storage;

import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.ByteBuffer;
import java.util.*;
import java.security.MessageDigest;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.example.demo.catalog.exception.CatalogStorageUnavailableException;

/** Optional local-development object durability for LocalStack Community.
 * Persist verified bytes before publishing metadata. No production behavior change.
 */
@Component
public class ProductImageCheckpoint {
    private final Path directory;
    public ProductImageCheckpoint(Environment environment,@Value("${catalog.images.checkpoint-directory:}") String path) {
        directory=environment.acceptsProfiles(Profiles.of("local"))&&!path.isBlank()?Path.of(path):null;
    }
    private Path file(String key) throws Exception {
        return directory.resolve(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(key.getBytes(java.nio.charset.StandardCharsets.UTF_8)))+".json");
    }
    public void save(String key,String type,byte[] bytes) {
        if(directory==null)return;
        try {
            if(!key.startsWith("images/products/"))throw new IllegalArgumentException();
            Files.createDirectories(directory);
            String sha=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            if(!key.endsWith("/"+sha))throw new IllegalArgumentException();
            String json="{\"key\":\""+key+"\",\"content_type\":\""+type+"\",\"sha256\":\""+sha+"\",\"bytes_base64\":\""+Base64.getEncoder().encodeToString(bytes)+"\"}";
            Path temporary=Files.createTempFile(directory,"checkpoint-",".tmp",PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
            try(var channel=FileChannel.open(temporary,StandardOpenOption.WRITE)) {
                ByteBuffer buffer=ByteBuffer.wrap(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                while(buffer.hasRemaining())channel.write(buffer);channel.force(true);
            }
            Files.move(temporary,file(key),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
            try(var channel=FileChannel.open(directory,StandardOpenOption.READ)){channel.force(true);}
        }catch(Exception failure){throw new CatalogStorageUnavailableException();}
    }
    public void delete(String key) {
        if(directory==null)return;
        try{Files.deleteIfExists(file(key));}catch(Exception failure){throw new CatalogStorageUnavailableException();}
    }
}
