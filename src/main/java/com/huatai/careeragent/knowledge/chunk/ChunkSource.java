package com.huatai.careeragent.knowledge.chunk;

import com.huatai.careeragent.file.FileType;

public record ChunkSource(FileType sourceType, String sourceTitle, String contentText) {
}
