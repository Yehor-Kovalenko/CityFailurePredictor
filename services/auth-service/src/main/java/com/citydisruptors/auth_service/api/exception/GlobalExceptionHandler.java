package com.citydisruptors.auth_service.api.exception;

import com.citydisruptors.auth_service.api.exception.dto.ErrorResponse;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private final MeterRegistry registry;

    public GlobalExceptionHandler(MeterRegistry registry) {
        this.registry = registry;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException ex) {

        registry.counter("app.errors.total", "type", "bad_request").increment();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(
                        "BAD_REQUEST",
                        ex.getMessage(),
                        LocalDateTime.now()
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {

        registry.counter("app.errors.total", "type", "internal_error").increment();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(
                        "INTERNAL_ERROR",
                        ex.getMessage(),
                        LocalDateTime.now()
                ));
    }
}
