package com.huatai.careeragent.file;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "uploaded_files")
public class UploadedFile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Enumerated(EnumType.STRING)
    @Column(name = "file_type", nullable = false, length = 32)
    private FileType fileType;

    @Column(name = "mime_type", nullable = false, length = 120)
    private String mimeType;

    @Column(name = "storage_path", nullable = false, length = 500)
    private String storagePath;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(name = "parse_status", nullable = false, length = 32)
    private ParseStatus parseStatus = ParseStatus.PENDING;

    @Column(name = "parsed_text")
    private String parsedText;

    @Column(name = "error_message")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected UploadedFile() {
    }

    public UploadedFile(
            Long userId,
            String fileName,
            FileType fileType,
            String mimeType,
            String storagePath,
            long sizeBytes
    ) {
        this.userId = userId;
        this.fileName = fileName;
        this.fileType = fileType;
        this.mimeType = mimeType;
        this.storagePath = storagePath;
        this.sizeBytes = sizeBytes;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getFileName() {
        return fileName;
    }

    public FileType getFileType() {
        return fileType;
    }

    public String getMimeType() {
        return mimeType;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public ParseStatus getParseStatus() {
        return parseStatus;
    }

    public String getParsedText() {
        return parsedText;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void markParseSuccess(String parsedText) {
        this.parseStatus = ParseStatus.SUCCESS;
        this.parsedText = parsedText;
        this.errorMessage = null;
    }

    public void markStatus(ParseStatus status) {
        this.parseStatus = status;
        if (status != ParseStatus.FAILED) this.errorMessage = null;
    }

    public void markParseFailed(String errorMessage) {
        this.parseStatus = ParseStatus.FAILED;
        this.errorMessage = summarize(errorMessage);
    }

    private String summarize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= 500 ? trimmed : trimmed.substring(0, 500);
    }
}
