package com.example.capstone.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.test.context.ActiveProfiles;

@WebMvcTest(SimpleController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
public class DebugTest {

    @Autowired
    private MockMvc mockMvc;

    @org.springframework.test.context.bean.override.mockito.MockitoBean 
    private com.example.capstone.security.JwtUtil jwtUtil;
    @org.springframework.test.context.bean.override.mockito.MockitoBean 
    private com.example.capstone.service.UserService userService;

    @Test
    void testSimpleCall() throws Exception {
        mockMvc.perform(get("/test-debug"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(content().string("debug-ok"));
    }
}

@org.springframework.web.bind.annotation.RestController
class SimpleController {
    @org.springframework.web.bind.annotation.GetMapping("/test-debug")
    public String debug() {
        return "debug-ok";
    }
}
