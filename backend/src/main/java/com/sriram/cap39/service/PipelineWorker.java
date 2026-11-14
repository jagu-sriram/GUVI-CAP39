package com.sriram.cap39.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
public class PipelineWorker {

    private final ProcessExecutionService processExecutionService;
    private final SnowflakeLoadService snowflakeLoadService;
    private final PipelineStatusService pipelineStatusService;
    private final JdbcTemplate jdbcTemplate;

    public PipelineWorker(
            ProcessExecutionService processExecutionService,
            SnowflakeLoadService snowflakeLoadService,
            PipelineStatusService pipelineStatusService,
            JdbcTemplate jdbcTemplate) {

        this.processExecutionService = processExecutionService;
        this.snowflakeLoadService = snowflakeLoadService;
        this.pipelineStatusService = pipelineStatusService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Async("pipelineExecutor")
    public CompletableFuture<Void> execute(
            String pipelineRunId,
            String uploadId) {

        String processingStatus = "PENDING";
        String loadingStatus = "PENDING";
        String currentStep = "STARTING";

        try {
            Map<String, Object> upload = getUpload(uploadId);

            String uploadStatus = value(upload, "UPLOAD_STATUS");

            /*
             * STEP 1: PYTHON / PANDAS PROCESSING
             *
             * If processing has already completed, skip it.
             * This is useful when retrying a failed Snowflake load.
             */
            if ("RECEIVED".equalsIgnoreCase(uploadStatus)) {

                currentStep = "PROCESSING";
                processingStatus = "RUNNING";

                pipelineStatusService.updateStep(
                        pipelineRunId,
                        "RUNNING",
                        currentStep,
                        processingStatus,
                        loadingStatus
                );

                processExecutionService.processUpload(uploadId);

                processingStatus = "COMPLETED";

            } else if ("COMPLETED".equalsIgnoreCase(uploadStatus)
                    || "LOAD_FAILED".equalsIgnoreCase(uploadStatus)) {

                processingStatus = "COMPLETED";

            } else if ("LOADED".equalsIgnoreCase(uploadStatus)) {

                pipelineStatusService.complete(pipelineRunId);
                return CompletableFuture.completedFuture(null);

            } else {

                throw new IllegalStateException(
                        "Cannot start pipeline. Current upload status: "
                                + uploadStatus
                );
            }

            /*
             * STEP 2: SNOWFLAKE LOADING
             */
            upload = getUpload(uploadId);

            String dataset = value(upload, "DATASET_NAME");
            String fileName = value(upload, "FILE_NAME");
            String acceptedS3Key = value(upload, "ACCEPTED_S3_KEY");

            int totalRecords = getInt(upload.get("TOTAL_RECORDS"));
            int acceptedRecords = getInt(upload.get("ACCEPTED_RECORDS"));
            int rejectedRecords = getInt(upload.get("REJECTED_RECORDS"));
            int duplicateRecords = getInt(upload.get("DUPLICATE_RECORDS"));

            if (acceptedS3Key == null || acceptedS3Key.isBlank()) {
                throw new IllegalStateException(
                        "Accepted S3 file key is missing."
                );
            }

            currentStep = "LOADING";
            loadingStatus = "RUNNING";

            pipelineStatusService.updateStep(
                    pipelineRunId,
                    "RUNNING",
                    currentStep,
                    processingStatus,
                    loadingStatus
            );

            snowflakeLoadService.load(
                    uploadId,
                    dataset,
                    fileName,
                    acceptedS3Key,
                    totalRecords,
                    acceptedRecords,
                    rejectedRecords,
                    duplicateRecords
            );

            loadingStatus = "COMPLETED";

            pipelineStatusService.complete(pipelineRunId);

        } catch (Exception exception) {

            String error = exception.getMessage();

            if (error == null || error.isBlank()) {
                error = exception.getClass().getSimpleName();
            }

            pipelineStatusService.fail(
                    pipelineRunId,
                    currentStep,
                    processingStatus,
                    loadingStatus,
                    error
            );

        }

        return CompletableFuture.completedFuture(null);
    }

    private Map<String, Object> getUpload(String uploadId) {

        return jdbcTemplate.queryForMap("""
            SELECT *
            FROM SRIRAM_CAP39_DB.SRIRAM_APP.SRIRAM_FILE_UPLOADS
            WHERE UPLOAD_ID = ?
            """, uploadId);
    }

    private String value(Map<String, Object> row, String column) {

        Object value = row.get(column);

        return value == null ? null : value.toString();
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