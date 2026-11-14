import pandas as pd

from etl.common import (
    STANDARDIZED_DIR,
    REPORTS_DIR,
    get_source_files,
    load_csv,
    save_csv
)

PLANT_TIMEZONE = "Asia/Kolkata"

DATE_COLUMNS = {
    "production_date",
    "received_date",
    "detected_at",
    "recorded_at",
    "event_timestamp",
    "rework_start",
    "rework_end"
}


def standardize_text(df):

    for col in df.columns:

        df[col] = df[col].astype("string").str.strip()

        df[col] = df[col].replace("", pd.NA)

    return df


def standardize_categories(df):

    uppercase_columns = [
        "batch_id",
        "product_id",
        "production_line",
        "shift",
        "status",
        "event_type",
        "material_lot_id",
        "supplier_id",
        "machine_id",
        "operator_id",
        "parameter_name",
        "unit",
        "defect_category",
        "severity",
        "rework_type",
        "rework_outcome",
        "relationship_type"
    ]

    for col in uppercase_columns:

        if col in df.columns:
            df[col] = df[col].str.upper()

    return df


def parse_timestamp(value):

    if pd.isna(value):
        return pd.NaT

    try:

        timestamp = pd.Timestamp(value)

        if timestamp.tzinfo is None:

            timestamp = timestamp.tz_localize(
                PLANT_TIMEZONE,
                ambiguous="NaT",
                nonexistent="NaT"
            )

        else:

            timestamp = timestamp.tz_convert(
                PLANT_TIMEZONE
            )

        return timestamp

    except (ValueError, TypeError, OverflowError):

        return pd.NaT


def standardize_dates(df, dataset_name):

    issues = []

    for col in DATE_COLUMNS.intersection(df.columns):

        original_values = df[col].copy()

        parsed_values = original_values.map(parse_timestamp)

        invalid_mask = (
            original_values.notna()
            & parsed_values.isna()
        )

        for index in df.index[invalid_mask]:

            issues.append({
                "dataset": dataset_name,
                "column": col,
                "original_value": original_values.loc[index],
                "issue": "INVALID_TIMESTAMP"
            })

        df[col] = parsed_values.map(
            lambda x: x.isoformat()
            if pd.notna(x) else pd.NA
        ).astype("string")

    return df, issues


def standardize_temperature(df):

    required = {
        "parameter_name",
        "parameter_value",
        "unit"
    }

    if not required.issubset(df.columns):
        return df

    # Normalize common unit spellings
    df["unit"] = df["unit"].replace({
        "CELCIUS": "C",
        "CELSIUS": "C",
        "°C": "C",
        "DEG C": "C",
        "FAHRENHEIT": "F",
        "°F": "F"
    })

    # Identify Fahrenheit temperature measurements
    mask = (
        df["parameter_name"].isin(["TEMPERATURE", "TEMP"])
        & df["unit"].eq("F")
    )

    values = pd.to_numeric(
        df["parameter_value"],
        errors="coerce"
    )

    valid_mask = mask & values.notna()

    # Fahrenheit to Celsius
    df.loc[valid_mask, "parameter_value"] = (
        (values[valid_mask] - 32) * 5 / 9
    ).round(3).astype(str)

    df.loc[mask, "unit"] = "C"

    return df


def standardize_datasets():

    timestamp_issues = []
    summary = []

    for file in get_source_files():

        dataset_name = file.stem

        df = load_csv(file)

        original_rows = len(df)

        df = standardize_text(df)
        df = standardize_categories(df)

        df, issues = standardize_dates(
            df,
            dataset_name
        )

        df = standardize_temperature(df)

        save_csv(
            df,
            STANDARDIZED_DIR / file.name
        )

        timestamp_issues.extend(issues)

        summary.append({
            "dataset": dataset_name,
            "original_rows": original_rows,
            "standardized_rows": len(df),
            "timestamp_issues": len(issues)
        })

        print("Standardized:", dataset_name)

    pd.DataFrame(summary).to_csv(
        REPORTS_DIR / "standardization_summary.csv",
        index=False
    )

    pd.DataFrame(timestamp_issues).to_csv(
        REPORTS_DIR / "timestamp_issues.csv",
        index=False
    )

    print("\nStandardization completed!")


if __name__ == "__main__":
    standardize_datasets()