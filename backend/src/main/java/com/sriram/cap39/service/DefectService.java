
package com.sriram.cap39.service;

import com.sriram.cap39.dto.DefectRequest;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.http.HttpStatus.*;

@Service
public class DefectService {

    private final JdbcTemplate jdbcTemplate;

    public DefectService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final String TABLE =
            "SRIRAM_CAP39_DB.SRIRAM_APP.SRIRAM_DEFECT_REGISTRATIONS";

    // 1. Register defect
    public Map<String, Object> registerDefect(DefectRequest defect) {

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + TABLE + " WHERE DEFECT_ID = ?",
                Integer.class,
                defect.defectId()
        );

        if (count != null && count > 0) {
            throw new ResponseStatusException(
                    CONFLICT, "Defect ID already exists"
            );
        }

        if (defect.affectedQuantity()
                .compareTo(defect.inspectedQuantity()) > 0) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Affected quantity cannot exceed inspected quantity"
            );
        }

        String registrationId = UUID.randomUUID().toString();

        String sql = """
            INSERT INTO SRIRAM_CAP39_DB.SRIRAM_APP.SRIRAM_DEFECT_REGISTRATIONS
            (
                REGISTRATION_ID,
                DEFECT_ID,
                BATCH_ID,
                INSPECTION_ID,
                DEFECT_CATEGORY,
                DEFECT_CODE,
                AFFECTED_QUANTITY,
                INSPECTED_QUANTITY,
                DETECTED_AT,
                SEVERITY,
                REGISTERED_BY,
                PROCESSING_STATUS
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING')
            """;

        jdbcTemplate.update(
                sql,
                registrationId,
                defect.defectId(),
                defect.batchId(),
                defect.inspectionId(),
                defect.defectCategory(),
                defect.defectCode(),
                defect.affectedQuantity(),
                defect.inspectedQuantity(),
                Timestamp.valueOf(defect.detectedAt()),
                defect.severity(),
                defect.registeredBy()
        );

        return Map.of(
                "message", "Defect registered successfully",
                "registrationId", registrationId,
                "defectId", defect.defectId(),
                "batchId", defect.batchId(),
                "processingStatus", "PENDING"
        );
    }

    // 2. Get all defects
    public List<Map<String, Object>> getAllDefects() {

        return jdbcTemplate.queryForList("""
            SELECT *
            FROM SRIRAM_CAP39_DB.SRIRAM_APP.SRIRAM_DEFECT_REGISTRATIONS
            ORDER BY REGISTERED_AT DESC
            """);
    }

    // 3. Get defect by ID
    public Map<String, Object> getDefectById(String defectId) {

        List<Map<String, Object>> results =
                jdbcTemplate.queryForList("""
                SELECT *
                FROM SRIRAM_CAP39_DB.SRIRAM_APP.SRIRAM_DEFECT_REGISTRATIONS
                WHERE DEFECT_ID = ?
                """, defectId);

        if (results.isEmpty()) {
            throw new ResponseStatusException(
                    NOT_FOUND, "Defect not found: " + defectId
            );
        }

        return results.get(0);
    }

    // 4. Get defects belonging to a batch
    public List<Map<String, Object>> getDefectsByBatch(
            String batchId) {

        return jdbcTemplate.queryForList("""
            SELECT *
            FROM SRIRAM_CAP39_DB.SRIRAM_APP.SRIRAM_DEFECT_REGISTRATIONS
            WHERE BATCH_ID = ?
            ORDER BY DETECTED_AT DESC
            """, batchId);
    }

    // 5. Update pending defect
    public Map<String, Object> updateDefect(
            String defectId, DefectRequest defect) {

        ensurePending(defectId);

        if (!defectId.equals(defect.defectId())) {
            throw new ResponseStatusException(
                    BAD_REQUEST, "Defect ID in URL and body must match"
            );
        }

        if (defect.affectedQuantity()
                .compareTo(defect.inspectedQuantity()) > 0) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Affected quantity cannot exceed inspected quantity"
            );
        }

        String sql = """
            UPDATE SRIRAM_CAP39_DB.SRIRAM_APP.SRIRAM_DEFECT_REGISTRATIONS
            SET BATCH_ID = ?,
                INSPECTION_ID = ?,
                DEFECT_CATEGORY = ?,
                DEFECT_CODE = ?,
                AFFECTED_QUANTITY = ?,
                INSPECTED_QUANTITY = ?,
                DETECTED_AT = ?,
                SEVERITY = ?,
                REGISTERED_BY = ?
            WHERE DEFECT_ID = ?
              AND PROCESSING_STATUS = 'PENDING'
            """;

        jdbcTemplate.update(
                sql,
                defect.batchId(),
                defect.inspectionId(),
                defect.defectCategory(),
                defect.defectCode(),
                defect.affectedQuantity(),
                defect.inspectedQuantity(),
                Timestamp.valueOf(defect.detectedAt()),
                defect.severity(),
                defect.registeredBy(),
                defectId
        );

        return Map.of(
                "message", "Defect updated successfully",
                "defectId", defectId
        );
    }

    // 6. Delete pending defect
    public Map<String, Object> deleteDefect(String defectId) {

        ensurePending(defectId);

        jdbcTemplate.update("""
            DELETE FROM SRIRAM_CAP39_DB.SRIRAM_APP.SRIRAM_DEFECT_REGISTRATIONS
            WHERE DEFECT_ID = ?
              AND PROCESSING_STATUS = 'PENDING'
            """, defectId);

        return Map.of(
                "message", "Pending defect deleted successfully",
                "defectId", defectId
        );
    }

    // Common pending-status validation
    private void ensurePending(String defectId) {

        List<Map<String, Object>> results =
                jdbcTemplate.queryForList("""
                SELECT PROCESSING_STATUS
                FROM SRIRAM_CAP39_DB.SRIRAM_APP.SRIRAM_DEFECT_REGISTRATIONS
                WHERE DEFECT_ID = ?
                """, defectId);

        if (results.isEmpty()) {
            throw new ResponseStatusException(
                    NOT_FOUND, "Defect not found: " + defectId
            );
        }

        String status = String.valueOf(
                results.get(0).get("PROCESSING_STATUS")
        );

        if (!"PENDING".equalsIgnoreCase(status)) {
            throw new ResponseStatusException(
                    CONFLICT,
                    "Only pending defects can be updated or deleted"
            );
        }
    }
}
