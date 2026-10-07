package com.example.app.web;

import com.example.app.service.DuplicateEmailException;
import com.example.app.service.EmployeeNotFoundException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebInputException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EmployeeNotFoundException.class)
    public ResponseEntity<Map<String, Object>> notFound(EmployeeNotFoundException e, ServerHttpRequest req) {
        return body(HttpStatus.NOT_FOUND, e.getMessage(), req, null);
    }

    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<Map<String, Object>> conflict(DuplicateEmailException e, ServerHttpRequest req) {
        return body(HttpStatus.CONFLICT, e.getMessage(), req, null);
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<Map<String, Object>> validation(WebExchangeBindException e, ServerHttpRequest req) {
        List<Map<String, String>> errors = e.getFieldErrors().stream()
                .map(f -> Map.of("field", f.getField(), "message", String.valueOf(f.getDefaultMessage())))
                .toList();
        return body(HttpStatus.BAD_REQUEST, "Validation failed", req, errors);
    }

    @ExceptionHandler(ServerWebInputException.class)
    public ResponseEntity<Map<String, Object>> badInput(ServerWebInputException e, ServerHttpRequest req) {
        return body(HttpStatus.BAD_REQUEST, "Malformed request: " + e.getReason(), req, null);
    }

    private ResponseEntity<Map<String, Object>> body(HttpStatus status, String message, ServerHttpRequest req,
                                                     List<Map<String, String>> errors) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("timestamp", Instant.now().toString());
        m.put("status", status.value());
        m.put("error", status.getReasonPhrase());
        m.put("message", message);
        m.put("path", req.getPath().value());
        if (errors != null) {
            m.put("errors", errors);
        }
        return ResponseEntity.status(status).body(m);
    }
}
