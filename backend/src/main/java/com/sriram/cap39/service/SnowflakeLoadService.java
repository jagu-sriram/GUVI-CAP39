
package com.sriram.cap39.service;

import com.opencsv.CSVReader;
import com.sriram.cap39.config.DatasetConfig;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class SnowflakeLoadService {

    private static final int BATCH_SIZE = 500;

    private final JdbcTemplate jdbcTemplate;
    private final LoadAuditService auditService;
    private final S3StorageService s3StorageService;
    private final TransactionTemplate transactionTemplate;

    public SnowflakeLoadService(
            JdbcTemplate jdbcTemplate,
            LoadAuditService auditService,
            S3StorageService s3StorageService,
            TransactionTemplate transactionTemplate) {

        this.jdbcTemplate = jdbcTemplate;
        this.auditService = auditService;
        this.s3StorageService = s3StorageService;
        this.transactionTemplate = transactionTemplate;
    }

    public Map<String, Object> load(
            String uploadId,
            String dataset,
            String fileName,
            String acceptedS3Key,
            int totalRecords,
            int acceptedRecords,
            int rejectedRecords,
            int duplicateRecords) throws Exception {

        DatasetConfig config = DatasetConfig.get(dataset);

        if (acceptedS3Key == null || acceptedS3Key.isBlank()) {
            throw new IllegalArgumentException(
                    "ACCEPTED_S3_KEY is missing for upload " + uploadId
            );
        }

        // Claim the upload so two requests cannot load it concurrently.
        int claimed = jdbcTemplate.update("""
            UPDATE SRIRAM_CAP39_DB.SRIRAM_APP.SRIRAM_FILE_UPLOADS
            SET UPLOAD_STATUS = 'LOADING',
                ERROR_MESSAGE = NULL
            WHERE UPLOAD_ID = ?
              AND UPLOAD_STATUS IN ('COMPLETED', 'LOAD_FAILED')
            """, uploadId);

        if (claimed != 1) {
            throw new IllegalStateException(
                    "Upload cannot be loaded. It may already be loading, " +
                            "or its current status is not COMPLETED/LOAD_FAILED."
            );
        }

        String runId = null;
        Path temporaryDirectory = null;

        try {
            runId = auditService.startRun(
                    config.rawTable(), fileName
            );

            // Download the accepted file from S3.
            temporaryDirectory = Files.createTempDirectory(
                    "cap39-load-" + uploadId + "-"
            );

            Path acceptedFile = temporaryDirectory.resolve("accepted.csv");

            s3StorageService.downloadFile(
                    acceptedS3Key,
                    acceptedFile
            );

            if (!Files.isRegularFile(acceptedFile)) {
                throw new IllegalStateException(
                        "Accepted CSV was not downloaded from S3."
                );
            }

            int actualAcceptedCount = countRows(acceptedFile);

            if (actualAcceptedCount != acceptedRecords) {
                throw new IllegalStateException(
                        "Accepted record count mismatch. Snowflake metadata: "
                                + acceptedRecords + ", downloaded CSV: "
                                + actualAcceptedCount
                );
            }

            final String finalRunId = runId;

            // All staging, merge, checkpoint and upload-status changes
            // execute within one Snowflake transaction.
            Integer rowsLoaded = transactionTemplate.execute(status -> {
                try {
                    int stagedRows = insertIntoStaging(
                            config,
                            acceptedFile,
                            uploadId,
                            finalRunId
                    );

                    if (stagedRows != actualAcceptedCount) {
                        throw new IllegalStateException(
                                "Staging count mismatch. Expected "
                                        + actualAcceptedCount + ", inserted "
                                        + stagedRows
                        );
                    }

                    int mergedRows = mergeIntoRaw(
                            config,
                            uploadId,
                            finalRunId
                    );

                    markStagingRowsMerged(
                            config,
                            uploadId,
                            finalRunId
                    );

                    updateCheckpoint(
                            config.rawTable(),
                            fileName,
                            uploadId,
                            finalRunId,
                            mergedRows
                    );

                    int updated = jdbcTemplate.update("""
                        UPDATE SRIRAM_CAP39_DB.SRIRAM_APP.SRIRAM_FILE_UPLOADS
                        SET TOTAL_RECORDS = ?,
                            ACCEPTED_RECORDS = ?,
                            REJECTED_RECORDS = ?,
                            DUPLICATE_RECORDS = ?,
                            UPLOAD_STATUS = 'LOADED',
                            ERROR_MESSAGE = NULL
                        WHERE UPLOAD_ID = ?
                          AND UPLOAD_STATUS = 'LOADING'
                        """,
                            totalRecords,
                            acceptedRecords,
                            rejectedRecords,
                            duplicateRecords,
                            uploadId
                    );

                    if (updated != 1) {
                        throw new IllegalStateException(
                                "Could not mark upload as LOADED."
                        );
                    }

                    // Audit success participates in the same transaction.
                    auditService.completeRun(
                            finalRunId,
                            totalRecords,
                            mergedRows,
                            rejectedRecords
                    );

                    return mergedRows;

                } catch (Exception exception) {
                    throw new IllegalStateException(
                            "Snowflake staging/loading transaction failed: "
                                    + exception.getMessage(),
                            exception
                    );
                }
            });

            if (rowsLoaded == null) {
                throw new IllegalStateException(
                        "Snowflake transaction returned no result."
                );
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("uploadId", uploadId);
            result.put("runId", runId);
            result.put("dataset", dataset);
            result.put("totalRecords", totalRecords);
            result.put("acceptedRecords", acceptedRecords);
            result.put("rejectedRecords", rejectedRecords);
            result.put("duplicateRecords", duplicateRecords);
            result.put("stagedRecords", actualAcceptedCount);
            result.put("rowsInsertedIntoRaw", rowsLoaded);
            result.put("stagingTable", config.stagingTable());
            result.put("rawTable", config.rawTable());
            result.put("status", "LOADED");

            return result;

        } catch (Exception exception) {

            String message = exception.getMessage() == null
                    ? "Unknown Snowflake loading error."
                    : exception.getMessage();

            if (message.length() > 1900) {
                message = message.substring(0, 1900);
            }

            // The loading transaction has rolled back before this section.
            // Record the failure separately so the audit survives rollback.
            if (runId != null) {
                try {
                    auditService.failRun(runId, message);
                } catch (Exception auditException) {
                    exception.addSuppressed(auditException);
                }
            }

            try {
                jdbcTemplate.update("""
                    UPDATE SRIRAM_CAP39_DB.SRIRAM_APP.SRIRAM_FILE_UPLOADS
                    SET UPLOAD_STATUS = 'LOAD_FAILED',
                        ERROR_MESSAGE = ?
                    WHERE UPLOAD_ID = ?
                      AND UPLOAD_STATUS = 'LOADING'
                    """, message, uploadId);
            } catch (Exception updateException) {
                exception.addSuppressed(updateException);
            }

            throw exception;

        } finally {
            if (temporaryDirectory != null) {
                deleteDirectory(temporaryDirectory);
            }
        }
    }

    private int insertIntoStaging(
            DatasetConfig config,
            Path csvFile,
            String uploadId,
            String runId) throws Exception {

        String businessColumns = config.columns().stream()
                .map(column -> "\"" + column.toUpperCase() + "\"")
                .collect(Collectors.joining(", "));

        String metadataColumns = """
            UPLOAD_ID, RUN_ID, STAGED_AT, STAGE_STATUS, ERROR_MESSAGE
            """;

        String placeholders = String.join(
                ", ",
                java.util.Collections.nCopies(
                        config.columns().size() + 2, "?"
                )
        );

        String insertSql = """
            INSERT INTO %s (%s, %s)
            VALUES (%s, CURRENT_TIMESTAMP(), 'STAGED', NULL)
            """.formatted(
                config.stagingTable(),
                businessColumns,
                metadataColumns,
                placeholders
        );

        int inserted = 0;

        try (
                Reader reader = Files.newBufferedReader(csvFile);
                CSVReader csv = new CSVReader(reader)
        ) {
            String[] header = csv.readNext();

            if (header == null) {
                throw new IllegalArgumentException(
                        "Accepted CSV has no header."
                );
            }

            if (header.length > 0) {
                header[0] = header[0].replace("\uFEFF", "");
            }

            String[] expected = config.columns().toArray(new String[0]);

            if (!Arrays.equals(header, expected)) {
                throw new IllegalArgumentException(
                        "CSV headers do not match the dataset schema. Expected: "
                                + String.join(",", expected)
                );
            }

            List<Object[]> batch = new ArrayList<>(BATCH_SIZE);

            String[] row;

            while ((row = csv.readNext()) != null) {

                if (row.length != expected.length) {
                    throw new IllegalArgumentException(
                            "Invalid CSV row. Expected "
                                    + expected.length + " columns but found "
                                    + row.length
                    );
                }

                Object[] arguments = new Object[expected.length + 2];

                for (int i = 0; i < row.length; i++) {
                    arguments[i] =
                            row[i] == null || row[i].isBlank()
                                    ? null
                                    : row[i];
                }

                arguments[expected.length] = uploadId;
                arguments[expected.length + 1] = runId;

                batch.add(arguments);

                if (batch.size() >= BATCH_SIZE) {
                    jdbcTemplate.batchUpdate(insertSql, batch);
                    inserted += batch.size();
                    batch.clear();
                }
            }

            if (!batch.isEmpty()) {
                jdbcTemplate.batchUpdate(insertSql, batch);
                inserted += batch.size();
            }
        }

        return inserted;
    }

    private int mergeIntoRaw(
            DatasetConfig config,
            String uploadId,
            String runId) {

        String columns = config.columns().stream()
                .map(column -> "\"" + column.toUpperCase() + "\"")
                .collect(Collectors.joining(", "));

        String primaryKey = "\"" +
                config.primaryKey().toUpperCase() + "\"";

        String sourceColumns = config.columns().stream()
                .map(column -> "source.\"" + column.toUpperCase() + "\"")
                .collect(Collectors.joining(", "));

        String mergeSql = """
            MERGE INTO %s target
            USING (
                SELECT %s
                FROM %s
                WHERE UPLOAD_ID = ?
                  AND RUN_ID = ?
                  AND STAGE_STATUS = 'STAGED'
                  AND %s IS NOT NULL
                QUALIFY ROW_NUMBER() OVER (
                    PARTITION BY %s
                    ORDER BY STAGED_AT DESC, RUN_ID DESC
                ) = 1
            ) source
            ON target.%s = source.%s
            WHEN NOT MATCHED THEN
                INSERT (%s)
                VALUES (%s)
            """.formatted(
                config.rawTable(),
                columns,
                config.stagingTable(),
                primaryKey,
                primaryKey,
                primaryKey,
                primaryKey,
                columns,
                sourceColumns
        );

        return jdbcTemplate.update(
                mergeSql,
                uploadId,
                runId
        );
    }

    private void markStagingRowsMerged(
            DatasetConfig config,
            String uploadId,
            String runId) {

        jdbcTemplate.update("""
            UPDATE %s
            SET STAGE_STATUS = 'MERGED'
            WHERE UPLOAD_ID = ?
              AND RUN_ID = ?
              AND STAGE_STATUS = 'STAGED'
            """.formatted(config.stagingTable()),
                uploadId,
                runId
        );
    }

    private void updateCheckpoint(
            String rawTable,
            String fileName,
            String uploadId,
            String runId,
            int rowsLoaded) {

        String shortTableName = rawTable.substring(
                rawTable.lastIndexOf('.') + 1
        );

        jdbcTemplate.update("""
            MERGE INTO SRIRAM_CAP39_DB.SRIRAM_STAGING.SRIRAM_LOAD_CHECKPOINTS target
            USING (
                SELECT ? AS TABLE_NAME,
                       ? AS FILE_NAME,
                       ? AS BATCH_ID,
                       ? AS RUN_ID,
                       ? AS ROWS_LOADED
            ) source
            ON target.TABLE_NAME = source.TABLE_NAME

            WHEN MATCHED THEN UPDATE SET
                LAST_LOADED_AT = CURRENT_TIMESTAMP(),
                LAST_FILE_NAME = source.FILE_NAME,
                LAST_BATCH_ID = source.BATCH_ID,
                LAST_SUCCESSFUL_RUN = source.RUN_ID,
                ROWS_LOADED = source.ROWS_LOADED,
                LOAD_STATUS = 'SUCCESS',
                UPDATED_AT = CURRENT_TIMESTAMP()

            WHEN NOT MATCHED THEN INSERT (
                TABLE_NAME,
                LAST_LOADED_AT,
                LAST_FILE_NAME,
                LAST_BATCH_ID,
                LAST_SUCCESSFUL_RUN,
                ROWS_LOADED,
                LOAD_STATUS,
                UPDATED_AT
            )
            VALUES (
                source.TABLE_NAME,
                CURRENT_TIMESTAMP(),
                source.FILE_NAME,
                source.BATCH_ID,
                source.RUN_ID,
                source.ROWS_LOADED,
                'SUCCESS',
                CURRENT_TIMESTAMP()
            )
            """,
                shortTableName,
                fileName,
                uploadId,
                runId,
                rowsLoaded
        );
    }

    private int countRows(Path file) throws Exception {

        try (
                Reader reader = Files.newBufferedReader(file);
                CSVReader csv = new CSVReader(reader)
        ) {
            if (csv.readNext() == null) {
                return 0;
            }

            int count = 0;

            while (csv.readNext() != null) {
                count++;
            }

            return count;
        }
    }

    private void deleteDirectory(Path directory) {

        try (var paths = Files.walk(directory)) {

            paths.sorted((a, b) -> b.compareTo(a))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (Exception ignored) {
                            // Cleanup must not mask the loading result.
                        }
                    });

        } catch (Exception ignored) {
            // Cleanup must not mask the loading result.
        }
    }
}
