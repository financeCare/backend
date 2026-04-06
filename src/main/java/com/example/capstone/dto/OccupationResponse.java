package com.example.capstone.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OccupationResponse {
    private String title;
    private String description;
    private String averageIncome;
    private String iconType; // e.g., "delivery", "computer", "teaching", "sales"
    private List<String> pros;
    private List<String> cons;
    private String potentialKeywords; // For Jooble search
    private String externalUrl; // Link to direct platform (e.g. Grab signup)
    private String platformName; // Name of the platform (e.g. "Grab")
    private Boolean isBestMatch; // Flag for income goal matching
}
