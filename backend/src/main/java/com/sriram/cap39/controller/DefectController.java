
package com.sriram.cap39.controller;

import com.sriram.cap39.dto.DefectRequest;
import com.sriram.cap39.service.DefectService;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/defects")
public class DefectController {

    private final DefectService defectService;

    public DefectController(DefectService defectService) {
        this.defectService = defectService;
    }

    // Register defect
    @PostMapping
    public Map<String, Object> registerDefect(
            @Valid @RequestBody DefectRequest request) {

        return defectService.registerDefect(request);
    }

    // Get all defects
    @GetMapping
    public List<Map<String, Object>> getAllDefects() {

        return defectService.getAllDefects();
    }

    // Get defect by ID
    @GetMapping("/{defectId}")
    public Map<String, Object> getDefectById(
            @PathVariable String defectId) {

        return defectService.getDefectById(defectId);
    }

    // Get defects by batch ID
    @GetMapping("/batch/{batchId}")
    public List<Map<String, Object>> getDefectsByBatch(
            @PathVariable String batchId) {

        return defectService.getDefectsByBatch(batchId);
    }

    // Update defect
    @PutMapping("/{defectId}")
    public Map<String, Object> updateDefect(
            @PathVariable String defectId,
            @Valid @RequestBody DefectRequest request) {

        return defectService.updateDefect(defectId, request);
    }

    // Delete pending defect
    @DeleteMapping("/{defectId}")
    public Map<String, Object> deleteDefect(
            @PathVariable String defectId) {

        return defectService.deleteDefect(defectId);
    }
}
