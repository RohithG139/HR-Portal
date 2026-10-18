package com.cubeworks.hr.employee;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * Stores and retrieves document files on the local filesystem (gate 2 implementation).
 *
 * <h2>Gate 2 note</h2>
 * <p>A production implementation would use object storage (S3, GCS, Azure Blob). For gate 2,
 * files are written to a configurable local directory (default: {@code ./uploads}).
 * The interface contract (store / retrieve by key) is the same regardless of the backend.</p>
 *
 * <h2>Storage key</h2>
 * <p>Each uploaded file receives a unique UUID string as its storage key. The key is stored
 * in {@code document.storage_key} and used to retrieve the file later.</p>
 */
@Service
public class DocumentStorageService {

    private static final Logger log = LoggerFactory.getLogger(DocumentStorageService.class);

    private final String uploadDir;

    /**
     * Constructor injection with configurable upload directory.
     *
     * @param uploadDir the directory to store uploads in (configurable via {@code storage.dir})
     */
    public DocumentStorageService(
            @Value("${storage.dir:./uploads}") final String uploadDir) {
        this.uploadDir = uploadDir;
    }

    /**
     * Stores a multipart file and returns a unique storage key.
     *
     * @param file the uploaded file
     * @return a UUID string that can later be passed to {@link #retrieve}
     * @throws IOException if writing to the filesystem fails
     */
    public String store(final MultipartFile file) throws IOException {
        final Path dir = Paths.get(uploadDir);
        Files.createDirectories(dir);
        final String key = UUID.randomUUID().toString();
        final Path dest = dir.resolve(key);
        file.transferTo(dest.toFile());
        log.info("Stored document with key={} size={} bytes", key, file.getSize());
        return key;
    }

    /**
     * Retrieves a stored document by its storage key.
     *
     * @param storageKey the UUID key assigned at upload time
     * @return the file as a {@link Resource}
     * @throws IOException if the file cannot be read
     */
    public Resource retrieve(final String storageKey) throws IOException {
        final Path file = Paths.get(uploadDir).resolve(storageKey);
        if (!Files.exists(file)) {
            throw new IOException("Document not found: " + storageKey);
        }
        return new ByteArrayResource(Files.readAllBytes(file));
    }
}
