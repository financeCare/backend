package com.example.capstone.service;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MinioService {

    private final String bucketName = "slips";
    private final MinioClient minioClient;

    @PostConstruct
    public void init() {
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
                log.info("Created bucket: {}", bucketName);
            }
        } catch (Exception e) {
            log.error("Error checking/creating bucket '{}': {}", bucketName, e.getMessage());
        }
    }

    public String uploadFile(MultipartFile file) throws Exception {
        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }

        String fileName = UUID.randomUUID().toString() + extension;
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String fullPath = datePath + "/" + fileName;

        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(fullPath)
                            .stream(inputStream, file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );
            return fullPath;
        }
    }

    public void deleteFile(String path) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(path)
                            .build()
            );
            log.info("Deleted file from Minio: {}", path);
        } catch (Exception e) {
            log.error("Failed to delete file from Minio {}: {}", path, e.getMessage());
        }
    }

    public InputStream getFile(String path) throws Exception {
        return minioClient.getObject(
                io.minio.GetObjectArgs.builder()
                        .bucket(bucketName)
                        .object(path)
                        .build()
        );
    }
}
