package com.example.capstone.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class JobSuggestionResponse {
    private String id;
    private String title;
    private String type;
    private String estimatedIncome;
    private String description;
    private String requirement;
    private Map<String, String> platformLinks;
    private boolean isRecommended;
}
