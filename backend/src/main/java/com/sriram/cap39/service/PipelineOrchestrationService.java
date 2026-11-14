package com.sriram.cap39.service;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PipelineOrchestrationService {

    private static final String PIPELINE_TABLE =
            "SRIRAM_CAP39_DB.SRIRAM_APP.SRIRAM_PIPELINE_RUNS";

    private final JdbcTemplate jdbcTemplate;
    private final PipelineWorker pipelineWorker;
    private final PipelineStatusService pipelineStatusService;

    public PipelineOrchestrationService(
            JdbcTemplate jdbcTemplate,
            PipelineWorker pipelineWorker,
            PipelineStatusService pipelineStatusService) {

        this.jdbcTemplate = jdbcTemplate;
        this.pipelineWorker = pipelineWorker;
        this.pipelineStatusService = pipelineStatusService;
    }

    public synchronized Map<String, Object> startPipeline(
            String uploadId,
            boolean retry) {

        Map<String, Object> upload = getUpload(uploadId);

        String uploadStatus = String.valueOf(
                upload.get("UPLOAD_STATUS")
        );

        int retryCount = 0;

        List<Map<String, Object>> previousRuns =
                jdbcTemplate.queryForList("""
                    SELECT PIPELINE_RUN_ID, PIPELINE_STATUS, RETRY_COUNT
                    FROM SRIRAM_CAP39_DB.SRIRAM_APP.SRIRAM_PIPELINE_RUNS
                    WHERE UPLOAD_ID = ?
                    ORDER BY CREATED_AT DESC
                    LIMIT 1
                    """, uploadId);

        if (!previousRuns.isEmpty()) {

            Map<String, Object> previous = previousRuns.get(0);

            String previousStatus = String.valueOf(
                    previous.get("PIPELINE_STATUS")
            );

            retryCount = getInt(previous.get("RETRY_COUNT"));

            if ("RUNNING".equalsIgnoreCase(previousStatus)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "A pipeline is already running for this upload."
                );
            }

            if (!retry && !"RECEIVED".equalsIgnoreCase(uploadStatus)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "This upload already has a pipeline run. "
                                + "Use the retry endpoint if it failed."
                );
            }

            if (retry && !"FAILED".equalsIgnoreCase(previousStatus)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Only failed pipeline runs can be retried."
                );
            }

        } else if (retry) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "No previous pipeline run exists to retry."
            );
        }

        if (retry) {

            if ("FAILED".equalsIgnoreCase(uploadStatus)) {

                int reset = jdbcTemplate.update("""
                    UPDATE SRIRAM_CAP39_DB.SRIRAM_APP.SRIRAM_FILE_UPLOADS
                    SET UPLOAD_STATUS = 'RECEIVED',
                        ERROR_MESSAGE = NULL
                    WHERE UPLOAD_ID = ?
                      AND UPLOAD_STATUS = 'FAILED'
                    """, uploadId);

                if (reset != 1) {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "Could not reset failed upload for retry."
                    );
                }

            } else if (!"LOAD_FAILED".equalsIgnoreCase(uploadStatus)) {

                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Retry is supported for FAILED processing or "
                                + "LOAD_FAILED loading. Current upload status: "
                                + uploadStatus
                );
            }

            retryCount++;
        } else {

            if (!"RECEIVED".equalsIgnoreCase(uploadStatus)
                    && !"COMPLETED".equalsIgnoreCase(uploadStatus)
                    && !"LOAD_FAILED".equalsIgnoreCase(uploadStatus)) {

                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Upload cannot start a pipeline. Current status: "
                                + uploadStatus
                );
            }
        }

        String pipelineRunId = UUID.randomUUID().toString();

        jdbcTemplate.update("""
            INSERT INTO SRIRAM_CAP39_DB.SRIRAM_APP.SRIRAM_PIPELINE_RUNS
            (
                PIPELINE_RUN_ID,
                UPLOAD_ID,
                PIPELINE_STATUS,
                CURRENT_STEP,
                PROCESSING_STATUS,
                LOADING_STATUS,
                STARTED_AT,
                RETRY_COUNT,
                ERROR_MESSAGE,
                CREATED_AT,
                UPDATED_AT
            )
            VALUES (
                ?, ?, 'RUNNING', 'QUEUED',
                'PENDING', 'PENDING',
                CURRENT_TIMESTAMP(), ?, NULL,
                CURRENT_TIMESTAMP(), CURRENT_TIMESTAMP()
            )
            """,
                pipelineRunId,
                uploadId,
                retryCount
        );

        try {
            pipelineWorker.execute(pipelineRunId, uploadId);

        } catch (Exception exception) {

            pipelineStatusService.fail(
                    pipelineRunId,
                    "SUBMISSION",
                    "PENDING",
                    "PENDING",
                    exception.getMessage()
            );

            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not submit pipeline to background worker.",
                    exception
            );
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("pipelineRunId", pipelineRunId);
        response.put("uploadId", uploadId);
        response.put("pipelineStatus", "RUNNING");
        response.put("currentStep", "QUEUED");
        response.put("retryCount", retryCount);
        response.put("message", "Pipeline submitted successfully.");

        return response;
    }

    private Map<String, Object> getUpload(String uploadId) {

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            SELECT *
            FROM SRIRAM_CAP39_DB.SRIRAM_APP.SRIRAM_FILE_UPLOADS
            WHERE UPLOAD_ID = ?
            """, uploadId);

        if (rows.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Upload not found: " + uploadId
            );
        }

        return rows.get(0);
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