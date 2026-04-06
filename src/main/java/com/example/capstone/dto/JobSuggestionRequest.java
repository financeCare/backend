package com.example.capstone.dto;

import lombok.Data;

@Data
public class JobSuggestionRequest {
    private String keywords;
    private String location;
    private Double extraIncomeNeeded;
}
