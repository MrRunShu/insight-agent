package com.insightagent.paper;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Local-disk {@link PaperStorage}. Files live under {@code app.papers.dir}; the storage
 * key is the path relative to that root. Path traversal outside the root is rejected.
 *
 * <p>To move to object storage later, add a {@code CosPaperStorage} implementing the same
 * interface and swap the bean — no caller changes.
 */
@Component
@Slf4j
public class LocalDiskPaperStorage implements PaperStorage {

    @Value("${app.papers.dir:${user.dir}/papers}")
    private String root;

    private Path resolve(String key) {
        Path base = Paths.get(root).toAbsolutePath().normalize();
        Path p = base.resolve(key).normalize();
        if (!p.startsWith(base)) {
            throw new IllegalArgumentException("Illegal storage key (path traversal): " + key);
        }
        return p;
    }

    @Override
    public String save(String key, byte[] data) throws IOException {
        Path p = resolve(key);
        Files.createDirectories(p.getParent());
        Files.write(p, data);
        return key;
    }

    @Override
    public byte[] load(String key) throws IOException {
        return Files.readAllBytes(resolve(key));
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            log.warn("[LocalDiskPaperStorage] delete failed for {}: {}", key, e.getMessage());
        }
    }

    @Override
    public boolean exists(String key) {
        return Files.exists(resolve(key));
    }
}
