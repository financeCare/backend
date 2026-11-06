package com.example.capstone.integration;

import com.example.capstone.exception.BusinessException;
import com.example.capstone.service.MinioService;
import io.minio.MinioClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Upload, download and delete against a real MinIO, so a client library bump is checked on the wire (skipped without Docker). */
@Testcontainers(disabledWithoutDocker = true)
class MinioStorageIntegrationTest {

    @Container
    static GenericContainer<?> minio = new GenericContainer<>(DockerImageName.parse("quay.io/minio/minio:RELEASE.2025-04-22T22-12-26Z"))
            .withEnv("MINIO_ROOT_USER", "test-user")
            .withEnv("MINIO_ROOT_PASSWORD", "test-password-123")
            .withCommand("server", "/data")
            .withExposedPorts(9000)
            .waitingFor(Wait.forHttp("/minio/health/ready").forPort(9000));

    static MinioService service;

    @BeforeAll
    static void start() {
        MinioClient client = MinioClient.builder()
                .endpoint("http://" + minio.getHost() + ":" + minio.getMappedPort(9000))
                .credentials("test-user", "test-password-123")
                .build();
        service = new MinioService(client);
        service.init();   // creates the bucket
    }

    @Test
    void uploadedFileCanBeReadBackAndThenDeleted() throws Exception {
        byte[] content = "slip-bytes".getBytes();
        MockMultipartFile file = new MockMultipartFile("files", "slip.png", "image/png", content);

        String path = service.uploadFile(file);

        try (InputStream in = service.getFile(path)) {
            assertArrayEquals(content, in.readAllBytes());
        }
        service.deleteFile(path);
        assertThrows(BusinessException.class, () -> service.getFile(path).readAllBytes());
    }
}
