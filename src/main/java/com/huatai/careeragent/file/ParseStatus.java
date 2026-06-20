package com.huatai.careeragent.file;

public enum ParseStatus {
    PENDING,
    PARSING,
    PARSED,
    SUCCESS,
    CHUNKING,
    EMBEDDING,
    READY,
    FAILED
}
