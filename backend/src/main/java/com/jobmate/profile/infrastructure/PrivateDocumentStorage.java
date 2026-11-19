package com.jobmate.profile.infrastructure;

import com.jobmate.common.exception.CustomException;
import com.jobmate.profile.domain.exception.ProfileErrorCode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PrivateDocumentStorage {

    private final Path root;

    public PrivateDocumentStorage(@Value("${jobmate.documents.path:${user.home}/.jobmate/documents}") String directory) {
        root = Path.of(directory).toAbsolutePath().normalize();
    }

    public String save(byte[] bytes, String extension) {
        String key = UUID.randomUUID() + "." + extension;
        try {
            Files.createDirectories(root, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
            if (Files.isSymbolicLink(root) || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Invalid storage directory");
            }
            Files.setPosixFilePermissions(root, PosixFilePermissions.fromString("rwx------"));
            Files.createFile(resolve(key), PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
            Files.write(resolve(key), bytes, StandardOpenOption.WRITE);
            return key;
        } catch (IOException | UnsupportedOperationException exception) {
            throw new CustomException(ProfileErrorCode.DOCUMENT_STORAGE_ERROR);
        }
    }

    public byte[] read(String key) {
        try {
            Path path = resolve(key);
            if (Files.isSymbolicLink(path) || Files.size(path) > SafeDocumentExtractor.MAX_BYTES) {
                throw new IOException("Invalid stored document");
            }
            return Files.readAllBytes(path);
        } catch (IOException exception) {
            throw new CustomException(ProfileErrorCode.DOCUMENT_STORAGE_ERROR);
        }
    }

    public boolean exists(String key) {
        Path path = resolve(key);
        return Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(path);
    }

    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException exception) {
            throw new CustomException(ProfileErrorCode.DOCUMENT_STORAGE_ERROR);
        }
    }

    private Path resolve(String key) {
        if (key == null || !key.matches("[0-9a-f-]{36}\\.(pdf|docx)")) {
            throw new CustomException(ProfileErrorCode.DOCUMENT_STORAGE_ERROR);
        }
        return root.resolve(key);
    }
}
