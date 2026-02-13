package com.example.capstone.exception;

import com.example.capstone.dto.error.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<Map<String, Object>> handleEmailExistsException(EmailAlreadyExistsException ex) {

        Map<String, Object> body = new HashMap<>();
        body.put("error", ex.getMessage());

        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    private ErrorResponse buildErrorResponse(String error, String message, WebRequest request) {
        String path = ((ServletWebRequest) request).getRequest().getRequestURI();

        return ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .error(error)
                .message(message)
                .details(request.getDescription(false))
                .path(path)
                .build();
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneralException(Exception ex, WebRequest request) {
        ex.printStackTrace();
        ErrorResponse error = buildErrorResponse(
                "INTERNAL_SERVER_ERROR",
                ex.getMessage(),
                request
        );
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException ex, WebRequest request) {
        ErrorResponse error = buildErrorResponse(
                "BUSINESS_ERROR",
                ex.getMessage(),
                request
        );
        return new ResponseEntity<>(error, ex.getStatus());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex, WebRequest request) {

        String message = ex.getBindingResult().getFieldErrors()
                .stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.joining(", "));

        ErrorResponse error = buildErrorResponse(
                "VALIDATION_ERROR",
                message,
                request
        );
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }
}
