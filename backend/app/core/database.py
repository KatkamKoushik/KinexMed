import os
from sqlalchemy import create_engine, event
from sqlalchemy.orm import declarative_base, sessionmaker

PROJECT_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
DEFAULT_DB_PATH = os.path.join(PROJECT_ROOT, "kinexmed.db").replace("\\", "/")

# Configurable database URL: Defaults to PostgreSQL if configured, or SQLite for local dev
DATABASE_URL = os.getenv(
    "DATABASE_URL",
    f"sqlite:///{DEFAULT_DB_PATH}"  # Fallback to local SQLite if PostgreSQL is not active
)

# Connect args for SQLite if used
connect_args = {"check_same_thread": False} if DATABASE_URL.startswith("sqlite") else {}

engine = create_engine(
    DATABASE_URL,
    connect_args=connect_args,
    echo=False
)

# Checklist 29-B002: Enforce SQLite WAL mode and foreign key integrity
if DATABASE_URL.startswith("sqlite"):
    @event.listens_for(engine, "connect")
    def set_sqlite_pragma(dbapi_connection, connection_record):
        cursor = dbapi_connection.cursor()
        cursor.execute("PRAGMA journal_mode=WAL")
        cursor.execute("PRAGMA foreign_keys=ON")
        cursor.close()

SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)

Base = declarative_base()

def run_migrations(target_engine=None):
    """
    Applies version-controlled Alembic migrations to align database schema to head.
    Stamps legacy unversioned databases cleanly to prevent table re-creation collisions.
    """
    from alembic.config import Config
    from alembic import command
    from sqlalchemy import inspect

    db_engine = target_engine or engine
    ini_path = os.path.join(PROJECT_ROOT, "backend", "alembic.ini")
    if not os.path.exists(ini_path):
        ini_path = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", "alembic.ini"))

    alembic_cfg = Config(ini_path)
    alembic_cfg.set_main_option("sqlalchemy.url", str(db_engine.url))

    with db_engine.connect() as connection:
        inspector = inspect(connection)
        tables = set(inspector.get_table_names())

    if "sessions" in tables and "alembic_version" not in tables:
        command.stamp(alembic_cfg, "head")
    else:
        command.upgrade(alembic_cfg, "head")

def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()
