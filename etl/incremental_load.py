
import os
import uuid
from datetime import datetime, timezone

import snowflake.connector
from dotenv import load_dotenv

load_dotenv()

# Snowflake configuration
conn = snowflake.connector.connect(
    account=os.getenv("SNOWFLAKE_ACCOUNT"),
    user=os.getenv("SNOWFLAKE_USER"),
    password=os.getenv("SNOWFLAKE_PASSWORD"),
    role=os.getenv("SNOWFLAKE_ROLE", "SYSADMIN"),
    warehouse="SRIRAM_CAP39_WH",
    database="SRIRAM_CAP39_DB",
    schema="SRIRAM_RAW"
)

DATASETS = {
    "batch_relationships.csv": "SRIRAM_BATCH_RELATIONSHIPS",
    "defects.csv": "SRIRAM_DEFECTS",
    "machines.csv": "SRIRAM_MACHINES",
    "material_lots.csv": "SRIRAM_MATERIAL_LOTS",
    "operators.csv": "SRIRAM_OPERATORS",
    "process_parameters.csv": "SRIRAM_PROCESS_PARAMETERS",
    "production_batches.csv": "SRIRAM_PRODUCTION_BATCHES",
    "production_events.csv": "SRIRAM_PRODUCTION_EVENTS",
    "production_lines.csv": "SRIRAM_PRODUCTION_LINES",
    "products.csv": "SRIRAM_PRODUCTS",
    "rework_records.csv": "SRIRAM_REWORK_RECORDS",
    "shift_schedule.csv": "SRIRAM_SHIFT_SCHEDULE",
    "suppliers.csv": "SRIRAM_SUPPLIERS"
}

STAGE = "@SRIRAM_CAP39_DB.SRIRAM_RAW.SRIRAM_CAP39_STAGE"
FILE_FORMAT = "SRIRAM_CAP39_DB.SRIRAM_RAW.SRIRAM_CAP39_CSV_HEADER_FF"

RUN_ID = str(uuid.uuid4())
cursor = conn.cursor()


def initialize_checkpoints():
    """Record the existing RAW tables as the initial baseline."""

    for filename, table_name in DATASETS.items():

        cursor.execute(
            f"SELECT COUNT(*) FROM SRIRAM_CAP39_DB.SRIRAM_RAW.{table_name}"
        )
        existing_rows = cursor.fetchone()[0]

        cursor.execute("""
            SELECT COUNT(*)
            FROM SRIRAM_CAP39_DB.SRIRAM_STAGING.SRIRAM_LOAD_CHECKPOINTS
            WHERE TABLE_NAME = %s
        """, (table_name,))

        checkpoint_exists = cursor.fetchone()[0]

        if checkpoint_exists == 0:
            cursor.execute("""
                INSERT INTO SRIRAM_CAP39_DB.SRIRAM_STAGING.SRIRAM_LOAD_CHECKPOINTS
                (
                    TABLE_NAME,
                    LAST_LOADED_AT,
                    LAST_FILE_NAME,
                    LAST_SUCCESSFUL_RUN,
                    ROWS_LOADED,
                    LOAD_STATUS,
                    UPDATED_AT
                )
                VALUES (
                    %s,
                    CURRENT_TIMESTAMP(),
                    %s,
                    'INITIAL_BASELINE',
                    %s,
                    'BASELINE_LOADED',
                    CURRENT_TIMESTAMP()
                )
            """, (table_name, filename, existing_rows))

            print(f"Baseline created: {table_name} ({existing_rows} rows)")


def load_dataset(filename, table_name):
    """Load a new staged CSV into its corresponding RAW table."""

    started_at = datetime.now(timezone.utc).replace(tzinfo=None)

    try:
        copy_sql = f"""
            COPY INTO SRIRAM_CAP39_DB.SRIRAM_RAW.{table_name}
            FROM {STAGE}
            FILES = ('{filename}')
            FILE_FORMAT = (FORMAT_NAME = '{FILE_FORMAT}')
            MATCH_BY_COLUMN_NAME = CASE_INSENSITIVE
            ON_ERROR = ABORT_STATEMENT
            FORCE = FALSE
        """

        cursor.execute(copy_sql)
        results = cursor.fetchall()

        rows_loaded = 0
        rows_read = 0
        status = "SKIPPED"
        error_message = None

        for result in results:
            # COPY INTO result:
            # file, status, rows_parsed, rows_loaded, errors_seen, ...
            file_status = result[1]
            rows_read += result[2] or 0
            rows_loaded += result[3] or 0

            if file_status == "LOADED":
                status = "SUCCESS"
            elif file_status == "LOAD_SKIPPED":
                status = "SKIPPED"
            else:
                status = file_status

        completed_at = datetime.now(timezone.utc).replace(tzinfo=None)

        cursor.execute("""
            INSERT INTO SRIRAM_CAP39_DB.SRIRAM_STAGING.SRIRAM_LOAD_AUDIT
            (
                RUN_ID, TABLE_NAME, FILE_NAME,
                STARTED_AT, COMPLETED_AT,
                ROWS_READ, ROWS_LOADED, ROWS_REJECTED,
                LOAD_STATUS, ERROR_MESSAGE
            )
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
        """, (
            RUN_ID,
            table_name,
            filename,
            started_at,
            completed_at,
            rows_read,
            rows_loaded,
            0,
            status,
            error_message
        ))

        # Update checkpoint only when new rows were successfully loaded.
        if status == "SUCCESS" and rows_loaded > 0:
            cursor.execute("""
                UPDATE SRIRAM_CAP39_DB.SRIRAM_STAGING.SRIRAM_LOAD_CHECKPOINTS
                SET
                    LAST_LOADED_AT = CURRENT_TIMESTAMP(),
                    LAST_FILE_NAME = %s,
                    LAST_SUCCESSFUL_RUN = %s,
                    ROWS_LOADED = %s,
                    LOAD_STATUS = 'SUCCESS',
                    UPDATED_AT = CURRENT_TIMESTAMP()
                WHERE TABLE_NAME = %s
            """, (filename, RUN_ID, rows_loaded, table_name))

        print(
            f"{table_name}: {status} | "
            f"Rows read: {rows_read} | Rows loaded: {rows_loaded}"
        )

    except Exception as error:
        completed_at = datetime.now(timezone.utc).replace(tzinfo=None)
        error_message = str(error)[:5000]

        cursor.execute("""
            INSERT INTO SRIRAM_CAP39_DB.SRIRAM_STAGING.SRIRAM_LOAD_AUDIT
            (
                RUN_ID, TABLE_NAME, FILE_NAME,
                STARTED_AT, COMPLETED_AT,
                ROWS_READ, ROWS_LOADED, ROWS_REJECTED,
                LOAD_STATUS, ERROR_MESSAGE
            )
            VALUES (%s, %s, %s, %s, %s, 0, 0, 0, 'FAILED', %s)
        """, (
            RUN_ID,
            table_name,
            filename,
            started_at,
            completed_at,
            error_message
        ))

        cursor.execute("""
            UPDATE SRIRAM_CAP39_DB.SRIRAM_STAGING.SRIRAM_LOAD_CHECKPOINTS
            SET
                LOAD_STATUS = 'FAILED',
                UPDATED_AT = CURRENT_TIMESTAMP()
            WHERE TABLE_NAME = %s
        """, (table_name,))

        print(f"{table_name}: FAILED | {error_message}")


try:
    print("\n========== INCREMENTAL LOAD STARTED ==========")
    print("Run ID:", RUN_ID)

    initialize_checkpoints()

    for filename, table_name in DATASETS.items():
        load_dataset(filename, table_name)

    print("\n========== INCREMENTAL LOAD COMPLETED ==========")

finally:
    cursor.close()
    conn.close()
