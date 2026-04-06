package com.example.capstone.controller;

import com.example.capstone.dto.JobSuggestionRequest;
import com.example.capstone.dto.JobSuggestionResponse;
import com.example.capstone.service.JoobleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/jobs")
@RequiredArgsConstructor
@Tag(name = "Job Suggestion", description = "Endpoints for job suggestions based on financial needs")
public class JobController {

    private final JoobleService joobleService;

    @PostMapping("/suggest")
    @Operation(summary = "Get job suggestions from Jooble")
    public ResponseEntity<List<JobSuggestionResponse>> getSuggestedJobs(@RequestBody JobSuggestionRequest request) {
        List<JobSuggestionResponse> jobs = joobleService.getSuggestedJobs(request);
        return ResponseEntity.ok(jobs);
    }
}
