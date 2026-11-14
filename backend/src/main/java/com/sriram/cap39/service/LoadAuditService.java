
package com.sriram.cap39.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

@Service
public class LoadAuditService {

    private final JdbcTemplate jdbcTemplate;

    public LoadAuditService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public String startRun(String tableName, String fileName) {
        String runId = UUID.randomUUID().toString();

        jdbcTemplate.update("""
            INSERT INTO SRIRAM_CAP39_DB.SRIRAM_STAGING.SRIRAM_LOAD_AUDIT
            (
                RUN_ID, TABLE_NAME, FILE_NAME,
                STARTED_AT, ROWS_READ, ROWS_LOADED,
                ROWS_REJECTED, LOAD_STATUS, ERROR_MESSAGE
            )
            VALUES (?, ?, ?, ?, 0, 0, 0, 'PROCESSING', NULL)
            """,
                runId,
                tableName,
                fileName,
                Timestamp.from(Instant.now())
        );

        return runId;
    }

    public void completeRun(
            String runId,
            int rowsRead,
            int rowsLoaded,
            int rowsRejected) {

        jdbcTemplate.update("""
            UPDATE SRIRAM_CAP39_DB.SRIRAM_STAGING.SRIRAM_LOAD_AUDIT
            SET COMPLETED_AT = ?,
                ROWS_READ = ?,
                ROWS_LOADED = ?,
                ROWS_REJECTED = ?,
                LOAD_STATUS = 'SUCCESS',
                ERROR_MESSAGE = NULL
            WHERE RUN_ID = ?
            """,
                Timestamp.from(Instant.now()),
                rowsRead,
                rowsLoaded,
                rowsRejected,
                runId
        );
    }

    public void failRun(String runId, String error) {
        jdbcTemplate.update("""
            UPDATE SRIRAM_CAP39_DB.SRIRAM_STAGING.SRIRAM_LOAD_AUDIT
            SET COMPLETED_AT = ?,
                LOAD_STATUS = 'FAILED',
                ERROR_MESSAGE = ?
            WHERE RUN_ID = ?
            """,
                Timestamp.from(Instant.now()),
                error == null ? "Unknown loading error" :
                        error.substring(0, Math.min(error.length(), 5000)),
                runId
        );
    }
}
