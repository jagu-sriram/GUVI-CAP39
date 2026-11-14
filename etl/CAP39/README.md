# CAP-39 Manufacturing Yield Loss & Defect Genealogy Analyzer — Sample Data

## Files
All 13 source and reference CSVs are in `data/raw/`. `data_quality_issue_catalog.csv` lists intentionally planted issues.

| File | Grain | Main key | Relationships |
|---|---|---|---|
| material_lots.csv | One row per received material lot | material_lot_id | supplier_id referenced by batches/events conceptually |
| production_batches.csv | One row per production batch | batch_id | Product, line, shift and production quantities |
| batch_relationships.csv | One row per parent-child genealogy edge | relationship_id | parent_batch_id and child_batch_id refer to batches |
| production_events.csv | One row per event | event_id | batch_id; material_lot_id for MATERIAL_ISSUED |
| process_parameters.csv | One measurement per batch/parameter | measurement_id | batch_id and machine_id |
| defects.csv | One defect observation per batch | defect_id | batch_id; inspection_id |
| rework_records.csv | One rework operation | rework_id | batch_id and defect_id |
| suppliers.csv | One supplier master record | supplier_id | Referenced by material_lots |
| products.csv | One product master record | product_id | Referenced by production_batches |
| production_lines.csv | One production line master record | production_line | Referenced by production_batches |
| machines.csv | One machine master record | machine_id | Referenced by events and parameters |
| operators.csv | One operator master record | operator_id | Referenced by production_events |
| shift_schedule.csv | One shift definition | shift | Referenced by production_batches; defines local shift boundaries |

## Scale
Approximately 40 production batches, 12 material lots, 14 genealogy edges, 200+ production events, 120+ process measurements, 37 defect observations and 15 rework operations, plus six small reference/master datasets. Exact row counts are in the CSV files.

## Important modelling notes
- These are synthetic training data, not real factory records.
- The files are interconnected, but intentionally contain invalid references, duplicate records, malformed timestamps, missing values and business-rule violations.
- Do not clean the raw files in place. Preserve them and write cleaned output to a separate `processed/` folder.
- Keep rejected records with a rejection reason. Do not silently delete them.
- A duplicate should be counted separately from a rejected business record in your reconciliation report. Define the rule consistently.
- Some batch IDs BAT-0041 onward appear as genealogy child batches but are not ordinary production batch master rows. Decide whether to model these as derived child batches or quarantine them; document the choice. This is intentional and is useful for discussing genealogy design.
- Some defect-to-rework links are intentionally invalid. Validate them before calculating rework success rates.
- Process parameter ranges in this sample are illustrative only. In a real factory, obtain approved engineering limits by product, machine and process step.
- Correlation between a process parameter and defects is a candidate signal, not proof of causation.

## Recommended first Pandas workflow
1. Load each raw CSV and inspect shape, columns, dtypes and null counts.
2. Profile duplicates, distinct values, numeric ranges and timestamp parse failures.
3. Normalize whitespace, identifiers, dates, units and controlled categories.
4. Validate primary keys, foreign keys and business rules.
5. Split each dataset into accepted, rejected and duplicate records with reason codes.
6. Reconcile input rows = accepted + rejected + duplicates (using one consistent precedence rule).
7. Export cleaned and rejected CSVs without modifying `data/raw/`.

## Timestamp policy to decide before coding
The data includes timestamps with no timezone, one timezone offset, multiple string formats and invalid values. For the exercise, document a plant-local timezone policy (for example, Asia/Kolkata) and do not silently assume that a naive timestamp is UTC.
