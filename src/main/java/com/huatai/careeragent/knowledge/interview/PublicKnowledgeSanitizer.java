package com.huatai.careeragent.knowledge.interview;

import com.huatai.careeragent.llm.security.PromptInjectionPolicy;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.regex.Pattern;

@Component
public class PublicKnowledgeSanitizer {
    private static final String REDACTED = "[已脱敏]";
    private static final Pattern EMAIL = Pattern.compile("(?i)[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}");
    private static final Pattern PHONE = Pattern.compile("(?<!\\d)(?:\\+?86[- ]?)?1[3-9]\\d{9}(?!\\d)");
    private static final Pattern QQ_WECHAT = Pattern.compile(
            "(?iu)(?:QQ|微信|V信|wechat|wx)\\s*[:：号]?\\s*[a-z0-9_-]{5,20}");

    public String sanitize(String value, String traceId) {
        if (value == null) return null;
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .replaceAll("[\\u200B-\\u200F\\u202A-\\u202E\\u2060\\uFEFF]", "")
                .replaceAll("\\s+", " ").trim();
        normalized = EMAIL.matcher(normalized).replaceAll(REDACTED);
        normalized = PHONE.matcher(normalized).replaceAll(REDACTED);
        normalized = QQ_WECHAT.matcher(normalized).replaceAll(REDACTED);
        return PromptInjectionPolicy.standard().protect(normalized, traceId).sanitizedContent();
    }
}
