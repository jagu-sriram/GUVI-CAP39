
package com.sriram.cap39.controller;

import com.sriram.cap39.dto.BatchRequest;
import com.sriram.cap39.service.BatchService;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/batches")
public class BatchController {

    private final BatchService batchService;

    public BatchController(BatchService batchService) {
        this.batchService = batchService;
    }

    // Register batch
    @PostMapping
    public Map<String, Object> registerBatch(
            @Valid @RequestBody BatchRequest request) {

        return batchService.registerBatch(request);
    }

    // List all batches
    @GetMapping
    public List<Map<String, Object>> getAllBatches() {

        return batchService.getAllBatches();
    }

    // Get batch by ID
    @GetMapping("/{batchId}")
    public Map<String, Object> getBatchById(
            @PathVariable String batchId) {

        return batchService.getBatchById(batchId);
    }

    // Update batch
    @PutMapping("/{batchId}")
    public Map<String, Object> updateBatch(
            @PathVariable String batchId,
            @Valid @RequestBody BatchRequest request) {

        return batchService.updateBatch(batchId, request);
    }

    // Delete pending batch
    @DeleteMapping("/{batchId}")
    public Map<String, Object> deleteBatch(
            @PathVariable String batchId) {

        return batchService.deleteBatch(batchId);
    }
}
