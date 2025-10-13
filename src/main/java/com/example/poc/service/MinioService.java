package com.example.poc.service;

import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.http.Method;
import lombok.Getter;
import org.springframework.stereotype.Service;

import java.io.InputStream;

@Getter
@Service
public class MinioService {
    private final MinioClient minioClient;
    private final String bucketName = "poc";

    public MinioService(MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    public InputStream getObject(String objectName) throws Exception {
        return minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .build()
        );
    }
    public String getPresignedUrl(String objectName) throws Exception {
        return minioClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                        .method(Method.GET)
                        .bucket(bucketName)
                        .object(objectName)
                        .expiry(60 * 60) // ลิงก์หมดอายุใน 1 ชั่วโมง
                        .build()
        );
    }

}
