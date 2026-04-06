package com.example.capstone.controller;

import com.example.capstone.dto.JobApplicationTrackingRequest;
import com.example.capstone.dto.JobSuggestionRequest;
import com.example.capstone.dto.JobSuggestionResponse;
import com.example.capstone.dto.OccupationResponse;
import com.example.capstone.entity.JobApplication;
import com.example.capstone.repository.JobApplicationRepository;
import com.example.capstone.service.JoobleService;
import com.example.capstone.service.OccupationDiscoveryService;
import com.example.capstone.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/jobs")
@RequiredArgsConstructor
@Tag(name = "Job Suggestion", description = "Endpoints for job suggestions based on financial needs")
public class JobController {

    private final JoobleService joobleService;
    private final JobApplicationRepository jobApplicationRepository;
    private final UserService userService;
    private final OccupationDiscoveryService occupationService;

    @GetMapping("/occupations")
    @Operation(summary = "Get recommended side-hustle occupations in Thailand")
    public ResponseEntity<List<OccupationResponse>> getOccupations() {
        return ResponseEntity.ok(occupationService.getRecommendedOccupations());
    }

    @PostMapping("/suggest")
    @Operation(summary = "Get job suggestions from Jooble")
    public ResponseEntity<List<JobSuggestionResponse>> getSuggestedJobs(
            @Valid @RequestBody JobSuggestionRequest request) {
        List<JobSuggestionResponse> jobs = joobleService.getSuggestedJobs(request);
        return ResponseEntity.ok(jobs);
    }

    @PostMapping("/track-application")
    @Operation(summary = "Track when a user clicks to apply for a job")
    public ResponseEntity<Void> trackApplication(
            @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody JobApplicationTrackingRequest request) {

        String token = authHeader.replace("Bearer ", "");
        UUID userId = userService.extractUserIdFromToken(token);

        JobApplication application = JobApplication.builder()
                .userId(userId)
                .jobId(request.getJobId())
                .job_title(request.getJobTitle())
                .platform(request.getPlatform())
                .build();

        jobApplicationRepository.save(application);
        return ResponseEntity.ok().build();
    }
}
