package com.huatai.careeragent.file.parser;

import java.util.Map;

public record DocumentParseResult(String contentText, Map<String, Object> metadata) {
}
