from pathlib import Path
import pandas as pd

from etl.common import REJECTED_DIR


def inspect_rejections():

    print("\n========== REJECTION ANALYSIS ==========\n")

    for file_path in sorted(REJECTED_DIR.glob("*.csv")):

        df = pd.read_csv(file_path, dtype="string")

        if df.empty:
            continue

        reason_columns = [
            col for col in df.columns
            if "reason" in col.lower() or "error" in col.lower()
        ]

        print(f"\nDataset: {file_path.name}")
        print(f"Rejected records: {len(df)}")

        for column in reason_columns:
            print(f"\n{column} frequency:")
            print(df[column].value_counts(dropna=False).to_string())

        if file_path.stem == "rework_records":
            print("\nAll rejected rework records:")
            print(df.to_string(index=False))

    print("\n========================================")


if __name__ == "__main__":
    inspect_rejections()