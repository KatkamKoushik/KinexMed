import pytest
from sqlalchemy import create_engine, inspect
from backend.app.core.database import run_migrations, Base

def test_alembic_migrations_execution(tmp_path):
    # Test running migrations against a clean temporary SQLite database
    db_file = tmp_path / "test_migration.db"
    test_db_url = f"sqlite:///{db_file.as_posix()}"
    test_engine = create_engine(test_db_url)

    # Apply migrations
    run_migrations(target_engine=test_engine)

    inspector = inspect(test_engine)
    tables = inspector.get_table_names()

    # Verify all production schema tables exist
    expected_tables = {
        "sessions",
        "session_reps",
        "session_evidence_events",
        "exercise_plans",
        "plan_exercises",
        "session_feedback",
        "alembic_version"
    }
    for expected in expected_tables:
        assert expected in tables, f"Expected table {expected} was not created by Alembic migration"

    # Verify sessions table indices
    indexes = {idx["name"] for idx in inspector.get_indexes("sessions")}
    assert "ix_sessions_device_started" in indexes
    assert "ix_sessions_exercise_created" in indexes

    # Verify idempotency: running migrations again on the same DB succeeds with no errors
    run_migrations(target_engine=test_engine)

def test_alembic_migrations_stamping_legacy_db(tmp_path):
    # Test legacy DB with pre-existing tables but no alembic_version table
    db_file = tmp_path / "legacy.db"
    test_db_url = f"sqlite:///{db_file.as_posix()}"
    test_engine = create_engine(test_db_url)

    # Create tables directly using metadata (simulating pre-alembic state)
    Base.metadata.create_all(bind=test_engine)

    inspector_before = inspect(test_engine)
    assert "sessions" in inspector_before.get_table_names()
    assert "alembic_version" not in inspector_before.get_table_names()

    # run_migrations should detect pre-existing schema and stamp head without collision
    run_migrations(target_engine=test_engine)

    inspector_after = inspect(test_engine)
    assert "alembic_version" in inspector_after.get_table_names()

