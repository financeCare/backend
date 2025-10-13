package com.example.poc.controller;

import com.example.poc.service.MinioService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;

@RestController
@RequestMapping("/minio")
public class minioController {
    private MinioService minioService;

    public minioController(MinioService minioService) {
        this.minioService = minioService;
    }


    @GetMapping("/download/{filename}")
    public ResponseEntity<byte[]> getFile(@PathVariable String filename) {
        try (InputStream stream = minioService.getObject(filename)) {
            byte[] content = stream.readAllBytes();
            return ResponseEntity.ok().body(content);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/url/{filename}")
    public String getFileUrl(@PathVariable String filename) throws Exception {
        return minioService.getPresignedUrl(filename);
    }
}
