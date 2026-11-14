
package com.sriram.cap39.controller;

import com.sriram.cap39.service.SnowflakeLoadService;
import com.sriram.cap39.service.UploadService;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/uploads")
public class LoadController {

    private final SnowflakeLoadService loadService;
    private final UploadService uploadService;

    public LoadController(
            SnowflakeLoadService loadService,
            UploadService uploadService) {

        this.loadService = loadService;
        this.uploadService = uploadService;
    }

    @PostMapping("/{uploadId}/load")
    public Map<String, Object> loadUpload(
            @PathVariable String uploadId) {

        Map<String, Object> upload =
                uploadService.getUploadById(uploadId);

        String dataset = String.valueOf(
                upload.get("DATASET_NAME")
        );

        String fileName = String.valueOf(
                upload.get("FILE_NAME")
        );

        String status = String.valueOf(
                upload.get("UPLOAD_STATUS")
        );

        String acceptedS3Key = upload.get("ACCEPTED_S3_KEY") == null
                ? null
                : String.valueOf(upload.get("ACCEPTED_S3_KEY"));

        if ("LOADED".equalsIgnoreCase(status)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This upload has already been loaded."
            );
        }

        if (!"COMPLETED".equalsIgnoreCase(status)
                && !"LOAD_FAILED".equalsIgnoreCase(status)) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Python processing must be COMPLETED before loading. "
                            + "Current status: " + status
            );
        }

        if (acceptedS3Key == null || acceptedS3Key.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Accepted S3 file key is missing."
            );
        }

        int totalRecords = getInt(upload.get("TOTAL_RECORDS"));
        int acceptedRecords = getInt(upload.get("ACCEPTED_RECORDS"));
        int rejectedRecords = getInt(upload.get("REJECTED_RECORDS"));
        int duplicateRecords = getInt(upload.get("DUPLICATE_RECORDS"));

        try {
            return loadService.load(
                    uploadId,
                    dataset,
                    fileName,
                    acceptedS3Key,
                    totalRecords,
                    acceptedRecords,
                    rejectedRecords,
                    duplicateRecords
            );

        } catch (ResponseStatusException exception) {
            throw exception;

        } catch (Exception exception) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Snowflake loading failed: "
                            + exception.getMessage(),
                    exception
            );
        }
    }

    private int getInt(Object value) {

        if (value == null) {
            return 0;
        }

        if (value instanceof Number number) {
            return number.intValue();
        }

        return Integer.parseInt(value.toString());
    }
}
