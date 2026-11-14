
import argparse
import json
import shutil
import subprocess
import sys

from pathlib import Path
from uuid import uuid4

import pandas as pd


PROJECT_ROOT = Path(__file__).resolve().parent

PROCESSED_DIR = PROJECT_ROOT / "data" / "processed"
UPLOAD_RUNS_DIR = PROJECT_ROOT / "data" / "upload_runs"
ETL_DIR = PROJECT_ROOT / "etl"

PRIMARY_KEYS = {
    "batch_relationships": ["relationship_id"],
    "defects": ["defect_id"],
    "machines": ["machine_id"],
    "material_lots": ["material_lot_id"],
    "operators": ["operator_id"],
    "process_parameters": ["measurement_id"],
    "production_batches": ["batch_id"],
    "production_events": ["event_id"],
    "production_lines": ["production_line"],
    "products": ["product_id"],
    "rework_records": ["rework_id"],
    "shift_schedule": ["shift"],
    "suppliers": ["supplier_id"]
}


def run_upload(upload_file, dataset, upload_id):

    if dataset not in PRIMARY_KEYS:
        raise ValueError(f"Unsupported dataset: {dataset}")

    upload_file = Path(upload_file).resolve()

    if not upload_file.is_file():
        raise FileNotFoundError(f"Upload file not found: {upload_file}")

    if upload_file.suffix.lower() != ".csv":
        raise ValueError("Only CSV files are supported.")

    run_id = upload_id or str(uuid4())

    
    run_dir = UPLOAD_RUNS_DIR / run_id
    workspace = run_dir / "workspace"
    raw_dir = workspace / "data" / "raw"
    result_dir = run_dir / "results"

    if run_dir.exists():
        summary_file = result_dir / "summary.json"

        if summary_file.exists():
            raise FileExistsError(
                f"Upload {run_id} has already completed. "
                "Use a new upload ID for a new upload."
            )

        print(f"Retrying incomplete upload: {run_id}", flush=True)
        shutil.rmtree(run_dir)

    raw_dir.mkdir(parents=True, exist_ok=True)
    result_dir.mkdir(parents=True, exist_ok=True)
    
    try:
        # 1. Copy existing ETL modules into isolated workspace.
        shutil.copytree(ETL_DIR, workspace / "etl")

        # 2. Copy existing accepted datasets as baseline/reference data.
        baseline_file = PROCESSED_DIR / f"{dataset}.csv"

        if not baseline_file.exists():
            raise FileNotFoundError(
                f"Baseline dataset not found: {baseline_file}"
            )

        baseline = pd.read_csv(baseline_file, dtype="string")
        incoming = pd.read_csv(upload_file, dtype="string")

        if list(baseline.columns) != list(incoming.columns):
            raise ValueError(
                "Uploaded CSV headers do not match the existing dataset."
            )

        # All other accepted datasets are needed for foreign-key validation.
        for file in PROCESSED_DIR.glob("*.csv"):
            shutil.copy2(file, raw_dir / file.name)

        # 3. Append new records to the baseline dataset.
        combined = pd.concat(
            [baseline, incoming],
            ignore_index=True
        )

        combined.to_csv(
            raw_dir / f"{dataset}.csv",
            index=False
        )

        # 4. Run the existing ETL modules in the isolated workspace.
        for module in [
            "etl.profile_data",
            "etl.standardize_data",
            "etl.clean_data"
        ]:

            print(f"Running {module}...", flush=True)

            subprocess.run(
                [sys.executable, "-m", module],
                cwd=workspace,
                check=True,
                capture_output=False
            )

        # 5. Read the processed results.
        processed_file = (
            workspace / "data" / "processed" / f"{dataset}.csv"
        )

        rejected_file = (
            workspace / "data" / "rejected" / f"{dataset}.csv"
        )

        duplicates_file = (
            workspace / "data" / "duplicates" / f"{dataset}.csv"
        )

        accepted_all = pd.read_csv(
            processed_file, dtype="string"
        )

        rejected_all = pd.read_csv(
            rejected_file, dtype="string"
        )

        duplicates_all = pd.read_csv(
            duplicates_file, dtype="string"
        )

        # 6. Identify records belonging to this upload.
        keys = PRIMARY_KEYS[dataset]

        if not set(keys).issubset(incoming.columns):
            raise ValueError(
                f"Missing primary key columns: {keys}"
            )

        def make_key_set(df):
            return set(
                df[keys].fillna("").astype(str).itertuples(
                    index=False, name=None
                )
            )

        incoming_keys = make_key_set(incoming)
        baseline_keys = make_key_set(baseline)

        new_keys = incoming_keys - baseline_keys

        def select_upload_rows(df):
            if df.empty or not set(keys).issubset(df.columns):
                return df.iloc[0:0].copy()

            row_keys = make_key_set(df)
            matching_keys = incoming_keys.intersection(row_keys)

            return df[
                df[keys].fillna("").astype(str).apply(
                    lambda row: tuple(row) in matching_keys,
                    axis=1
                )
            ].copy()

        accepted_new = accepted_all[
            accepted_all[keys].fillna("").astype(str).apply(
                lambda row: tuple(row) in new_keys,
                axis=1
            )
        ].copy()

        rejected_upload = select_upload_rows(rejected_all)
        duplicates_upload = select_upload_rows(duplicates_all)

        # 7. Save upload-specific outputs.
        accepted_new.to_csv(
            result_dir / "accepted.csv", index=False
        )

        rejected_upload.to_csv(
            result_dir / "rejected.csv", index=False
        )

        duplicates_upload.to_csv(
            result_dir / "duplicates.csv", index=False
        )

        summary = {
            "upload_id": run_id,
            "dataset": dataset,
            "input_records": len(incoming),
            "baseline_records": len(baseline),
            "accepted_records": len(accepted_new),
            "rejected_records": len(rejected_upload),
            "duplicate_records": len(duplicates_upload),
            "results_directory": str(result_dir),
            "status": "COMPLETED"
        }

        with open(result_dir / "summary.json", "w") as output:
            json.dump(summary, output, indent=4)

        print(json.dumps(summary, indent=4), flush=True)

        return summary

    except Exception:
        print("UPLOAD PROCESSING FAILED", flush=True)
        raise


if __name__ == "__main__":

    parser = argparse.ArgumentParser()

    parser.add_argument("--file", required=True)
    parser.add_argument("--dataset", required=True)
    parser.add_argument("--upload-id", required=False)

    args = parser.parse_args()

    result = run_upload(
        args.file,
        args.dataset,
        args.upload_id
    )
