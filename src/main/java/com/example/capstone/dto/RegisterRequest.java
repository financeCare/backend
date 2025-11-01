package com.example.capstone.dto;

import lombok.Data;

import java.util.Date;

@Data
public class RegisterRequest {
    private String username;
    private String password;
    private String email;
    private Date dob;
}
