package com.huatai.careeragent.file.parser;

import com.huatai.careeragent.common.error.BusinessException;
import com.huatai.careeragent.file.UploadedFile;
import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class DocumentParserService {
    private static final int MAX_PARSE_CHARS = 1_000_000;

    private final Tika tika = new Tika();

    public DocumentParseResult parse(UploadedFile uploadedFile) {
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, uploadedFile.getFileName());

        String contentText;
        try (InputStream inputStream = Files.newInputStream(Path.of(uploadedFile.getStoragePath()))) {
            contentText = tika.parseToString(inputStream, metadata, MAX_PARSE_CHARS);
        } catch (IOException e) {
            throw new BusinessException("FILE_READ_FAILED", "Failed to read uploaded file", HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (Exception e) {
            throw new BusinessException("DOCUMENT_PARSE_FAILED", "Failed to parse document", HttpStatus.UNPROCESSABLE_ENTITY);
        }

        String normalizedText = normalize(contentText);
        if (!StringUtils.hasText(normalizedText)) {
            throw new BusinessException("DOCUMENT_CONTENT_EMPTY", "Document content is empty after parsing", HttpStatus.UNPROCESSABLE_ENTITY);
        }

        return new DocumentParseResult(normalizedText, toMap(metadata));
    }

    private String normalize(String contentText) {
        if (contentText == null) {
            return "";
        }
        return contentText.replace("\r\n", "\n").replace('\r', '\n').trim();
    }

    private Map<String, Object> toMap(Metadata metadata) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (String name : metadata.names()) {
            values.put(name, metadata.get(name));
        }
        return values;
    }
}
