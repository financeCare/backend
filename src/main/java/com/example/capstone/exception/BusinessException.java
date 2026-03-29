package com.example.capstone.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
 public class BusinessException extends RuntimeException {
 
     private final HttpStatus status;
     private final String errorCode;
 
     public BusinessException(String message, HttpStatus status) {
         super(message);
         this.status = status;
         this.errorCode = "BUSINESS_ERROR";
     }
 
     public BusinessException(String message, String errorCode, HttpStatus status) {
         super(message);
         this.errorCode = errorCode;
         this.status = status;
     }
 }
