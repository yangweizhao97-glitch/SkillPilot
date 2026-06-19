package com.huatai.careeragent.file;

public record StoredFile(
        String originalFileName,
        String mimeType,
        String storagePath,
        long sizeBytes
) {
}
