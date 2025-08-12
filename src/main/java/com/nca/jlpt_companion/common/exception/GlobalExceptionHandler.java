package com.nca.jlpt_companion.common.exception;

import com.nca.jlpt_companion.common.dto.ApiError;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.MDC;
import org.springframework.context.support.DefaultMessageSourceResolvable;
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
        List<ApiError.FieldErrorItem> fields = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ApiError.FieldErrorItem(fe.getField(), fe.getDefaultMessage()))
                .toList();
        String msg = ex.getBindingResult().getAllErrors().stream()
                .findFirst().map(DefaultMessageSourceResolvable::getDefaultMessage)
                .orElse("Validation error");
        var body = ApiError.of(HttpStatus.BAD_REQUEST, msg, path(req), traceId(), fields);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
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

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneral(Exception ex, ServletWebRequest req) {
        // LOG details for internal (stacktrace), but DO NOT expose to client
        var body = ApiError.of(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", path(req), traceId());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
