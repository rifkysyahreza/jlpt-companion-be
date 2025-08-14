package com.nca.jlpt_companion.common.exception;

import com.nca.jlpt_companion.common.dto.ApiError;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static String path(ServletWebRequest req) {
        return (req != null && req.getRequest() != null) ? req.getRequest().getRequestURI() : "";
    }

    private static String traceId() {
        return MDC.get("traceId"); // make sure filter/log config set traceId
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, ServletWebRequest req) {
        List<ApiError.FieldErrorItem> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new ApiError.FieldErrorItem(e.getField(), e.getDefaultMessage()))
                .toList();

        return ResponseEntity.badRequest().body(
                ApiError.of(HttpStatus.BAD_REQUEST, "Validation error", req.getRequest().getRequestURI(),
                        traceId(), fieldErrors)
        );
    }

    @ExceptionHandler({
            ConstraintViolationException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<ApiError> handleBadRequest(Exception ex, ServletWebRequest req) {
        var body = ApiError.of(HttpStatus.BAD_REQUEST, "Bad request", path(req), traceId());
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, ServletWebRequest req) {
        var body = ApiError.of(HttpStatus.FORBIDDEN, "Forbidden", path(req), traceId());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleConflict(DataIntegrityViolationException ex, ServletWebRequest req) {
        var body = ApiError.of(HttpStatus.CONFLICT, "Data integrity violation", path(req), traceId());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException ex, ServletWebRequest req) {
        return ResponseEntity.badRequest().body(
                ApiError.of(HttpStatus.BAD_REQUEST, ex.getMessage(), req.getRequest().getRequestURI(), traceId())
        );
    }

    @ExceptionHandler(AppExceptions.ConflictException.class)
    public ResponseEntity<ApiError> handleConflict(AppExceptions.ConflictException ex, ServletWebRequest req) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                ApiError.of(HttpStatus.CONFLICT, ex.getMessage(), req.getRequest().getRequestURI(), traceId())
        );
    }

    @ExceptionHandler(AppExceptions.AuthFailedException.class)
    public ResponseEntity<ApiError> handleAuthFailed(AppExceptions.AuthFailedException ex, ServletWebRequest req) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                ApiError.of(HttpStatus.UNAUTHORIZED, ex.getMessage(), req.getRequest().getRequestURI(), traceId())
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneral(Exception ex, ServletWebRequest req) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                ApiError.of(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), req.getRequest().getRequestURI(), traceId())
        );
    }

    @ExceptionHandler(AppExceptions.ForbiddenException.class)
    public ResponseEntity<ApiError> handleForbidden(AppExceptions.ForbiddenException ex, ServletWebRequest req) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                ApiError.of(HttpStatus.FORBIDDEN, ex.getMessage(), req.getRequest().getRequestURI(), traceId())
        );
    }
}
