package com.webox.common;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Uniform response envelope: {@code { code, message, data, requestId }}.
 *
 * @param code      stable machine-readable code ({@code OK} on success)
 * @param message   English, user-facing
 * @param data      payload, absent on failures without extra context
 * @param requestId correlation id, also emitted as the {@code X-Request-Id} header
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(String code, String message, T data, String requestId) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(ErrorCode.OK.name(), ErrorCode.OK.message(), data, RequestIdFilter.currentRequestId());
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(ErrorCode.OK.name(), ErrorCode.OK.message(), null, RequestIdFilter.currentRequestId());
    }

    public static <T> ApiResponse<T> error(ErrorCode code, String message, T data) {
        return new ApiResponse<>(code.name(), message, data, RequestIdFilter.currentRequestId());
    }

    public static ApiResponse<Object> error(ErrorCode code) {
        return error(code, code.message());
    }

    public static ApiResponse<Object> error(ErrorCode code, String message) {
        return new ApiResponse<>(code.name(), message, null, RequestIdFilter.currentRequestId());
    }
}
