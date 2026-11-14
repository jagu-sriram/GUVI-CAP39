
package com.sriram.cap39.controller;

import com.sriram.cap39.service.ProcessExecutionService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/uploads")
public class ProcessController {

    private final ProcessExecutionService processExecutionService;

    public ProcessController(
            ProcessExecutionService processExecutionService) {
        this.processExecutionService = processExecutionService;
    }

    @PostMapping("/{uploadId}/process")
    public Map<String, Object> processUpload(
            @PathVariable String uploadId) {

        return processExecutionService.processUpload(uploadId);
    }
}
