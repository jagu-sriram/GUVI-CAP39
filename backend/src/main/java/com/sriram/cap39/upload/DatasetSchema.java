
package com.sriram.cap39.upload;

import java.util.Map;

public final class DatasetSchema {

    private DatasetSchema() {}

    private static final Map<String, String[]> DATASETS = Map.ofEntries(

            Map.entry("batch_relationships", new String[]{
                    "relationship_id", "parent_batch_id", "child_batch_id",
                    "relationship_type", "event_timestamp"
            }),

            Map.entry("defects", new String[]{
                    "defect_id", "batch_id", "inspection_id",
                    "defect_category", "defect_code", "affected_quantity",
                    "inspected_quantity", "detected_at", "severity"
            }),

            Map.entry("machines", new String[]{
                    "machine_id", "machine_name", "machine_type", "machine_status"
            }),

            Map.entry("material_lots", new String[]{
                    "material_lot_id", "material_type", "supplier_id",
                    "supplier_name", "received_date", "certificate_status",
                    "quantity_received", "unit"
            }),

            Map.entry("operators", new String[]{
                    "operator_id", "operator_label", "team_id", "operator_status"
            }),

            Map.entry("process_parameters", new String[]{
                    "measurement_id", "batch_id", "machine_id",
                    "parameter_name", "parameter_value", "unit", "recorded_at"
            }),

            Map.entry("production_batches", new String[]{
                    "batch_id", "product_id", "production_line", "shift",
                    "planned_quantity", "produced_quantity",
                    "production_date", "status"
            }),

            Map.entry("production_events", new String[]{
                    "event_id", "batch_id", "event_type", "event_timestamp",
                    "material_lot_id", "quantity", "operator_id", "machine_id"
            }),

            Map.entry("production_lines", new String[]{
                    "production_line", "line_name", "plant_id", "line_status"
            }),

            Map.entry("products", new String[]{
                    "product_id", "product_name", "product_family",
                    "primary_material_type", "product_revision"
            }),

            Map.entry("rework_records", new String[]{
                    "rework_id", "batch_id", "defect_id", "rework_type",
                    "rework_quantity", "rework_start", "rework_end",
                    "rework_outcome", "rework_station"
            }),

            Map.entry("shift_schedule", new String[]{
                    "shift", "start_local_time", "end_local_time"
            }),

            Map.entry("suppliers", new String[]{
                    "supplier_id", "supplier_name", "country", "supplier_status"
            })
    );

    public static String[] getHeaders(String datasetName) {
        return DATASETS.get(datasetName);
    }

    public static boolean isSupported(String datasetName) {
        return DATASETS.containsKey(datasetName);
    }
}
