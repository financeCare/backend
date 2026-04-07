package com.example.capstone.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class JobSuggestionRequest {
    @NotBlank(message = "Keywords cannot be blank")
    private String keywords;

    private String location;

    private List<String> skills;
}
