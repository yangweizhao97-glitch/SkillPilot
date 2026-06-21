package com.huatai.careeragent.llm.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PromptInjectionPolicy {
    private static final Logger log = LoggerFactory.getLogger(PromptInjectionPolicy.class);
    private static final int MAX_CONTEXT_CHARS = 50_000;
    private static final String REDACTED = "[REDACTED_BY_PROMPT_SECURITY_POLICY]";
    private static final PromptInjectionPolicy STANDARD = new PromptInjectionPolicy(List.of(
            rule("BOUNDARY_ESCAPE", RiskLevel.HIGH,
                    "(?i)</?\\s*untrusted[_ -]?context\\s*>", REDACTED),
            rule("INSTRUCTION_OVERRIDE", RiskLevel.HIGH,
                    "(?iu)(ignore|disregard|override|forget)\\s+(all\\s+)?(previous|prior|system|developer)\\s+"
                            + "(instructions?|prompts?|messages?)|(ignore|disregard|override).{0,12}(schema|json format)|"
                            + "忽略.{0,12}(之前|以上|系统|开发者).{0,8}(指令|提示)|覆盖.{0,8}(系统|开发者).{0,8}(指令|提示)",
                    REDACTED),
            rule("ROLE_SPOOFING", RiskLevel.HIGH,
                    "(?iu)<\\s*/?\\s*(system|assistant|developer)\\s*>|(^|[\\r\\n])\\s*(system|assistant|developer)\\s*:",
                    REDACTED),
            rule("PROMPT_EXFILTRATION", RiskLevel.HIGH,
                    "(?iu)(reveal|print|show|return|expose|leak).{0,24}(system prompt|developer message|api[_ -]?key|secret|credentials?)|"
                            + "(输出|显示|泄露|返回).{0,20}(系统提示|开发者消息|密钥|凭证)",
                    REDACTED),
            rule("TOOL_MANIPULATION", RiskLevel.MEDIUM,
                    "(?iu)(you\\s+(must|should)|please|now)\\s+(call|invoke|execute|run).{0,20}(tool|function|shell|command)|"
                            + "(请|必须|立即).{0,6}(调用|执行).{0,16}(工具|函数|命令|脚本)",
                    REDACTED),
            rule("BEARER_TOKEN", RiskLevel.HIGH,
                    "(?i)bearer\\s+[a-z0-9._~-]{8,}", "Bearer ***"),
            rule("JWT", RiskLevel.HIGH,
                    "eyJ[a-zA-Z0-9_-]+\\.eyJ[a-zA-Z0-9_-]+\\.[a-zA-Z0-9_-]+", "***JWT***"),
            rule("API_KEY", RiskLevel.HIGH,
                    "(?i)(sk|dashscope)-[a-z0-9_-]{12,}", "***API_KEY***")
    ));

    private final List<Rule> rules;

    PromptInjectionPolicy(List<Rule> rules) {
        this.rules = List.copyOf(rules);
    }

    public static PromptInjectionPolicy standard() {
        return STANDARD;
    }

    public PolicyDecision protect(String rawContent, String traceId) {
        String original = rawContent == null ? "" : rawContent;
        String sanitized = normalize(original);
        List<Finding> findings = new ArrayList<>();
        for (Rule rule : rules) {
            Matcher matcher = rule.pattern().matcher(sanitized);
            int matches = 0;
            while (matcher.find()) {
                matches++;
            }
            if (matches > 0) {
                findings.add(new Finding(rule.id(), rule.risk(), matches));
            }
        }
        for (Rule rule : rules) {
            sanitized = rule.pattern().matcher(sanitized)
                    .replaceAll(Matcher.quoteReplacement(rule.replacement()));
        }
        if (sanitized.length() > MAX_CONTEXT_CHARS) {
            sanitized = sanitized.substring(0, MAX_CONTEXT_CHARS)
                    + "\n[TRUNCATED_BY_PROMPT_SECURITY_POLICY originalLength=" + sanitized.length() + "]";
            findings.add(new Finding("CONTEXT_TOO_LARGE", RiskLevel.MEDIUM, 1));
        }
        RiskLevel risk = findings.stream().map(Finding::risk).max(Enum::compareTo).orElse(RiskLevel.NONE);
        PolicyAction action = findings.isEmpty() ? PolicyAction.ALLOW : PolicyAction.SANITIZE;
        PolicyDecision decision = new PolicyDecision(action, risk, sanitized, List.copyOf(findings), sha256(original));
        if (!findings.isEmpty()) {
            log.warn("Prompt security policy applied: action={}, risk={}, rules={}, contentLength={}, contentHash={}, traceId={}",
                    action, risk, findings.stream().map(Finding::ruleId).toList(), original.length(),
                    decision.contentHash().substring(0, 16), safeTraceId(traceId));
        }
        return decision;
    }

    private String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .replaceAll("[\\u200B-\\u200F\\u202A-\\u202E\\u2060\\uFEFF]", "");
    }

    private String safeTraceId(String traceId) {
        if (traceId == null) return "unknown";
        String safe = traceId.replaceAll("[^a-zA-Z0-9_-]", "_");
        return safe.substring(0, Math.min(safe.length(), 80));
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static Rule rule(String id, RiskLevel risk, String expression, String replacement) {
        return new Rule(id, risk, Pattern.compile(expression), replacement);
    }

    private record Rule(String id, RiskLevel risk, Pattern pattern, String replacement) { }
    public enum PolicyAction { ALLOW, SANITIZE }
    public enum RiskLevel { NONE, LOW, MEDIUM, HIGH }
    public record Finding(String ruleId, RiskLevel risk, int matchCount) { }
    public record PolicyDecision(PolicyAction action, RiskLevel risk, String sanitizedContent,
                                 List<Finding> findings, String contentHash) { }
}
