package com.huatai.careeragent.file;

import java.time.Instant;

public final class FileDtos {
    private FileDtos() {
    }

    public record UploadedFileResponse(
            Long fileId,
            String fileName,
            FileType fileType,
            String mimeType,
            long sizeBytes,
            ParseStatus parseStatus,
            Instant createdAt
    ) {
        public static UploadedFileResponse from(UploadedFile uploadedFile) {
            return new UploadedFileResponse(
                    uploadedFile.getId(),
                    uploadedFile.getFileName(),
                    uploadedFile.getFileType(),
                    uploadedFile.getMimeType(),
                    uploadedFile.getSizeBytes(),
                    uploadedFile.getParseStatus(),
                    uploadedFile.getCreatedAt()
            );
        }
    }

    public record ParseFileResponse(
            Long fileId,
            Long documentId,
            ParseStatus parseStatus
    ) {
        public static ParseFileResponse success(UploadedFile uploadedFile, Long documentId) {
            return new ParseFileResponse(uploadedFile.getId(), documentId, uploadedFile.getParseStatus());
        }
    }
}
