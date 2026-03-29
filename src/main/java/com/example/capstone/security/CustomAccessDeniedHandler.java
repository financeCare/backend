package com.example.capstone.security;
 
 import com.example.capstone.dto.error.ErrorResponse;
 import com.fasterxml.jackson.databind.ObjectMapper;
 import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
 import jakarta.servlet.ServletException;
 import jakarta.servlet.http.HttpServletRequest;
 import jakarta.servlet.http.HttpServletResponse;
 import org.springframework.http.HttpStatus;
 import org.springframework.http.MediaType;
 import org.springframework.security.access.AccessDeniedException;
 import org.springframework.security.web.access.AccessDeniedHandler;
 import org.springframework.stereotype.Component;
 
 import java.io.IOException;
 import java.time.LocalDateTime;
 
 @Component
 public class CustomAccessDeniedHandler implements AccessDeniedHandler {
 
     private final ObjectMapper objectMapper;
 
     public CustomAccessDeniedHandler() {
         this.objectMapper = new ObjectMapper();
         this.objectMapper.registerModule(new JavaTimeModule());
     }
 
     @Override
     public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException) 
             throws IOException, ServletException {
         
         response.setStatus(HttpStatus.FORBIDDEN.value());
         response.setContentType(MediaType.APPLICATION_JSON_VALUE);
 
         ErrorResponse errorResponse = ErrorResponse.builder()
                 .timestamp(LocalDateTime.now())
                 .error("FORBIDDEN")
                 .message(accessDeniedException.getMessage() != null ? accessDeniedException.getMessage() : "Access is denied")
                 .details("You do not have permission to access this resource")
                 .path(request.getRequestURI())
                 .build();
 
         response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
     }
 }
