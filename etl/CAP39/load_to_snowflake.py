
import os
import snowflake.connector
from dotenv import load_dotenv

load_dotenv()

DATABASE = "SRIRAM_CAP39_DB"
SCHEMA = "SRIRAM_RAW"
WAREHOUSE = "SRIRAM_CAP39_WH"
STAGE = "SRIRAM_CAP39_STAGE"
FILE_FORMAT = "SRIRAM_CAP39_CSV_HEADER_FF"

# Exact filenames from data/processed/
FILES = [
    "batch_relationships.csv",
    "defects.csv",
    "machines.csv",
    "material_lots.csv",
    "operators.csv",
    "process_parameters.csv",
    "production_batches.csv",
    "production_events.csv",
    "production_lines.csv",
    "products.csv",
    "rework_records.csv",
    "shift_schedule.csv",
    "suppliers.csv"
]


def get_connection():
    return snowflake.connector.connect(
        account=os.getenv("SNOWFLAKE_ACCOUNT"),
        user=os.getenv("SNOWFLAKE_USER"),
        password=os.getenv("SNOWFLAKE_PASSWORD"),
        warehouse=WAREHOUSE,
        database=DATABASE,
        schema=SCHEMA,
        role=os.getenv("SNOWFLAKE_ROLE", "SYSADMIN")
    )


def load_file(cursor, filename):
    table_name = "SRIRAM_" + filename.replace(".csv", "").upper()
    stage_path = f"@{DATABASE}.{SCHEMA}.{STAGE}"
    format_path = f"{DATABASE}.{SCHEMA}.{FILE_FORMAT}"

    print(f"\nProcessing: {filename}")
    print(f"Target table: {table_name}")

    # Create a table using the CSV header and inferred data types
    create_sql = f"""
        CREATE TABLE IF NOT EXISTS {DATABASE}.{SCHEMA}.{table_name}
        USING TEMPLATE (
            SELECT ARRAY_AGG(OBJECT_CONSTRUCT(*))
                   WITHIN GROUP (ORDER BY ORDER_ID)
            FROM TABLE(
                INFER_SCHEMA(
                    LOCATION => '{stage_path}',
                    FILE_FORMAT => '{format_path}',
                    FILES => ('{filename}'),
                    IGNORE_CASE => TRUE
                )
            )
        )
    """

    cursor.execute(create_sql)

    # Load the staged CSV into the matching table columns
    copy_sql = f"""
        COPY INTO {DATABASE}.{SCHEMA}.{table_name}
        FROM {stage_path}
        FILES = ('{filename}')
        FILE_FORMAT = (FORMAT_NAME = '{format_path}')
        MATCH_BY_COLUMN_NAME = CASE_INSENSITIVE
        ON_ERROR = 'ABORT_STATEMENT'
    """

    cursor.execute(copy_sql)
    results = cursor.fetchall()

    for result in results:
        print(
            f"File: {result[0]}, "
            f"Status: {result[1]}, "
            f"Rows loaded: {result[3]}, "
            f"Errors: {result[4]}"
        )

    # Verify target table row count
    cursor.execute(
        f"SELECT COUNT(*) FROM {DATABASE}.{SCHEMA}.{table_name}"
    )
    row_count = cursor.fetchone()[0]

    print(f"Total rows in {table_name}: {row_count}")


def main():
    connection = get_connection()
    cursor = connection.cursor()

    successful = []
    failed = []

    try:
        for filename in FILES:
            try:
                load_file(cursor, filename)
                successful.append(filename)
            except Exception as error:
                print(f"FAILED: {filename}")
                print(error)
                failed.append(filename)

        print("\n========== LOAD SUMMARY ==========")
        print(f"Successful: {len(successful)}")
        print(f"Failed: {len(failed)}")

        if failed:
            print("Failed files:", failed)

    finally:
        cursor.close()
        connection.close()


if __name__ == "__main__":
    main()
