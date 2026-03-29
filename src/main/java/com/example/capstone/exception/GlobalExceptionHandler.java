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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.HttpRequestMethodNotSupportedException;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleEmailExistsException(EmailAlreadyExistsException ex, WebRequest request) {
        ErrorResponse error = buildErrorResponse(
                "EMAIL_ALREADY_EXISTS",
                ex.getMessage(),
                request
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
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
        logger.error("Internal Server Error: ", ex);
        
        String message = "An unexpected error occurred.";
        // In development/test, we might want to see the message, but in production we hide it.
        // For now, we'll keep it simple but remove the manual file logging.
        
        ErrorResponse error = buildErrorResponse(
                "INTERNAL_SERVER_ERROR",
                ex.getMessage() != null ? ex.getMessage() : message,
                request
        );
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException ex, WebRequest request) {
        ErrorResponse error = buildErrorResponse(
                ex.getErrorCode(),
                ex.getMessage(),
                request
        );
        return new ResponseEntity<>(error, ex.getStatus());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex, WebRequest request) {
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> 
            fieldErrors.put(error.getField(), error.getDefaultMessage())
        );

        String message = "Validation failed for " + ex.getBindingResult().getObjectName();

        ErrorResponse error = buildErrorResponse(
                "VALIDATION_ERROR",
                message,
                request
        );
        error.setFieldErrors(fieldErrors);
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadableException(HttpMessageNotReadableException ex, WebRequest request) {
        ErrorResponse error = buildErrorResponse(
                "BAD_REQUEST",
                "Malformed JSON request or invalid data format",
                request
        );
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex, WebRequest request) {
        ErrorResponse error = buildErrorResponse(
                "BAD_REQUEST",
                "Invalid argument: " + ex.getMessage(),
                request
        );
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoHandlerFoundException(NoHandlerFoundException ex, WebRequest request) {
        ErrorResponse error = buildErrorResponse(
                "NOT_FOUND",
                "The requested resource was not found",
                request
        );
        return new ResponseEntity<>(error, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErrorResponse> handleMissingServletRequestPartException(MissingServletRequestPartException ex, WebRequest request) {
        ErrorResponse error = buildErrorResponse(
                "BAD_REQUEST",
                "Required part is missing: " + ex.getRequestPartName(),
                request
        );
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ErrorResponse> handleMultipartException(MultipartException ex, WebRequest request) {
        ErrorResponse error = buildErrorResponse(
                "BAD_REQUEST",
                "Multipart request is invalid: " + ex.getMessage(),
                request
        );
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException ex, WebRequest request) {
        ErrorResponse error = buildErrorResponse(
                "PAYLOAD_TOO_LARGE",
                "File size exceeds the maximum limit",
                request
        );
        return new ResponseEntity<>(error, HttpStatus.PAYLOAD_TOO_LARGE);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupportedException(HttpRequestMethodNotSupportedException ex, WebRequest request) {
        ErrorResponse error = buildErrorResponse(
                "METHOD_NOT_ALLOWED",
                "Request method '" + ex.getMethod() + "' is not supported",
                request
        );
        return new ResponseEntity<>(error, HttpStatus.METHOD_NOT_ALLOWED);
    }
}
