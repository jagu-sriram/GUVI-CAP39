package com.sriram.cap39.service;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PipelineStatusService {

    private static final String PIPELINE_TABLE =
            "SRIRAM_CAP39_DB.SRIRAM_APP.SRIRAM_PIPELINE_RUNS";

    private final JdbcTemplate jdbcTemplate;

    public PipelineStatusService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> getLatestStatus(String uploadId) {

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            SELECT
                PIPELINE_RUN_ID,
                UPLOAD_ID,
                PIPELINE_STATUS,
                CURRENT_STEP,
                PROCESSING_STATUS,
                LOADING_STATUS,
                STARTED_AT,
                COMPLETED_AT,
                RETRY_COUNT,
                ERROR_MESSAGE,
                CREATED_AT,
                UPDATED_AT
            FROM SRIRAM_CAP39_DB.SRIRAM_APP.SRIRAM_PIPELINE_RUNS
            WHERE UPLOAD_ID = ?
            ORDER BY CREATED_AT DESC
            LIMIT 1
            """, uploadId);

        if (rows.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "No pipeline run found for upload: " + uploadId
            );
        }

        return new LinkedHashMap<>(rows.get(0));
    }

    public void updateStep(
            String pipelineRunId,
            String pipelineStatus,
            String currentStep,
            String processingStatus,
            String loadingStatus) {

        jdbcTemplate.update("""
            UPDATE SRIRAM_CAP39_DB.SRIRAM_APP.SRIRAM_PIPELINE_RUNS
            SET PIPELINE_STATUS = ?,
                CURRENT_STEP = ?,
                PROCESSING_STATUS = ?,
                LOADING_STATUS = ?,
                UPDATED_AT = CURRENT_TIMESTAMP()
            WHERE PIPELINE_RUN_ID = ?
            """,
                pipelineStatus,
                currentStep,
                processingStatus,
                loadingStatus,
                pipelineRunId
        );
    }

    public void complete(String pipelineRunId) {

        jdbcTemplate.update("""
            UPDATE SRIRAM_CAP39_DB.SRIRAM_APP.SRIRAM_PIPELINE_RUNS
            SET PIPELINE_STATUS = 'COMPLETED',
                CURRENT_STEP = 'COMPLETED',
                PROCESSING_STATUS = 'COMPLETED',
                LOADING_STATUS = 'COMPLETED',
                COMPLETED_AT = CURRENT_TIMESTAMP(),
                UPDATED_AT = CURRENT_TIMESTAMP(),
                ERROR_MESSAGE = NULL
            WHERE PIPELINE_RUN_ID = ?
            """, pipelineRunId);
    }

    public void fail(
            String pipelineRunId,
            String currentStep,
            String processingStatus,
            String loadingStatus,
            String errorMessage) {

        String message = errorMessage == null
                ? "Unknown pipeline error."
                : errorMessage;

        if (message.length() > 1900) {
            message = message.substring(0, 1900);
        }

        jdbcTemplate.update("""
            UPDATE SRIRAM_CAP39_DB.SRIRAM_APP.SRIRAM_PIPELINE_RUNS
            SET PIPELINE_STATUS = 'FAILED',
                CURRENT_STEP = ?,
                PROCESSING_STATUS = ?,
                LOADING_STATUS = ?,
                COMPLETED_AT = CURRENT_TIMESTAMP(),
                UPDATED_AT = CURRENT_TIMESTAMP(),
                ERROR_MESSAGE = ?
            WHERE PIPELINE_RUN_ID = ?
            """,
                currentStep,
                processingStatus,
                loadingStatus,
                message,
                pipelineRunId
        );
    }
}