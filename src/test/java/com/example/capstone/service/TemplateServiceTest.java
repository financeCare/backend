package com.example.capstone.service;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

public class TemplateServiceTest {

    private final TemplateService templateService = new TemplateService();

    @Test
    void testLoadTemplate_NotFound() {
        assertThrows(IOException.class, () -> {
            templateService.loadTemplate("non-existent-path.html");
        });
    }
    

}
