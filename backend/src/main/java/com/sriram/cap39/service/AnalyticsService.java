
package com.sriram.cap39.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AnalyticsService {

    private static final String DB =
            "SRIRAM_CAP39_DB.SRIRAM_ANALYTICS.";

    private final JdbcTemplate jdbcTemplate;

    public AnalyticsService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // ---------------------------------------------------------
    // OVERVIEW KPIs
    // ---------------------------------------------------------

    public Map<String, Object> getOverview() {

        Map<String, Object> result = new LinkedHashMap<>();

        result.put("totalBatches", scalar("""
            SELECT COUNT(DISTINCT BATCH_ID)
            FROM SRIRAM_CAP39_DB.SRIRAM_ANALYTICS.SRIRAM_VW_BATCH_PERFORMANCE
            """));

        result.put("plannedQuantity", scalar("""
            SELECT COALESCE(SUM(PLANNED_QUANTITY), 0)
            FROM SRIRAM_CAP39_DB.SRIRAM_ANALYTICS.SRIRAM_VW_BATCH_PERFORMANCE
            """));

        result.put("producedQuantity", scalar("""
            SELECT COALESCE(SUM(PRODUCED_QUANTITY), 0)
            FROM SRIRAM_CAP39_DB.SRIRAM_ANALYTICS.SRIRAM_VW_BATCH_PERFORMANCE
            """));

        result.put("totalDefectRecords", scalar("""
            SELECT COUNT(DISTINCT DEFECT_ID)
            FROM SRIRAM_CAP39_DB.SRIRAM_ANALYTICS.SRIRAM_VW_DEFECT_ANALYSIS
            """));

        result.put("totalAffectedQuantity", scalar("""
            SELECT COALESCE(SUM(AFFECTED_QUANTITY), 0)
            FROM SRIRAM_CAP39_DB.SRIRAM_ANALYTICS.SRIRAM_VW_DEFECT_ANALYSIS
            """));

        result.put("averageProductionAttainmentPercent", scalar("""
            SELECT COALESCE(AVG(PRODUCTION_ATTAINMENT_PERCENT), 0)
            FROM SRIRAM_CAP39_DB.SRIRAM_ANALYTICS.SRIRAM_VW_BATCH_PERFORMANCE
            """));

        result.put("totalProductionShortfall", scalar("""
            SELECT COALESCE(SUM(PRODUCTION_SHORTFALL), 0)
            FROM SRIRAM_CAP39_DB.SRIRAM_ANALYTICS.SRIRAM_VW_YIELD_LOSS_ANALYSIS
            """));

        result.put("totalReworkQuantity", scalar("""
            SELECT COALESCE(SUM(REWORK_QUANTITY), 0)
            FROM SRIRAM_CAP39_DB.SRIRAM_ANALYTICS.SRIRAM_VW_REWORK_ANALYSIS
            """));

        result.put("machineCount", scalar("""
            SELECT COUNT(DISTINCT MACHINE_ID)
            FROM SRIRAM_CAP39_DB.SRIRAM_ANALYTICS.SRIRAM_VW_MACHINE_PERFORMANCE
            """));

        result.put("supplierCount", scalar("""
            SELECT COUNT(DISTINCT SUPPLIER_ID)
            FROM SRIRAM_CAP39_DB.SRIRAM_ANALYTICS.SRIRAM_VW_SUPPLIER_QUALITY
            """));

        return result;
    }

    // ---------------------------------------------------------
    // YIELD LOSS
    // ---------------------------------------------------------

    public List<Map<String, Object>> getYieldLoss(
            String productionLine,
            String shift,
            String fromDate,
            String toDate,
            int limit) {

        Map<String, String> filters = new LinkedHashMap<>();

        filters.put("PRODUCTION_LINE", productionLine);
        filters.put("SHIFT", shift);
        filters.put("PRODUCTION_DATE_FROM", fromDate);
        filters.put("PRODUCTION_DATE_TO", toDate);

        return query(
                "SRIRAM_VW_YIELD_LOSS_ANALYSIS",
                filters,
                limit
        );
    }

    // ---------------------------------------------------------
    // DEFECT ANALYSIS
    // ---------------------------------------------------------

    public List<Map<String, Object>> getDefects(
            String productionLine,
            String shift,
            String defectCategory,
            String fromDate,
            String toDate,
            int limit) {

        Map<String, String> filters = new LinkedHashMap<>();

        filters.put("PRODUCTION_LINE", productionLine);
        filters.put("SHIFT", shift);
        filters.put("DEFECT_CATEGORY", defectCategory);
        filters.put("DETECTED_AT_FROM", fromDate);
        filters.put("DETECTED_AT_TO", toDate);

        return query(
                "SRIRAM_VW_DEFECT_ANALYSIS",
                filters,
                limit
        );
    }

    // ---------------------------------------------------------
    // PRODUCTION ANALYSIS
    // ---------------------------------------------------------

    public Map<String, Object> getProduction(
            String productionLine,
            String shift,
            int limit) {

        Map<String, Object> result = new LinkedHashMap<>();

        Map<String, String> batchFilters = new LinkedHashMap<>();
        batchFilters.put("PRODUCTION_LINE", productionLine);
        batchFilters.put("SHIFT", shift);

        Map<String, String> machineFilters = new LinkedHashMap<>();

        Map<String, String> parameterFilters = new LinkedHashMap<>();

        result.put("batchPerformance", query(
                "SRIRAM_VW_BATCH_PERFORMANCE",
                batchFilters,
                limit
        ));

        result.put("machinePerformance", query(
                "SRIRAM_VW_MACHINE_PERFORMANCE",
                machineFilters,
                limit
        ));

        result.put("processParameters", query(
                "SRIRAM_VW_PROCESS_PARAMETER_ANALYSIS",
                parameterFilters,
                limit
        ));

        return result;
    }

    // ---------------------------------------------------------
    // GENEALOGY AND MATERIAL TRACEABILITY
    // ---------------------------------------------------------


    public Map<String, Object> getGenealogy(String batchId, int limit) {

        int safeLimit = Math.max(1, Math.min(limit, 1000));

        Map<String, Object> result = new LinkedHashMap<>();

        List<Map<String, Object>> genealogyRows;

        if (batchId == null || batchId.isBlank()) {
            genealogyRows = jdbcTemplate.queryForList("""
            SELECT *
            FROM SRIRAM_CAP39_DB.SRIRAM_ANALYTICS.SRIRAM_VW_BATCH_GENEALOGY
            LIMIT ?
            """, safeLimit);
        } else {
            genealogyRows = jdbcTemplate.queryForList("""
            SELECT *
            FROM SRIRAM_CAP39_DB.SRIRAM_ANALYTICS.SRIRAM_VW_BATCH_GENEALOGY
            WHERE PARENT_BATCH_ID = ?
               OR CHILD_BATCH_ID = ?
            LIMIT ?
            """, batchId, batchId, safeLimit);
        }

        Map<String, String> materialFilters = new LinkedHashMap<>();
        materialFilters.put("BATCH_ID", batchId);

        result.put("batchGenealogy", genealogyRows);
        result.put("materialTraceability", query(
                "SRIRAM_VW_MATERIAL_TRACEABILITY",
                materialFilters,
                safeLimit
        ));

        return result;
    }


    // ---------------------------------------------------------
    // ROOT-CAUSE CANDIDATES
    // ---------------------------------------------------------

    public List<Map<String, Object>> getRootCauses(
            String productionLine,
            String shift,
            String candidateStatus,
            int limit) {

        Map<String, String> filters = new LinkedHashMap<>();

        filters.put("PRODUCTION_LINE", productionLine);
        filters.put("SHIFT", shift);
        filters.put("ROOT_CAUSE_CANDIDATE_STATUS", candidateStatus);

        return query(
                "SRIRAM_VW_ROOT_CAUSE_CANDIDATES",
                filters,
                limit
        );
    }

    // ---------------------------------------------------------
    // REWORK
    // ---------------------------------------------------------

    public List<Map<String, Object>> getRework(
            String productionLine,
            String shift,
            String fromDate,
            String toDate,
            int limit) {

        Map<String, String> filters = new LinkedHashMap<>();

        filters.put("PRODUCTION_LINE", productionLine);
        filters.put("SHIFT", shift);
        filters.put("REWORK_START_FROM", fromDate);
        filters.put("REWORK_START_TO", toDate);

        return query(
                "SRIRAM_VW_REWORK_ANALYSIS",
                filters,
                limit
        );
    }

    // ---------------------------------------------------------
    // SUPPLIER QUALITY
    // ---------------------------------------------------------

    public List<Map<String, Object>> getSuppliers(int limit) {

        return query(
                "SRIRAM_VW_SUPPLIER_QUALITY",
                Map.of(),
                limit
        );
    }

    // ---------------------------------------------------------
    // COMMON SAFE QUERY BUILDER
    // ---------------------------------------------------------

    private List<Map<String, Object>> query(
            String viewName,
            Map<String, String> filters,
            int requestedLimit) {

        int limit = Math.max(1, Math.min(requestedLimit, 1000));

        String sql = "SELECT * FROM " + DB + viewName;
        List<Object> parameters = new ArrayList<>();
        List<String> conditions = new ArrayList<>();

        for (Map.Entry<String, String> filter : filters.entrySet()) {

            String column = filter.getKey();
            String value = filter.getValue();

            if (value == null || value.isBlank()) {
                continue;
            }

            switch (column) {

                case "PRODUCTION_LINE":
                case "SHIFT":
                case "DEFECT_CATEGORY":
                case "BATCH_ID":
                case "ROOT_CAUSE_CANDIDATE_STATUS":

                    conditions.add(column + " = ?");
                    parameters.add(value);
                    break;

                case "PRODUCTION_DATE_FROM":
                    conditions.add("PRODUCTION_DATE >= TO_DATE(?)");
                    parameters.add(value);
                    break;

                case "PRODUCTION_DATE_TO":
                    conditions.add("PRODUCTION_DATE < DATEADD(day, 1, TO_DATE(?))");
                    parameters.add(value);
                    break;

                case "DETECTED_AT_FROM":
                    conditions.add("DETECTED_AT >= TO_DATE(?)");
                    parameters.add(value);
                    break;

                case "DETECTED_AT_TO":
                    conditions.add("DETECTED_AT < DATEADD(day, 1, TO_DATE(?))");
                    parameters.add(value);
                    break;

                case "REWORK_START_FROM":
                    conditions.add("REWORK_START >= TO_DATE(?)");
                    parameters.add(value);
                    break;

                case "REWORK_START_TO":
                    conditions.add("REWORK_START < DATEADD(day, 1, TO_DATE(?))");
                    parameters.add(value);
                    break;

                default:
                    throw new IllegalArgumentException(
                            "Unsupported analytics filter: " + column
                    );
            }
        }

        if (!conditions.isEmpty()) {
            sql += " WHERE " + String.join(" AND ", conditions);
        }

        sql += " LIMIT " + limit;

        return jdbcTemplate.queryForList(sql, parameters.toArray());
    }

    private Object scalar(String sql) {
        return jdbcTemplate.queryForObject(sql, Object.class);
    }
}
