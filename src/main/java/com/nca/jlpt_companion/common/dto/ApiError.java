package com.nca.jlpt_companion.common.dto;

import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;

public record ApiError(
        int status,
        String error,
        String message,
        String path,
        Instant timestamp,
        String traceId,
        List<FieldErrorItem> errors
) {
    public static ApiError of(HttpStatus status, String message, String path, String traceId) {
        return new ApiError(status.value(), status.getReasonPhrase(), message, path, Instant.now(), traceId, List.of());
    }
    public static ApiError of(HttpStatus status, String message, String path, String traceId, List<FieldErrorItem> errors) {
        return new ApiError(status.value(), status.getReasonPhrase(), message, path, Instant.now(), traceId, errors);
    }

    public record FieldErrorItem(String field, String message) {}
}
