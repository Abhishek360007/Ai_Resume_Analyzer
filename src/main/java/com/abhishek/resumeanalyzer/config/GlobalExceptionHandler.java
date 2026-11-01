package com.abhishek.resumeanalyzer.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Global exception handler for REST endpoints.
 *
 * <p>Converts framework and application exceptions into a consistent
 * {@code { "error": ... }} JSON body instead of leaking default error pages
 * or stack traces to API clients.</p>
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Bean-validation failures (e.g. {@code @Valid} on request bodies).
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fieldError -> fieldError.getDefaultMessage() != null
                                ? fieldError.getDefaultMessage()
                                : "Invalid value",
                        (first, second) -> first,
                        LinkedHashMap::new
                ));
        return ResponseEntity.badRequest().body(Map.of(
                "error", "Validation failed",
                "fieldErrors", fieldErrors
        ));
    }

    /**
     * Business-rule violations raised directly from service/controller code.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        String message = ex.getMessage() != null ? ex.getMessage() : "Bad request";
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }

    /**
     * Missing/invalid authentication or authorization from method security.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Access denied"));
    }

    /**
     * Requests for resources that do not exist.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Resource not found"));
    }

    /**
     * Catch-all for anything unexpected.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Internal server error"));
    }
}
