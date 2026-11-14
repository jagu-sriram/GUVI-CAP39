import subprocess
import sys

steps = [
    "etl.profile_data",
    "etl.standardize_data",
    "etl.clean_data"
]

for step in steps:ls -lh etl/

    print("\n===================================")
    print("Running:", step)
    print("===================================\n")

    subprocess.run(
        [sys.executable, "-m", step],
        check=True
    )

print("\nCAP-39 PIPELINE EXECUTED SUCCESSFULLY!")