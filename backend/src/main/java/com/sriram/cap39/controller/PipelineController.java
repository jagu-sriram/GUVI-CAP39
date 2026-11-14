package com.sriram.cap39.controller;

import com.sriram.cap39.service.PipelineOrchestrationService;
import com.sriram.cap39.service.PipelineStatusService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/uploads")
public class PipelineController {

    private final PipelineOrchestrationService orchestrationService;
    private final PipelineStatusService statusService;

    public PipelineController(
            PipelineOrchestrationService orchestrationService,
            PipelineStatusService statusService) {

        this.orchestrationService = orchestrationService;
        this.statusService = statusService;
    }

    @PostMapping("/{uploadId}/pipeline")
    public ResponseEntity<Map<String, Object>> startPipeline(
            @PathVariable String uploadId) {

        Map<String, Object> response =
                orchestrationService.startPipeline(uploadId, false);

        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(response);
    }

    @GetMapping("/{uploadId}/pipeline")
    public ResponseEntity<Map<String, Object>> getPipelineStatus(
            @PathVariable String uploadId) {

        return ResponseEntity.ok(
                statusService.getLatestStatus(uploadId)
        );
    }

    @PostMapping("/{uploadId}/pipeline/retry")
    public ResponseEntity<Map<String, Object>> retryPipeline(
            @PathVariable String uploadId) {

        Map<String, Object> response =
                orchestrationService.startPipeline(uploadId, true);

        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(response);
    }
}