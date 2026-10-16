package com.example.capstone.service;

import com.example.capstone.exception.BusinessException;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MinioService {

    private static final String BUCKET_NAME = "slips";
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Bangkok");

    private final MinioClient minioClient;

    @PostConstruct
    public void init() {
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(BUCKET_NAME).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(BUCKET_NAME).build());
                log.info("Created bucket: {}", BUCKET_NAME);
            }
        } catch (Exception e) {
            log.error("Error checking/creating bucket '{}': {}", BUCKET_NAME, e.getMessage());
        }
    }

    public String uploadFile(MultipartFile file) throws IOException {
        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }

        String fileName = UUID.randomUUID().toString() + extension;
        String datePath = LocalDate.now(DEFAULT_ZONE).format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String fullPath = String.join("/", datePath, fileName);

        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(BUCKET_NAME)
                            .object(fullPath)
                            .stream(inputStream, file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );
            return fullPath;
        } catch (Exception e) {
            throw new BusinessException("Failed to upload file to storage: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public void deleteFile(String path) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(BUCKET_NAME)
                            .object(path)
                            .build()
            );
            log.info("Deleted file from Minio: {}", path);
        } catch (Exception e) {
            log.error("Failed to delete file from Minio {}: {}", path, e.getMessage());
        }
    }

    public InputStream getFile(String path) {
        try {
            return minioClient.getObject(
                    io.minio.GetObjectArgs.builder()
                            .bucket(BUCKET_NAME)
                            .object(path)
                            .build()
            );
        } catch (Exception e) {
            throw new BusinessException("Failed to retrieve file from storage: " + e.getMessage(), HttpStatus.NOT_FOUND);
        }
    }
}
