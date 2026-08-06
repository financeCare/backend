package com.example.capstone.service;

import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
public class TemplateService  {

    public String loadTemplate(String path) throws IOException {

        InputStream inputStream = getClass()
                .getClassLoader()
                .getResourceAsStream(path);

        if (inputStream == null) {
            throw new FileNotFoundException("Template not found at path: " + path);
        }

        return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    }

}
