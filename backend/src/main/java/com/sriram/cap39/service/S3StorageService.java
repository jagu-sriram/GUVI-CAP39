
package com.sriram.cap39.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@Service
public class S3StorageService {

    private final S3Client s3Client;
    private final String bucketName;

    public S3StorageService(
            S3Client s3Client,
            @Value("${cap39.aws.bucket}") String bucketName) {
        this.s3Client = s3Client;
        this.bucketName = bucketName;
    }

    public String uploadMultipartFile(
            MultipartFile file,
            String objectKey) throws IOException {

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .contentType(
                        file.getContentType() == null
                                ? "text/csv"
                                : file.getContentType()
                )
                .build();

        s3Client.putObject(
                request,
                RequestBody.fromInputStream(
                        file.getInputStream(),
                        file.getSize()
                )
        );

        return objectKey;
    }

    public String uploadFile(
            Path localFile,
            String objectKey,
            String contentType) {

        if (!Files.isRegularFile(localFile)) {
            throw new IllegalArgumentException(
                    "File does not exist: " + localFile
            );
        }

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .contentType(contentType)
                .build();

        s3Client.putObject(
                request,
                RequestBody.fromFile(localFile)
        );

        return objectKey;
    }

    public Path downloadFile(
            String objectKey,
            Path destination) throws IOException {

        Path parent = destination.getParent();

        if (parent != null) {
            Files.createDirectories(parent);
        }

        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .build();

        s3Client.getObject(
                request,
                ResponseTransformer.toFile(destination)
        );

        return destination;
    }

    public boolean objectExists(String objectKey) {
        try {
            s3Client.headObject(
                    HeadObjectRequest.builder()
                            .bucket(bucketName)
                            .key(objectKey)
                            .build()
            );
            return true;
        } catch (NoSuchKeyException exception) {
            return false;
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) {
                return false;
            }
            throw exception;
        }
    }

    public void deleteObject(String objectKey) {
        s3Client.deleteObject(
                DeleteObjectRequest.builder()
                        .bucket(bucketName)
                        .key(objectKey)
                        .build()
        );
    }

    public String getBucketName() {
        return bucketName;
    }

    public Map<String, Object> testConnection() {
        String testKey = "health-checks/cap39-s3-test.txt";
        String testContent = "CAP39 S3 connection successful";

        try {
            s3Client.putObject(
                    software.amazon.awssdk.services.s3.model.PutObjectRequest.builder()
                            .bucket(bucketName)
                            .key(testKey)
                            .contentType("text/plain")
                            .build(),
                    software.amazon.awssdk.core.sync.RequestBody.fromString(testContent)
            );

            String downloadedContent = s3Client.getObjectAsBytes(
                    software.amazon.awssdk.services.s3.model.GetObjectRequest.builder()
                            .bucket(bucketName)
                            .key(testKey)
                            .build()
            ).asUtf8String();

            if (!testContent.equals(downloadedContent)) {
                throw new IllegalStateException("S3 content verification failed.");
            }

            s3Client.deleteObject(
                    software.amazon.awssdk.services.s3.model.DeleteObjectRequest.builder()
                            .bucket(bucketName)
                            .key(testKey)
                            .build()
            );

            return Map.of(
                    "status", "SUCCESS",
                    "bucket", bucketName,
                    "upload", "SUCCESS",
                    "download", "SUCCESS",
                    "verification", "SUCCESS",
                    "cleanup", "SUCCESS"
            );

        } catch (Exception exception) {
            throw new IllegalStateException(
                    "S3 connection test failed: " + exception.getMessage(),
                    exception
            );
        }
    }
}
