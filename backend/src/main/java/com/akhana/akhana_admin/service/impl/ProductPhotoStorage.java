package com.akhana.akhana_admin.service.impl;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Optional;

// CAT-12: names come from the server only. A file is written apart and published with a hard link, which fails
// if the name exists: a saved photo is never replaced, so the copy off the VPS can treat every file as final.
@Component
public class ProductPhotoStorage {

    private static final String TEMP_DIR = ".tmp";

    private final Path directory;

    public ProductPhotoStorage(@Value("${app.product-photos.dir}") String directory) {
        this.directory = Path.of(directory).toAbsolutePath().normalize();
    }

    public void store(String fileName, byte[] content) throws IOException {
        Path temp = directory.resolve(TEMP_DIR);
        Files.createDirectories(temp);
        Path partial = Files.createTempFile(temp, null, null);
        try {
            Files.write(partial, content);
            Files.createLink(directory.resolve(fileName), partial);
        } finally {
            Files.deleteIfExists(partial);
        }
    }

    public Optional<byte[]> read(String fileName) throws IOException {
        try {
            return Optional.of(Files.readAllBytes(directory.resolve(fileName)));
        } catch (NoSuchFileException ex) {
            return Optional.empty();
        }
    }

    // Only for files of a save that failed, which no row points to; a registered photo is never deleted (RG-01)
    public void deleteUnregistered(String fileName) throws IOException {
        Files.deleteIfExists(directory.resolve(fileName));
    }
}
