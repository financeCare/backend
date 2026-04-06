package com.example.capstone.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class JobApplicationTrackingRequest {
    @NotBlank(message = "Job ID is required")
    private String jobId;
    
    @NotBlank(message = "Job Title is required")
    private String jobTitle;
    
    @NotBlank(message = "Platform is required")
    private String platform;
}
