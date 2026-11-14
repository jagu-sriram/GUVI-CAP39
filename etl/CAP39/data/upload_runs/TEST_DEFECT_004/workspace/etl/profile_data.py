import pandas as pd

from etl.common import (
    REPORTS_DIR,
    get_source_files,
    load_csv
)


def profile_datasets():

    files = get_source_files()

    if not files:
        raise FileNotFoundError("No CSV files found in data/raw")

    dataset_summary = []
    column_summary = []

    for file in files:

        df = load_csv(file)
        dataset_name = file.stem

        print("\nProcessing:", dataset_name)

        # Dataset-level profiling
        dataset_summary.append({
            "dataset": dataset_name,
            "total_rows": len(df),
            "total_columns": len(df.columns),
            "duplicate_rows": df.duplicated().sum()
        })

        # Column-level profiling
        for col in df.columns:

            column_summary.append({
                "dataset": dataset_name,
                "column": col,
                "data_type": str(df[col].dtype),
                "missing_values": df[col].isnull().sum(),
                "missing_percentage": round(
                    df[col].isnull().mean() * 100, 2
                ),
                "unique_values": df[col].nunique()
            })

        print("Rows:", len(df))
        print("Columns:", len(df.columns))
        print("Duplicates:", df.duplicated().sum())

    # Convert summaries into DataFrames
    dataset_report = pd.DataFrame(dataset_summary)
    column_report = pd.DataFrame(column_summary)

    # Export reports
    dataset_report.to_csv(
        REPORTS_DIR / "dataset_profile.csv",
        index=False
    )

    column_report.to_csv(
        REPORTS_DIR / "column_profile.csv",
        index=False
    )

    print("\nData profiling completed!")
    print("Reports saved in:", REPORTS_DIR)


if __name__ == "__main__":
    profile_datasets()