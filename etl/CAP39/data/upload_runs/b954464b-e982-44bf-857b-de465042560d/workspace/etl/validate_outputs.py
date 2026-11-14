from pathlib import Path
import pandas as pd

from etl.common import (
    RAW_DIR,
    STANDARDIZED_DIR,
    PROCESSED_DIR,
    REJECTED_DIR,
    DUPLICATES_DIR
)


def get_row_count(file_path):
    if not file_path.exists():
        return None

    return len(pd.read_csv(file_path, dtype="string"))


def validate_outputs():

    print("\n========== ETL OUTPUT VALIDATION ==========\n")

    raw_files = list(RAW_DIR.glob("*.csv"))

    if not raw_files:
        print("No raw CSV files found.")
        return

    all_valid = True

    for raw_file in sorted(raw_files):

        filename = raw_file.name

        raw_count = get_row_count(raw_file)
        standardized_count = get_row_count(STANDARDIZED_DIR / filename)
        accepted_count = get_row_count(PROCESSED_DIR / filename)
        rejected_count = get_row_count(REJECTED_DIR / filename)
        duplicate_count = get_row_count(DUPLICATES_DIR / filename)

        print(f"\nDataset: {filename}")
        print(f"Raw records:          {raw_count}")
        print(f"Standardized records: {standardized_count}")
        print(f"Accepted records:     {accepted_count}")
        print(f"Rejected records:     {rejected_count}")
        print(f"Duplicate records:    {duplicate_count}")

        if None in (
            standardized_count,
            accepted_count,
            rejected_count,
            duplicate_count
        ):
            print("STATUS: MISSING OUTPUT FILE")
            all_valid = False
            continue

        if raw_count != standardized_count:
            print("STATUS: STANDARDIZATION COUNT MISMATCH")
            all_valid = False
            continue

        accounted_records = (
            accepted_count + rejected_count + duplicate_count
        )

        if raw_count != accounted_records:
            print("STATUS: RECONCILIATION FAILED")
            all_valid = False
        else:
            print("STATUS: PASSED")

    print("\n===========================================")
    print("OVERALL RESULT:", "PASSED" if all_valid else "CHECK REQUIRED")


if __name__ == "__main__":
    validate_outputs()