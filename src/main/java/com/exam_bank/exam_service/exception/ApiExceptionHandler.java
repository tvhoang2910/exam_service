package com.exam_bank.exam_service.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class ApiExceptionHandler {

    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public ResponseEntity<Void> handleAsyncRequestNotUsable(
            AsyncRequestNotUsableException exception,
            HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path != null && path.startsWith("/api/v1/exam/sse/")) {
            log.info("Client disconnected from SSE stream on {}: {}", path, exception.getMessage());
        } else {
            log.warn("Async request became unusable on {}: {}", path, exception.getMessage());
        }
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatusException(
            ResponseStatusException exception,
            HttpServletRequest request) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        String reason = exception.getReason() == null ? status.getReasonPhrase() : exception.getReason();
        log.warn("Client error {} {} on {}: {}",
                status.value(), status.getReasonPhrase(), request.getRequestURI(), reason);
        return ResponseEntity.status(status).body(buildErrorBody(status, reason, request));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrityViolation(
            DataIntegrityViolationException exception,
            HttpServletRequest request) {
        HttpStatus status = HttpStatus.CONFLICT;
        String reason = "Operation violates data integrity constraints.";
        log.warn("Data integrity violation on {}: {}", request.getRequestURI(),
                exception.getMostSpecificCause().getMessage());
        return ResponseEntity.status(status).body(buildErrorBody(status, reason, request));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoResourceFound(
            NoResourceFoundException exception,
            HttpServletRequest request) {
        HttpStatus status = HttpStatus.NOT_FOUND;
        String reason = exception.getMessage() == null ? "Resource not found." : exception.getMessage();
        log.warn("Resource not found on {}: {}", request.getRequestURI(), reason);
        return ResponseEntity.status(status).body(buildErrorBody(status, reason, request));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(
            Exception exception,
            HttpServletRequest request) {
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        String reason = "An unexpected error occurred.";
        log.error("Unexpected error on {}: {}", request.getRequestURI(), exception.getMessage(), exception);
        return ResponseEntity.status(status).body(buildErrorBody(status, reason, request));
    }

    private Map<String, Object> buildErrorBody(HttpStatus status, String message, HttpServletRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        body.put("path", request.getRequestURI());
        return body;
    }
}
