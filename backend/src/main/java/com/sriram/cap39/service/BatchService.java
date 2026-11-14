
package com.sriram.cap39.service;

import com.sriram.cap39.dto.BatchRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.http.HttpStatus.*;

@Service
public class BatchService {

    private final JdbcTemplate jdbcTemplate;

    public BatchService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // 1. Register a new batch
    public Map<String, Object> registerBatch(BatchRequest batch) {

        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM SRIRAM_CAP39_DB.SRIRAM_APP.SRIRAM_BATCH_REGISTRATIONS
                WHERE BATCH_ID = ?
                """,
                Integer.class,
                batch.batchId()
        );

        if (count != null && count > 0) {
            throw new ResponseStatusException(
                    CONFLICT, "Batch ID already exists"
            );
        }

        String registrationId = UUID.randomUUID().toString();

        String sql = """
            INSERT INTO SRIRAM_CAP39_DB.SRIRAM_APP.SRIRAM_BATCH_REGISTRATIONS
            (
                REGISTRATION_ID, BATCH_ID, PRODUCT_ID,
                PRODUCTION_LINE, SHIFT, PLANNED_QUANTITY,
                PRODUCED_QUANTITY, PRODUCTION_DATE, STATUS,
                REGISTERED_BY, PROCESSING_STATUS
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING')
            """;

        jdbcTemplate.update(
                sql,
                registrationId,
                batch.batchId(),
                batch.productId(),
                batch.productionLine(),
                batch.shift(),
                batch.plannedQuantity(),
                batch.producedQuantity(),
                Date.valueOf(batch.productionDate()),
                batch.status(),
                batch.registeredBy()
        );

        return Map.of(
                "message", "Batch registered successfully",
                "registrationId", registrationId,
                "batchId", batch.batchId(),
                "processingStatus", "PENDING"
        );
    }

    // 2. Get all batches
    public List<Map<String, Object>> getAllBatches() {

        return jdbcTemplate.queryForList("""
            SELECT *
            FROM SRIRAM_CAP39_DB.SRIRAM_APP.SRIRAM_BATCH_REGISTRATIONS
            ORDER BY REGISTERED_AT DESC
            """);
    }

    // 3. Get a batch by ID
    public Map<String, Object> getBatchById(String batchId) {

        List<Map<String, Object>> results =
                jdbcTemplate.queryForList("""
                SELECT *
                FROM SRIRAM_CAP39_DB.SRIRAM_APP.SRIRAM_BATCH_REGISTRATIONS
                WHERE BATCH_ID = ?
                """, batchId);

        if (results.isEmpty()) {
            throw new ResponseStatusException(
                    NOT_FOUND, "Batch not found: " + batchId
            );
        }

        return results.get(0);
    }

    // 4. Update a pending batch
    public Map<String, Object> updateBatch(
            String batchId, BatchRequest batch) {

        ensurePending(batchId);

        String sql = """
            UPDATE SRIRAM_CAP39_DB.SRIRAM_APP.SRIRAM_BATCH_REGISTRATIONS
            SET PRODUCT_ID = ?,
                PRODUCTION_LINE = ?,
                SHIFT = ?,
                PLANNED_QUANTITY = ?,
                PRODUCED_QUANTITY = ?,
                PRODUCTION_DATE = ?,
                STATUS = ?,
                REGISTERED_BY = ?
            WHERE BATCH_ID = ?
              AND PROCESSING_STATUS = 'PENDING'
            """;

        jdbcTemplate.update(
                sql,
                batch.productId(),
                batch.productionLine(),
                batch.shift(),
                batch.plannedQuantity(),
                batch.producedQuantity(),
                Date.valueOf(batch.productionDate()),
                batch.status(),
                batch.registeredBy(),
                batchId
        );

        return Map.of(
                "message", "Batch updated successfully",
                "batchId", batchId
        );
    }

    // 5. Delete a pending batch
    public Map<String, Object> deleteBatch(String batchId) {

        ensurePending(batchId);

        jdbcTemplate.update("""
            DELETE FROM SRIRAM_CAP39_DB.SRIRAM_APP.SRIRAM_BATCH_REGISTRATIONS
            WHERE BATCH_ID = ?
              AND PROCESSING_STATUS = 'PENDING'
            """, batchId);

        return Map.of(
                "message", "Pending batch deleted successfully",
                "batchId", batchId
        );
    }

    // Common check for update and delete
    private void ensurePending(String batchId) {

        List<Map<String, Object>> results =
                jdbcTemplate.queryForList("""
                SELECT PROCESSING_STATUS
                FROM SRIRAM_CAP39_DB.SRIRAM_APP.SRIRAM_BATCH_REGISTRATIONS
                WHERE BATCH_ID = ?
                """, batchId);

        if (results.isEmpty()) {
            throw new ResponseStatusException(
                    NOT_FOUND, "Batch not found: " + batchId
            );
        }

        String status = String.valueOf(
                results.get(0).get("PROCESSING_STATUS")
        );

        if (!"PENDING".equalsIgnoreCase(status)) {
            throw new ResponseStatusException(
                    CONFLICT,
                    "Only pending batches can be updated or deleted"
            );
        }
    }
}
