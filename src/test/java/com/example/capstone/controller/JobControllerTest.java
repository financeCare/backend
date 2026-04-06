package com.example.capstone.controller;

import com.example.capstone.dto.JobApplicationTrackingRequest;
import com.example.capstone.dto.JobSuggestionRequest;
import com.example.capstone.dto.JobSuggestionResponse;
import com.example.capstone.repository.JobApplicationRepository;
import com.example.capstone.service.JoobleService;
import com.example.capstone.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(JobController.class)
@AutoConfigureMockMvc
public class JobControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private JoobleService joobleService;

    @MockitoBean
    private JobApplicationRepository jobApplicationRepository;

    @MockitoBean
    private UserService userService;

    @Test
    @WithMockUser
    void testGetSuggestedJobs_Success() throws Exception {
        // Arrange
        JobSuggestionRequest request = new JobSuggestionRequest();
        request.setKeywords("developer");
        request.setExtraIncomeNeeded(5000.0);

        JobSuggestionResponse response = new JobSuggestionResponse();
        response.setId("1");
        response.setTitle("Java Developer");
        
        when(joobleService.getSuggestedJobs(any(JobSuggestionRequest.class)))
                .thenReturn(Collections.singletonList(response));

        // Act & Assert
        mockMvc.perform(post("/jobs/suggest")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("1"))
                .andExpect(jsonPath("$[0].title").value("Java Developer"));
    }

    @Test
    @WithMockUser
    void testGetSuggestedJobs_InvalidRequest() throws Exception {
        // Arrange (missing keywords and extraIncomeNeeded)
        JobSuggestionRequest request = new JobSuggestionRequest();

        // Act & Assert
        mockMvc.perform(post("/jobs/suggest")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void testTrackApplication_Success() throws Exception {
        // Arrange
        JobApplicationTrackingRequest request = new JobApplicationTrackingRequest();
        request.setJobId("job-123");
        request.setJobTitle("Software Engineer");
        request.setPlatform("LinkedIn");

        UUID mockUserId = UUID.randomUUID();
        when(userService.extractUserIdFromToken(anyString())).thenReturn(mockUserId);

        // Act & Assert
        mockMvc.perform(post("/jobs/track-application")
                .with(csrf())
                .header("Authorization", "Bearer fake-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void testTrackApplication_InvalidRequest() throws Exception {
        // Arrange (missing fields)
        JobApplicationTrackingRequest request = new JobApplicationTrackingRequest();

        // Act & Assert
        mockMvc.perform(post("/jobs/track-application")
                .with(csrf())
                .header("Authorization", "Bearer fake-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
