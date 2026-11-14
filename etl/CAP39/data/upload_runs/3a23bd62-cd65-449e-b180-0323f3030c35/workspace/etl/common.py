from pathlib import Path
import pandas as pd

# Project directory
PROJECT_ROOT = Path(__file__).resolve().parents[1]

# Input and output directories
RAW_DIR = PROJECT_ROOT / "data" / "raw"
STANDARDIZED_DIR = PROJECT_ROOT / "data" / "standardized"
PROCESSED_DIR = PROJECT_ROOT / "data" / "processed"
REJECTED_DIR = PROJECT_ROOT / "data" / "rejected"
DUPLICATES_DIR = PROJECT_ROOT / "data" / "duplicates"
REPORTS_DIR = PROJECT_ROOT / "reports"

# Create output directories automatically
for directory in [
    STANDARDIZED_DIR,
    PROCESSED_DIR,
    REJECTED_DIR,
    DUPLICATES_DIR,
    REPORTS_DIR
]:
    directory.mkdir(parents=True, exist_ok=True)


def load_csv(path):
    return pd.read_csv(path, dtype="string")


def save_csv(df, path):
    df.to_csv(path, index=False)


def get_source_files():
    return sorted(RAW_DIR.glob("*.csv"))