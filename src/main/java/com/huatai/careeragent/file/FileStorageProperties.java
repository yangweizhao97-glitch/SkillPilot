package com.huatai.careeragent.file;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties(prefix = "career-agent.storage")
public record FileStorageProperties(
        String uploadDir,
        DataSize maxUploadSize
) {
    public FileStorageProperties {
        if (maxUploadSize == null) {
            maxUploadSize = DataSize.ofMegabytes(20);
        }
    }
}
