
package com.sriram.cap39.service;

import com.opencsv.CSVReader;
import com.sriram.cap39.upload.DatasetSchema;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.*;

@Service
public class UploadService {

    private static final String UPLOAD_TABLE =
            "SRIRAM_CAP39_DB.SRIRAM_APP.SRIRAM_FILE_UPLOADS";

    private final JdbcTemplate jdbcTemplate;
    private final S3StorageService s3StorageService;

    public UploadService(
            JdbcTemplate jdbcTemplate,
            S3StorageService s3StorageService) {
        this.jdbcTemplate = jdbcTemplate;
        this.s3StorageService = s3StorageService;
    }

    public Map<String, Object> uploadFile(
            MultipartFile file,
            String uploadedBy) {

        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Please select a CSV file."
            );
        }

        if (uploadedBy == null || uploadedBy.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "uploadedBy is required."
            );
        }

        String originalName = Paths.get(
                file.getOriginalFilename() == null
                        ? ""
                        : file.getOriginalFilename()
        ).getFileName().toString();

        if (!originalName.toLowerCase(Locale.ROOT).endsWith(".csv")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Only CSV files are supported."
            );
        }

        String datasetName = originalName
                .substring(0, originalName.length() - 4)
                .toLowerCase(Locale.ROOT);

        String[] expectedHeaders = DatasetSchema.getHeaders(datasetName);

        if (expectedHeaders == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unsupported dataset: " + datasetName
            );
        }

        validateHeaders(file, expectedHeaders);

        String uploadId = UUID.randomUUID().toString();

        String rawS3Key = "raw/" + datasetName
                + "/" + uploadId + "/original.csv";

        boolean uploadedToS3 = false;

        try {
            s3StorageService.uploadMultipartFile(file, rawS3Key);
            uploadedToS3 = true;

            jdbcTemplate.update("""
                INSERT INTO SRIRAM_CAP39_DB.SRIRAM_APP.SRIRAM_FILE_UPLOADS
                (
                    UPLOAD_ID,
                    FILE_NAME,
                    DATASET_NAME,
                    FILE_PATH,
                    RAW_S3_KEY,
                    UPLOADED_BY,
                    UPLOAD_STATUS
                )
                VALUES (?, ?, ?, ?, ?, ?, 'RECEIVED')
                """,
                    uploadId,
                    originalName,
                    datasetName,
                    rawS3Key,
                    rawS3Key,
                    uploadedBy
            );

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("uploadId", uploadId);
            response.put("fileName", originalName);
            response.put("datasetName", datasetName);
            response.put("rawS3Key", rawS3Key);
            response.put("uploadStatus", "RECEIVED");
            response.put("message",
                    "CSV uploaded to S3 and registered successfully.");

            return response;

        } catch (Exception exception) {

            if (uploadedToS3) {
                try {
                    s3StorageService.deleteObject(rawS3Key);
                } catch (Exception cleanupException) {
                    exception.addSuppressed(cleanupException);
                }
            }

            if (exception instanceof ResponseStatusException responseException) {
                throw responseException;
            }

            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Upload failed: " + exception.getMessage(),
                    exception
            );
        }
    }

    private void validateHeaders(
            MultipartFile file,
            String[] expectedHeaders) {

        try (CSVReader reader = new CSVReader(
                new InputStreamReader(
                        file.getInputStream(),
                        StandardCharsets.UTF_8
                ))) {

            String[] actualHeaders = reader.readNext();

            if (actualHeaders == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "CSV file is empty."
                );
            }

            actualHeaders[0] = actualHeaders[0].replace("\uFEFF", "");

            if (!Arrays.equals(expectedHeaders, actualHeaders)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "CSV headers do not match the expected schema. Expected: "
                                + String.join(", ", expectedHeaders)
                );
            }

        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unable to read CSV headers: " + exception.getMessage(),
                    exception
            );
        }
    }

    public List<Map<String, Object>> getAllUploads() {
        return jdbcTemplate.queryForList("""
            SELECT *
            FROM SRIRAM_CAP39_DB.SRIRAM_APP.SRIRAM_FILE_UPLOADS
            ORDER BY UPLOADED_AT DESC
            """);
    }

    public Map<String, Object> getUploadById(String uploadId) {

        List<Map<String, Object>> results =
                jdbcTemplate.queryForList("""
                SELECT *
                FROM SRIRAM_CAP39_DB.SRIRAM_APP.SRIRAM_FILE_UPLOADS
                WHERE UPLOAD_ID = ?
                """, uploadId);

        if (results.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Upload not found."
            );
        }

        return results.get(0);
    }
}
