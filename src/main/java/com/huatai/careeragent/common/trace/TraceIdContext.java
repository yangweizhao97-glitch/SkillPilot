package com.huatai.careeragent.common.trace;

import org.slf4j.MDC;
import org.springframework.util.StringUtils;

import java.util.UUID;

public final class TraceIdContext {
    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String MDC_KEY = "traceId";

    private TraceIdContext() {
    }

    public static String currentTraceId() {
        String traceId = MDC.get(MDC_KEY);
        if (StringUtils.hasText(traceId)) {
            return traceId;
        }
        return "trace_" + UUID.randomUUID().toString().replace("-", "");
    }

    public static void set(String traceId) {
        MDC.put(MDC_KEY, traceId);
    }

    public static void clear() {
        MDC.remove(MDC_KEY);
    }
}
