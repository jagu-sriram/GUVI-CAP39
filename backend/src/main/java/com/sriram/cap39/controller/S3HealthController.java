package com.sriram.cap39.controller;

import com.sriram.cap39.service.S3StorageService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/health")
public class S3HealthController {

    private final S3StorageService s3StorageService;

    public S3HealthController(S3StorageService s3StorageService) {
        this.s3StorageService = s3StorageService;
    }

    @PostMapping("/s3")
    public Map<String, Object> testS3Connection() {
        return s3StorageService.testConnection();
    }
}