
package com.sriram.cap39.controller;

import com.sriram.cap39.service.UploadService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/uploads")
public class UploadController {

    private final UploadService uploadService;

    public UploadController(UploadService uploadService) {
        this.uploadService = uploadService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("uploadedBy") String uploadedBy) {

        return uploadService.uploadFile(file, uploadedBy);
    }

    @GetMapping
    public List<Map<String, Object>> getAllUploads() {
        return uploadService.getAllUploads();
    }

    @GetMapping("/{uploadId}")
    public Map<String, Object> getUploadById(
            @PathVariable String uploadId) {

        return uploadService.getUploadById(uploadId);
    }
}
