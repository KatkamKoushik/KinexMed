import pytest
from sqlalchemy import text
from backend.app.core.database import engine, get_db
from backend.app.schemas.session import SessionCreate, RepCreate

def test_sqlite_wal_and_foreign_keys_pragmas():
    """Checklist 29-B002: Verify SQLite WAL journal mode and foreign key enforcement."""
    with engine.connect() as conn:
        # Check foreign keys
        fk_result = conn.execute(text("PRAGMA foreign_keys;")).scalar()
        assert fk_result == 1, "Foreign keys pragma must be active (1)"

        # Check journal mode (WAL or memory for test environments)
        jm_result = conn.execute(text("PRAGMA journal_mode;")).scalar()
        assert jm_result.upper() in ["WAL", "MEMORY"], f"Journal mode should be WAL or MEMORY, got {jm_result}"

def test_bounded_payload_reps_validation():
    """Checklist 28-B002: Verify that unbounded payloads exceeding 500 reps are rejected."""
    excessive_reps = [
        RepCreate(
            rep_number=i,
            is_valid=True,
            start_timestamp_ms=1000 + i * 100,
            peak_timestamp_ms=1050 + i * 100,
            end_timestamp_ms=1100 + i * 100,
            duration_ms=100,
            peak_knee_angle=90.0,
            start_knee_angle=160.0,
            end_knee_angle=160.0,
        )
        for i in range(1, 502)
    ]

    with pytest.raises(Exception):
        SessionCreate(
            started_at=1000,
            completed_at=60000,
            duration_seconds=59.0,
            total_reps=501,
            valid_reps=501,
            reps=excessive_reps
        )

