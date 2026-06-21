package com.huatai.careeragent.file;

import com.huatai.careeragent.common.error.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.apache.tika.Tika;

@Service
public class LocalFileStorageService {
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "application/pdf",
            "text/plain",
            "text/markdown",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    );

    private final FileStorageProperties properties;
    private final Tika tika = new Tika();

    public LocalFileStorageService(FileStorageProperties properties) {
        this.properties = properties;
    }

    public StoredFile store(Long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("FILE_EMPTY", "Uploaded file is empty", HttpStatus.BAD_REQUEST);
        }

        long maxBytes = properties.maxUploadSize().toBytes();
        if (file.getSize() > maxBytes) {
            throw new BusinessException("FILE_TOO_LARGE", "Uploaded file exceeds size limit", HttpStatus.BAD_REQUEST);
        }

        String mimeType = detectMimeType(file, sanitizeFileName(file.getOriginalFilename()));
        if (!ALLOWED_MIME_TYPES.contains(mimeType)) {
            throw new BusinessException("UNSUPPORTED_FILE_TYPE", "Unsupported file type", HttpStatus.BAD_REQUEST);
        }

        String originalFileName = sanitizeFileName(file.getOriginalFilename());
        String extension = extensionOf(originalFileName);
        String storedFileName = UUID.randomUUID() + extension;
        Path userDir = Path.of(properties.uploadDir()).toAbsolutePath().normalize().resolve(String.valueOf(userId));
        Path targetPath = userDir.resolve(storedFileName).normalize();

        if (!targetPath.startsWith(userDir)) {
            throw new BusinessException("INVALID_FILE_NAME", "Invalid file name", HttpStatus.BAD_REQUEST);
        }

        try {
            Files.createDirectories(userDir);
            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new BusinessException("FILE_STORAGE_FAILED", "Failed to store uploaded file", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        return new StoredFile(originalFileName, mimeType, targetPath.toString(), file.getSize());
    }

    private String detectMimeType(MultipartFile file, String fileName) {
        try (InputStream input = file.getInputStream()) {
            return normalizeMimeType(tika.detect(input, fileName));
        } catch (IOException exception) {
            throw new BusinessException("FILE_TYPE_DETECTION_FAILED", "Failed to inspect uploaded file",
                    HttpStatus.BAD_REQUEST);
        }
    }

    public void deleteQuietly(String storagePath) {
        if (!StringUtils.hasText(storagePath)) return;
        try {
            Files.deleteIfExists(Path.of(storagePath));
        } catch (IOException ignored) {
            // A scheduled storage reconciliation can remove an unreachable file later.
        }
    }

    private String normalizeMimeType(String contentType) {
        if (!StringUtils.hasText(contentType)) {
            return "application/octet-stream";
        }
        String normalized = contentType.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "text/x-web-markdown", "text/x-markdown" -> "text/markdown";
            default -> normalized;
        };
    }

    private String sanitizeFileName(String originalFileName) {
        String fileName = StringUtils.hasText(originalFileName) ? originalFileName : "uploaded-file";
        fileName = Path.of(fileName).getFileName().toString();
        fileName = fileName.replaceAll("[^a-zA-Z0-9._ -]", "_").trim();
        if (!StringUtils.hasText(fileName)) {
            return "uploaded-file";
        }
        return fileName;
    }

    private String extensionOf(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot < 0 || lastDot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(lastDot);
    }
}
