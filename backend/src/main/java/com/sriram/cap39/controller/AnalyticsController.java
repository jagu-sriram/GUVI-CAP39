
package com.sriram.cap39.controller;

import com.sriram.cap39.service.AnalyticsService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    // Executive dashboard KPIs
    @GetMapping("/overview")
    public Map<String, Object> getOverview() {
        return analyticsService.getOverview();
    }

    // Yield loss and production attainment
    @GetMapping("/yield-loss")
    public List<Map<String, Object>> getYieldLoss(
            @RequestParam(required = false) String productionLine,
            @RequestParam(required = false) String shift,
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate,
            @RequestParam(defaultValue = "100") int limit) {

        return analyticsService.getYieldLoss(
                productionLine, shift, fromDate, toDate, limit
        );
    }

    // Defect records and severity
    @GetMapping("/defects")
    public List<Map<String, Object>> getDefects(
            @RequestParam(required = false) String productionLine,
            @RequestParam(required = false) String shift,
            @RequestParam(required = false) String defectCategory,
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate,
            @RequestParam(defaultValue = "100") int limit) {

        return analyticsService.getDefects(
                productionLine,
                shift,
                defectCategory,
                fromDate,
                toDate,
                limit
        );
    }

    // Batch, machine and process parameter performance
    @GetMapping("/production")
    public Map<String, Object> getProduction(
            @RequestParam(required = false) String productionLine,
            @RequestParam(required = false) String shift,
            @RequestParam(defaultValue = "100") int limit) {

        return analyticsService.getProduction(
                productionLine, shift, limit
        );
    }

    // Batch genealogy and material traceability
    @GetMapping("/genealogy")
    public Map<String, Object> getGenealogy(
            @RequestParam(required = false) String batchId,
            @RequestParam(defaultValue = "100") int limit) {

        return analyticsService.getGenealogy(batchId, limit);
    }

    // Root cause candidates (correlations, not proven causes)
    @GetMapping("/root-causes")
    public List<Map<String, Object>> getRootCauses(
            @RequestParam(required = false) String productionLine,
            @RequestParam(required = false) String shift,
            @RequestParam(required = false) String candidateStatus,
            @RequestParam(defaultValue = "100") int limit) {

        return analyticsService.getRootCauses(
                productionLine, shift, candidateStatus, limit
        );
    }

    // Rework records and outcomes
    @GetMapping("/rework")
    public List<Map<String, Object>> getRework(
            @RequestParam(required = false) String productionLine,
            @RequestParam(required = false) String shift,
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate,
            @RequestParam(defaultValue = "100") int limit) {

        return analyticsService.getRework(
                productionLine, shift, fromDate, toDate, limit
        );
    }

    // Supplier quality
    @GetMapping("/suppliers")
    public List<Map<String, Object>> getSuppliers(
            @RequestParam(defaultValue = "100") int limit) {

        return analyticsService.getSuppliers(limit);
    }
}
