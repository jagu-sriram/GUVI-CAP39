package com.sriram.cap39.service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class ProcessExecutionService {

    private final JdbcTemplate jdbcTemplate;
    private final JsonMapper jsonMapper;
    private final S3StorageService s3StorageService;

    @Value("${cap39.python-executable}")
    private String pythonExecutable;

    @Value("${cap39.python-project-dir}")
    private String pythonProjectDir;

    @Value("${cap39.python-script}")
    private String pythonScript;

    @Value("${cap39.process-timeout-seconds:600}")
    private long timeoutSeconds;

    @Value("${cap39.etl-results-dir}")
    private String etlResultsDir;

    public ProcessExecutionService(
            JdbcTemplate jdbcTemplate,
            JsonMapper jsonMapper,
            S3StorageService s3StorageService) {
        this.jdbcTemplate = jdbcTemplate;
        this.jsonMapper = jsonMapper;
        this.s3StorageService = s3StorageService;
    }

    public Map<String, Object> processUpload(String uploadId) {

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
            SELECT RAW_S3_KEY, DATASET_NAME, UPLOAD_STATUS
            FROM SRIRAM_CAP39_DB.SRIRAM_APP.SRIRAM_FILE_UPLOADS
            WHERE UPLOAD_ID = ?
            """, uploadId);

        if (rows.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Upload not found."
            );
        }

        Map<String, Object> upload = rows.get(0);

        String rawS3Key = (String) upload.get("RAW_S3_KEY");
        String dataset = String.valueOf(upload.get("DATASET_NAME"));
        String currentStatus = String.valueOf(upload.get("UPLOAD_STATUS"));

        if (rawS3Key == null || rawS3Key.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This upload does not have a RAW_S3_KEY. Re-upload the CSV using the S3-enabled upload endpoint."
            );
        }

        if (!"RECEIVED".equalsIgnoreCase(currentStatus)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Upload cannot be processed. Current status: " + currentStatus
            );
        }

        int claimed = jdbcTemplate.update("""
            UPDATE SRIRAM_CAP39_DB.SRIRAM_APP.SRIRAM_FILE_UPLOADS
            SET UPLOAD_STATUS = 'PROCESSING',
                ERROR_MESSAGE = NULL
            WHERE UPLOAD_ID = ?
              AND UPLOAD_STATUS = 'RECEIVED'
            """, uploadId);

        if (claimed != 1) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Upload is already being processed or has changed status."
            );
        }

        Path projectPath = Path.of(pythonProjectDir)
                .toAbsolutePath().normalize();
        Path scriptPath = Path.of(pythonScript)
                .toAbsolutePath().normalize();
        Path resultsBase = Path.of(etlResultsDir)
                .toAbsolutePath().normalize();

        Path uploadRunDir = resultsBase.resolve(uploadId);
        Path resultsDir = uploadRunDir.resolve("results");

// Keep Java logs outside the Python ETL workspace.
        Path logPath = Path.of(
                System.getProperty("java.io.tmpdir"),
                "cap39-" + uploadId + "-spring_process.log"
        );

        Path temporaryDirectory = null;

        try {
            if (!Files.isRegularFile(scriptPath)) {
                throw new IllegalStateException(
                        "Python processing script not found: " + scriptPath
                );
            }

            Files.createDirectories(uploadRunDir);

// Ensure the process log exists before redirecting Python output.
            Files.deleteIfExists(logPath);
            Files.createFile(logPath);

            temporaryDirectory = Files.createTempDirectory(
                    "cap39-" + uploadId + "-"
            );

            Path downloadedCsv = temporaryDirectory.resolve("original.csv");

            // Download the original CSV from S3.
            s3StorageService.downloadFile(rawS3Key, downloadedCsv);

            if (!Files.isRegularFile(downloadedCsv)) {
                throw new IllegalStateException(
                        "Original CSV could not be downloaded from S3."
                );
            }

            System.out.println("========== CAP39 PYTHON CONFIG ==========");
            System.out.println("Python executable: " + pythonExecutable);
            System.out.println("Python project directory: " + projectPath);
            System.out.println("Python script: " + scriptPath);
            System.out.println("Python executable exists: "
                    + Files.isExecutable(Path.of(pythonExecutable)));
            System.out.println("==========================================");

            ProcessBuilder builder = new ProcessBuilder(
                    pythonExecutable,
                    scriptPath.toString(),
                    "--file", downloadedCsv.toString(),
                    "--dataset", dataset,
                    "--upload-id", uploadId
            );

            builder.directory(new File(projectPath.toString()));

// Remove environment overrides that can interfere with venv Python.
            builder.environment().remove("PYTHONHOME");
            builder.environment().remove("PYTHONPATH");

            builder.redirectErrorStream(true);
            builder.redirectOutput(
                    ProcessBuilder.Redirect.to(logPath.toFile())
            );

            System.out.println("PYTHONHOME: "
                    + builder.environment().get("PYTHONHOME"));
            System.out.println("PYTHONPATH: "
                    + builder.environment().get("PYTHONPATH"));

            // First verify that this exact Python executable can import Pandas.
            ProcessBuilder testBuilder = new ProcessBuilder(
                    pythonExecutable,
                    "-c",
                    "import sys, pandas; print('PYTHON:', sys.executable); print('PREFIX:', sys.prefix); print('PANDAS:', pandas.__version__)"
            );

            testBuilder.directory(new File(projectPath.toString()));
            testBuilder.environment().remove("PYTHONHOME");
            testBuilder.environment().remove("PYTHONPATH");
            testBuilder.redirectErrorStream(true);

            Process testProcess = testBuilder.start();

            StringBuilder testOutput = new StringBuilder();

            try (var reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(testProcess.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    testOutput.append(line).append(System.lineSeparator());
                    System.out.println("[PYTHON PREFLIGHT] " + line);
                }
            }

            int testExitCode = testProcess.waitFor();

            if (testExitCode != 0) {
                throw new IllegalStateException(
                        "Python preflight failed. Output: " + testOutput
                );
            }

            System.out.println("Python preflight passed.");

// Now execute the actual ETL.
            Process process = builder.start();

            boolean finished = process.waitFor(
                    timeoutSeconds, TimeUnit.SECONDS
            );

            if (!finished) {
                process.destroyForcibly();
                process.waitFor();
                throw new IllegalStateException(
                        "Python ETL timed out after "
                                + timeoutSeconds + " seconds."
                );
            }

            if (process.exitValue() != 0) {
                throw new IllegalStateException(
                        "Python ETL exited with code "
                                + process.exitValue()
                );
            }

            // Confirm all required ETL outputs exist.
            Path acceptedFile = resultsDir.resolve("accepted.csv");
            Path rejectedFile = resultsDir.resolve("rejected.csv");
            Path duplicatesFile = resultsDir.resolve("duplicates.csv");
            Path summaryFile = resultsDir.resolve("summary.json");

            requireFile(acceptedFile);
            requireFile(rejectedFile);
            requireFile(duplicatesFile);
            requireFile(summaryFile);

            Map<String, Object> summary = jsonMapper.readValue(
                    summaryFile.toFile(),
                    new TypeReference<Map<String, Object>>() {}
            );

            int total = getCount(summary, "input_records");
            int accepted = getCount(summary, "accepted_records");
            int rejected = getCount(summary, "rejected_records");
            int duplicates = getCount(summary, "duplicate_records");

            String acceptedKey = "processed/" + uploadId + "/accepted.csv";
            String rejectedKey = "processed/" + uploadId + "/rejected.csv";
            String duplicatesKey = "processed/" + uploadId + "/duplicates.csv";
            String summaryKey = "processed/" + uploadId + "/summary.json";
            String logKey = "logs/" + uploadId + "/processing.log";

            // Upload ETL outputs and log to S3.
            s3StorageService.uploadFile(
                    acceptedFile, acceptedKey, "text/csv"
            );
            s3StorageService.uploadFile(
                    rejectedFile, rejectedKey, "text/csv"
            );
            s3StorageService.uploadFile(
                    duplicatesFile, duplicatesKey, "text/csv"
            );
            s3StorageService.uploadFile(
                    summaryFile, summaryKey, "application/json"
            );
            s3StorageService.uploadFile(
                    logPath, logKey, "text/plain"
            );

            // Persist output keys and counts in Snowflake.
            int updated = jdbcTemplate.update("""
                UPDATE SRIRAM_CAP39_DB.SRIRAM_APP.SRIRAM_FILE_UPLOADS
                SET ACCEPTED_S3_KEY = ?,
                    REJECTED_S3_KEY = ?,
                    DUPLICATES_S3_KEY = ?,
                    SUMMARY_S3_KEY = ?,
                    LOG_S3_KEY = ?,
                    TOTAL_RECORDS = ?,
                    ACCEPTED_RECORDS = ?,
                    REJECTED_RECORDS = ?,
                    DUPLICATE_RECORDS = ?,
                    UPLOAD_STATUS = 'COMPLETED',
                    ERROR_MESSAGE = NULL
                WHERE UPLOAD_ID = ?
                  AND UPLOAD_STATUS = 'PROCESSING'
                """,
                    acceptedKey,
                    rejectedKey,
                    duplicatesKey,
                    summaryKey,
                    logKey,
                    total,
                    accepted,
                    rejected,
                    duplicates,
                    uploadId
            );

            if (updated != 1) {
                throw new IllegalStateException(
                        "Could not update upload metadata after ETL."
                );
            }

            Map<String, Object> response = new LinkedHashMap<>(summary);
            response.put("upload_id", uploadId);
            response.put("upload_status", "COMPLETED");
            response.put("accepted_s3_key", acceptedKey);
            response.put("rejected_s3_key", rejectedKey);
            response.put("duplicates_s3_key", duplicatesKey);
            response.put("summary_s3_key", summaryKey);
            response.put("log_s3_key", logKey);

            return response;

        } catch (Exception exception) {

            String message = exception.getMessage() == null
                    ? "Unknown processing error."
                    : exception.getMessage();

            if (message.length() > 1900) {
                message = message.substring(0, 1900);
            }

            // Preserve the processing log in S3 when available.
            if (Files.isRegularFile(logPath)) {
                try {
                    s3StorageService.uploadFile(
                            logPath,
                            "logs/" + uploadId + "/processing.log",
                            "text/plain"
                    );

                    jdbcTemplate.update("""
                        UPDATE SRIRAM_CAP39_DB.SRIRAM_APP.SRIRAM_FILE_UPLOADS
                        SET LOG_S3_KEY = ?
                        WHERE UPLOAD_ID = ?
                        """,
                            "logs/" + uploadId + "/processing.log",
                            uploadId
                    );

                } catch (Exception logException) {
                    exception.addSuppressed(logException);
                }
            }

            jdbcTemplate.update("""
                UPDATE SRIRAM_CAP39_DB.SRIRAM_APP.SRIRAM_FILE_UPLOADS
                SET UPLOAD_STATUS = 'FAILED',
                    ERROR_MESSAGE = ?
                WHERE UPLOAD_ID = ?
                  AND UPLOAD_STATUS = 'PROCESSING'
                """, message, uploadId);

            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }

            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Upload processing failed. Check the processing log in S3 and the upload error message.",
                    exception
            );

        } finally {
            if (temporaryDirectory != null) {
                deleteDirectory(temporaryDirectory);
            }

            try {
                Files.deleteIfExists(logPath);
            } catch (Exception ignored) {
                // Do not mask the processing result.
            }
        }
    }

    private void requireFile(Path path) {
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException(
                    "Expected ETL output was not created: " + path
            );
        }
    }

    private int getCount(Map<String, Object> summary, String key) {
        Object value = summary.get(key);

        if (!(value instanceof Number number)) {
            throw new IllegalStateException(
                    "Missing or invalid count in summary.json: " + key
            );
        }

        return number.intValue();
    }

    private void deleteDirectory(Path directory) {
        try (var paths = Files.walk(directory)) {
            paths.sorted((a, b) -> b.compareTo(a))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (Exception ignored) {
                            // Temporary-file cleanup must not mask ETL result.
                        }
                    });
        } catch (Exception ignored) {
            // Temporary-file cleanup must not mask ETL result.
        }
    }
}