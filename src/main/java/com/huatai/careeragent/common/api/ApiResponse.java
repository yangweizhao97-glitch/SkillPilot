package com.huatai.careeragent.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.huatai.careeragent.common.trace.TraceIdContext;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        T data,
        ApiError error,
        String traceId
) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, TraceIdContext.currentTraceId());
    }

    public static ApiResponse<Void> fail(ApiError error) {
        return new ApiResponse<>(false, null, error, TraceIdContext.currentTraceId());
    }
}
