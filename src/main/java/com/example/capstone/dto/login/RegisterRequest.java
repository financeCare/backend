package com.example.capstone.dto.login;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Date;

@Data
public class RegisterRequest {
    private String username;
    @NotBlank(message = "Password is required")
    private String password;
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;
    private Date dob;
}
