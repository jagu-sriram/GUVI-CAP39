import pandas as pd

from etl.common import (
    STANDARDIZED_DIR,
    PROCESSED_DIR,
    REJECTED_DIR,
    DUPLICATES_DIR,
    REPORTS_DIR,
    load_csv,
    save_csv
)


# Primary keys for the datasets
PRIMARY_KEYS = {
    "material_lots": ["material_lot_id"],
    "production_batches": ["batch_id"],
    "batch_relationships": ["relationship_id"],
    "production_events": ["event_id"],
    "process_parameters": ["measurement_id"],
    "defects": ["defect_id"],
    "rework_records": ["rework_id"],
    "suppliers": ["supplier_id"],
    "products": ["product_id"],
    "machines": ["machine_id"],
    "operators": ["operator_id"]
}


# Fields that should not be missing
REQUIRED_FIELDS = {
    "material_lots": [
        "material_lot_id",
        "supplier_id"
    ],
    "production_batches": [
        "batch_id",
        "product_id",
        "production_line",
        "shift"
    ],
    "batch_relationships": [
        "relationship_id",
        "parent_batch_id",
        "child_batch_id",
        "relationship_type"
    ],
    "production_events": [
        "event_id",
        "batch_id",
        "event_type",
        "event_timestamp"
    ],
    "process_parameters": [
        "measurement_id",
        "batch_id",
        "machine_id",
        "parameter_name",
        "parameter_value"
    ],
    "defects": [
        "defect_id",
        "batch_id",
        "defect_category",
        "affected_quantity",
        "inspected_quantity"
    ],
    "rework_records": [
        "rework_id",
        "batch_id",
        "defect_id",
        "rework_quantity"
    ]
}


# Numeric fields that cannot be negative
NON_NEGATIVE_COLUMNS = [
    "quantity_received",
    "planned_quantity",
    "produced_quantity",
    "quantity",
    "parameter_value",
    "affected_quantity",
    "inspected_quantity",
    "rework_quantity"
]


def read_dataset(name):

    path = STANDARDIZED_DIR / f"{name}.csv"

    if path.exists():
        return load_csv(path)

    return None


def add_rejection_reason(reasons, mask, reason):

    mask = mask.fillna(False)

    for index in reasons.index[mask]:

        existing = reasons.loc[index]

        if existing:
            reasons.loc[index] = existing + "|" + reason
        else:
            reasons.loc[index] = reason


def get_reference_values(df, column):

    if df is None or column not in df.columns:
        return set()

    return set(
        df[column].dropna().astype(str)
    )


def clean_dataset(name, df, references):

    original_count = len(df)

    # --------------------------------
    # 1. Identify exact duplicates
    # --------------------------------

    duplicate_mask = df.duplicated(keep="first")

    duplicates = df[duplicate_mask].copy()

    df = df[~duplicate_mask].copy()

    # --------------------------------
    # 2. Initialize rejection reasons
    # --------------------------------

    reasons = pd.Series(
        "",
        index=df.index,
        dtype="string"
    )

    # --------------------------------
    # 3. Required field validation
    # --------------------------------

    for column in REQUIRED_FIELDS.get(name, []):

        if column in df.columns:

            missing = (
                df[column].isna()
                | df[column].astype("string").str.strip().eq("")
            )

            add_rejection_reason(
                reasons,
                missing,
                f"MISSING_{column.upper()}"
            )

    # --------------------------------
    # 4. Primary key validation
    # --------------------------------

    for column in PRIMARY_KEYS.get(name, []):

        if column in df.columns:

            missing = df[column].isna()

            add_rejection_reason(
                reasons,
                missing,
                f"MISSING_PRIMARY_KEY_{column.upper()}"
            )

            duplicate_keys = (
                df[column].notna()
                & df[column].duplicated(keep="first")
            )

            add_rejection_reason(
                reasons,
                duplicate_keys,
                f"DUPLICATE_PRIMARY_KEY_{column.upper()}"
            )

    # --------------------------------
    # 5. Numeric validation
    # --------------------------------

    for column in NON_NEGATIVE_COLUMNS:

        if column not in df.columns:
            continue

        numeric_values = pd.to_numeric(
            df[column],
            errors="coerce"
        )

        invalid_numeric = (
            df[column].notna()
            & numeric_values.isna()
        )

        negative_values = numeric_values < 0

        add_rejection_reason(
            reasons,
            invalid_numeric,
            f"INVALID_NUMERIC_{column.upper()}"
        )

        add_rejection_reason(
            reasons,
            negative_values,
            f"NEGATIVE_{column.upper()}"
        )

    # --------------------------------
    # 6. Foreign key validation
    # --------------------------------

    if name == "material_lots":

        if "supplier_id" in df.columns:

            valid_suppliers = references["supplier_ids"]

            add_rejection_reason(
                reasons,
                df["supplier_id"].notna()
                & ~df["supplier_id"].isin(valid_suppliers),
                "UNKNOWN_SUPPLIER_ID"
            )

    elif name == "production_batches":

        if "product_id" in df.columns:

            add_rejection_reason(
                reasons,
                df["product_id"].notna()
                & ~df["product_id"].isin(references["product_ids"]),
                "UNKNOWN_PRODUCT_ID"
            )

        if "production_line" in df.columns:

            add_rejection_reason(
                reasons,
                df["production_line"].notna()
                & ~df["production_line"].isin(references["line_ids"]),
                "UNKNOWN_PRODUCTION_LINE"
            )

        if "shift" in df.columns:

            add_rejection_reason(
                reasons,
                df["shift"].notna()
                & ~df["shift"].isin(references["shift_ids"]),
                "UNKNOWN_SHIFT"
            )

    elif name in [
        "production_events",
        "process_parameters",
        "defects",
        "rework_records"
    ]:

        if "batch_id" in df.columns:

            add_rejection_reason(
                reasons,
                df["batch_id"].notna()
                & ~df["batch_id"].isin(references["batch_ids"]),
                "UNKNOWN_BATCH_ID"
            )

    # --------------------------------
    # 7. Production event references
    # --------------------------------

    if name == "production_events":

        if "material_lot_id" in df.columns:

            invalid_material = (
                df["material_lot_id"].notna()
                & ~df["material_lot_id"].isin(
                    references["material_ids"]
                )
            )

            add_rejection_reason(
                reasons,
                invalid_material,
                "UNKNOWN_MATERIAL_LOT_ID"
            )

        if "machine_id" in df.columns:

            invalid_machine = (
                df["machine_id"].notna()
                & ~df["machine_id"].isin(
                    references["machine_ids"]
                )
            )

            add_rejection_reason(
                reasons,
                invalid_machine,
                "UNKNOWN_MACHINE_ID"
            )

        if "operator_id" in df.columns:

            invalid_operator = (
                df["operator_id"].notna()
                & ~df["operator_id"].isin(
                    references["operator_ids"]
                )
            )

            add_rejection_reason(
                reasons,
                invalid_operator,
                "UNKNOWN_OPERATOR_ID"
            )

    # --------------------------------
    # 8. Process parameter references
    # --------------------------------

    if name == "process_parameters":

        if "machine_id" in df.columns:

            invalid_machine = (
                df["machine_id"].notna()
                & ~df["machine_id"].isin(
                    references["machine_ids"]
                )
            )

            add_rejection_reason(
                reasons,
                invalid_machine,
                "UNKNOWN_MACHINE_ID"
            )

    # --------------------------------
    # 9. Defect quantity validation
    # --------------------------------

    if name == "defects":

        if {
            "affected_quantity",
            "inspected_quantity"
        }.issubset(df.columns):

            affected = pd.to_numeric(
                df["affected_quantity"],
                errors="coerce"
            )

            inspected = pd.to_numeric(
                df["inspected_quantity"],
                errors="coerce"
            )

            add_rejection_reason(
                reasons,
                affected > inspected,
                "AFFECTED_QUANTITY_EXCEEDS_INSPECTED"
            )

    # --------------------------------
    # 10. Rework-to-defect validation
    # --------------------------------

    if name == "rework_records":

        if {"defect_id", "batch_id"}.issubset(df.columns):

            defect_batch_map = references["defect_batch_map"]

            unknown_defect = (
                df["defect_id"].notna()
                & ~df["defect_id"].isin(defect_batch_map)
            )

            add_rejection_reason(
                reasons,
                unknown_defect,
                "UNKNOWN_DEFECT_ID"
            )

            expected_batch = df["defect_id"].map(
                defect_batch_map
            )

            mismatch = (
                expected_batch.notna()
                & df["batch_id"].notna()
                & expected_batch.ne(df["batch_id"])
            )

            add_rejection_reason(
                reasons,
                mismatch,
                "DEFECT_BATCH_MISMATCH"
            )

    # --------------------------------
    # 11. Batch relationship validation
    # --------------------------------

    if name == "batch_relationships":

        if "relationship_type" in df.columns:

            invalid_type = (
                df["relationship_type"].notna()
                & ~df["relationship_type"].isin(
                    ["SPLIT", "MERGE"]
                )
            )

            add_rejection_reason(
                reasons,
                invalid_type,
                "INVALID_RELATIONSHIP_TYPE"
            )

        # Do not reject a child batch merely because it is
        # absent from production_batches. Some genealogy nodes
        # may be derived child batches.

    # --------------------------------
    # 12. Separate accepted/rejected
    # --------------------------------

    rejected_mask = reasons.ne("")

    accepted = df[~rejected_mask].copy()

    rejected = df[rejected_mask].copy()

    rejected["rejection_reason"] = (
        reasons[rejected_mask]
    )

    return accepted, rejected, duplicates, original_count


def main():

    # Load master/reference datasets
    suppliers = read_dataset("suppliers")
    products = read_dataset("products")
    lines = read_dataset("production_lines")
    shifts = read_dataset("shift_schedule")
    machines = read_dataset("machines")
    operators = read_dataset("operators")
    batches = read_dataset("production_batches")
    materials = read_dataset("material_lots")
    defects = read_dataset("defects")
    relationships = read_dataset("batch_relationships")

    # Include parent and child genealogy nodes as known batch IDs.
    batch_ids = get_reference_values(batches, "batch_id")

    if relationships is not None:

        batch_ids |= get_reference_values(
            relationships, "parent_batch_id"
        )

        batch_ids |= get_reference_values(
            relationships, "child_batch_id"
        )

    defect_batch_map = {}

    if defects is not None and {
        "defect_id", "batch_id"
    }.issubset(defects.columns):

        defect_batch_map = dict(
            zip(
                defects["defect_id"].astype(str),
                defects["batch_id"].astype(str)
            )
        )

    references = {

        "supplier_ids": get_reference_values(
            suppliers, "supplier_id"
        ),

        "product_ids": get_reference_values(
            products, "product_id"
        ),

        "line_ids": get_reference_values(
            lines, "production_line"
        ),

        "shift_ids": get_reference_values(
            shifts, "shift"
        ),

        "machine_ids": get_reference_values(
            machines, "machine_id"
        ),

        "operator_ids": get_reference_values(
            operators, "operator_id"
        ),

        "batch_ids": batch_ids,

        "material_ids": get_reference_values(
            materials, "material_lot_id"
        ),

        "defect_batch_map": defect_batch_map
    }

    summary = []

    for file in sorted(STANDARDIZED_DIR.glob("*.csv")):

        name = file.stem

        df = load_csv(file)

        accepted, rejected, duplicates, original_count = (
            clean_dataset(name, df, references)
        )

        save_csv(
            accepted,
            PROCESSED_DIR / file.name
        )

        save_csv(
            rejected,
            REJECTED_DIR / file.name
        )

        save_csv(
            duplicates,
            DUPLICATES_DIR / file.name
        )

        summary.append({

            "dataset": name,

            "input_rows": original_count,

            "duplicate_rows": len(duplicates),

            "accepted_rows": len(accepted),

            "rejected_rows": len(rejected),

            "reconciliation_check": (
                original_count
                == len(duplicates)
                + len(accepted)
                + len(rejected)
            )
        })

        print(
            f"{name}: "
            f"Accepted={len(accepted)}, "
            f"Rejected={len(rejected)}, "
            f"Duplicates={len(duplicates)}"
        )

    report = pd.DataFrame(summary)

    report.to_csv(
        REPORTS_DIR / "cleaning_reconciliation_report.csv",
        index=False
    )

    print("\nData cleaning completed!")


if __name__ == "__main__":
    main()